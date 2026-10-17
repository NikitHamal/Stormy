# Oro — next steps (2026-10-17, after #28 round 2)

All free to build and run.

1. **Visual edit mode.** Tap an element in the live preview, say or type "make this bigger / warmer / move it left"; Orobi gets the element's selector, styles and a cropped screenshot. The preview inspector already exists.
2. **Live voice agent.** Hold the mic: Whistle streams words while you talk (`needle_stream_transcribe_process`), Orobi starts working on the partial brief, and you can interrupt.
3. **On-device embeddings for project search.** Needle's `needle_embed` (already linked) gives a local semantic index of the project, so "where is the pricing table" finds the file without sending code anywhere.
4. **Design-from-photo.** Photograph a sketch or a website you like; a vision model turns it into a starting layout in the project.
5. **Self-healing loop.** After every run, automatically render the changed pages (`screenshot_preview`), read preview errors and run `audit_page`; if something regressed, Orobi fixes it before saying "done".
6. **Version history browser.** A timeline of checkpoints per project with visual diffs (before/after screenshots), restore any point, branch from it.
7. **Template marketplace from skills.** Let skills carry starter files; share a skill + template as one link, install it in one tap.
8. **Multi-agent "crew".** A planner, a builder and a reviewer subagent with their own models (e.g. a local model for review, a cloud model for building), visible as lanes in the chat.
9. **Offline docs pack.** Bundle MDN-style references for HTML/CSS/JS in a small on-device index that `web_fetch` falls back to when offline.
10. **Android widgets and share-to-Oro.** Share a screenshot, URL or text from any app into a new Oro chat; a home-screen widget shows the last run's status and "Continue".
11. **Accessible-by-default mode.** Every run ends with an automatic accessibility pass; the chat shows a score that must not go down.
12. **Collaborative projects over GitHub.** Pull requests from Oro: Orobi opens a branch, commits, and opens a PR with screenshots; review comments come back as tasks.
13. **Battery- and data-aware runs.** On low battery or metered data, prefer on-device or cheap models, pause background runs, and say why.
14. **StormyWeb ↔ Compose sync check.** A script that diffs StormyWeb tokens with `Color.kt` / `Type.kt` in CI, so the HTML replica never drifts.
