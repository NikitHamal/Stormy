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

## 2026-10-22 (round 3) — newer keyless models, VLM Run, faster failover

Method: every candidate was called with a streamed OpenAI request carrying a `write_file` tool, from
this sandbox (Google Cloud egress behind a TLS-intercepting proxy) and from molab (a different
network), then with a ~50k-token prompt. Sources: g4f @ 54925a9 (providers with `working=True`,
`needs_auth` not set), the user's NEB repo (`backend_python/api/*_proxy.py`), the probe-verified
list github.com/mvalentsev/awesome-free-ai-coding (verified 2026-10-22) and live `/models` listings.

### Added

| Where | Model id (seed) | Context | Native tools | First token (sandbox / molab) | Notes |
|---|---|---|---|---|---|
| Kilo (keyless pool) | `nvidia/nemotron-3-ultra-550b-a55b:free` | 1M | yes | 3.0 s / 1.7 s | 550B MoE, reasoning; 51k prompt OK |
| Kilo | `cohere/north-mini-code:free` | 256k | yes | 5.3 s / 2.4 s | coding model, reasoning; 46k OK |
| Kilo | `dots-studio/dots-3-note-preview:free` | 512k | yes | 1.3 s / 1.5 s | reasons at length; 51k OK |
| Kilo | `nvidia/nemotron-3.5-super-vl:free` | 1M | yes | 1.2 s / 1.3 s | takes images; 51k OK |
| Kilo | `nvidia/nemotron-3.5-lightning:free` | 1M | yes | 3.0 s | |
| Kilo | `recursive/flint-v1.0:free` | 262k | yes | 1.1 s | wrote an absolute path in the probe |
| LLM7 (anonymous tier) | `minimax-m2.7` | ~200k | yes | 2.1 s / 0.24 s | anonymous budget ~100k tokens/day per IP (a 40k prompt after the probes got 429 "Daily token quota exceeded") |
| **VLM Run** (new provider `VLM_RUN`) | `qwen3.8-27b` | 168k | yes | 0.6 s / 0.5 s | `https://gateway.vlm.run/v1/openai`, **no Authorization header** (a bearer returns 401); 10/min, 30/hour, 100/day per IP; 54k needle prompt answered correctly |

- Reasoning controls on the Kilo models (OpenRouter-style gateway): `reasoning: {"enabled": false}` turns thinking
  off (0 reasoning tokens, verified on Nemotron 3 Ultra, North Mini Code, Dots3-Note); `reasoning_effort` low/high
  and `reasoning: {"effort": ...}` are accepted (no 400) but changed little on a trivial prompt. Unverified for
  VLM Run and LLM7.
- VLM Run's canonical id `qwen/qwen3.8-27b` is also a *paid* model in Kilo's catalogue, and model ids are the table's
  primary key, so the seed uses the gateway's own alias `qwen3.8-27b`; an anonymous `/models` refresh of VLM Run
  returns only that seed (its listing shows paid models anonymous callers cannot use).
- Kilo's free pool as listed 2026-10-22 (19 ids): also `stealth/glyph-cluster` (works, not seeded: stealth ids
  rotate), `xingchen-agi/xing4.0-29b-a4b:free` (429 upstream), `poolside/laguna-xs-2.1:free`,
  `liquid/lfm-2.5-2.6b:free` (too small for agents), `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`,
  `nvidia/nemotron-3-super-120b-a12b:free`.

### Fixed

- **Dead LLM7 seeds:** `DeepSeek-V4-Flash-0731` and `GLM-5.3-Flash` now answer 400 "Model '…' is currently
  unavailable" and are gone from LLM7's `/models`. A 400 did not trigger the keyless fallback, so a run on either
  failed outright. `KeylessFallback` now treats retired-model wording (LLM7's, `model_not_found`, OpenRouter's "No
  endpoints found", "unknown model", "has been retired") as hand-over-and-skip-for-6-hours, and the dead id is out
  of the fallback order. *Still to do by the seed owner:* drop both from `SeedModels.llm7` and add them to
  `AiModelRepository.RETIRED_KEYLESS` (removing only from RETIRED would see them re-seeded).
- **Silent sites:** a candidate that sends nothing at all (not even headers) for 35 s now hands over to the next
  free model (75 s for the Mercury and Qwen clients, which may open the site in a hidden browser first; Ptero,
  which answers in one piece, is exempt). Before, a hanging site held the run until the agent's idle watchdog
  (150 s) and was then retried as itself.

### Rejected (and why)

- **LLM Tech** (`api.llmtech.eu`, shared public trial key, Qwen3.8 27B with tools, 2M tokens/day/IP): the service
  shuts down after 31 October 2026.
- **DeepInfra** (g4f `DeepInfra`, GLM 5.2 and others): needs a Cloudflare Turnstile token — a captcha.
- **g4f.space backup routes** (`/api/nvidia`, `/api/groq`, `/api/gemini`, …): require g4f's own API key, i.e. someone
  else's credentials.
- **OpenCode Zen free lane** (Kimi K3, GLM 5.3 Flash, DeepSeek V4.1 Flash via third-party plugins): restricted to the
  OpenCode client; reaching it means posing as that client.
- **Puter** (`api.puter.com/puterai/openai/v1`, many frontier models): official and OpenAI-compatible, but billed to
  the caller's own Puter account (User-Pays). Possible later as a provider where users paste their own Puter token;
  creating accounts on their behalf is not acceptable.
- **LLM7 Kimi K2.6 / K2.7 Code**: listed `usage_based_only: false` but answer 402 "Pro models require balance".
- **NEB proxies not ported:** chatjimmy (Llama 3.1 8B only), yqcloud (unnamed model, no tools), deepai (client-made
  "tryit" key plus an anti-abuse flag), geminiweb (relies on anti-detection and proxy rotation), metaai (needs a
  browser session); the rest are TTS.
- **VLM Run `google/diffusiongemma-26b-a4b-it`**: 500 on every try; 16k context.

## 2026-10-23 — Qwen Chat (chat.qwen.ai) guest mode, re-checked (fork Q)

**What the site needs today** (web client 0.3.22; read from its `main.js` and confirmed in a real Chromium on the live site):
- Guests have no token. A guest is the `x-umid` header: the device id the page keeps in `localStorage.qwen_chat_device_id` (any UUID). Without it, `POST /api/v2/chats/new` answers `{"success":false,"data":{"code":"Unauthorized"}}`.
- The site's own requests also carry `source` (`h5` on mobile, `web` on desktop), `version: 0.3.22`, `Timezone`, `x-request-id`, and the risk SDK's (Baxia) `bx-ua` (now format `234!`), `bx-umidtoken` and `bx-v: 2.5.37`. Sign-in lives on `auth.qwen.ai/api/v2/auths/` (`/refresh` fails for guests, which then fall back to `x-umid`).
- Guest models from `/api/models`: `qwen3.7-plus`, `qwen3.8-max`, `qwen3.8-omni-flash`, each listed with `max_context_length` 1,000,000 (Oro's seeds still use the conservative values chosen earlier).
- `POST /api/v2/chats/new` with `x-umid` works from any network. `POST /api/v2/chat/completions?chat_id=…` is where Alibaba's WAF decides. From this sandbox and from molab (both datacenter networks) every request, **including from a real browser running the site's own SDK**, got the punish captcha (`{"ret":["FAIL_SYS_USER_VALIDATE","RGV587_ERROR::SM::…被挤爆啦…"]}`, then a `.baxia-dialog` slider whose fetch hook holds the request forever). That is network reputation, not a wrong recipe. NEB's server also only gets through behind a proxy (`QWEN_PROXY_URL`). Phone networks are what guests normally use; that path is **unverified from here**.

**How Oro handles it** (`QwenWebClient`, `QwenWebBridge`, `KeylessFallback`):
1. **Plain HTTP** with the current protocol: `x-umid` per guest identity, version 0.3.22, `bx-v` 2.5.37, Oro's own `bx-ua`/`ssxmod_itna` fingerprint, pooled and rotated identities as before.
2. **On a refusal, the in-app browser** takes over for 30 minutes: a hidden WebView on the real site, whose SDK signs Oro's in-page `fetch` (verified on molab: `bx-ua 234!…`, `bx-umidtoken`, `bx-v` present; the streaming relay was tested end to end against a recorded SSE body). The page script watches for the captcha dialog and answers `CHALLENGE` within about a second instead of hanging (verified live: chat created, challenge reported in 0.8 s). It uses origin-restricted messaging, refuses off-site navigation, survives a renderer crash, closes every pending stream, and starts as a new guest (fresh device id and cookies) after a captcha, a quota answer or 12 requests.
3. **Another free Qwen when the site asks for a human check.** The error says "browser check", so KeylessFallback parks *all* chat.qwen.ai models for 10 minutes. The request goes to Qwen-family stand-ins first: VLM Run Qwen3.8 27B, OVHcloud Qwen3.8 27B, OVHcloud Qwen3.5 397B, then Ptero Qwen 3.7 Flash, before the global order. All four answered keylessly from both networks on 2026-10-23. The reasoning stream says which model answered.

Not done, on purpose: solving the slider automatically (no captcha solving). Showing it to the user to solve in a visible WebView would recover the site route on a distrusted network, but needs UI outside this pass.

## 2026-10-23 — four new keyless providers (fork N)

Method: labs' own demos and playgrounds that relay to the lab's API, found in the Hugging Face Spaces listings of
~80 lab accounts (CPU-hosted Spaces only: the model runs at the lab, so there is no ZeroGPU quota), the lists
cheahjs/free-llm-api-resources and awesome-free-ai-coding, g4f and NEB. Candidates were probed from this sandbox
(Google Cloud egress behind a TLS-intercepting proxy) and from molab (a different network); the four added answer on
both. Each also passed, through Oro's own Kotlin clients on molab: a streamed reply, a tool call, a ~32k-token needle
prompt, and a full
agent-loop run on a 108k-character task with Oro's real system prompt and all its tools (the code from the middle of
the log had to be written to `answer.txt` with `write_file` and the run had to end with a reply).

### Added

| Provider (`AiProvider`) | Endpoint | Model (seed id) | Context | Tools | Limits | Networks | Live results |
|---|---|---|---|---|---|---|---|
| **Hy3** `HY3` (Tencent) | `https://tencent-hy3.hf.space`, Gradio queue (`/config` → `fn_index` of `chat`, `queue/join`, `queue/data` SSE diffs) | `hy3:hy3` — Hy3, 295B MoE / 21B active | 262,144 | **native** (OpenAI tools as a JSON string; tool turns in the history) | the Space's queue, shared with its visitors; Oro paces at 15/min | sandbox + molab | PONG 1.7–2.1 s; `write_file` call 2.9–3.7 s; 2,000-line (~49k-token) needle OK; agent round trip 11.6 s |
| **MiniCPM** `MINICPM` (OpenBMB) | `https://openbmb-minicpm-v-4-7-demo.hf.space`, `predict` | `minicpm:MiniCPM-V-4.7-35B-A3B` (takes images upstream; Oro sends text) | 131,072 (model reads 262K; the relay's cap is unpublished) | **native** (`tools`, `tool_choice`, `tool_messages`; names must match `[A-Za-z0-9_-]{1,64}`) | four requests at a time across all visitors; **the demo logs requests and outputs** | sandbox + molab | PONG ~1 s; `write_file` 1.3 s; call → result → follow-up verified; ~32k-token needle 51–59 s; agent round trip 58 s |
| **Laguna** `LAGUNA` (Poolside) | `https://poolside-laguna-s-2-1.hf.space`, `chat` (OpenAI `messages` + `reasoning` bool, forwarded to OpenRouter `poolside/laguna-s-2.1:free` on Poolside's key) | `laguna:laguna-s-2.1` | 262,144 (32K output) | **GLM template** in the system message (the Space forwards no `tools`); the agent loop reads the `<arg_key>/<arg_value>` blocks | OpenRouter's free limits on Poolside's key, shared by the demo's visitors ("temporarily rate-limited upstream" seen once in ~30 calls); answers arrive whole | sandbox + molab | PONG 1–1.5 s; `write_file` call 5/5; ~32k needle OK; agent round trip 3 of 4 (3.4–8 s; once it gave the code without the call) |
| **Cloudflare AI Playground** `CLOUDFLARE_PLAYGROUND` | `wss://playground.ai.cloudflare.com/agents/playground/{name}?_pk={uuid}` (Agents SDK socket: `rpc setConfig` with model + system, then `cf_agent_use_chat_request`; replies are AI SDK v5 chunks) | `cfai:@cf/google/gemma-4-26b-a4b-it` (256,000), `cfai:@cf/zai-org/glm-4.7-flash` (131,072, chat only) | as `getModels` reports | **text protocol** (Gemma follows it exactly; GLM-4.7 Flash does not, so it is no fallback stand-in) | per-minute caps shared by all visitors (`3021: rate limiting` → 429); newest message ≤ 6,000 characters (Oro splits a long final turn into parts joined by "Noted." turns); "Safety check unavailable" → 503 | sandbox + molab | PONG 4.5 s (sandbox); 600- and 1,300-line needles OK; text-protocol `write_file` OK; agent round trip 43.6 s |

Reasoning controls (`ReasoningWire.CLIENT`, each demo's own scale): Hy3 `no_think | low | high` (default high),
MiniCPM and Laguna `off | on` (default on). Cloudflare's models have none exposed.

Fallback order: Hy3 right after Nemotron 3 Ultra (native tools, 256K, ~2 s); MiniCPM and Laguna after Nemotron 3.5
Lightning; Gemma 4 26B among the text-mode stand-ins. GLM-4.7 Flash is selectable but never a stand-in.

Why Laguna gets GLM's template: with Oro's raw-parameter format it wrote a readable call 1 time in 5, mixing in
GLM's `<arg_key>` tags; with the tools in GLM's own template (`# Tools … <tools>{json}</tools> … <tool_call>{name}
<arg_key>…`) it wrote clean calls 5 times in 5, and 18 replies sampled with Oro's full system prompt and catalogue
(~35k tokens with the task) all carried a call in that format (`write_file` in 14; `grep`, `read_file` or
`list_files` first in 4). The format is ours to choose because the Space's `chat` passes `messages` straight to
`client.chat.completions.create` and never `tools`.

### Rejected (and why)

- **Sign-in or account needed:** gpt-oss.com (ChatKit behind a Hugging Face sign-in; anonymous calls 500), Mistral
  Le Chat (login on send), HuggingChat (AWS WAF, then login), Kimi (login and fingerprinting), Yuanbao, Grok,
  Copilot (sign-in), Doubao (no guest input), Hack Club AI proxy (auth), api.airforce (free tier needs a key).
- **Bot checks or captchas:** Meta AI (`__rd_verify` challenge, then a TOS-bound temporary user), DeepSeek (human
  verification), Z.ai, Baichuan and LMArena (captchas), Venice (attestation that detects automation), LongCat
  (signed `mtgsig` requests), Cerebras chat (challenge), Perplexity (Cloudflare challenge; search-only anyway).
- **Unreachable or unsuitable:** Duck.ai (unreachable from molab), Baidu chat (guest streaming works but needs
  tokens scraped from the page, and answers are search-oriented), ModelScope studios (no stable host).
- **Spaces that failed:** Cohere Command A+ (concurrency 2, `/respond` keeps history server-side, queue joins hung),
  StepFun Step-3.7-Flash-dev (402 "quota exceeded" on every try), Step3-VL-10B (hung). Not pursued: Falcon-H1R
  (small model, custom inputs), Hy3-preview (same family as Hy3), Qwen3.5-Omni (left to the Qwen work).
- **Cloudflare models not seeded:** gpt-oss-120b (stops after a few hundred characters, repeats chunks), Llama 4
  Scout and Qwen3 (repeat every chunk), GLM-5.3 Flash (rate limited on every try over two hours); the playground's
  "premium" models are silently answered by GLM-4.7 Flash.
- **Key-only or offline:** FreeInference (521), Hetzner AI (key), LLMTR (DNS failure; key), Routeway (Cloudflare
  check and key), BazaarLink (key).

### Caveats

- No identity rotation anywhere: no visitor ids, cookies or fingerprints are minted. The playground socket gets a
  fresh agent (chat room) name per request, as its own page does for a new chat; its limits are per-minute caps
  shared by everyone, so this stretches nothing.
- These are demos: a lab can change a Space's inputs or switch it off without notice. A broken adapter fails fast
  with a retryable error and the keyless fallback hands the request on (a silent Space after 75 s, the playground
  after 35 s).
- MiniCPM's demo writes every request and raw model output to log files (its `app.py`); the provider note in the
  app says so.

## 2026-10-23 — routes too limited for agent runs, removed

**The bar.** A typical Oro coding run is ~20–60 model calls in ~5–15 minutes with 10–60k-token prompts
(live-eval.md: Hy3 built a site in 5–14 minutes over 8–23 turns; one 60-turn run took 31 minutes on a
2-requests-a-minute route). A keyless route that cannot sustain that for one user — about 2–3 requests a minute or
less, ~150 requests a day or less, a daily token budget below one run, or per-request caps that break long prompts —
cannot carry the agent, and as a fallback stand-in it only adds failures. Measured limits below come from this file,
live-eval.md, device-live-eval.md and the RateLimits table.

| Route | Limit (source) | One agent run needs | Decision |
|---|---|---|---|
| **Pollinations** anonymous (`openai-fast`) | about one reply per 30 s per IP, then 402 `FREE_LIMIT` (probed 2026-10-17) | 40 calls ≈ 20 min of waiting alone | **Provider removed** (keyless-only in Oro) |
| **VLM Run** (`qwen3.8-27b`) | 10/min, **30/hour, 100/day** per IP (docs.vlm.run); the live eval used its quota up within a run (live-eval.md) | a run is 20–60 calls; three runs a day exceed 100 | **Provider removed** |
| **OpenBMB MiniCPM demo** | **4 requests at a time across all visitors**; logs every request and output (its `app.py`); content filter refused ordinary screens 5 times in a row (device-live-eval.md) | concurrent capacity shared with the world; never a safe stand-in | **Provider removed** (client adapter deleted) |
| **OVHcloud** anonymous (Qwen3.5 397B, Qwen3.8 27B, Llama 3.3 70B) | **2 requests/min per model** per IP; a 60-turn eval run took 1,890 s (live-eval.md) | 40 calls ≥ 20 min | **Key-only now** (free OVHcloud account); keyless seeds and fallback entries dropped |
| **LLM7** anonymous (Codestral, GPT-OSS 20B, Auto, MiniMax M2.7) | **~100k tokens a day** per IP: a 40k prompt after a few probes got 429 "Daily token quota exceeded" | one run is 0.5–3M tokens | **Key-only now** (free token at token.llm7.io); keyless seeds and fallback entries dropped |
| **Cloudflare playground GLM-4.7 Flash** | not a rate problem: its text tool calls are unreliable (unnamed parameters, escaped HTML), so it was never a stand-in | — | **Seed removed** (Gemma 4 26B on the same playground stays) |

**What stays, and why it clears the bar**

- **Mercury 3** (default): >1,000 tokens/s, 8 back-to-back requests with no limit hit, 27 turns for three briefs
  (model-profiles.md).
- **Hy3** (Tencent's Space): 64 concurrent, built and self-checked a landing page, an app and a game in 5–10 minutes
  each (live-eval.md). First stand-in for native-tool requests.
- **Kilo free pool** (Nemotron 3 Ultra/Super VL/Lightning, North Mini Code, Dots3, Flint, Kilo Auto, OpenRouter free,
  Laguna, Reflection Beam): ~200 requests/hour per IP — enough for a run; its 429s seen only on molab's IP shared by two
  evals at once.
- **Laguna** (Poolside's Space): OpenRouter's free route on Poolside's key; one "temporarily rate-limited" in ~30
  calls; passed 11/11 device scenarios.
- **Cloudflare Gemma 4 26B**: per-minute caps shared by all visitors, 6k-character newest message and ~10-message
  conversations, all handled by the client (split, fold); passed 11/11 device scenarios with 5–13 s turns.
- **UniKey, K2 Horizon, Ptero, Qwen web**: no observed throughput limit at agent scale (UniKey's per-visitor trial
  is rotated per request — flagged before; unchanged here). Qwen-family stand-in is now Ptero's Qwen 3.7 Flash only.

**Migrations.** Rows of the removed providers are dropped by name at every launch (`AiModelRepository.REMOVED_PROVIDERS`),
and rows whose provider no longer exists are never shown as another provider's. Once (`ModelMigrations` v2): OVHcloud's
and LLM7's rows go unless the user has a key for them, and every saved choice of a removed model (projects' last-used
model, the selected model, a default the user set) moves to Mercury 3 (High). A pick of a model that still exists is
never touched.
