# Oro — launch film (Cinetic)

A 35 s launch film for Oro, directed and built with the [Cinetic](https://github.com/Leonxlnx/cinetic)
skill: one beat-locked timeline drives picture, sound and QA; everything is rendered, measured and
critiqued. It replaces the 15 s cut in `../launch-video/` as the hero film.

**The idea:** *All this, or one sentence.* A pile of toolchain windows (npm errors, installers, API-key
prompts, paywalls) collapses into one point that draws itself into Orobi's infinity loop. That loop is
the film's device: it is the send button, the spinner of every tool row, the seed of the iris into the
live site, the preview pill, the model chip's icon, the publish spinner, and finally Orobi himself in
the lockup.

**Spec:** 1920×1080 @ 60 fps, 35 s, 120 BPM (beat 30 f, bar 120 f), crisp pace, 25 words of copy.
Score: earshot crisp kit + synth band in F major; the drop lands on the live-site reveal (40%), the
lockup is a stop-down with one hit. Re-layouts: 9:16 and 1:1 from the same timeline (never a crop).

| File | What it is |
|---|---|
| `BRIEF.md`, `TREATMENT.md` | the brief (hard bans + brand-supplied exceptions) and the treatment: concepts, drawn techniques, beat sheet, copy |
| `src/timeline.ts` | the only source of time: acts, cues, copy (`grid-check` validates it) |
| `src/acts/*` | nine acts: pile, turn, build, live, change, fly, free, ship, end |
| `src/ui/kit.tsx` | Oro's product UI drawn from the app (composer, tool rows, Mira's site, UI block, publish sheet) |
| `src/data.ts` | every product string and number, asserted |
| `src/sync.ts` | sound events computed from the picture code → `out/cues.json` |
| `audio/score.json`, `audio/kit.json`, `audio/kit/` | the score and the rendered earshot kit (so it rebuilds without earshot) |
| `deliver/` | the finished films and poster |

## Render
```bash
npm install                      # Remotion 4.0.529; Chromium headless shell via REMOTION_BROWSER
npm run check                    # tsc + lint-film + grid-check
npx tsx scripts/export-cues.ts && python3 scripts/audio/kit.py render && npm run audio
bash scripts/render.sh Film out/preview.mp4 --preview                 # fast preview
bash scripts/render.sh Film out/film.mp4 --blur --budget 4 \
  --accept-fast 812-845 --accept-fast 1280-1325 --accept-fast 1765-1845 --accept-cut 834-842
REMOTION_FORMAT=9x16 bash scripts/render.sh Film out/film-9x16.mp4 --preview   # social re-layouts
REMOTION_FORMAT=1x1  bash scripts/render.sh Film out/film-1x1.mp4 --preview
```
Python tools need numpy, scipy, soundfile, pyloudnorm, opencv, librosa; earshot 0.3+ renders the kit.

## QA (shipped files)
- `probe.py` and `check-sync.py`: PASS on the master, 9:16 and 1:1 (BT.709, yuv420p, AAC 48 kHz, lag ≤ 48 samples).
- `forensics.py`: clean with the designed cuts declared (`--cuts 834-841,1200,1320,1560,1800 --freeze-ok 225-240`) except one reading hold on "Free models." (f1359–1423), kept on purpose.
- `av-audit.py`: warnings only (a few effects masked under the drop, the crash hit's onset). Reports in `deliver/qa/`.
