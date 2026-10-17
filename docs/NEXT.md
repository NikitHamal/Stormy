# Oro — next steps (2026-10-17)

High-leverage, production-ready ideas, roughly in priority order.

1. **Checkpoints and per-run rewind.** Snapshot touched files before every agent run (cheap: copy-on-write into `.oro/checkpoints/<runId>`), show "Restore to before this message" on each user bubble. This is the single biggest trust feature of Cursor and Claude Code.
2. **Diff review gate.** Optional "ask before writing" mode: edits queue as hunks in a review sheet (accept / reject per hunk) instead of hitting disk immediately. Reuses DiffView and the approval plumbing.
3. **Background runs + notifications.** Move the agent loop into a foreground `Service` bound to the session, so a run survives leaving the editor or locking the phone, and post a notification when it finishes or asks a question. Sessions already isolate history; this makes many parallel chats real.
4. **Preview feedback loop.** Pipe WebView `console.error`, uncaught exceptions and failed network requests back to Orobi as a tool (`get_preview_errors`), plus a `screenshot_preview` tool for vision models. The agent then fixes what it broke without the user copying errors.
5. **MCP client.** Support remote MCP servers (Streamable HTTP + OAuth) as a tool source per project. Instant ecosystem: GitHub, Supabase, Figma, docs search.
6. **Prompt caching and cost meter.** Send `cache_control` breakpoints on the Anthropic path and keep the system prompt + tool list byte-stable on OpenAI-style providers; show per-session tokens and estimated cost next to the context ring.
7. **Model routing.** A cheap/fast model for titles, compaction, commit messages and subagent search; the main model for edits. Per-session model override (store `modelId` on the session).
8. **Project instructions file.** Read `ORO.md` / `AGENTS.md` from the project root into the system prompt (opencode/Codex convention), editable from project settings.
9. **One-tap deploy.** Publish the project to GitHub Pages / Netlify / Cloudflare Pages from the app, with the live URL posted into the chat. Closes the "build a website on my phone" loop.
10. **Templates gallery 2.0 + "remix".** Start from a prompt-generated template preview, or remix any public GitHub repo / CodePen into a project.
11. **Voice prompts.** On-device speech-to-text (Android `SpeechRecognizer`) in the composer; on a phone, talking is faster than typing a long brief.
12. **Session search and export.** Full-text search across every session of every project (FTS4 table over `chat_messages`), and export a session as Markdown or share link.
13. **Crash-free low-end path.** Baseline Profile generated on a real low-end device in CI (Gradle Managed Device), plus a macrobenchmark for cold start and chat scroll so regressions fail the build.
14. **Key vault hardening.** Move API keys from DataStore into EncryptedSharedPreferences / Keystore-wrapped storage, with biometric unlock for revealing keys.
