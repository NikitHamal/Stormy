# BRIEF: Oro launch film

**Spec:** `1920x1080@60, 33s, 120BPM, pace: crisp, audio: earshot kit (crisp) + music`
**Format:** launch film (cinetic §1 row 1) · **Engine:** Remotion 4.0.529 · **Deliverables:** 16:9 master, 9:16 and 1:1 re-layouts, poster.

**Product (one line):** Oro is an Android app where you describe a website and its agent, Orobi, builds it live on your phone, on free models, with no key.
**Viewer:** a builder scrolling a feed (X, YouTube, Play Store listing) and the repo's README; often muted.
**Pace:** crisp, by request ("go all out"): 120 BPM, fragments up to 5 words, up to 10 on screen, never a sentence at once.

## Assumptions (inferred, not told)
- The 15 s film in `launch-video/` is the previous cut; this replaces it as the hero film and keeps the same product truths (Orobi, live preview, free models, publish).
- Data on screen is invented but consistent (`src/data.ts` asserts it): a portfolio for "Mira Okafor, street photographer".
- Netlify publish and the model names are real features of the app (`DeploySheet`, keyless providers).

## Hard bans (copied from SKILL.md, the QA contract)
- Words: no eyebrow/kicker labels, no stacked taglines, no "Introducing…", no full sentence at once, no text walls or filler text (fake metrics, decorative mono captions, lorem ipsum).
- Type: no serif, no italic or oblique (or a skew faking one).
- Colour: no orange/amber/beige/cream/tan/sand; no neon glows, bloom or halos; no purple/violet/indigo; no multi-hue gradient; no glassmorphism.
- Decoration: no emoji or stock icons, no AI sparkles, particles, confetti, lens flares or code rain; no bouncy overshoot except a true landing; no per-letter tricks on copy except the one hero word.

## Brand-supplied exceptions (the brand wins)
- **Magenta `#F800F8`** (Orobi's markings and the app's working colour) — inside the banned purple/neon region. It is Oro's identity. Used as the one accent, meaning "Orobi is doing it" / "just made by Orobi". Lines marked `// cinetic:brand-supplied magenta`.
- **Peach `#FDDFC6`** (Orobi's body on dark) — inside the beige region. Mascot body only, never a paper or a glow. Marked `// cinetic:brand-supplied peach`.
- **Poppins** (the app's UI face) — a geometric sans; chosen because the product is set in it. JetBrains Mono for code.
- **The infinity loop** (Orobi's body and the app's loader) is the film's device: it is the app's own object.
- **Ink-navy stage `#090C17` / `#10142A` and Orobi's ink `#1A1F4A`** — inside the banned indigo region by hue; Oro's ink is a navy, and the stage is tinted toward it so the film carries the brand's world. Marked `// cinetic:brand-supplied ink-navy` / `ink`.
