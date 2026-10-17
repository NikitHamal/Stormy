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
| `core/data/.../ai/local/` | On-device LLMs (MediaPipe LLM Inference, Qwen 2.5 `.task`) |
| `core/data/.../ai/skills/` | Skills (Markdown playbooks, `use_skill`) |
| `app/src/main/cpp/` | JNI bridge to the Needle/Whistle engine (`libneedle.a` per ABI) |
| `core/data/` | Everything not UI: AI providers, agent loop, tools, Room DB, repositories |
| `core/data/.../ai/agent/` | `AgentLoop`, `TextToolCallParser`, `TextToolProtocol`, `HistoryPruner`, `Autonomy` |
| `core/data/.../ai/tools/` | `AgentTools` (schemas), `ToolExecutor` (dispatch), `WorkspaceTools` (file tools), `UserQuestions` |
| `core/data/.../ai/providers/` | Custom providers, `KeyPool` (multi-key failover), `AnthropicClient`, presets |
| `core/data/.../ai/mcp/` | MCP Streamable HTTP client, registry, OAuth |
| `core/data/.../ai/checkpoint/`, `ai/usage/`, `ai/context/`, `preview/`, `deploy/` | Checkpoints, token/cost meter, project instructions, preview diagnostics, Netlify/GitHub Pages deploys |
| `core/designsystem/` | Theme, tokens (`Space`, `OroCorners`, `Sizes`), Orobi mascot, Expressive components (`FormSheet`, `ActionSheet`, `SegmentedPicker`) |
| `StormyWeb/` | HTML/CSS/JS replica of the whole app for fast UI experiments (open `StormyWeb/index.html`) |
| `launch-video/` | Deterministic 15 s launch film + procedural audio (`npm run render:all`) |
| `docs/` | `NEXT.md` roadmap, `perf-notes.md`, `harness-notes.md` |

## Rules of the road
- **Branch:** work on `oro-dev`. CI (`.github/workflows/android.yml`) builds and runs unit tests on push; it cancels superseded runs on the same branch.
- **No local Android build is assumed.** Write compile-safe Kotlin: grep for every symbol you call, check imports, exhaustive `when`, no smart casts on another module's public properties. Let CI compile; fix what it reports.
- **Tests:** JVM unit tests only (`core/data/src/test`, `app/src/test`). Add one for any pure logic you touch.
- **Room:** schema version lives in `OroDatabase`; every bump needs a migration whose SQL matches what Room generates, plus the exported JSON in `core/data/schemas`.
- **Settings JSON** (providers, MCP) lives in DataStore via `PreferencesRepository`; prefer additive fields with defaults.
- **UI:** Material 3 Expressive, tokens from the design system. One-line labels (`maxLines = 1`) on segmented controls and buttons. Sheets for short choices, full screens for forms with lists (e.g. `ProviderEditorScreen`).
- **Performance:** low-end phones are the target. Stable keys and `contentType` in lazy lists, no heavy work in composition, respect Lite mode (`OroTheme.liteMode`).
- **Agent harness changes:** keep tool schemas valid for strict providers (see `ToolSchemaTest`), keep the system prompt prefix stable (prompt caching), and add parser tests with real model output when touching `TextToolCallParser`.
- **Secrets:** API keys and tokens stay in DataStore; never write them into the project folder (`.oro/` may be committed by users).
- **Commits:** imperative subject, body explains why; end with the co-author trailer used in history.

## Handy facts
- Agent tool names: `read_file, write_file, edit_file, multi_edit, list_files, glob, grep, delete_file, move_file, check_syntax, get_preview_errors, screenshot_preview, audit_page, web_fetch, ask_user, save_memory, use_skill, update_plan, spawn_subagent, request_approval` + `mcp__<server>__<tool>`.
- Text-protocol models (K2 and other web bridges) get `TextToolProtocol.render(...)`; recovered calls are parsed by `TextToolCallParser`.
- Project-level instructions for Orobi: `ORO.md`, `AGENTS.md`, `CLAUDE.md`, `.oro/instructions.md` in a user's project root.
- Per-project app data: `.oro/` (checkpoints, usage, deploy state, logs, skills).
