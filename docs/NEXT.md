# Oro — next steps (2026-10-17, after the size cut)

Done from the previous list: live Whistle, project search, design from a photo, self-checking runs,
checkpoint timeline, skills with starter files, offline docs pack, GitHub pull requests.

1. **PR review loop.** Pull a PR's review comments into the chat as tasks; Orobi fixes them and pushes to the same branch.
2. **Pull request button.** A "Propose as PR" action in the Git screen and the timeline (pick a checkpoint and open a PR from it), next to the existing `open_pull_request` tool.
3. **Before/after screenshots in the timeline.** Store a phone-width render with each checkpoint; the timeline shows them side by side, and PR bodies embed them.
4. **Voice commands while Orobi works.** Live Whistle already streams; say "stop", "undo that" or "make it blue" during a run and it becomes an interrupt or a queued follow-up.
5. **Semantic search with a small budget.** Needle's `needle_embed` is already linked; an index of up to a few thousand chunks would add meaning-based matches to `search_project` without new libraries.
6. **Docs pack you can extend.** Let skills and projects add their own entries to `docs_lookup` (a framework's API, a design system's tokens), so offline answers cover what the project really uses.
7. **Self-check scores in the chat.** Show what the check found and fixed (overflow, broken images, syntax, console errors) as a compact card instead of a one-line note.
8. **Starter gallery.** A "New project from skill" flow that lists skills with starter files as templates with a preview render each.
9. **Photo to components.** For a photo of a full app screen, split the design into sections and build them one by one, checking each against the photo with `screenshot_preview`.
10. **APK size guard in CI.** Fail the build when the release APK grows more than 1 MB over the last release, so a heavy dependency never slips in again.
11. **Low-end device profile.** Detect RAM and CPU at first launch; pick lighter defaults (Lite mode, fewer preview renders in self-check, smaller history window).
12. **Offline queue.** Messages typed without network wait in a queue and run when the connection returns, with a notification.
13. **Per-project secrets vault.** API keys a site needs (maps, forms) kept encrypted, injected into the preview, never written into files or sent to the model.
14. **Share a run as a replay.** Export a chat run (plan, tool calls, diffs, screenshots) as a small HTML page someone can scroll through.
