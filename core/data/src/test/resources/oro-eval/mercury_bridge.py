"""The live eval's stand-in for Oro's in-app Mercury WebView (MercuryWebBridge):
headless Chromium keeps https://chat.inceptionlabs.ai/ open (passing Vercel's
check like any browser) and runs the bridge's same-origin requests in the page.

Protocol on stdin/stdout, one JSON object per line:
  in : {"id": "...", "body": "<request JSON as a string>"}
  out: {"id": "...", "t": "line", "v": "<one response line>"} ... then {"id": "...", "t": "end"}
The first line of every reply is "HTTP <code> <content-type>", as from the app's bridge.
"""
import asyncio, json, sys

SITE = "https://chat.inceptionlabs.ai/"
SCRIPT = r"""
async ([body]) => {
  const out = [];
  let tok = window.__oroMercuryToken;
  const mint = async () => {
    const s = await fetch('/api/session', { credentials: 'include', headers: { 'accept': '*/*' } });
    if (!s.ok) throw new Error('could not start a guest session (HTTP ' + s.status + ')');
    const j = await s.json();
    tok = j.token; window.__oroMercuryToken = tok; window.__oroMercuryExpires = Date.now() + 50 * 60 * 1000;
  };
  if (!tok || Date.now() > (window.__oroMercuryExpires || 0)) await mint();
  const post = () => {
    document.cookie = 'session=' + tok + '; path=/; secure; samesite=lax';
    return fetch('/api/chat', { method: 'POST', credentials: 'include',
      headers: { 'content-type': 'application/json', 'accept': 'text/event-stream, */*', 'x-session-token': tok }, body });
  };
  let r = await post();
  if (r.status === 403) { await mint(); r = await post(); }
  window.__oroReader = { reader: r.body.getReader(), dec: new TextDecoder(), buf: '' };
  return 'HTTP ' + r.status + ' ' + (r.headers.get('content-type') || '');
}
"""
# Lines are pulled in batches so the stream is relayed as it arrives.
PULL = r"""
async () => {
  const s = window.__oroReader; const lines = [];
  for (;;) {
    const { done, value } = await s.reader.read();
    if (done) { s.buf += s.dec.decode(); if (s.buf) lines.push(s.buf); s.buf = ''; return { done: true, lines }; }
    s.buf += s.dec.decode(value, { stream: true });
    let i; while ((i = s.buf.indexOf('\n')) >= 0) { lines.push(s.buf.slice(0, i)); s.buf = s.buf.slice(i + 1); }
    if (lines.length) return { done: false, lines };
  }
}
"""

def emit(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()

async def main():
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, args=["--disable-blink-features=AutomationControlled"])
        ctx = await browser.new_context(
            user_agent="Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
            viewport={"width": 412, "height": 915}, locale="en-US")
        page = await ctx.new_page()

        async def ensure_page():
            if "chat.inceptionlabs.ai" not in page.url or "Checkpoint" in (await page.title()):
                await page.goto(SITE, wait_until="domcontentloaded", timeout=60000)
                for _ in range(30):
                    if "Checkpoint" not in (await page.title()):
                        break
                    await asyncio.sleep(2)

        loop = asyncio.get_running_loop()
        # Requests carry the whole conversation: far past asyncio's 64 KB default line limit.
        reader = asyncio.StreamReader(limit=256 * 1024 * 1024)
        await loop.connect_read_pipe(lambda: asyncio.StreamReaderProtocol(reader), sys.stdin)
        while True:
            raw = await reader.readline()
            if not raw:
                break
            try:
                req = json.loads(raw)
            except Exception:
                continue
            rid = req.get("id", "")
            try:
                await ensure_page()
                head = await page.evaluate(SCRIPT, [req["body"]])
                emit({"id": rid, "t": "line", "v": head})
                while True:
                    chunk = await page.evaluate(PULL)
                    for line in chunk["lines"]:
                        emit({"id": rid, "t": "line", "v": line})
                    if chunk["done"]:
                        break
                if head.startswith("HTTP 429") and "html" in head:
                    await page.goto(SITE, wait_until="domcontentloaded", timeout=60000)
            except Exception as e:
                emit({"id": rid, "t": "line", "v": "HTTP 0 error"})
                emit({"id": rid, "t": "line", "v": str(e)[:300]})
            emit({"id": rid, "t": "end"})
        await browser.close()

asyncio.run(main())
