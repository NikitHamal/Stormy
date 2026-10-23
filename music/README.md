# Oro — Original Soundtrack

Five instrumental pieces for Oro and Orobi, about 11 minutes in all. There are no samples, no
recordings and no vocals. Every note and every sound is made in code by the small synthesiser in
`engine/`, from the scores in `songs/`.

| # | Track | Key · tempo | Length | What it says |
|---|---|---|---|---|
| 1 | **Orobi (Main Theme)** | D major · 100 | 2:38 | The whole app in one arc. Bells draw the motif in the dark, the way the loader draws itself. The piano wakes Orobi with the theme, the work finds its groove, strings lift it, and the anthem is the moment the site goes live. Then a quiet bridge, and home. |
| 2 | **Infinity Loop** | F major · 80, swung | 2:17 | Working together late. Warm electric piano on a ii–V–I–vi loop, vinyl dust and a lazy beat. The motif is half-whispered on a nylon pluck and is never quite the same twice. Made to sit under a long coding session. |
| 3 | **Describe It** | A major · 122 | 1:46 | The spark. You type one sentence and something starts building. The arps are keystrokes, the chords breathe with the kick like a preview refreshing, and the hook is Orobi's motif sped up and grinning. |
| 4 | **Night Build** | B minor → D major · 68 | 2:28 | Orobi keeps working while you sleep. Pads breathe, a soft heartbeat keeps time, and the piano remembers the motif slowly, in minor. At the end the harmony turns to D major: the same notes seen from the other side, and it is morning. |
| 5 | **Unleashed** | D → E major · 132 | 1:53 | Ship it. Driving drums and the full theme on a big lead. For the last chorus the whole song lifts a whole step, from D (home) to E: the same tune, further. |

**The leitmotif.** Every track quotes one phrase, defined once as `MOTIF` / `ANSWER` / `THEME` in
`songs/theory.mjs`:

- It rises 1–3–5 (D–F♯–A), like the infinity loader drawing itself.
- It turns down 6–5–3–2 (B–A–F♯–E), like Orobi's blink.
- The answer climbs again and rests on the third: curiosity, then warmth.

Each track gives the phrase a different character:

- **Orobi:** played straight.
- **Infinity Loop:** laid back, behind the beat.
- **Describe It:** in bouncing sixteenths.
- **Night Build:** in minor, then major.
- **Unleashed:** in harmony, and then a key higher.

## Files
```
engine/core.mjs         DSP: oscillators (PolyBLEP), state-variable filters, envelopes, voices
engine/instruments.mjs  supersaw pad, strings, FM electric piano, additive piano, Karplus-Strong pluck,
                        synth arp, FM bells, lead, bass, 808, full drum kit, risers, impacts, vinyl
engine/song.mjs         tempo grid + swing, score language, drum patterns, side-chain, reverb, delay, master
songs/theory.mjs        keys, scale degrees, chord voicings, Orobi's leitmotif
songs/0N-*.mjs          one score per track
render.mjs              render -> 24-bit WAV + MP3 320k (cover art embedded) + spectrogram + report.json
cover.svg               album art (Orobi, brand geometry)
index.html              a small player for the rendered album
out/                    rendered tracks (MP3 committed; WAVs are rebuilt, not committed)
```

## Render
```bash
cd music
npm install              # ffmpeg-static (if its install script is blocked: node node_modules/ffmpeg-static/install.js)
npm run render           # all five tracks, about 4 minutes on 2 CPU cores
node render.mjs 3        # just track 3
```
Rendering is deterministic: a seeded random generator drives the humanisation and noise, so the
same code produces the same audio. Mastering is two-pass ffmpeg `loudnorm` to -14 LUFS integrated
and under -1 dBTP, which suits streaming platforms. The master chain also high-passes at 34 Hz,
cuts a little below 75 Hz (phones can't play that range; it only uses up headroom), adds a touch
of air above 8 kHz, then applies glue compression and a soft clip.

## Writing music with it
A score is plain JavaScript:
```js
const s = new Song({ title: 'New piece', bpm: 96, bars: 32 });
s.track('piano', { gain: 0.3, reverb: 0.3 });
s.seq('piano', I.piano, s.bar(0), score('D5', THEME));                   // the theme, in D
s.drums('drums', 4, 8, { kick: 'x...x...x...x...', snare: '....x.......x...' });
s.chord('pad', I.pad, s.bar(0), chord('D', 3, 'maj9'), 16);
```
Note tokens are `NOTE:beats`, `r:beats` for a rest and `[A3,C#4,E4]:beats` for a chord, with an
optional `@velocity`. Drum patterns use one character per 16th: `x` hit, `o` soft, `X` accent,
`.` rest.
