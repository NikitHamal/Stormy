# Mercury 3 (Inception) — how NEB calls it (research, 2026-10-22)

Source: NikitHamal/NEB `backend_python/api/inception_proxy.py`, commit 7323fc4e (2026-10-19).

## Keyless web recipe
1. `GET https://chat.inceptionlabs.ai/api/session` -> JSON `{token}` (guest). NEB caches ~1 h (its guess), refresh <60 s left, 403 on chat clears it.
2. `POST https://chat.inceptionlabs.ai/api/chat` with headers `x-session-token: <token>` and `cookie: session=<token>`, plus Chrome-like headers (origin/referer chat.inceptionlabs.ai, sec-fetch-*: empty/cors/same-origin, desktop Chrome UA).
   Body (Vercel AI SDK v5 useChat shape), no model/temperature/tools:
   `{"reasoningEffort":"high","webSearchEnabled":false,"voiceMode":false,"timezone":"UTC","id":"<16 hex>","messages":[{"parts":[{"type":"text","text":"..."}],"id":"<16 hex>","role":"system|user|assistant"}],"trigger":"submit-message"}`
3. Response: SSE `data: {json}` events split by blank lines. `{"type":"text-delta","delta":"..."}` -> APPEND. Ends at `data: [DONE]` or EOF. (AI SDK v5 also has `reasoning-delta`, `error` with `errorText`, unverified.)
- Reasoning efforts: high | medium | low | instant (NEB sends high).
- No native tools on the keyless endpoint -> text tool protocol.
- Bot check: Vercel Security Checkpoint (HTTP 429 + `x-vercel-mitigated: challenge`, HTML body) from a datacenter IP even with Chrome TLS impersonation. Phone IPs untested. Robust fallback: hidden WebView on https://chat.inceptionlabs.ai/ (runs the JS check), then fetch('/api/session') + fetch('/api/chat') inside the page, chunks back via @JavascriptInterface.

## Official API (needs key)
`POST https://api.inceptionlabs.ai/v1/chat/completions`, OpenAI-compatible, `model: mercury-3` (1M ctx, 131k out, tools/json/structured), `reasoning_effort` instant|low|medium|high (default low). `/v1/models` is public. `"diffusing": true` streams full-text replacements.
Other ids: mercury-2.5 (260k), mercury-2 (128k), mercury-edit-2 (FIM).

## Ptero
ptero.pro serves `mercury-2.5:free`, `mercury-2:free` (non-streaming, X-MLP-Guest-Token, 30 rpm).
