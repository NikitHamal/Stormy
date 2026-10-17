# Oro — 15s launch film

A deterministic motion-graphics composition (HTML/CSS/SVG + JS) and a headless render pipeline.
Every frame is a pure function of time `t`: the page exposes `window.renderAt(t)` and the renderer
seeks frame by frame. There are no wall-clock animations, CSS transitions, or randomness. The
same `t` always produces the same pixels (verified byte-identical across fresh browsers).

```
launch-video/
  config.json          ← ALL copy, colours, timings, formats, fps, encode settings, scene order
  src/index.html       ← composition page (?format=landscape|vertical, ?preview=1 for scrubber)
  src/css/style.css    ← styles; colours come from config via CSS vars (--dark-bg, --mascot-magenta…)
  src/js/main.js       ← builds the stage, timeline from config, camera/parallax, window.renderAt(t)
  src/js/core.js       ← easing (Material emphasized/standard, back-out), tween/keys/saccades/blinks/hop helpers
  src/js/orobi.js      ← Orobi mascot, infinity loader, sparkle glyph (brand-locked geometry)
  src/js/phone.js      ← phone frame + the photographer site that Orobi "builds"
  src/js/layout.js     ← per-format geometry (landscape vs vertical); scenes read positions from here
  src/js/scenes/*.js   ← one file per scene (wake, describe, live, models, endcard)
  src/js/preview.js    ← scrubber UI (only loaded with ?preview=1)
  scripts/render.mjs   ← headless Chromium → PNG frames piped into ffmpeg (H.264)
  scripts/stills.mjs   ← PNG key frames / QA sweeps / poster
  scripts/contact.mjs  ← tiles a folder of stills into contact sheets for review
  scripts/serve.mjs    ← static server for preview
  assets/fonts/        ← Poppins 400/500/600/700 + JetBrains Mono (OFL, bundled for offline renders)
  out/                 ← committed deliverables (mp4s + poster.png)
```

## Setup and rendering

```bash
cd launch-video
npm install
npx playwright install chromium          # once; on bare Linux also: sudo npx playwright install-deps chromium
npm run render                           # → out/oro-launch-1080p.mp4   (1920x1080, 60fps)
npm run render:vertical                  # → out/oro-launch-vertical.mp4 (1080x1920, 60fps)
npm run render:all                       # both + out/poster.png
npm run stills                           # config.stills → out/stills/*.png (gitignored)
npm run preview                          # http://127.0.0.1:5173/src/index.html?preview=1  (Space=play, ←/→ = frame)
```

Useful flags: `node scripts/render.mjs --format vertical --fps 30 --from 2.5 --to 6 --out out/test.mp4 --workers 2`,
`node scripts/stills.mjs --every 0.5 --dir out/qa [--format vertical]` and then
`node scripts/contact.mjs --dir out/qa` to review a full sweep at once. `node scripts/stills.mjs --poster` writes `out/poster.png` at `meta.poster`.

How rendering works: `scripts/lib.mjs` serves `launch-video/` on a random localhost port. Chromium opens
`src/index.html?format=…` at exactly the output resolution (DPR 1) and waits for `window.__ready`, which
is set after the bundled fonts have loaded. N pages (`encode.workers`, default 2) render interleaved frames.
Each frame calls `renderAt(i/fps)`, then CDP `Page.captureScreenshot` (PNG). A small reorder buffer
with bounded look-ahead writes the PNGs in order to ffmpeg's stdin (`image2pipe`). No frames
touch the disk. ffmpeg (`ffmpeg-static`) encodes `libx264 -preset slow -crf 18 -tune animation
-pix_fmt yuv420p -movflags +faststart` (BT.709 tags). A full 900-frame 1080p60 render takes about 60s on 2 CPUs and produces about 2.7MB with audio.

Vertical (1080x1920) keeps key content out of the top ~8% and bottom ~15% that Reels/Shorts/TikTok cover, runs text larger (`.fmt-v` overrides in style.css), and lays the providers out as two labelled groups instead of the landscape orbit.

Check an output with:
`node_modules/ffprobe-static/bin/linux/x64/ffprobe -v error -show_entries stream=width,height,r_frame_rate,nb_frames:format=duration out/oro-launch-1080p.mp4`

## Sound

Everything you hear is synthesised in `scripts/audio.mjs` -- no samples, no downloads, seeded PRNG,
so the same code always produces the same bytes. `npm run audio` writes `out/oro-launch-audio.wav`;
every `render` regenerates it (about 5 s) and muxes it as AAC 192k in the same encode
(`--no-audio` for picture only). `node scripts/audio.mjs --stems` also writes music/sfx stems.

- **One timeline.** `src/js/cues.js` imports the `T` beat tables from every scene and maps them
  through the scene durations in `config.json`, exactly like `renderAt`. Retime a scene and its
  sounds move with it; add a beat and add one `add(t, kind)` line.
- **Score.** 120 BPM in D major, bars starting at 0.5 s so the 2.5 s and 12.5 s cuts are downbeats
  (6.0 / 10.0 land on beat three). Intro pad → light kick/hats (describe) → full groove with
  16th arp (live, drums drop out under the whip) → half-time lift in the light scene → snare roll and
  riser into the end-card impact, then the tonic chord rings out.
- **Sound design.** Typing ticks per prompt character, send tap + whoosh, Orobi's gulp, hop/land,
  wake chirp and glances, token plinks (pentatonic), page-build pops, live ping, the whip pan
  (panned right → left with the world), per-row checks rising in pitch, a success chime, the match cut
  shimmer, provider plucks, riser + sub-drop impact, wordmark plucks, logo chime and sparkles.
- **Mix.** Kick side-chains the bed; the bed ducks under the SFX bus (envelope follower); shared
  Freeverb-style reverb and a ping-pong delay; tanh saturation; two-pass ffmpeg `loudnorm` to
  -14 LUFS integrated / -1 dBTP. Every voice has 1 ms in / 4 ms out fades (no clicks).

## Editing (for a future agent)

**You usually only need `config.json`.**

- **Copy:** each scene object has its strings (`headline`, `subline`, `prompt`, `tokens`, `toolRun`, `free`/`byo`
  providers, `wordmark`, `tagline`, `platform`, and `site.*` for the fake portfolio). After a copy change, render stills to
  check that nothing collides. Long headlines can overlap the phone/panel in landscape; shorten the copy or change `size` in `layout.js`.
- **Colours:** `palette.dark`, `palette.light` and `palette.mascot`. They are exposed to CSS as `--dark-*`, `--light-*` and `--mascot-*`.
  The mascot colours are brand-fixed, so don't change them.
- **Timing:** `scenes[].duration`. Each scene is authored in *design seconds* (`design` in its module), and a
  different duration **retimes that scene's internal beats proportionally** (local time = elapsed × design / duration).
  Keep `meta.duration` equal to the sum of the scene durations.
- **Scene order:** the order of `scenes[]` is the playback order. Every scene draws its own full state, so reordering
  works mechanically. The crafted hand-offs (Orobi's position at cuts, the row→light-sheet match cut, the hop across the iris)
  assume the default order, so expect to retouch transitions after reordering.
- **Resolution/fps/encoding:** `formats.*.width/height/out`, `meta.fps`, `encode.*`.
- **Positions per format:** `src/js/layout.js` (landscape and vertical share all scene code).
- **Choreography:** each scene file starts with a `T` table of beat times (design seconds). Motion uses
  `ease.emph` (cubic-bezier(.05,.7,.1,1)) for entrances, `ease.std` (.2,0,0,1) for standard moves, `ease.back` for landings, and
  `hop()` for squash and stretch. Rules: no `Date`, no `Math.random`, no CSS transitions or animations, and never read the DOM to time anything.

Scene module API: `{ id, design, underlay?, build(ctx, sceneCfg) → { roots: [...] , ...refs }, render(lt, ctx, sceneCfg, refs) }`.
`render` sets `ctx.o` (Orobi state), `ctx.cam` ({x,y,s,blur}: camera with per-layer parallax: bg 0.35, type 0.82, main 1, fore 1.18),
`ctx.bg` (stage colour) and drives `ctx.phone`. `underlay: s` keeps the previous scene's last frame visible underneath for `s` seconds.

## Scene list (default timings)

| # | id | time | content |
|---|----|------|---------|
| 1 | `wake` | 0.0–2.5s | Black. The infinity loader draws in (dash accelerates, then settles to 1 loop per 1.6s). The loop inflates into Orobi: the body fades in, the markings slide in and Orobi hops. He lands with a squash, his eyes blink open and he glances left, then right. Camera eases from 1.12× to 1×. |
| 2 | `describe` | 2.5–6.0s | "Describe it." (masked word reveal). A prompt pill expands and types "Build a portfolio for a photographer" while Orobi's eyes follow the caret in saccades. On send, the pill collapses into an orb that arcs into Orobi (gulp squash). Orobi turns back into the loader (thinking) and code tokens `<nav> <h1> grid { } <img> </>` arc into a phone rising into frame. |
| 3 | `live` | 6.0–10.0s | "See it live." The site assembles in the phone with a staggered build: nav, hero photo (masked), title, gallery tiles, contact, CTA. The page scrolls and the Live dot pings green. The loader pops back into Orobi, who peeks from behind the phone. A **whip pan** (horizontal motion blur) leads to "Built with you." and the agent tool-run: Read index.html → Edited style.css +24 −3 → Wrote gallery.html → Committed "Add gallery" → ✓ **Site is live**. Each step has a mini infinity spinner that becomes a check. |
| 4 | `models` | 10.0–12.5s | **Match cut:** the green "Site is live" row grows into a full light frame. Ink Orobi (peach eyes) drops in with a squash. "Any model. / No key." Free/no-key chips (Pollinations, Kilo, OVHcloud, K2 Horizon, filled) orbit on an inner ring, and bring-your-own chips (Gemini, Groq, OpenRouter, NVIDIA, Cerebras, Mistral, outlined with a key icon) orbit on a counter-rotating outer ring, passing in front of and behind Orobi. Everything exits and Orobi takes off. |
| 5 | `endcard` | 12.5–15.0s | **Match cut on action:** mid-hop, a dark iris opens from Orobi and he flips from ink to peach. He lands beside the "Oro" wordmark (Poppins SemiBold), which slides out from behind him. Sparkles burst (magenta and apricot), followed by the tagline "Build for the web, from a sentence." and the "ANDROID" label. **Fully static from ~14.1s** to the end, so the last frame works as a poster/loop. |

The poster is `meta.poster` = 14.6s.

## Brand rules (must hold in any edit)

- Product **Oro**; assistant/mascot **Orobi**. Positioning: a pocket studio for the web. You describe a website, Orobi
  builds it with you, and you see it live on your phone.
- Only real features: an agent that reads, writes and edits project files; live preview; free models with no key
  (Pollinations, Kilo, OVHcloud, K2 Horizon) and bring-your-own (Gemini, Groq, OpenRouter, NVIDIA, Cerebras, Mistral);
  Git built in; onboarding with a prompt-to-page flow. **No invented features, ratings, user counts or prices.**
- Fonts: Poppins (UI and headlines, 400/500/600/700) and JetBrains Mono (code). Both are bundled in `assets/fonts`.
- Dark palette: bg #0B0D12, surface #12151C, surface2 #181C25, surface3 #222834, text #E8ECF2, muted #8A93A3,
  outline #262C38, accent #F2F4F7. Light palette: bg #F7F8FA, surface #FFFFFF, text #0E1117, muted #5A6373.
- Mascot colours (fixed): peach #FDDFC6, magenta #F800F8, ink #1A1F4A, apricot #FFB38A, success #3DDC97.
  **The UI is monochrome; the mascot carries the colour.** Green is used only for success/live states.
- Orobi geometry is locked in `src/js/orobi.js` (`GEO`). The viewBox is `200 400 1200 820`, the body is evenodd so both loops are open
  holes, and the magenta markings are clipped to the body. Eyes are at (717,634) and (885,634), rx 11.5 and ry 14.5, with a white catchlight
  of r 3.6 at (+3.4, −5.2). Gaze is clamped to ±10 horizontally, 2.5 up and 6 down (enforced in `createOrobi`). Glances are ~150ms saccades followed by a hold.
  Blinks are a ~100ms vertical squash to 0.1. On dark backgrounds Orobi has a peach body and ink eyes; on light backgrounds an ink body and peach eyes.
  The ground shadow is an ellipse at (800,1185), rx 400, ry 24.
- Infinity loader: Orobi's own centre line, stroke 90 with round caps, a 20% magenta track and a 24/76 magenta dash (pathLength 100),
  running at 1 loop per 1.6s. The signature transition is loader ⇄ Orobi: the loop fades and fattens while the body fades in, the markings slide in and the eyes blink open.
- Sparkle: a four-point star made of four quadratic curves pinched to 15% of the radius (`sparklePath`), in magenta or apricot.
- Motion: springy but controlled. Use emphasized-decelerate for entrances, standard for moves, a small back-out overshoot on landings,
  and squash and stretch on hops.

## Notes

- The video is silent by design (no audio track).
- `ffmpeg-static` ships ffmpeg 7 (no `drawtext`). `ffprobe-static` is included for verification.
- Fonts are © their authors under the SIL OFL (licences are in `assets/fonts/`).
