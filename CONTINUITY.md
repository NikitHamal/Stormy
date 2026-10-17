# CONTINUITY.md — session ledger

Update this file at the end of every working session (or before a risky step), so a new agent
or a cut-off session can resume. Newest entry on top. Keep entries short and factual.

## How to resume
1. `git log --oneline -15` on `oro-dev`, and read the latest entry below.
2. Check the last CI run for the branch (Actions → Android CI). Fix compile errors first.
3. Continue from "Next" of the latest entry.

---

## 2026-10-17 — issue #28, second round (in progress)
**Done this session**
- Wrap fixes (one-line segmented labels, sheet buttons), provider editor as a full screen,
  well-known vendors seeded as built-ins, fetched models default off.
- Merged `main` (StormyWeb). Removed the duplicate Chat/Agent chip from the empty chat.
- Autonomy: Leashed (approve every change) / Unleashed (asks only for risky steps) in Chat settings.
- AGENTS.md + this ledger.

**In progress / next** (see the issue comment for the full list)
- Dictation: system SpeechRecognizer + Whistle on-device STT (JNI to `libneedle.a`, model downloaded on demand).
- Preview screenshot / responsive check / page audit tools (offscreen WebView, images to vision models).
- MCP OAuth sign-in, project sharing (zip), skills (`.oro/skills/*.md`).
- Offline local LLM (MediaPipe LLM Inference, Qwen 2.5 0.5B/1.5B `.task` from litert-community).
- StormyWeb: full modular replica of the Oro app. Launch video: a real landing page in the live scene.

## 2026-10-17 — issue #28, first round
Caret fix, Chat split-button tab, plan docked on composer, one-line tool rows, single-stage
editor state, ORO.md/AGENTS.md instructions, preview errors to the agent, Anthropic prompt
caching + per-chat usage meter, checkpoints, background runs + notifications, MCP client,
Netlify / GitHub Pages deploy. CI green at `af811ef`.

## 2026-10-17 — harness / providers / sessions overhaul
Multi-question ask_user, K2 tool-call fixes, custom OpenAI/Anthropic providers with key
failover, multiple chat sessions per project (Room v6), Lite mode, launch video with sound.
