# Browser agent — design

The device agent (accessibility-driven, `use_phone`) is out of `main`: Play policy
prohibits accessibility use that lets an app "autonomously initiate, plan, and
execute actions" outside verified accessibility tools (research note of
2026-10-24). It lives on in `oro-dev` if it is ever needed again.

In its place: a browser agent that runs entirely inside Oro — an in-app
WebView engine driven by the same `AgentLoop` as everything else. No
accessibility service, no overlay permission, no notification listener:
nothing a store reviewer can object to.

## Shape

Mirrors the device agent's proven structure, with the phone replaced by a page:

| Device agent (gone) | Browser agent |
|---|---|
| `Phone` interface (JVM-testable seam) | `Browser` interface |
| `DeviceController` (gestures, intents) | `BrowserEngine` (WebViews, JS bridge) |
| `ScreenReader` → `ScreenModel` | snapshot JS → `PageModel`/`PageState` |
| `DeviceAgent` singleton (State/Steps/run) | `BrowserAgent` singleton |
| `DeviceTools` + `DevicePrompt` | `BrowserTools` + `BrowserPrompt` |
| island overlay (accessibility window) | `BrowserScreen` + in-app pill (no permissions) |
| `use_phone` delegation tool | `use_browser` delegation tool |
| `SensitiveGuard` | `WebGuard` |
| `FormFill` | `FormFillWeb` |
| human check hand-off | `CaptchaWatch` hand-off (same policy: never solve it) |

## Engine

- `BrowserEngine` (app-process singleton) owns up to 3 tabs, each a `WebView`
  created with the application context, measured/laid out at a phone viewport
  and never attached to a window unless the user is watching. JS evaluation and
  `draw()` work detached, so runs continue when the screen is closed, the user
  edits code, switches chats, or locks the phone (a foreground `AgentRunService`
  run keeps the process alive).
- One JS bridge (`addJavascriptInterface`) + a document-start script
  (`WebViewCompat.addDocumentStartJavaScript`, fallback: inject per page). The
  start script runs in every frame, cross-origin included: frames register,
  post snapshots in chunks (no evaluateJavascript size limits), and poll a
  command queue so actions reach cross-origin iframes (Stripe forms, pay
  buttons) the parent document cannot touch.
- Captcha frames (reCAPTCHA/hCaptcha/Turnstile/Arkose/challenge pages) register
  observe-only: they feed detection, never take commands. The check is the
  user's to pass — the run waits, the banner says so, and it resumes itself
  when the check is gone.

## The page as a numbered list

Same trick that made the device agent work with weak models: the observation
is a compact numbered list (`[12] button "Submit"`, `[13] textbox "Email"`),
not a DOM dump or an image. Elements carry a `data-oid` tag; an action
re-resolves its oid against the live DOM (selector fallback, refusal on
ambiguity) so a stale index never clicks the wrong thing. Every action result
carries the fresh observation and an explicit "the page did not change" that
escalates on repeats. Password values are never in the observation — or
anywhere the model can read.

## One-shot forms, secrets, checks

- `fill_form` fills a whole form in one call (label/placeholder/aria/name
  matching, radios, selects, checkboxes, scroll-to, per-field report, never
  guesses an ambiguous name), then optionally presses the form's own submit.
- Secrets never travel through the model:
  - `WebVault` (Keystore AES-GCM, per-domain entries) — `use_credential(id)`
    fills username+password inside the engine; the result names the fields,
    never the values.
  - `ask_user_secret` — a masked card in `BrowserScreen`; the user types into
    the page directly (focus mode) or into the card. The model only learns
    that a secret was provided.
  - HTTP-auth prompts and confirmations are answered in the UI, not the chat.
- Sensitive actions (pay, buy, send, delete, post, publish, donate, install,
  password entry outside the vault) wait for an explicit Allow, by default
  (`WebGuard` judges the exact resolved element, coordinates included).

## Web muscle

- `web_search`: keyless search for every agent (coding agent, subagents,
  browser agent): DuckDuckGo HTML → DDG Lite → Mojeek → public SearXNG,
  parsed to title/url/snippet, redirect links decoded, deduped.
- `read_page`: readability-style markdown/text/links extraction, plus raw
  `html` mode for scraping markup or code samples.
- `extract`: structured scraping in one call -- one JSON row per selector
  match, fields by child selector or attribute (tables, card grids, lists).
- `evaluate`: guarded JS for everything else.
- `screenshot`: viewport or full page, JPEG, for vision models only
  (`VisionSupport`).
- Downloads are saved by the engine and readable as text (`read_download`);
  uploads come from project files (`upload_file`).
- `wait_for(text|selector|url, gone)` + DOM-stability settle; navigation waits
  on request inflight + structure hash, not a fixed sleep.

## Coordinating with it (and with subagents)

Delegation never has to block: `spawn_subagent(background=true)` and
`use_browser(background=true)` hand back a session id at once, and the
coordinator keeps working while the delegate works. Three tools close the
loop -- `agents_status` (live phases, rounds, last step, queued follow-ups),
`follow_up` (a message that lands before the session's next round: subagent
loops and browser runs both drain their queue between rounds), and
`wait_for_agent` (collect the summary when it is needed; a timeout leaves the
run going). Background browser runs register as orchestrator sessions
(`Subagents.BROWSER_SESSION`), so subagents and browser work are watched with
the same tools and the same cards.

The user's surfaces for a live run: the tool card's sheet (live transcript,
plus an eye that opens the page), the browser screen, and a notification when
the app is in the background. There is deliberately no floating pill and no
home-screen entry: the browser is the coding agent's tool, not a parallel app.

## Two ways in (plus subagents)

- `BrowserScreen`: the user types a task and watches it live (URL bar, tabs,
  transcript, Pause/Stop), or just browses manually — the engine is a real
  browser.
- `use_browser`: the coding agent hands over a task mid-run
  (`open_preview=true` starts the dev server and opens the project's live
  preview). Subagents with the `BROWSER` capability (explore, plan, build,
  general) can call it too; one browser run at a time, busy runs say so.
- While a run is live and the user is anywhere else in Oro, a pill shows the
  current step and opens the screen; the run never stops because the user
  navigated away.

## Testability

Everything with logic is pure Kotlin behind the `Browser` seam: `PageModel`
parsing/rendering, `FormFillWeb` matching, `CaptchaWatch`, `WebGuard`,
`WebSearch` parsers, and `BrowserAgent`'s run loop against a scripted
`FakeBrowser` (scenario bench, like the device agent had). The WebView itself
is thin: JS in, JSON out, actions on the main thread.
