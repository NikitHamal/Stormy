# StormyWeb — Oro, in HTML

A modular HTML/CSS/JS replica of the Oro Android app, for trying UI and UX ideas in a browser
before writing Compose. Open `index.html` (any static server, e.g. `npx serve StormyWeb`; ES
modules need http, not file://). The toolbar jumps to any screen, flips light/dark and fits the
phone to the window.

```
css/tokens.css      colours (light + dark), type scale, space, corners: mirrored from core/designsystem
css/components.css  buttons, switch, SegmentedPicker, chips, fields, bottom sheets, snackbar, avatars
css/screens.css     stage + per-screen styles (editor tabs, chat, tool rows, ask_user, plan, composer, code)
js/app.js           stage, theme, navigation stack, screen registry
js/data.js          mock state shaped like the app's models
js/ui/kit.js        sheet / formSheet / actionSheet / snackbar / segmented / switch / field helpers
js/ui/orobi.js      Orobi (brand geometry) and the infinity loader
js/screens/*.js     one module per screen; each exports { render(params) -> element }
```

Screens: onboarding, home, editor (chat + code tabs, chats sheet, model picker, chat settings with
Leashed/Unleashed, context + usage, publish, ask_user stepper, docked plan, restore files, mic),
preview, browser (the browser agent: URL bar, live page, scripted runs that pause on the real
interactions — a secret only you type, an approval only you give — run bar, transcript, composer),
settings, AI providers, provider editor, MCP servers, voice dictation, skills, AI models.

**Keeping it in sync:** when a Compose screen changes, change its module here (and the tokens if
a colour or size moved). Names match the Kotlin files they mirror.
