# CONTINUITY.md — session ledger

Update this file at the end of every working session (or before a risky step), so a new agent
or a cut-off session can resume. Newest entry on top. Keep entries short and factual.

## How to resume
1. `git log --oneline -15` on `oro-dev`, and read the latest entry below.
2. Check the last CI run for the branch (Actions → Android CI). Fix compile errors first.
3. Continue from "Next" of the latest entry.

---

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
