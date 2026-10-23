# CONTINUITY.md — session ledger

Update this file at the end of every working session (or before a risky step), so a new agent
or a cut-off session can resume. Newest entry on top. Keep entries short and factual.

## How to resume
1. `git log --oneline -15` on `oro-dev`, and read the latest entry below.
2. Check the last CI run for the branch (Actions → Android CI). Fix compile errors first.
3. Continue from "Next" of the latest entry.

---

## 2026-10-22 — round 2 review and fix pass
Three read-only reviews (device engine, harness + providers, island + device UI) found ~45 real issues; three parallel fixers (ci/g1, ci/g2, ci/g3) fixed them, each green on CI, squash-merged.
- **Device engine (Targets, DeviceArgs):** a call's target is resolved once (index remapped from the screen the model saw, tap_text text/label, x,y hit-tested), SensitiveGuard judges that exact target (coordinates included), and the action uses it; stale screens are re-captured after waits; Pause stops observation too; Oro in front mid-run makes actions wait; ElementRemap refuses look-alikes; tap_text never acts on part of a word; State.endReason on every ending (no "done" without finish_task); wrap-up job separate from the run (Send works right after a run); use_phone refuses retries after the user's Stop.
- **Harness:** TextToolCallParser scans raw values as opaque (a README showing an example call no longer runs it or truncates the file); early stop and truncation guard checked on every call; retry budgets reset after progress; StuckDetector keys reads on a write epoch; raw values typed by each run's tool schemas (`AgentLoopConfig.toolSchemas`).
- **Providers:** WebView bridges survive renderer death; Mercury bridge origin-restricted, never hangs pending streams; community calls cancel immediately on Stop; AI SDK error frames are errors.
- **Island:** Stop on the collapsed pill; clearPathFor(region) only clears the island under an injected gesture; focusable only while the user types; re-clamped on rotation/growth; fixed pill width (no per-frame window resizes); TalkBack pane titles.
- **Screenshots:** push to `shots/<name>` (or ci/shots) and run `shots.py --branch shots/<name>` (AgentSky task folder `task/tools/`; the job log carries base64 PNGs prefixed `ORO|`). Locally: `./gradlew :app:testDebugUnitTest -Poro.screenshots=true`.

---

## 2026-10-22 — round 2: harness, providers, device engine, island
Built in four parallel worktrees (ci/harness, ci/providers, ci/device, ci/deviceui), each green on CI, squash-merged into oro-dev.
- **Harness:** no step cap (StuckDetector nudges, then ends only real loops); text protocol teaches raw `<function=…><parameter=…>` values (no JSON escaping); cut-off calls never run (resend in parts, `write_file append`); text-mode streams stop where a model invents a `<tool_result>`; write/edit results carry a quick file-local check; `read_file paths[]`, `grep context`; system prompt snapshotted per run (prompt caches stay warm).
- **Providers:** Mercury (community: chat.inceptionlabs.ai guest token, AI SDK v5 stream, `MercuryWebBridge` WebView fallback for the Vercel check), Inception official API (key, native tools), UniKey (community, keyless trial: Claude Opus 4.8, GPT-5.6 Terra, Grok 4.3, DeepSeek V4, MiniMax M3, Kimi K2.7 Code; fresh visitor id per request). Research: `docs/research/keyless-providers.md`, `docs/research/mercury.md`.
- **Device engine:** plain-Kotlin `ScreenModel` (compact lines, rolling hash), event-driven settle, tools tap_text / scroll_to / wait_for / read_page / quick_action / media / look / device_status, `AdSkipper`, unlimited actions by default, escalating stuck signals.
- **use_phone:** the coding agent hands a task to the device agent (`DeviceAgent.runForResult`, origin CHAT) and gets summary + notes back; `open_preview` has it QA the live preview in the browser. Leashed asks first.
- **Island + screen:** Compose overlay (`device/overlay/`) with Orobi loader, shimmer status, collapse/expand, inline approvals and replies; device screen rebuilt from the editor's chat components.

**Unverified on a device** (CI only compiles and runs JVM tests): every gesture and new device tool, ad skipping on real YouTube, numbered screenshots, the island's Compose lifecycle and inline reply keyboard, use_phone's round trip back to the chat, Mercury's WebView path, UniKey limits from a phone IP, web-chat models following the new raw-parameter format.

**Next:** run the CI APK on a phone: enable Oro Device Agent, try "play lofi on YouTube and skip ads", then a chat run that calls use_phone with open_preview. Fix what breaks first.

---

## 2026-10-18 — launch film v2 (Cinetic)
- `launch-film/`: 35 s launch film built with the Cinetic skill (github.com/Leonxlnx/cinetic): BRIEF/TREATMENT, `pick.py` technique draw, `timeline.ts` (120 BPM, crisp, grid-check pass), 9 acts, product UI from the app, earshot crisp kit + synth score (-14 LUFS, drop on the live-site reveal), motion-blurred masters (budget 4x, samples in `out/samples-master.json`, not committed).
- Deliverables in `launch-film/deliver/` (round 2; round-3 layout/script pass is code-only, user renders locally): 1080p60 master, 9:16 and 1:1 re-layouts (`REMOTION_FORMAT=9x16|1x1`, same timeline), poster, QA reports.
- Gates: probe + check-sync PASS on all three; forensics clean except one declared reading hold (f1359-1423, "Free models.").
- Tools used here: ffmpeg 7 static, Playwright's chromium headless shell (`REMOTION_BROWSER`), Python venv with numpy/scipy/librosa/opencv, earshot 0.3 in its own venv.

## 2026-10-18 — keyless reliability
- Probed every keyless model live (streamed tool call). Kilo 12/16 free OK (rest: upstream 429/503), OVH chat models OK, Pollinations OK, LLM7 only "turbo" + non-usage_based_only models work anonymously, OpenCode Zen free tier refuses non-OpenCode clients (left key-only), Ptero re-routes most ids to qwen3.7-flash.
- `KeylessFallback`: keyless requests move to the next healthy free model on quota/auth/overload before any output; busy models skipped for a while. Order is in the file.
- LLM7 `/models` filtered anonymously (`usage_based_only`, `tier`); retired stale rows in `AiModelRepository.addMissingKeylessSeeds`.
- Read tool: path from read_file's header (was picking a file named inside the code); sheet shows the file, contents folded.

## 2026-10-18 — crash fix, generative UI, keyless providers
**Done**
- Clone crashed on Android < 13: JGit 7 calls `InputStream.readNBytes` (API 33). Pinned JGit **5.13.5** (Java 8). Do not bump JGit past 5.13 while minSdk < 33.
- Crash screen redesigned (`ui/screens/debug/CrashActivity`): summary + hint, highlighted trace, earlier crashes (`crash/CrashReports`, files in `filesDir/crashes`), copy/share/report.
- Tool calls: tap opens a per-kind bottom sheet (`ToolCard.ToolDetailSheet`); no inline expansion.
- Whistle live streaming reverted (buggy); batch record-then-transcribe again.
- Generative UI: ```` ```ui ```` blocks in replies render with A2UI Compose (`androidx.a2ui.compose` 1.0.0-alpha01). Protocol/repair in `core/data/.../ai/genui/GenUi.kt`; catalog in `app/.../ui/genui/` (18 basic + 11 Oro components). Taps → `EditorViewModel.sendText`. Toggle: chat settings "Rich answers".
- Providers: Qwen (chat.qwen.ai guest; `QwenFingerprint` + `QwenWebClient`, identity pool, WebView fallback `community/QwenWebBridge`), Ptero (`PteroClient`), LLM7 (OpenAI-compatible).

**Unverified on a device**: A2UI rendering of every component, Qwen over the phone's network (plain HTTP was challenged from a datacenter IP; WebView path untested), Ptero/LLM7 limits.

## 2026-10-17 — size cut + suggestions round
**Done**
- Removed on-device LLMs and MediaPipe (APK back toward ~4 MB). Whistle STT kept.
- Whistle live mode: `Whistle.Stream` (`needle_stream_transcribe_*` via JNI), words appear while you talk.
- `search_project` (`ai/search/ProjectSearch`, BM25 + synonyms, lexical, not embeddings) and `docs_lookup` (`resources/oro/webdocs.json`, offline).
- Design from a photo: composer photo button, images to vision models (`EditorViewModel.attachImage`).
- Self-check after agent runs (`SelfCheck`, setting in chat settings, one extra fix turn at most).
- Checkpoint timeline screen (`ui/screens/timeline`), per-file diff and restore.
- Skills carry starter files (```` ```file:path ```` blocks; built-in `landing-page-starter`).
- `open_pull_request` tool (`git/PullRequests`, Git Data API; uploads only changed files; never deletes; always asks the user).

**Unverified on a device**: live Whistle latency on low-end phones, PR flow against a real repo, self-check rendering cost.

## 2026-10-17 — issue #28, second round
**Done**
- Wrap fixes; full-screen provider editor (`ProviderEditorScreen`); vendor presets seeded as built-ins; fetched models default off.
- Merged `main`; StormyWeb rewritten as a modular replica of the app (see `StormyWeb/README.md`).
- Leashed / Unleashed autonomy (`AutonomyPolicy`), removed the duplicate Chat/Agent chip.
- Dictation: system `SpeechRecognizer` + Whistle on-device (JNI in `app/src/main/cpp`, weak `std::__hash_memory` shim for older NDK libc++).
- `screenshot_preview` / `audit_page` (`WebViewPagePreviewer`), images to vision models (`ChatRequestMessage.images`).
- MCP OAuth sign-in (`McpOAuth`, `oro-mcp://callback`), share project (zip / live link), skills (`use_skill`).
- On-device LLMs were added then removed (MediaPipe grew the APK from ~4 MB to ~60 MB). Do not re-add without a size budget.
- Launch video: real landing page in the live scene, re-rendered.

**Unverified on a device** (CI only compiles): Whistle transcription quality, off-screen WebView capture (may draw blank on some WebView versions; metrics still work), MCP OAuth against real servers.

**Next**: see `docs/NEXT.md`.

## 2026-10-17 — issue #28, first round
Caret fix, Chat split-button tab, plan docked on composer, one-line tool rows, single-stage
editor state, ORO.md/AGENTS.md instructions, preview errors to the agent, Anthropic prompt
caching + per-chat usage meter, checkpoints, background runs + notifications, MCP client,
Netlify / GitHub Pages deploy. CI green at `af811ef`.

## 2026-10-17 — harness / providers / sessions overhaul
Multi-question ask_user, K2 tool-call fixes, custom OpenAI/Anthropic providers with key
failover, multiple chat sessions per project (Room v6), Lite mode, launch video with sound.
