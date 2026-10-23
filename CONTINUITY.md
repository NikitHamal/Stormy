# CONTINUITY.md — session ledger

Update this file at the end of every working session (or before a risky step), so a new agent
or a cut-off session can resume. Newest entry on top. Keep entries short and factual.

## How to resume
1. `git log --oneline -15` on `oro-dev`, and read the latest entry below.
2. Check the last CI run for the branch (Actions → Android CI). Fix compile errors first.
3. Continue from "Next" of the latest entry.

---

## 2026-10-24 — round 5: act through Android's APIs, island glow, speed
Built in local worktrees, compiled and tested on molab (`/tmp/tools/molab_test.sh <worktree> <name>` ships the diff and runs Gradle there), pushed one commit per green CI run.
- **Direct control (`ci/direct`):** `media` seeks exactly through the playing app's media session (`MediaControl`, needs Notification access via `OroNotificationListener`; a "Media control" row in device settings), plus `media("status")`. Sliders are listed with their range (`slider "Volume" 35/100`) and `set_value(index, value)` sets them: ACTION_SET_PROGRESS, else scroll steps, else one press-hold-move steered by the value read back; a hidden YouTube bar is brought back with one tap inside the same call. Typing reads the field back and, on Android 13+, falls back to the service's own keyboard connection (`flagInputMethodEditor`: commitText, the field's editor action). A tap by coordinates reports what the app says it hit (`TapFeedback`).
- **Island glow (`ci/island`):** the full-screen edge glow is gone (owner's call); the island itself carries the light: `overlay/IslandGlow` draws a 1.5 dp rim of Orobi's colours turning around the pill/card, a short spark running along it and a 7 dp breathing halo (stacked faint strokes, no blur), warm and slow for a question or approval, out when paused/done/failed; draw phase only, Lite = still rim. `IslandGlowRoom` (10 dp) is the window's margin for it. The "Edge glow" setting is removed.
- **Speed and aim (`ci/speed`):** device steps run at the model's quick reasoning level (`ReasoningControls.quickLevel`: "low" where the scale has it; Mercury 3 was thinking at High before every tap); `AiRepository.streamChat(reasoningLevel=)` overrides one call. `UiActivity` tells settle a busy screen (a playing video sends events nonstop) from a transition: there it waits for structure to stop changing, at most 1 s (it sat out the 2.5 s cap after every action); value-only redraws are cosmetic, so a capture is reused across them. A spinner that was already there (or a determinate progress bar) no longer adds up to 1.35 s per action. `TapPlan`: an x,y on or within a fingertip of one small plain control taps that control by its node (the result says so); a part inside a much bigger clickable (a menu icon in a card) is tapped where it is, not through the card's click. A comparing task's second pick made without looking at the rest of the list gets the list read for it. Live eval (`docs/research/device-live-eval.md`, round 5): North mini code and Laguna 11/11 (Mercury still blocked from molab).
- **Research (2026-10-24):** the "system-level" agent the owner remembered is most likely Eta (github.com/Mangi-11/Eta: Android APIs first, accessibility as fallback, root/LSPosed for more; PolyForm Noncommercial). Others: DroidRun/Mobilerun, Minitap, Operit, Ruto-GLM and Roubao (Shizuku), AutoDroid-V2 (scripts), AppAgentX (learned shortcuts). AppFunctions is allowlisted (not usable by Oro); Computer Control is OEM-only. Play policy: Accessibility API use that lets an app "autonomously initiate, plan, and execute actions" is prohibited unless the app is a verified accessibility tool -- a release risk for the device agent that the owner has to weigh before the Play Store step.

**Next:** on a phone, with the CI APK: Mercury 3 device runs (quick level: does it feel fast, any wrong picks?), YouTube seek through `media("seek")` with Notification access on and off, `set_value` on Settings sliders, typing into a web form, the island glow over light and dark apps. Decide the Play Store question (Accessibility policy, above) before the release step.

---

## 2026-10-23 — round 4: edge glow, exact touches, parallel tool calls, forms, human checks
Local worktrees, compiled and tested on molab (20 CPUs, JDK 21 + Android SDK in `/home/marimo/oro`), squash-merged onto oro-dev.
- **Edge glow:** `overlay/EdgeGlowView` + `GlowMask`: Orobi's colours light the display's edge (its real rounded corners) while a run is Working, fade out on pause/question/approval/end, swell on each action; Lite mode = one still frame; "Edge glow" switch in the device settings (`Settings.edgeGlow`). `AgentOverlay.drawingLayer()` is the shared untouchable full-screen layer (cutout included).
- **Touch accuracy:** ScreenReader clipped bounds to `displayMetrics`, which can leave out the navigation bar, so bottom controls' centres moved up; bounds, gestures and the island now share `DisplaySize` (the whole display). ACTION_CLICK taps mark the live node's bounds; marks are kept in screen pixels and mapped into the window when drawn (`TouchGeometry`). A tap frames its element, typing frames its field; marks are cleared before a vision screenshot.
- **Coding agent tool calls:** `ToolBatch` schedules a turn's calls by the paths they touch (at most 4 at once, results in call order): reads together, writes to different files together, same-file calls in order; ask_user, request_approval, run_command, open_pull_request, use_phone, MCP and unknown tools run alone. Read-only subagents (explore, plan, review) run side by side; `DelegationHandler.startSubagent` returns the session id so each spawn waits on its own. The editor makes file-changing calls take turns (the approval sheet and undo group hold one call).
- **Device agent:** `fill_form` fills a whole form in one call (`FormFill`: a field by its own name, else the label above it or beside it on its row; each element used once; same-name fields reported, never guessed; radio groups take the option showing the value; pickers are opened and the option chosen; fields further down scrolled to; one approval for a sensitive form; `submit=true` presses the form's own button, never "Continue with Google"). A turn's actions run in order and stop at the first that does not work (`turnBroken`), finish_task included; `AgentLoop` now ends a run only on a finish_task that went through. Human checks (`ScreenState.humanCheck`: widget labels, not the reCAPTCHA badge, a ticked box is passed) are handed to the user: on-screen actions wait on the island ("Pass the check on screen") and the run carries on by itself once the check is gone (`askUntil`); a note records that the user passed it. Orobi never solves captchas itself.
- **molab:** paired through marimo (`uvx marimo pair prompt --url … --with-token`, token via `MARIMO_TOKEN`). The notebook shows auto-refreshing cards: `/home/marimo/oro/status/*.json` (builds, tests) and `/home/marimo/oro/previews/**.png` (+ a `.json` sidecar for title/note). MockK needs ByteBuddy's agent at JVM start under gVisor: `/root/.gradle/init.d/molab-mockk.gradle` (molab only, not in the repo).

**Next:** install the CI APK on a phone: the edge glow over a few apps (light, dark, video), tap marks on small controls near the bottom, a sign-up form with fill_form, a site with a captcha. Then the phone checks from the entry below.

---

## 2026-10-23 — overnight: crash fix, models, providers, harness, device, preview, evals
Parallel local worktrees squash-merged into oro-dev; every merge green on CI (last: 9fca5c4). Builds ran on molab (20 CPUs) until its sandbox was terminated (HTTP 410), then on GitHub CI only.
- **Long chats:** a run's transcript is one message; unlimited runs passed SQLite's ~2 MB row limit and opening the chat crashed. Oversized rows are repaired in SQL once per install (head + tail kept, fences closed), transcripts stay under ~160 KB at the source (`TranscriptCompactor`, RunTranscript caps), parsing is off the main thread with a `ParsedBlocksCache`, long runs window to the newest blocks, chat open/send races fixed.
- **Models:** Mercury 3 is one model with Instant/Low/Medium/High and the default (High). `ReasoningControls`: each model's exact effort scale and wire values, saved per model (GPT-5.x minimal–xhigh, Claude low–max over Anthropic's API, Gemini, Grok, DeepSeek/GLM, Qwen thinking switch…). Context windows corrected (docs/research/models.md). `ModelProfiles` tune the harness per model (Mercury: whole files, more fix passes, render after each round — about half the turns in a live A/B).
- **Providers:** new keyless Hy3 (Tencent), Laguna (Poolside), Cloudflare AI Playground (Gemma 4 26B), newer Kilo free models; Qwen hardened (current guest protocol, captcha-aware WebView, falls back to other free Qwen hosts). Removed for limits too low for an agent run: Pollinations, VLM Run, MiniCPM (also logged conversations), OVHcloud/LLM7 keyless (key users keep them). Users of removed models migrate to Mercury 3.
- **Coding harness:** golden-task tests, `RunLedger` for long runs, `list_files symbols=true`, MockApi accounts/relations/uploads (secrets outside the project, never published), `BriefCheck` + one tap-to-answer question card for vague requests ("Ask before building" setting), `VisualGate` final render check, credited Openverse images, parser handles real models' template drift. Live eval (`docs/research/live-eval.md`): Hy3 text mode went from failing every brief to finishing them.
- **Device agent:** scenario bench (`Phone`, `ScenarioBench`), screen cues (scroll capacity, icon positions, keyboard, sign-in/captcha hints), one-step quick actions, review fixes (targets resolved once and guarded, axis-aware scrolls, keyboard safety). Live eval with real free models (`docs/research/device-live-eval.md`): 4 of 6 models pass all 11 scripted scenarios after fixes.
- **Preview + templates:** device presets, sturdier WebView, CSS hot-swap, exact edit-mode write-back with undo and "Ask Orobi about this element"; seven studio starter templates on the UI kit with picker thumbnails.
- **UI:** CI screenshots of every main screen; settings, providers, models, git, timeline, publish, preview polished to the chat/device design.
- **CI/repo:** only `main` and `oro-dev` exist (main merged into oro-dev with oro-dev's StormyWeb kept, so PR #30 merges); oro-dev writes the Gradle cache; "[shots]" in a commit message renders screenshots.

**Unverified on a device:** everything above runs only in JVM tests, Roborazzi renders and live evals against real models from datacenter IPs — first install the CI APK and open a long chat, run a vague request with Mercury 3, use edit mode, and try the device agent.

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
