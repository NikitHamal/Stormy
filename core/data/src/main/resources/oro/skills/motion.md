---
name: motion
description: Timing, easing, scroll reveals, micro-interactions, view transitions and GSAP timelines
---
# Motion & animation

Motion explains change: where something came from, what it is now, what you can do next.

## Timing
| Use | Duration | Easing |
|---|---|---|
| Hover/press feedback | 100–150ms | ease-out |
| Small UI (toggle, tooltip, dropdown) | 150–250ms | `cubic-bezier(.16,1,.3,1)` |
| Modals, sheets, page sections | 300–450ms | `cubic-bezier(.16,1,.3,1)` in, `.7,0,.84,0` out |
| Hero/storytelling | 600–1200ms | expo-out, staggered 60–90ms |
Exits are ~30% faster than entrances. Never linear for UI (except spinners/marquees).

## Performance rules
- Animate only `transform` and `opacity` (and `filter` sparingly). Never `top/left/width/height`.
- `will-change` only during the animation. Use `requestAnimationFrame` for JS loops.
- Respect users: wrap non-essential motion
  `@media (prefers-reduced-motion: reduce){*,*::before,*::after{animation-duration:.01ms!important;animation-iteration-count:1!important;transition-duration:.01ms!important;scroll-behavior:auto!important}}`

## Scroll reveal (no library)
```css
.reveal{opacity:0;transform:translateY(24px);transition:opacity .7s var(--ease-out),transform .7s var(--ease-out)}
.reveal.is-in{opacity:1;transform:none}
```
```js
const io = new IntersectionObserver((entries)=>{
  entries.forEach(e=>{ if(e.isIntersecting){ e.target.classList.add('is-in'); io.unobserve(e.target);} });
},{threshold:.15, rootMargin:'0px 0px -10% 0px'});
document.querySelectorAll('.reveal').forEach((el,i)=>{ el.style.transitionDelay = `${(i%6)*70}ms`; io.observe(el); });
```

## Micro-interactions that make UI feel alive
- Buttons: lift on hover (`translateY(-1px)`), squash on press (`scale(.97)`), ripple optional.
- Cards: shadow + lift; image inside zooms `scale(1.05)` over 600ms.
- Inputs: focus ring grows; floating labels; shake on error (`@keyframes shake{25%{transform:translateX(-4px)}75%{transform:translateX(4px)}}`).
- Toggles/checkboxes: spring easing `cubic-bezier(.34,1.56,.64,1)`.
- Counters: count up when visible. Lists: stagger children 40–60ms.
- Success: check-mark stroke draw (`stroke-dasharray` + `stroke-dashoffset` animation).
- Loading: skeleton shimmer, not spinners, for content areas.
- Page/view changes: View Transitions API with fallback:
```js
function go(update){ document.startViewTransition ? document.startViewTransition(update) : update(); }
```
- Magnetic / tilt hover on hero cards (pointermove → `rotateX/rotateY` ≤ 8deg, reset on leave).
- Marquee logos: duplicate the track, `animation: scroll 30s linear infinite`, pause on hover.

## Keyframe library
```css
@keyframes fade-up{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:none}}
@keyframes scale-in{from{opacity:0;transform:scale(.94)}to{opacity:1;transform:none}}
@keyframes shimmer{to{background-position:-200% 0}}
@keyframes float{50%{transform:translateY(-10px)}}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes pulse-ring{0%{box-shadow:0 0 0 0 color-mix(in srgb,var(--brand) 50%,transparent)}100%{box-shadow:0 0 0 14px transparent}}
@keyframes gradient-pan{to{background-position:200% center}}
```

## Motion graphics / complex timelines
For hero sequences, SVG path drawing, scroll-scrubbed scenes or text splitting, use GSAP from a CDN:
`<script src="https://cdn.jsdelivr.net/npm/gsap@3/dist/gsap.min.js"></script>` (+ `ScrollTrigger.min.js`).
```js
gsap.registerPlugin(ScrollTrigger);
gsap.from('.hero h1 .word',{y:'110%',opacity:0,duration:.9,ease:'expo.out',stagger:.06});
gsap.to('.parallax',{yPercent:-20,ease:'none',scrollTrigger:{trigger:'.parallax',scrub:true}});
```
Lottie for illustrated loops: `<script src="https://unpkg.com/@lottiefiles/lottie-player@latest/dist/lottie-player.js"></script>`.
Canvas particle/gradient backgrounds: cap DPR at 2, pause when tab hidden (`document.hidden`).

Motion must never block reading or input. Everything interactive works before animations finish.
