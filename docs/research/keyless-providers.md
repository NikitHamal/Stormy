# Keyless model endpoints — research for Oro (2026-10-22, fork F2)

Sources: g4f (xtekky/gpt4free @ 54925a9, providers with `working=True`, `needs_auth=False`),
the user's NEB repo (`backend_python/api/*_proxy.py`), live probes from this sandbox.

Sandbox caveat: egress is a Google Cloud datacenter IP behind a TLS-intercepting proxy. Sites with
bot checks (Vercel, Cloudflare, Alibaba WAF) challenge it even when a phone would pass, and some
certificate chains fail through the proxy. "Blocked here" is not "broken on a phone".

## Implemented in Oro (branch ci/providers)

| Provider | Endpoint | Auth | Stream | Tools | Verified |
|---|---|---|---|---|---|
| **UniKey (community)** `UNIKEY` | `POST https://www.getunikey.ai/api/anonymous/chat/completions` | none; header `X-Anonymous-Fingerprint: rnd<32 hex>` fresh per request (trial = 3 msgs per id) | OpenAI SSE (`reasoning_content` for thinking models) | **No** (400 "anonymous chat does not support tools"), no images | Live, 2026-10-22: 40k-token prompt OK, back-to-back requests OK |
| **Mercury (community)** `MERCURY` | `GET https://chat.inceptionlabs.ai/api/session` → token; `POST /api/chat` (`x-session-token` + `cookie: session=`) | guest token | Vercel AI SDK v5 SSE (`text-delta` appended) | No | Recipe from NEB (7323fc4e). Here: Vercel checkpoint (429 + `x-vercel-mitigated: challenge`); headless Chromium also failed ("Code 21"). Oro falls back to a hidden WebView on the site. **Unverified on a phone.** |
| **Inception (official)** `INCEPTION` | `https://api.inceptionlabs.ai/v1` (OpenAI-compatible) | key (free tier: 1,000 req/min) — platform.inceptionlabs.ai/dashboard/api-keys | OpenAI SSE | Yes | `/v1/models` public: mercury-3 (1M), mercury-2.5 (260k), mercury-2 (128k), mercury-decide (not chat) |

UniKey: which model actually answers (stream `model` field), 2026-10-22:

| Requested id | Served by | Seeded as |
|---|---|---|
| claude-opus-4-8 | claude-opus-4-8 | Claude Opus 4.8 (UniKey) |
| claude-opus-4-7 | claude-opus-4-7 | Claude Opus 4.7 (UniKey) |
| gpt-5.5 | gpt-5.6-terra | GPT-5.6 Terra (UniKey) |
| x-ai/grok-4.3 | x-ai/grok-4.3 (reasoning) | Grok 4.3 (UniKey) |
| deepseek/deepseek-v4-pro | deepseek-v4-pro (~12 s) | DeepSeek V4 Pro (UniKey) |
| deepseek/deepseek-v4-flash | deepseek-v4-flash (reasoning) | DeepSeek V4 Flash (UniKey) |
| minimax/minimax-m3 | minimax-m3 (~1.7 s) | MiniMax M3 (UniKey) |
| moonshotai/kimi-k2.7-code | kimi-k2.7-code (reasoning) | Kimi K2.7 Code (UniKey) |
| moonshotai/kimi-k3 | kimi-k2.7-code | not seeded (mislabelled) |
| z-ai/glm-5.2 | 503 "No available channel" | not seeded |
| google/gemini-3.1-pro-preview | 403 provider ToS | not seeded |
| google/gemini-3.5-flash | gemini-3.1-pro-preview | not seeded (label unclear) |

## Works, not added (and why)

- **TryingOpen** (`POST https://www.tryingopen.com/api/open`, AI SDK v5 body `{id, trigger:"submit-message", model, effort: quick|balanced|deep, messages[{id, role, parts}]}`, no auth). Fast (~1 s) and strong
  zero-retention models answer: qwen/qwen3.8-27b, deepseek/deepseek-v4.1-flash-1017, deepseek/deepseek-v4-flash-0731,
  nvidia/nemotron-3-ultra-550b-a55b, reflection/beam-1.0-501b-a23b, thinkingmachines/inkling, meta/muse-spark-open.
  Others answer "run out of API credit" / "paused" / "isn't on this page". **Blocker: a hard request cap of
  ~20–24k characters total** (HTTP 413 "There's too much text in this chat now") — smaller than Oro's agent
  system prompt plus any real context. Also the site gives the model its own server-side `createFile` tool, which
  it prefers over text tool calls. Good for short chat only; not worth a provider that fails in agent mode.
- **Poolside guest** (`chat.poolside.ai`: POST /api/chats → POST /api/chat (202, streamUrl) → GET stream, AI SDK
  text-delta). Laguna S/XS 2.1. Not added: the same Laguna model is already keyless via Kilo with native tools.
- **Motif** (`chat.motiftech.io/api/v1/enterprise/chat`, SSE `chat.text`, server-side conversation id): one
  lesser-known 102B model. Not probed further.

## Not usable keyless (from a phone, without a browser or a user's cookies)

- **QwenCloud try-ai** (Qwen3.8 Max, Qwen3 Coder Plus): every call needs an Alibaba `bx-umidtoken` risk token
  taken from a real browser session. Oro already reaches Qwen3.8 Max through chat.qwen.ai (Qwen web provider).
- **LongCat** (Meituan): requests need an H5guard `mtgsig` signature from the site's SDK (NEB runs it in a Node VM).
  Possible later through a WebView bridge like Qwen/Mercury.
- **Cloudflare playground** (g4f): driven through a real browser over CDP (Agents SDK / websockets).
- **chat-gpt-oss.com** (g4f ChatGptOss, gpt-oss-120b / gpt-5-nano, single prompt, no history): certificate chain
  failed through this sandbox's proxy; unverified. GPT-OSS is already keyless via other providers.
- **Opera Aria** (g4f): needs an anonymous OAuth signup with Opera's client secret — impersonating Opera's app.
- **ChatGPT anonymous** (g4f): sentinel / proof-of-work tokens, Cloudflare.
- **GLM (chat.z.ai)**: Aliyun captcha.
- **DeepAI**: fingerprint-derived "tryit" key with an anti-abuse flag; weak free models.
- Gemini web anonymous (NEB geminiweb): Flash-Lite only, batchexecute protocol with page-scraped tokens; deferred.

## Ideas for later
- A generic "site WebView bridge" (Qwen, Mercury, LongCat share the pattern: load the site, let its JS pass the
  check or sign requests, fetch same-origin, stream lines back over a JavascriptInterface).
- Re-check TryingOpen if its cap rises; its zero-retention models are excellent and very fast.
