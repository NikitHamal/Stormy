// Scene 2 — "Describe it." A prompt pill types, Orobi swallows it and turns back into the loader
// to think; code tokens arc out of the loader into a phone that rises into frame.
import { el, box, css, show, ease, tw, prog, keys, saccades, blinks, lerp, clamp, r2, px, maskedWords, wobble, atCentre, bump } from '../core.js';
import { loaderHead } from '../orobi.js';
import { T as WAKE } from './wake.js';

export const T = {
  move: [0, 0.7], head: 0.12, headOut: 2.02, sub: 0.32,
  pill: [0.28, 0.75], type: [0.72, 1.85], send: 1.9, collapse: [2.0, 2.2], gulp: [2.2, 2.36],
  toLoop: 2.4, think: [2.45, 3.0], phone: [2.42, 3.05], tokens: 2.66, tokenGap: 0.06, tokenFly: 0.46,
};

export default {
  id: 'describe',
  design: 3.5,
  build(ctx, sc) {
    const { L, layers } = ctx;
    const g = L.describe;
    const type = box(layers.type, 'scene-describe');
    const main = box(layers.main, 'scene-describe');
    const fore = box(layers.fore, 'scene-describe');

    const head = maskedWords(type, sc.headline, 'headline');
    css(head.root, { left: px(g.head.x), top: px(g.head.y), width: px(g.head.w), textAlign: g.head.align, fontSize: px(g.head.size) });
    const sub = maskedWords(type, sc.subline, 'subline');
    css(sub.root, { left: px(g.head.x), top: px(g.head.y + g.head.size * 1.18), width: px(g.head.w), textAlign: g.head.align });

    const pill = box(main, 'pill');
    const pText = el('span', { class: 'pill-text' }, pill);
    const typed = el('span', {}, pText);
    const caret = el('i', { class: 'caret' }, pText);
    const ph = el('span', { class: 'pill-ph' }, pill); ph.textContent = sc.promptPlaceholder;
    const send = box(pill, 'pill-send');
    send.innerHTML = `<svg viewBox="0 0 24 24"><path d="M12 19V5M5.5 11.5 12 5l6.5 6.5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
    const orb = box(fore, 'orb');

    const tokens = sc.tokens.map((s) => { const n = box(fore, 'token'); n.textContent = s; return n; });
    return { roots: [type, main, fore], head, sub, pill, typed, caret, ph, send, orb, tokens };
  },
  render(t, ctx, sc, R) {
    const { L, W, H } = ctx;
    const g = L.describe, w0 = L.wake;
    ctx.cam.s = 1 + 0.02 * bump(prog(t, 0, 3.5));
    ctx.cam.y = H / 2 - 12 * bump(prog(t, 0, 3.5));

    // --- Headline
    R.head.render(t, T.head, T.headOut);
    R.sub.render(t, T.sub, T.headOut, 0.03, 0.6, 0.3);

    // --- Orobi travels from centre to its spot, watches the caret, gulps, then becomes the loader.
    const mv = ease.emph(prog(t, ...T.move));
    let cx = lerp(w0.cx, g.orobi.cx, mv), cy = lerp(w0.cy, g.orobi.cy, mv), w = lerp(w0.w, g.orobi.w, mv);
    const th = ease.emph(prog(t, ...T.think));
    cx = lerp(cx, g.think.cx, th); cy = lerp(cy, g.think.cy, th); w = lerp(w, g.think.w, th);
    const nChars = sc.prompt.length;
    const ty = prog(t, ...T.type);
    const gaze = saccades(t, [[-1, ...WAKE.glances.at(-1).slice(1)], [0.3, -8, 6], [1.02, -4, 6], [1.32, 1, 6], [1.62, 6, 6], [1.86, 9, 5], [2.06, 0, 6]]);
    const gulp = wobble(t - T.gulp[1], 2.8, 7);
    const sqz = t > T.gulp[1] ? gulp : 0;
    const loopIn = ease.std(prog(t, T.toLoop + 0.05, T.toLoop + 0.4));
    ctx.o = {
      ...atCentre(cx, cy, w), theme: 'dark',
      body: 1 - ease.std(prog(t, T.toLoop + 0.08, T.toLoop + 0.42)),
      marks: 1 - ease.emphIn(prog(t, T.toLoop, T.toLoop + 0.25)),
      eyes: Math.min(blinks(t, [1.45]), keys(t, [[T.collapse[1], 1], [T.gulp[1], 1.12], [T.toLoop, 0.08]])),
      gx: gaze.x, gy: gaze.y,
      sx: 1 + 0.1 * sqz - 0.04 * ease.smooth(prog(t, T.gulp[0], T.gulp[1])) * (t < T.gulp[1] ? 1 : 0),
      sy: 1 - 0.13 * sqz + 0.05 * ease.smooth(prog(t, T.gulp[0], T.gulp[1])) * (t < T.gulp[1] ? 1 : 0),
      loop: loopIn, loopWidth: lerp(150, 90, loopIn), loopDraw: 1,
      head: loaderHead(t, T.toLoop + 0.05), dashOn: ease.std(prog(t, T.toLoop + 0.15, T.toLoop + 0.45)),
      shadow: 1 - loopIn,
    };

    // --- Prompt pill
    const P = g.pill;
    const open = ease.emph(prog(t, ...T.pill));
    const col = ease.emphIn(prog(t, ...T.collapse));
    const pw = lerp(P.h, P.w, open) * (1 - col) + P.h * col;
    const pillOn = t >= T.pill[0] && t < T.collapse[1] + 0.001;
    show(R.pill, pillOn);
    if (pillOn) {
      css(R.pill, {
        left: px(P.cx - pw / 2), top: px(P.cy - P.h / 2), width: px(pw), height: px(P.h),
        opacity: String(r2(clamp(prog(t, T.pill[0], T.pill[0] + 0.12)))),
        transform: `scale(${r2(0.7 + 0.3 * ease.back(prog(t, T.pill[0], T.pill[0] + 0.45)))})`,
        background: col > 0 ? `color-mix(in srgb, ${ctx.P.dark.accent} ${r2(col * 100)}%, ${ctx.P.dark.surface2})` : '',
      });
      const n = Math.floor(ty * nChars + 1e-6);
      R.typed.textContent = sc.prompt.slice(0, n);
      const caretOn = t >= T.type[0] - 0.15 && (ty < 1 ? true : Math.floor((t - T.type[1]) / 0.26) % 2 === 1);
      css(R.caret, { opacity: caretOn && col < 0.05 ? '1' : '0' });
      css(R.ph, { opacity: String(r2(open * (1 - prog(t, T.type[0] - 0.12, T.type[0])))) });
      css(R.typed.parentNode, { opacity: String(r2(1 - prog(t, T.collapse[0], T.collapse[0] + 0.1))) });
      const press = keys(t, [[T.send - 0.02, 1], [T.send + 0.06, 0.84], [T.send + 0.3, 1, ease.back]]);
      css(R.send, {
        transform: `scale(${r2(press * (1 - col))})`,
        background: n > 0 ? ctx.P.dark.accent : ctx.P.dark.surface3,
        color: n > 0 ? ctx.P.dark.bg : ctx.P.dark.muted,
        opacity: String(r2(open)),
      });
    }
    // Orb: the collapsed prompt arcs into Orobi's mouth.
    const ok = prog(t, T.collapse[1], T.gulp[1]);
    show(R.orb, ok > 0 && ok < 1);
    if (ok > 0 && ok < 1) {
      const k = ctx.o.w / 1200;
      const mx = ctx.o.x, my = ctx.o.y - (1148 - 730) * k;
      const e = ease.emphIn(ok);
      const qx = lerp(lerp(P.cx, P.cx, e), lerp(P.cx, mx, e), e);
      const c1y = Math.min(P.cy, my) - 80;
      const qy = (1 - e) * (1 - e) * P.cy + 2 * (1 - e) * e * c1y + e * e * my;
      const s = lerp(P.h, P.h * 0.22, e);
      css(R.orb, { left: px(qx - s / 2), top: px(qy - s / 2), width: px(s), height: px(s), opacity: String(r2(1 - prog(ok, 0.85, 1))) });
    }

    // --- Phone rises into frame
    const pg = L.phone;
    const pk = ease.emph(prog(t, ...T.phone));
    const ph = ctx.phone;
    if (t >= T.phone[0]) {
      ph.set({
        cx: pg.cx + (L.V ? 0 : (1 - pk) * 120), cy: lerp(H + pg.h * 0.6, pg.cy, pk), h: pg.h,
        rot: (1 - pk) * (L.V ? 0 : 7),
      });
      ph.content({ bt: -1, skelOn: ease.std(prog(t, T.phone[1] - 0.3, T.phone[1])), live: 0 });
    } else ph.set({ cx: 0, cy: 0, h: pg.h, opacity: 0 });

    // --- Code tokens arc from the loader into the screen
    const from = { x: ctx.o.x, y: ctx.o.y - (1148 - 822) * (ctx.o.w / 1200) };
    const pw2 = pg.h * 0.462;
    R.tokens.forEach((n, i) => {
      const t0 = T.tokens + i * T.tokenGap;
      const u = prog(t, t0, t0 + T.tokenFly);
      show(n, u > 0 && u < 1);
      if (!(u > 0 && u < 1)) return;
      const e = ease.inOut(u);
      const to = { x: pg.cx + (((i * 37) % 5) - 2) * pw2 * 0.12, y: pg.cy - pg.h * 0.3 + (i / (R.tokens.length - 1)) * pg.h * 0.55 };
      const c = L.V
        ? { x: (i % 2 ? 1 : -1) * (300 + i * 20) + (from.x + to.x) / 2, y: (from.y + to.y) / 2 }
        : { x: (from.x + to.x) / 2, y: Math.min(from.y, to.y) - 220 - i * 26 };
      const x = (1 - e) ** 2 * from.x + 2 * (1 - e) * e * c.x + e * e * to.x;
      const y = (1 - e) ** 2 * from.y + 2 * (1 - e) * e * c.y + e * e * to.y;
      const dx = 2 * (1 - e) * (c.x - from.x) + 2 * e * (to.x - c.x);
      const dy = 2 * (1 - e) * (c.y - from.y) + 2 * e * (to.y - c.y);
      const ang = clamp(Math.atan2(dy, dx) * 57.3 * 0.12, -14, 14);
      const s = keys(u, [[0, 0.4], [0.3, 1.05, ease.emph], [1, 0.5, ease.in]]);
      css(n, {
        transform: `translate(${r2(x)}px, ${r2(y)}px) translate(-50%, -50%) rotate(${r2(ang)}deg) scale(${r2(s)})`,
        opacity: String(r2(Math.min(prog(u, 0, 0.12), 1 - prog(u, 0.82, 1)))),
      });
    });
  },
};
