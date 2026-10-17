// Shared helpers for the render scripts: a tiny static server and a page loader.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const config = () => JSON.parse(fs.readFileSync(path.join(ROOT, 'config.json'), 'utf8'));

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.ttf': 'font/ttf', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.png': 'image/png' };

export function serve(port = 0) {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      const url = decodeURIComponent(new URL(req.url, 'http://x').pathname);
      let file = path.normalize(path.join(ROOT, url === '/' ? '/src/index.html' : url));
      if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end('not found'); }
      res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
      fs.createReadStream(file).pipe(res);
    });
    srv.listen(port, '127.0.0.1', () => resolve({ srv, url: `http://127.0.0.1:${srv.address().port}` }));
  });
}

export async function openComposition(format, { browser } = {}) {
  const cfg = config();
  const { width, height } = cfg.formats[format];
  const { srv, url } = await serve();
  const own = !browser;
  browser = browser || await chromium.launch({ args: ['--disable-gpu', '--font-render-hinting=none', '--disable-lcd-text'] });
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.error('[page]', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('[console]', m.text()); });
  await page.goto(`${url}/src/index.html?format=${format}`);
  await page.waitForFunction(() => window.__ready || window.__error, null, { timeout: 60000 });
  const err = await page.evaluate(() => window.__error);
  if (err) throw new Error(err);
  const comp = await page.evaluate(() => ({ W: window.__comp.W, H: window.__comp.H, fps: window.__comp.fps, duration: window.__comp.duration }));
  const cdp = await page.context().newCDPSession(page);
  return {
    cfg, comp, page, browser,
    async frame(t, format = 'png') {
      await page.evaluate((tt) => window.renderAt(tt), t);
      const { data } = await cdp.send('Page.captureScreenshot', { format, ...(format === 'jpeg' ? { quality: 95 } : {}), optimizeForSpeed: true, captureBeyondViewport: false });
      return Buffer.from(data, 'base64');
    },
    async close() { if (own) await browser.close(); else await page.close().catch(() => {}); srv.close(); },
  };
}

export function arg(name, def) {
  const i = process.argv.indexOf(`--${name}`);
  if (i < 0) return def;
  const v = process.argv[i + 1];
  return v === undefined || v.startsWith('--') ? true : v;
}
