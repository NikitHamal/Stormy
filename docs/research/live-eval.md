# Live eval: free models through Oro's real coding harness (2026-10-23, fork E1)

Question: do keyless models get professional results through Oro's agent? This runs the real
harness end to end and was used to find and fix where it fails them.

## Method

`LiveEvalTest` (core/data unit tests) **only runs with `ORO_LIVE_EVAL=1`**; normal CI and unit runs skip it
(JUnit `Assume`), and `reprobe()` needs `ORO_EVAL_REPROBE=1`. Run it on molab:

```
ORO_LIVE_EVAL=1 ORO_EVAL_OUT=/root/oro/eval/out/<tag> ORO_EVAL_MODELS="hy3:hy3,hy3:hy3@text,qwen3.8-27b" \
ORO_EVAL_TASKS=landing,app,game ORO_EVAL_MAX_TURNS=60 ./gradlew :core:data:testDebugUnitTest --tests '*LiveEvalTest*'
```

- **Real pieces:** `AgentLoop`, the agent-mode system prompt and tool catalogue from `AiRepository.systemMessage`,
  `ProjectTools`/`WorkspaceTools`/`CraftTools`, `QualityGate` on "done", `ProviderRateLimiter`, and the keyless
  providers through `AiProviderManager` (stand-ins off: `KeylessFallback.enabledIds = emptySet()`). `@text`
  forces the text tool protocol on a model that has native calls.
- **Stand-ins:** the preview tools (`screenshot_preview`, `audit_page`, `get_preview_errors`) use headless
  Chromium (Playwright, `oro-eval/browse.py`) instead of the WebView, with the same measuring JavaScript and
  the same text (`PageMetrics` formats it for both). `ask_user` answers "I'm away, make the sensible choices";
  approvals are granted. The project is served by `EmbeddedDevServer`, so `/api` is the real `MockApi`.
- **Measured per run:** outcome, turns, seconds, tool calls and errors, resend/truncation/reasoning nudges,
  quality-gate passes, tokens (when the provider reports them), files; then screenshots at 390×844 and
  1280×800, the page audit at both widths, the static `QualityAuditor`, and a smoke test (app: add an item
  through the UI, check it shows and survives a reload; game: press keys/swipe and check the canvas changes).

Tasks (`EvalTasks.kt`): **landing** — "Himalayan Brew", a specialty coffee roastery in Thamel, Kathmandu;
**app** — a habit tracker persisted through `/api` with loading, empty and error states; **game** — Snake with
swipe and arrow keys, a best score and a neon look.

Models (keyless only): Tencent **Hy3** (`hy3:hy3`, native and `@text`), **VLM Run** `qwen3.8-27b`,
**OVHcloud** `Qwen3.5-397B-A17B`. Kilo's `nvidia/nemotron-3-ultra-550b-a55b:free` failed a smoke run on upstream
idle timeouts before the first token on agent-sized prompts and was left out of the matrix. Mercury 3 was
not run (its community route sits behind Vercel's bot check from molab, see keyless-providers.md).

## Before → after

Before = oro-dev 752347e plus the eval (b63c675). After = 809d9b4. Page issues: the audit as it was →
the corrected audit re-run on the same files (`reprobe`); after-runs use the corrected audit.

| Model | Task | Before | After |
|---|---|---|---|
| Hy3 text protocol | landing | **failed in 43 s**: 2 resend nudges, every call unreadable, no page | **done** in 841 s, 23 turns, 0 resend nudges, 3 page issues |
| Hy3 text protocol | app | **failed in 25 s** (same) | running at time of writing |
| Hy3 text protocol | game | **failed** after 454 s (same; some files written, then the run ended) | running |
| Hy3 native | landing | done, 307 s, 16 turns; page issues 19 → 12 | failed after 11 turns: Hy3 kept answering 400 "rejected by an internal MaaS component" (4 retries; provider side) |
| Hy3 native | app | done, 619 s, 23 turns; page issues 0 | done, 536 s, **8 turns**; 1 page issue |
| Hy3 native | game | done, 418 s, 7 turns; page issues 8 → 0 | running |
| VLM Run qwen3.8-27b | landing | **failed** after 359 s: "VLM Run request failed (200)" (a dropped stream) | not measured: VLM Run's per-IP quota (30/h, 100/day) was used up |
| VLM Run qwen3.8-27b | app | done, 886 s, 20 turns, but **loading, empty and error all visible at once** (5 hidden elements displayed) | not measured (quota) |
| VLM Run qwen3.8-27b | game | done, 742 s, 14 turns; page issues 0 | not measured (quota) |
| OVHcloud Qwen3.5-397B | landing | **turn cap**: 60 turns, 1890 s, 17+ turns chasing a bare "8 tap targets" count; page issues 46 → 9; header CTA at 1.5:1 contrast | not re-run (2 requests/min; time) |
| OVHcloud Qwen3.5-397B | app | done, 1494 s, 49 turns; page issues 4 (old audit) | not re-run |

No run in either round needed a quality-gate pass: the static auditor found nothing blocking in any finished
project.

Screenshots (local copies): before — `/data/workspace/eval/base/hy3_native_landing/{desktop_top,phone}.jpg`,
`…/qwen3.8-27b_app/phone.jpg` (all three states shown), `…/ovh_landing/desktop_top.jpg` (unreadable header CTA),
`…/hy3_game/phone.jpg`, `…/vlm_game/phone.jpg`; after — `/data/workspace/eval/after/hy3text_landing/{desktop_top,phone}.jpg`
(real latte-art, Himalaya and bean photos from Openverse, credited). On molab: `/root/oro/eval/out/{base,after}/<model>/<task>/`.

## What broke, and what changed

1. **Hy3's text protocol drifted into its own chat template** — `<tool_call:6124c78e>use_skill>`,
   `<function>list_files>`, `<parameter>content>value` — so nothing parsed and the run ended in under a minute.
   `TextToolCallParser` rewrites those lines to the taught markup (function names must be real tools; only
   line-start markup is touched, so file bodies that mention the tags stay as written). The resend nudge now
   quotes the line it could not read. Tests: `TextToolCallParserTest` (4 new, verbatim shapes from the runs).
2. **A stream that broke after HTTP 200 ended the run** as "request failed (200)". It is now a dropped
   connection, which retries. Test: `StreamDropTest` (a real socket that cuts a chunked 200).
3. **`hidden` beaten by `display`**: `.state{display:flex}` showed loading, empty and error together. The kit
   now has `[hidden]{display:none!important}`, `QualityAuditor` flags hidden elements whose class sets display,
   the screenshot metrics count hidden elements that still render, and `ui-states` says how to toggle states.
   Tests: `QualityAuditorTest` (2 new), `PageMetricsTest`.
4. **Photos unrelated to the brief** (picsum gave a skyscraper and a mushroom to a cafe; invented Unsplash ids
   404). `web_fetch` on Openverse returns a clean numbered list of openly licensed photos with credits
   (`ImageSearch`), and the prompts and design skill teach it. After: VLM Run (first, interrupted attempt) and Hy3 in text mode
   both searched it; the finished Hy3 page uses matching photos with credits. Test: `ImageSearchTest`.
5. **The page audit cried wolf**: modern colour syntax, gradients, hidden menus and inline links read as
   contrast or tap-target failures (46 issues on the OVH page, 9 real). Colours now go through canvas,
   backgrounds are composited, hidden and inline-in-text elements are skipped, a desktop pass adds what only
   shows wide, and tap targets are measured at touch widths only (flagged under 40×40 or thinner than 32 px).
6. **Metrics that counted but did not name**: OVH's Qwen resized icons for 20 turns because "8 tap targets
   smaller than 40px" never said which. Metrics now name the first few small targets, small texts and
   low-contrast texts with their size or ratio, and the outermost unclipped boxes that push the page wider
   (the old check only listed boxes wider than the screen, so a narrow box pushed off the edge was invisible).
7. **Keyless order**: Hy3 is tried before Kilo's free 550B, and "upstream idle timeout" parks a model like a
   silent one (`KeylessFallbackTest`, `KeylessSeedsTest`).

## Still weak

- **Header CTA contrast.** Two of the three finished landing builds (OVH before; Hy3 text after, despite the new skill line
  and metric) shipped a header button whose text took the nav link colour (`.nav a` outranks `.btn`).
  The metric names it; the models did not act on it. A static check for that selector pattern, or a gate on
  the desktop audit, is the next step.
- **Provider limits dominate.** VLM Run's anonymous quota is per IP and shared by everything on molab; Hy3's
  demo intermittently rejects long requests with a MaaS 400; OVH allows 2 requests/min, so a 60-turn run
  takes half an hour; Kilo's free pool times out on big prompts.
- **App smoke test is inconclusive**: on both apps it typed and saved (a POST reached `/api`) but did not find
  the text afterwards, and on the after-run Hy3 app it found no form to fill. The probe needs a look before
  its "persists" column can be trusted.
- Headless Chromium on molab is not the Android WebView; layout and fonts matched in the screenshots checked,
  but WebView-only behaviour (renderer limits, file:// quirks) is not covered.
- Hy3 reports no token usage, so token columns are empty for it.
- Not re-verified: that the new hidden-state check fires on the VLM Run app, and how OVH's Qwen does with
  the named metrics (both need another run when the quotas allow).
