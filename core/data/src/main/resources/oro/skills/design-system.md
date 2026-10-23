---
name: design-system
description: Tokens, type scale, spacing, colour, depth and component recipes for professional UI. Load first for any new UI or redesign
---
# Design system & visual craft

Professional UI is consistency, not decoration. Define tokens once, use them everywhere.

## 1. Tokens (put in `:root`, never hard-code values in components)
```css
:root{
  /* colour: one brand hue + neutrals + semantic. Pick the hue from the brief. */
  --brand-h: 248;                       /* change this one number to re-theme */
  --brand: hsl(var(--brand-h) 84% 60%);
  --brand-strong: hsl(var(--brand-h) 84% 52%);
  --brand-soft: hsl(var(--brand-h) 90% 96%);
  --bg: #ffffff; --surface: #f8fafc; --surface-2: #f1f5f9;
  --text: #0f172a; --text-2: #475569; --text-3: #94a3b8;
  --border: #e2e8f0;
  --success:#16a34a; --warning:#d97706; --danger:#dc2626;
  /* type */
  --font: "Inter", ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;
  --font-display: "Inter Tight", var(--font);
  --step--1: clamp(.83rem,.8rem + .15vw,.9rem);
  --step-0: clamp(1rem,.96rem + .2vw,1.06rem);
  --step-1: clamp(1.2rem,1.1rem + .5vw,1.4rem);
  --step-2: clamp(1.44rem,1.25rem + .9vw,1.9rem);
  --step-3: clamp(1.73rem,1.4rem + 1.6vw,2.6rem);
  --step-4: clamp(2.07rem,1.5rem + 2.8vw,3.6rem);
  /* space: 4px grid */
  --s-1:4px; --s-2:8px; --s-3:12px; --s-4:16px; --s-5:24px; --s-6:32px; --s-7:48px; --s-8:64px; --s-9:96px;
  /* shape & depth */
  --r-sm:8px; --r-md:12px; --r-lg:20px; --r-full:999px;
  --shadow-1: 0 1px 2px rgb(15 23 42 / .06), 0 1px 3px rgb(15 23 42 / .08);
  --shadow-2: 0 4px 12px rgb(15 23 42 / .08), 0 2px 4px rgb(15 23 42 / .06);
  --shadow-3: 0 20px 48px -12px rgb(15 23 42 / .25);
  /* motion */
  --ease-out: cubic-bezier(.16,1,.3,1); --ease-in-out: cubic-bezier(.65,0,.35,1);
  --ease-spring: cubic-bezier(.34,1.56,.64,1);
  --dur-1:120ms; --dur-2:200ms; --dur-3:320ms; --dur-4:600ms;
}
@media (prefers-color-scheme: dark){ :root:not([data-theme=light]){
  --bg:#0b0f19; --surface:#111827; --surface-2:#1f2937; --text:#f1f5f9; --text-2:#cbd5e1;
  --text-3:#64748b; --border:#1f2937; --brand-soft: hsl(var(--brand-h) 50% 16%);
}}
```
Tailwind projects: express the same tokens in `tailwind.config` (`theme.extend.colors.brand`, fontFamily) instead.

## 2. Rules that separate pro from amateur
- **One accent colour.** Neutrals do 90% of the work. Never rainbow buttons.
- **Type hierarchy:** max 2 families; 3–4 sizes per screen. Headings: tight leading (1.1–1.2),
  `letter-spacing:-.02em`, weight 700–800. Body: 1.5–1.65 leading, 60–75ch line length.
- **Spacing on the 4px grid.** Section padding `clamp(64px, 10vw, 128px)` block. Generous whitespace
  reads as premium. Related things close, unrelated things far (proximity).
- **Alignment:** a centred max-width container (`min(1200px, 100% - 2*var(--s-5))`). Everything
  snaps to its edges. Left-align body text; centre only short hero copy.
- **Depth:** cards = surface + 1px border + soft shadow; elevate on hover (shadow-2, translateY(-2px)).
- **Radius consistency:** pick one scale and stick to it; nested radius = outer − padding.
- **Contrast:** body text ≥ 4.5:1. Grey text on coloured backgrounds is the #1 amateur tell. White on a
  mid-tone brand button often lands near 3:1: darken the button (or use dark text) until it passes.
  `screenshot_preview` lists low-contrast text by element; fix each one it names.
- **Images:** `object-fit: cover`, fixed `aspect-ratio`, `loading="lazy"`, real alt text. For photos
  that match the content, `web_fetch` `https://api.openverse.org/v1/images/?q=<subject>&license_type=commercial&page_size=8`
  (one or two plain words, e.g. `latte art`, `coffee beans`) and use the returned URLs, with a small
  credit line. Never invent `images.unsplash.com/photo-…` ids — they 404. `https://picsum.photos/seed/<word>/1200/800`
  is random: only for abstract placeholders. Never broken `img.jpg` paths.
- **Icons:** one set only. Lucide via CDN (`<script src="https://unpkg.com/lucide@latest"></script>`
  then `lucide.createIcons()`, markup `<i data-lucide="arrow-right"></i>`), or inline SVG.
- **Fonts:** load from Google Fonts with `display=swap` and `preconnect`.
- **Details that sell it:** `::selection` in brand colour, `scroll-behavior:smooth`,
  `-webkit-font-smoothing:antialiased`, `text-wrap:balance` on headings, `text-wrap:pretty` on
  paragraphs, styled `:focus-visible` rings, custom scrollbar only if subtle.

## 3. Component recipes
```css
.btn{display:inline-flex;align-items:center;gap:.5em;padding:.75em 1.25em;border-radius:var(--r-full);
  font-weight:600;border:1px solid transparent;cursor:pointer;
  transition:transform var(--dur-1) var(--ease-out),box-shadow var(--dur-2),background var(--dur-2)}
.btn-primary{background:var(--brand);color:#fff;box-shadow:0 6px 20px -6px var(--brand)}
.btn-primary:hover{background:var(--brand-strong);transform:translateY(-1px)}
.btn:active{transform:translateY(0) scale(.98)}
.btn:focus-visible{outline:3px solid color-mix(in srgb,var(--brand) 40%,transparent);outline-offset:2px}
.card{background:var(--bg);border:1px solid var(--border);border-radius:var(--r-lg);padding:var(--s-5);
  box-shadow:var(--shadow-1);transition:transform var(--dur-3) var(--ease-out),box-shadow var(--dur-3)}
.card:hover{transform:translateY(-3px);box-shadow:var(--shadow-3)}
.input{width:100%;padding:.8em 1em;border:1px solid var(--border);border-radius:var(--r-md);
  background:var(--bg);color:var(--text);transition:border-color var(--dur-2),box-shadow var(--dur-2)}
.input:focus{outline:none;border-color:var(--brand);box-shadow:0 0 0 4px var(--brand-soft)}
.glass{background:color-mix(in srgb,var(--bg) 70%,transparent);backdrop-filter:blur(16px) saturate(1.4);
  border:1px solid color-mix(in srgb,var(--text) 8%,transparent)}
```
Hero backgrounds that look expensive: layered radial gradients (`radial-gradient(60% 50% at 20% 10%,
hsl(var(--brand-h) 90% 70% / .35), transparent)`), a subtle grid/noise overlay, or a blurred blob.

## 4. Layout
- Mobile first. Grid: `grid-template-columns: repeat(auto-fit, minmax(min(280px,100%),1fr))`.
- Sticky header: `position:sticky;top:0;backdrop-filter:blur(12px)`; shrink/add border on scroll.
- Breakpoints only where the content breaks (≈640, 900, 1200). Touch targets ≥ 44px.
- Test mentally at 360px wide: no horizontal scroll, no text smaller than 14px.

If the project has no system yet, call `add_ui_kit` to install Oro's production kit, then build on it.
