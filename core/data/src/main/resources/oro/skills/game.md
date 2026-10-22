---
name: game
description: Canvas game loop, input, state machine and juice: shake, particles, Web Audio sound
---
# Browser games with juice

## Skeleton
```js
const cv = document.querySelector('canvas'), ctx = cv.getContext('2d');
function resize(){ const dpr = Math.min(devicePixelRatio, 2);
  cv.width = innerWidth*dpr; cv.height = innerHeight*dpr; cv.style.width = innerWidth+'px';
  cv.style.height = innerHeight+'px'; ctx.setTransform(dpr,0,0,dpr,0,0); }
addEventListener('resize', resize); resize();
let last = performance.now(), acc = 0; const STEP = 1/60;
function frame(now){ acc += Math.min(.1,(now-last)/1000); last = now;
  while(acc >= STEP){ update(STEP); acc -= STEP; } draw(); requestAnimationFrame(frame); }
requestAnimationFrame(frame);
```
- **State machine**: `menu → playing → paused → gameover`, each with its own UI overlay (HTML on top of canvas, styled with the design system).
- **Input**: keyboard + pointer + touch. Mobile: on-screen controls or tap/swipe gestures; prevent scroll (`touch-action:none` on canvas).
- **Pause** when `document.hidden`. Save high score in localStorage.

## Juice (what makes it feel good)
- Screen shake (decaying random offset), hit-stop (freeze 40–80ms on impact), squash & stretch.
- Particles on events (pool them; 20–60 per burst, fade + shrink + gravity).
- Easing for everything that moves in UI; tween score changes; floating "+10" text.
- Trails, glow (`ctx.shadowBlur` sparingly), flashing on damage, camera lerp follow.
- Sound via Web Audio (no files needed):
```js
const AC = new (window.AudioContext||window.webkitAudioContext)();
function beep(f=440, d=.08, type='square', vol=.15){ const o=AC.createOscillator(), g=AC.createGain();
  o.type=type; o.frequency.setValueAtTime(f,AC.currentTime); o.frequency.exponentialRampToValueAtTime(f*.5,AC.currentTime+d);
  g.gain.setValueAtTime(vol,AC.currentTime); g.gain.exponentialRampToValueAtTime(.001,AC.currentTime+d);
  o.connect(g).connect(AC.destination); o.start(); o.stop(AC.currentTime+d); }
```
  Resume the AudioContext on the first user gesture. Provide a mute toggle.
- Difficulty curve, combo multipliers, satisfying game-over screen with stats + "Play again".
3D: use Three.js (use_skill "three-d").
