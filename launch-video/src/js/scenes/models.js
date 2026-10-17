// Scene 4 — light-theme beat. The final "Site is live" row expands into a light frame; ink Orobi
// drops in; provider chips orbit (free/no-key inner ring, bring-your-own outer ring).
import { el, box, css, show, ease, tw, prog, keys, saccades, blinks, lerp, clamp, r2, px, maskedWords, hop, atCentre, bump } from '../core.js';

export const T = {
  expand: [0, 0.5], drop: [0.4, 0.7], head: [0.42, 0.62], sub: 0.86, legend: 1.0,
  chips: 0.7, chipGap: 0.035, chipIn: 0.55, out: 2.02, takeoff: 2.27,
};

/** The hop that carries Orobi across the cut into the end card. u = seconds since takeoff. */
export const HANDOFF = { air: 0.82, height: 300 };
export function handoff(u, L) {
  const a = L.models, b = L.end.orobi;
  const k = prog(u, 0, HANDOFF.air);
  const from = atCentre(a.c.x, a.c.y, a.w), to = atCentre(b.cx, b.cy, b.w);
  const e = ease.inOut(k);
  const h = hop(u, 0, HANDOFF.air, 1, { antic: 0.13, squash: 0.17 });
  return {
    x: lerp(from.x, to.x, e), y: lerp(from.y, to.y, e) - HANDOFF.height * h.lift, w: lerp(from.w, to.w, e),
    sx: h.sx, sy: h.sy, lift: 0, shadow: 1 - Math.min(1, h.lift * 1.6),
  };
}

const KEY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="15" r="4"/><path d="M11 12l8-8M16 7l3 3"/></svg>';

export default {
  id: 'models',
  design: 2.5,
  underlay: 0.5,
  build(ctx, sc) {
    const { L, layers } = ctx;
    const g = L.models;
    const type = box(layers.type, 'scene-models');
    const main = box(layers.main, 'scene-models');
    const fore = box(layers.fore, 'scene-models');
    const lines = sc.headline.map((s, i) => {
      const m = maskedWords(type, s, 'headline on-light');
      css(m.root, { left: px(g.head.x), top: px(g.head.y + i * g.head.size * 1.02), width: px(g.head.w), textAlign: g.head.align, fontSize: px(g.head.size) });
      return m;
    });
    const subY = g.head.y + sc.headline.length * g.head.size * 1.02 + 22;
    const sub = maskedWords(type, sc.subline, 'subline on-light');
    css(sub.root, { left: px(g.head.x), top: px(subY), width: px(g.head.w), textAlign: g.head.align });
    const legend = box(type, 'legend', { left: px(g.head.x), top: px(subY + 70), width: px(g.head.w), justifyContent: g.head.align === 'center' ? 'center' : 'flex-start' });
    legend.innerHTML = `<span><i class="lg-free"></i>${sc.freeLabel}</span><span><i class="lg-byo"></i>${sc.byoLabel}</span>`;
    const chips = [
      ...sc.free.map((s, i, a) => ({ s, ring: 0, i, count: a.length, free: true })),
      ...sc.byo.map((s, i, a) => ({ s, ring: 1, i, count: a.length, free: false })),
    ].map((c) => {
      const n = box(main, 'chip ' + (c.free ? 'chip-free' : 'chip-byo'));
      n.innerHTML = c.free ? `<span>${c.s}</span>` : `${KEY}<span>${c.s}</span>`;
      return { ...c, n };
    });
    const sheet = box(fore, 'sheet');
    return { roots: [type, main, fore], lines, sub, legend, chips, sheet };
  },
  render(t, ctx, sc, R) {
    const { L, W, H, P } = ctx;
    const g = L.models;

    // --- Match-cut: the "Site is live" row grows into the light frame.
    const ex = ease.emph(prog(t, ...T.expand));
    const r0 = ctx.shared.doneRow;
    const sheetOn = t < T.expand[1];
    show(R.sheet, sheetOn);
    if (sheetOn) {
      css(R.sheet, {
        left: px(lerp(r0.x, -2, ex)), top: px(lerp(r0.y, -2, ex)),
        width: px(lerp(r0.w, W + 4, ex)), height: px(lerp(r0.h, H + 4, ex)),
        borderRadius: px(lerp(r0.r, 0, ex)), background: P.light.bg,
        opacity: String(r2(clamp(prog(t, 0, 0.06)))),
      });
    }
    if (t >= T.expand[1]) ctx.bg = P.light.bg;
    const live = t >= T.expand[1] - 0.001;
    R.roots.slice(0, 2).forEach((n) => show(n, live));
    if (!live) { return; }
    ctx.cam.s = 1 + 0.025 * bump(prog(t, 0.5, 2.5));

    // --- Kinetic headline
    R.lines.forEach((m, i) => m.render(t, T.head[i], T.out + i * 0.05, 0.07));
    R.sub.render(t, T.sub, T.out, 0.03);
    const lg = ease.emph(prog(t, T.legend, T.legend + 0.5)), lgo = ease.emphIn(prog(t, T.out, T.out + 0.3));
    css(R.legend, { opacity: String(r2(lg * (1 - lgo))), transform: `translateY(${r2((1 - lg) * 16 - lgo * 16)}px)` });

    // --- Orobi (ink on light)
    const base = atCentre(g.c.x, g.c.y, g.w);
    const fall = prog(t, ...T.drop);
    const h = hop(t, T.drop[1] - 0.3, T.drop[1], 0, { squash: 0.18 });
    const gz = saccades(t, [[0.95, -8, 2], [1.4, 8, 0], [1.85, -3, 5]]);
    let o = {
      ...base, theme: 'light', body: 1, marks: 1, loop: 0,
      eyes: blinks(t, [1.25]), gx: gz.x, gy: gz.y,
      lift: (1 - ease.in(fall)) * 1500 * (t < T.drop[1] ? 1 : 0),
      sx: t < T.drop[1] ? 0.94 : h.sx, sy: t < T.drop[1] ? 1.08 : h.sy,
      shadow: ease.std(fall), opacity: t >= T.drop[0] ? 1 : 0,
    };
    if (t >= T.takeoff - 0.15) o = { ...o, ...handoff(t - T.takeoff, L) };
    ctx.o = o;

    // --- Orbiting provider chips
    R.chips.forEach((c) => {
      const ring = g.rings[c.ring];
      const t0 = T.chips + (c.ring * R.chips.filter((x) => x.ring === 0).length + c.i) * T.chipGap;
      const kin = ease.back(prog(t, t0, t0 + T.chipIn), 1.3);
      const kout = ease.emphIn(prog(t, T.out + c.i * 0.02, T.out + 0.4 + c.i * 0.02));
      const dir = c.ring ? -1 : 1;
      const th = (c.i / c.count) * Math.PI * 2 + (c.ring ? 0.4 : 0) + dir * (0.5 * t + 0.9 * (1 - ease.emph(prog(t, t0, t0 + 0.9))));
      const rad = kin * (1 + 0.7 * kout);
      const x = g.c.x + Math.cos(th) * ring.rx * rad;
      const y = g.c.y + 20 + Math.sin(th) * ring.ry * rad;
      const depth = (Math.sin(th) + 1) / 2;
      const s = (0.84 + 0.16 * depth) * (0.5 + 0.5 * kin);
      css(c.n, {
        transform: `translate(${r2(x)}px, ${r2(y)}px) translate(-50%, -50%) scale(${r2(s)})`,
        opacity: String(r2(clamp(prog(t, t0, t0 + 0.12)) * (1 - kout) * (0.55 + 0.45 * depth))),
        zIndex: String(Math.sin(th) > 0 ? 12 : 8),
        display: t < t0 || kout >= 1 ? 'none' : '',
      });
    });
  },
};
