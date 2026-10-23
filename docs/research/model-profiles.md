# Model profiles — one harness, tuned per model (2026-10-23)

`core/data/.../ai/agent/ModelProfiles.kt` picks a `ModelProfile` for each model; the editor
(`EditorViewModel.runAgent`) and the live eval (`EvalRun`) apply it. `DEFAULT` is the harness as it
was, so every model without a better profile behaves exactly as before.

## Knobs

| Knob | What it changes | Where it lands |
|---|---|---|
| `wholeFiles` | Write each file whole in one call; the "long files in parts / append" advice is dropped from the text protocol | `TextToolProtocol.render(…, wholeFiles)`, prompt section |
| `qualityPasses` | Fix passes the final check may ask for | `AgentLoopConfig.maxQualityPasses` |
| `previewEachRound` | After a round that changed pages, styles or scripts, the app renders the changed pages at phone width and adds what it measured to the tool results | `AgentLoopConfig.roundNote` → `VisualGate.roundNote` |
| `visualFinalCheck` | The final check also renders changed pages (phone + desktop) and sends back hard defects | `VisualGate.review` after `QualityGate.review` |
| `idleTimeoutMs` | Stream silence tolerated before a retry | `AgentLoopConfig.idleTimeoutMs` |
| `streamPushMs` | Base cadence of chat pushes while a reply streams | `EditorViewModel.pushIntervalMs` |
| `maxOutputTokens` | Output budget where the provider takes one | `streamChat(maxTokens)` |
| `contextBudgetTokens` | Ceiling on the context the pruner fills (below the model's window) | `prepare` budget |
| `inlineProjectChars` | A small project's files go into the run's context snapshot (all or nothing) | `ProjectSnapshot.inline` |
| `promptSection` | Constant text appended to the system prompt (after the craft standard, before the per-run context, so prompt caches hold) | `AiRepository.systemMessage(profile = …)` |

The visual checks are high-precision on purpose (AGENTS.md: a false positive costs every model a turn):
hard defects are a page that scrolls sideways, images that fail to load, elements with `hidden` that
still show, and text under 2:1 contrast (all but invisible — the header-button case). Ordinary low
contrast, small text and small tap targets stay in the round note as information. Low-end phones skip
the per-round render (the final check still renders).

## Profiles

| Profile | Models | wholeFiles | passes | preview each round | idle | push | max out | context cap | inline project |
|---|---|---|---|---|---|---|---|---|---|
| `mercury` | Mercury keyless (chat.inceptionlabs.ai), Inception API, `mercury*` on gateways | yes | 3 | yes | 90 s | 40 ms | 32,768 | 128k | 120k chars |
| `frontier` | Claude Opus/Sonnet/Fable, GPT-5+, o3/o4, Gemini 2.5+, Grok 4+, DeepSeek V4+, Qwen 3.x Max, Kimi K2.5+/K3, GLM 5+, MiniMax M3+ (on keyed or gateway routes) | yes | 2 | yes | 150 s | 80 ms | provider | 256k | — |
| `fast` | Groq, Cerebras | no | 3 | yes | 90 s | 50 ms | provider | window | — |
| `community` | every other guest bridge (K2, Qwen web, Ptero, UniKey, lab demos, Cloudflare) | no | 2 | no | 150 s | 80 ms | site | 128k | — |
| `default` | everything else | no | 2 | no | 150 s | 80 ms | provider | window | — |

A guest bridge is `community` whatever model it names (Ptero answers its `mercury-*` ids with Qwen).

## Mercury 3 — what was measured (keyless site, from molab, 2026-10-23)

- Plain HTTP passed Vercel's checkpoint for a window (07:19–07:36 UTC), then the checkpoint returned
  for every plain request (monitored every 2 min for over an hour). **Headless Chromium passes it
  instantly** and `fetch('/api/session')` works in the page — the path Oro's hidden-WebView bridge
  (`MercuryWebBridge`) takes on a phone. The eval uses the same in-page script through Playwright
  (`EvalMercuryBridge` + `oro-eval/mercury_bridge.py`).
- Speed: a short reply in 0.3–0.7 s (Instant / High); a complete 1,549-line, 84k-character page in one
  reply in 42 s at High (15 s of hidden reasoning, then the text); eight back-to-back requests, no limit hit.
- Context: a needle found in a 251k-character (~60k-token) prompt in 1.9 s and in an 838k-character
  (~200k-token) prompt in 4.4 s.
- Stream: `reasoning-start`/`reasoning-end` with no reasoning text, `text-start`, a few large
  `text-delta`s, `text-end`, `[DONE]` — **no `finish` frame**. The AI SDK parser no longer synthesises
  "stop" when a stream ends without one: the end reason stays unknown, so a file write left open at the
  very end is treated as possibly cut off and resent (the truncation guard), never written half. No such
  resend happened in the runs below.
- Request: unchanged from NEB's 2026-10-19 proxy (`reasoningEffort`, `webSearchEnabled:false`,
  `voiceMode:false`, `timezone`, `id`, `messages[].parts`, `trigger:"submit-message"`); effort High by
  default (Instant/Low/Medium/High in the picker).

## Live A/B (Oro's real harness, real Mercury 3 keyless, 3 briefs; see docs/research/live-eval.md)

Same code, `ORO_EVAL_PROFILE=off` (DEFAULT) vs the Mercury profile:

| Brief | Profile | Outcome | Turns | Tool calls | Tool errors | Page issues | Smoke | Time |
|---|---|---|---|---|---|---|---|---|
| landing | off | done | 18 | 87 | 2 | 1 | — | 361 s |
| landing | mercury | done | 15 | 45 | 2 | 5 | — | 243 s |
| app | off | done | 13 | 42 | 2 | 0 | add ✓ / persist ✓ | 163 s |
| app | mercury | done | 7 | 21 | 0 | 1 | add ✓ / persist ✓ | 117 s |
| game | off | done | 20 | 19 | 0 | 0 | moves ✓ | 148 s |
| game | mercury | done | 5 | 14 | 0 | 0 | moves ✓ | 103 s |
| **all** | off | 3/3 | **51** | **148** | 4 | 1 | | **672 s** |
| **all** | mercury | 3/3 | **27** | **80** | 2 | 6 | | **463 s** |

47% fewer turns, 46% fewer tool calls, 31% less wall time, same outcomes and smoke results. The
landing page's extra issues were "image larger than shown" (both arms) and one real defect the
profile run missed: the desktop header button's label at 1.5:1 contrast (the phone layout folds it into
the menu, so the phone-only round notes never showed it). Fixed after this run: the final visual check
renders phone **and** desktop, and text under 2:1 is now a hard defect (covered by `VisualGateTest`).
The live re-run of the Mercury arm with that fix was lost: the molab sandbox was terminated mid-run
(HTTP 410 "sandbox terminated"). Re-run it with the command below when a box is available.

Reproduce (on a box with Playwright + Chromium, JDK 21 and the Android SDK):

    ORO_LIVE_EVAL=1 ORO_EVAL_OUT=<dir> ORO_EVAL_MODELS=mercury:mercury-3 ORO_EVAL_TASKS=landing,app,game \
    ORO_EVAL_MAX_TURNS=40 ORO_EVAL_PROFILE=off|mercury \
      ./gradlew :core:data:testDebugUnitTest --tests '*LiveEvalTest*' --rerun

## Unverified / next

- The Mercury-profile re-run with the desktop + invisible-text final check (lost with the sandbox).
- Mercury on a real phone network (plain HTTP vs the WebView path, and its real output cap).
- The per-round render cost on low-end phones (skipped there by design).
- Other profiles (`frontier`, `fast`) were set from documented model behaviour, not measured live here.
