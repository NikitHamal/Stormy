// Scene 1 — black. The infinity loader draws in, runs, morphs into Orobi, who lands, wakes and glances.
import { ease, tw, prog, keys, saccades, hop, atCentre, lerp } from '../core.js';
import { loaderHead } from '../orobi.js';

export const T = {
  drawStart: 0.15, morph: 1.3, morphEnd: 1.75, takeoff: 1.42, land: 1.8, wake: 1.92,
  glances: [[2.14, -9, 1], [2.38, 8, 4]],
};

export default {
  id: 'wake',
  design: 2.5,
  build() { return { roots: [] }; },
  render(t, ctx) {
    const g = ctx.L.wake;
    ctx.cam.s = tw(t, 0, 2.5, 1.12, 1.0, ease.std);
    const head = loaderHead(t, T.drawStart);
    const h = hop(t, T.takeoff, T.land, 90, { antic: 0.12, squash: 0.16 });
    const gz = saccades(t, T.glances);
    ctx.o = {
      ...atCentre(g.cx, g.cy, g.w), theme: 'dark',
      loop: Math.min(prog(t, T.drawStart, T.drawStart + 0.08), 1 - ease.std(prog(t, T.morph + 0.05, T.morphEnd))),
      loopDraw: head / 100, head, dashOn: Math.min(1, head / 24),
      loopWidth: tw(t, T.morph, T.morphEnd, 90, 150, ease.std),
      body: ease.std(prog(t, T.morph, T.morphEnd - 0.05)),
      marks: ease.emph(prog(t, T.morph + 0.18, T.morph + 0.7)),
      eyes: keys(t, [[T.wake, 0.08], [T.wake + 0.09, 1.1, ease.std], [T.wake + 0.2, 1, ease.std]]),
      gx: gz.x, gy: gz.y,
      lift: h.lift, sx: h.sx, sy: h.sy,
      shadow: prog(t, T.morph, T.morphEnd),
    };
  },
};
