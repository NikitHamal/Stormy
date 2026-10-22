# AGENTS.md — working on Oro

Oro is a native Android AI coding IDE for building websites on a phone. Its agent is **Orobi**.
This file is for any coding agent (or person) picking the project up. Read it first, then
`CONTINUITY.md` for what is in flight.

## Repo map
| Path | What lives there |
|---|---|
| `app/` | Compose UI, screens, view models, Android services (`com.oro.app`) |
| `app/src/main/java/com/oro/app/ui/screens/editor/` | Editor: `EditorViewModel` (runs, sessions, checkpoints, usage), `chat/ChatTab`, `sessions/`, `deploy/`, `agent/RunTranscript` |
| `app/src/main/java/com/oro/app/agent/` | `BackgroundRuns` + `AgentRunService` (foreground keep-alive, notifications) |
| `app/src/main/java/com/oro/app/voice/` | Dictation: system speech and Whistle (on-device) |
| `app/src/main/java/com/oro/app/preview/` | `WebViewPagePreviewer` (off-screen renders for screenshot / responsive / audit tools) |
| `core/data/.../ai/skills/` | Skills (Markdown playbooks with optional starter files, `use_skill`); craft playbooks live in `core/data/src/main/resources/oro/skills/` |
| `core/data/.../ai/craft/` | Quality bar for generated sites: `QualityAuditor` (deterministic review behind `check_syntax`), `QualityGate` (auto fix pass when a run ends), `UiKit` (`add_ui_kit`, files in `resources/oro/kit/`) |
| `core/data/.../preview/MockApi.kt` | REST backend for every project: `/api/<collection>` over `db.json`, served by the preview server |
| `app/src/main/java/com/oro/app/device/` | Device agent: `OroAccessibilityService` (+ `AdSkipper`), `ScreenReader` → `ScreenModel` (plain-Kotlin screen: compact numbered elements, rolling-hash signature), `DeviceController` (gestures, media keys), `DeviceAgent` (observe → act loop, `runForResult` for chat delegation), `DeviceTools` + `DevicePrompt`, `TextMatch` (tap_text / scroll_to fuzzy matching), `ElementRemap` (stale-index safety), `QuickActions` (intents), `ScreenshotTaker` (numbered screenshots, API 30+), `SensitiveGuard`, `DeviceSettingsStore`; floating island in `AgentOverlay` + `overlay/` (Compose on the accessibility overlay: `AgentIsland`, `IslandModel`, `OverlayLifecycleOwner`, `DragFrame`, `TouchFeedbackView`); screen in `ui/screens/device/` (`DeviceAgentScreen`, `DeviceTranscript`, `DeviceSettingsSheet`) |
| `core/data/.../ai/search/` | `ProjectSearch` (on-device BM25) and `WebDocs` (offline docs pack) |
| `core/data/.../git/PullRequests.kt` | GitHub PRs through the Git Data API |
| `app/.../ui/screens/timeline/` | Checkpoint timeline (diff and restore) |
| `core/data/.../ai/genui/` + `app/.../ui/genui/` | Generative UI: ```ui blocks → A2UI Compose (protocol, catalog, renderer) |
| `core/data/.../ai/community/` | Community (keyless web) providers: K2, Qwen, Ptero, Mercury (Inception, with `app/.../community/MercuryWebBridge` for the Vercel check), UniKey; `AiSdkStream` parses Vercel AI SDK v5 streams. Research notes: `docs/research/` |
| `app/src/main/cpp/` | JNI bridge to the Needle/Whistle engine (`libneedle.a` per ABI) |
| `core/data/` | Everything not UI: AI providers, agent loop, tools, Room DB, repositories |
| `core/data/.../ai/agent/` | `AgentLoop` (no step cap; truncation guard, early stop on invented results), `StuckDetector`, `TextToolCallParser`, `TextToolProtocol`, `HistoryPruner`, `Autonomy` |
| `core/data/.../ai/tools/` | `AgentTools` (schemas), `DeviceDelegationTools` (`use_phone`), `ToolExecutor` (dispatch), `WorkspaceTools` (file tools), `UserQuestions` |
| `core/data/.../ai/providers/` | Custom providers, `KeyPool` (multi-key failover), `AnthropicClient`, presets |
| `core/data/.../ai/mcp/` | MCP Streamable HTTP client, registry, OAuth |
| `core/data/.../ai/checkpoint/`, `ai/usage/`, `ai/context/`, `preview/`, `deploy/` | Checkpoints, token/cost meter, project instructions, preview diagnostics, Netlify/GitHub Pages deploys |
| `core/designsystem/` | Theme, tokens (`Space`, `OroCorners`, `Sizes`), Orobi mascot, Expressive components (`FormSheet`, `ActionSheet`, `SegmentedPicker`) |
| `StormyWeb/` | HTML/CSS/JS replica of the whole app for fast UI experiments (open `StormyWeb/index.html`) |
| `launch-film/` | The hero launch film (35 s, Remotion, built with the Cinetic skill): brief, treatment, beat-locked timeline, earshot score, motion-blurred masters in `deliver/` (16:9, 9:16, 1:1) |
| `launch-video/` | The earlier 15 s launch film + procedural audio (`npm run render:all`) |
| `music/` | Oro — Original Soundtrack: code-synthesised music engine, five scores, renders (`npm run render`) |
| `docs/` | `NEXT.md` roadmap, `perf-notes.md`, `harness-notes.md` |

## Principles
- **Cut taglines, helper text and secondary labels.** If a screen needs them to make sense, fix the layout.
- **Make the best decision that elevates all three:** user experience (UX), developer experience (DX) and
  agent experience (AX) — always, without breaking anything.

## Rules of the road
- **Branch:** work on `oro-dev`. CI (`.github/workflows/android.yml`) builds and runs unit tests on push; it cancels superseded runs on the same branch.
- **No local Android build is assumed.** Write compile-safe Kotlin: grep for every symbol you call, check imports, exhaustive `when`, no smart casts on another module's public properties. Let CI compile; fix what it reports.
- **Tests:** JVM unit tests only (`core/data/src/test`, `app/src/test`). Add one for any pure logic you touch.
- **Room:** schema version lives in `OroDatabase`; every bump needs a migration whose SQL matches what Room generates, plus the exported JSON in `core/data/schemas`.
- **Settings JSON** (providers, MCP) lives in DataStore via `PreferencesRepository`; prefer additive fields with defaults.
- **UI:** Material 3 Expressive, tokens from the design system. One-line labels (`maxLines = 1`) on segmented controls and buttons. Sheets for short choices, full screens for forms with lists (e.g. `ProviderEditorScreen`).
- **Performance:** low-end phones are the target. Stable keys and `contentType` in lazy lists, no heavy work in composition, respect Lite mode (`OroTheme.liteMode`).
- **Quality gate:** `QualityAuditor` checks must stay high-precision -- a false positive costs every model a turn and teaches it to ignore the gate. Anything ambiguous is a TIP, never an ERROR. Fuzzy edit matching (`TextEdits`) must fail on ambiguity.
- **Device agent safety:** acts only during a task the user started (from the device screen, or a `use_phone` call in a chat the user is running; Leashed mode asks first); Pause/Stop always on screen; sensitive actions (send, pay, delete, passwords) ask first by default; never act on a stale element index; dial with ACTION_DIAL only, never place a call or send a message from an intent.
- **Images:** check `VisionSupport.supports(model)` (not the id overload) before sending images: community web bridges carry text only.
- **Agent harness changes:** keep tool schemas valid for strict providers (see `ToolSchemaTest`), keep the system prompt prefix stable (prompt caching), and add parser tests with real model output when touching `TextToolCallParser`.
- **Secrets:** API keys and tokens stay in DataStore; never write them into the project folder (`.oro/` may be committed by users).
- **Commits:** imperative subject, body explains why; end with the co-author trailer used in history.

## Handy facts
- Agent tool names: `read_file, write_file, edit_file, multi_edit, list_files, glob, grep, delete_file, move_file, check_syntax (no path = whole-project review), get_preview_errors, screenshot_preview, audit_page, web_fetch, search_project, docs_lookup, ask_user, save_memory, use_skill, add_ui_kit, open_pull_request, update_plan, spawn_subagent, request_approval`, `use_phone` (only while the accessibility service is connected) + `mcp__<server>__<tool>`. `read_file` takes `paths` (batch), `write_file` takes `append`, `grep` takes `context`.
- Device agent tool names: `tap, tap_text, type_text, scroll, scroll_to, press_key, open_app, open_url, quick_action, media, long_press, double_tap, swipe, drag, pinch, wait, wait_for, read_screen, read_page, look (vision only), device_status, list_apps, take_note, ask_user, finish_task`.
- Text-protocol models (web bridges, providers without function calling) get `TextToolProtocol.render(...)`, which teaches one raw-value format: `<tool_call><function=NAME><parameter=KEY>raw value</parameter></function></tool_call>` (lists/objects as JSON inside a parameter). `TextToolCallParser` still reads every older format; a call cut off by the output limit is never run.
- Project-level instructions for Orobi: `ORO.md`, `AGENTS.md`, `CLAUDE.md`, `.oro/instructions.md` in a user's project root.
- Per-project app data: `.oro/` (checkpoints, usage, deploy state, logs, skills).
