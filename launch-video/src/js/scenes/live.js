// Scene 3 — "See it live." The site assembles in the phone; Orobi pops back out of the loader and
// peeks; then a whip-pan to the agent tool-run list ending in "Site is live".
import { el, box, css, show, ease, tw, prog, keys, saccades, blinks, lerp, clamp, r2, px, maskedWords, hop, atCentre } from '../core.js';
import { loaderHead, miniLoader, staticOrobi } from '../orobi.js';
import Describe, { T as DT } from './describe.js';

export const T = {
  peek: [0, 0.55], build: 0.08, scroll: [1.3, 1.9], live: 1.45,
  morph: [1.18, 1.55], hop: [1.24, 1.56], eyes: 1.6, glance: 1.74,
  whip: [1.98, 2.3], head: 0.18, sub: 0.4, head2: 2.12, sub2: 2.3, rows: 2.3, rowGap: 0.27, spin: 0.2,
};

const ICONS = {
  read: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h4"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  write: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M12 11v6M9 14h6"/>',
  git: '<circle cx="6.5" cy="5.5" r="2.3"/><circle cx="6.5" cy="18.5" r="2.3"/><circle cx="17.5" cy="7.5" r="2.3"/><path d="M6.5 7.8v8.4M17.5 9.8c0 4.2-5.5 4-10 6.6"/>',
  done: '<path d="M6 12.5l4 4L18.5 8"/>',
};
const svgIcon = (k) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[k]}</svg>`;

// Panel design metrics (design px, before format scale)
const PW = 780, PAD = 36, ROW0 = 196, ROWH = 76, ROWG = 10;

export default {
  id: 'live',
  design: 4.0,
  build(ctx, sc) {
    const { L, layers, P, W } = ctx;
    const g = L.live;
    const F = ctx.factors;
    const typeA = box(layers.type, 'scene-live'), typeB = box(layers.type, 'scene-live');
    const mainB = box(layers.main, 'scene-live');

    const head = maskedWords(typeA, sc.headline, 'headline');
    css(head.root, { left: px(g.head.x), top: px(g.head.y), width: px(g.head.w), textAlign: g.head.align, fontSize: px(g.head.size) });
    const sub = maskedWords(typeA, sc.subline, 'subline');
    css(sub.root, { left: px(g.head.x), top: px(g.head.y + g.head.size * 1.18), width: px(g.head.w), textAlign: g.head.align });
    const head2 = maskedWords(typeB, sc.headline2, 'headline');
    css(head2.root, { left: px(g.head.x), top: px(g.head.y), width: px(g.head.w), textAlign: g.head.align, fontSize: px(g.head.size) });
    const sub2 = maskedWords(typeB, sc.subline2, 'subline');
    css(sub2.root, { left: px(g.head.x), top: px(g.head.y + g.head.size * 1.18), width: px(L.V ? g.head.w : 860), textAlign: g.head.align });

    // Agent panel
    const n = sc.toolRun.length;
    const PH = ROW0 + n * ROWH + (n - 1) * ROWG + PAD;
    const sc0 = g.panel.w / PW;
    const panel = box(mainB, 'panel', { width: px(PW), height: px(PH), left: px(g.panel.cx - PW / 2), top: px(g.panel.cy - PH / 2) });
    const bubble = box(panel, 'bubble', { right: px(PAD), left: 'auto', top: px(PAD) });
    bubble.textContent = sc.agentPrompt;
    const agent = box(panel, 'agent', { left: px(PAD), top: px(124) });
    staticOrobi(el('span', { class: 'agent-ava' }, agent), P, 66);
    el('span', { class: 'agent-name' }, agent, sc.agentTitle);
    const status = el('span', { class: 'agent-status' }, agent);
    const rows = sc.toolRun.map((r, i) => {
      const done = r.icon === 'done';
      const row = box(panel, 'row' + (done ? ' row-done' : ''), { left: px(PAD), top: px(ROW0 + i * (ROWH + ROWG)), width: px(PW - PAD * 2), height: px(ROWH) });
      el('span', { class: 'row-ic' }, row, svgIcon(r.icon));
      const txt = el('span', { class: 'row-txt' }, row);
      el('span', { class: 'row-verb' }, txt, r.verb);
      if (r.file) el('span', { class: 'row-file' }, txt, r.file);
      if (r.add) el('span', { class: 'row-add' }, txt, r.add);
      if (r.del) el('span', { class: 'row-del' }, txt, r.del);
      const st = el('span', { class: 'row-st' }, row);
      const spin = miniLoader(st, P, 40);
      const chk = el('span', { class: 'row-chk' }, st, svgIcon('done'));
      return { row, spin, chk, done, ic: row.firstChild };
    });
    // Screen-space rect of the final row (used by the next scene's match-cut expansion).
    const pl = g.panel.cx - (PW / 2) * sc0, pt = g.panel.cy - (PH / 2) * sc0;
    ctx.shared.doneRow = { x: pl + PAD * sc0, y: pt + (ROW0 + (n - 1) * (ROWH + ROWG)) * sc0, w: (PW - PAD * 2) * sc0, h: ROWH * sc0, r: 18 * sc0 };
    return { roots: [typeA, typeB, mainB], typeA, typeB, mainB, head, sub, head2, sub2, panel, rows, status, sc0 };
  },
  render(t, ctx, sc, R) {
    const { L, W, H } = ctx;
    const g = L.live, pg = L.phone, F = ctx.factors;

    // --- Whip pan (coordinate swap at the midpoint keeps everything continuous)
    const wp = prog(t, ...T.whip);
    const we = ease.inOut(wp);
    const after = wp >= 0.5;
    ctx.cam.blur = 110 * Math.sin(Math.PI * wp) ** 2;
    const push = ease.std(prog(t, 0, T.whip[0])) * (1 - we);
    ctx.cam.s = 1 + 0.035 * push;
    ctx.cam.x = W / 2 + W * (we - (after ? 1 : 0)) + 40 * push * (L.V ? 0 : 1);
    const offA = (f) => (after ? -W * f : 0);
    const offB = (f) => (after ? 0 : W * f);
    css(R.typeA, { transform: `translateX(${r2(offA(F.type))}px)`, display: after && wp >= 1 ? 'none' : '' });
    css(R.typeB, { transform: `translateX(${r2(offB(F.type))}px)`, display: wp <= 0 ? 'none' : '' });
    css(R.mainB, { transform: `translateX(${r2(offB(F.main))}px)`, display: wp <= 0 ? 'none' : '' });

    R.head.render(t, T.head);
    R.sub.render(t, T.sub, Infinity, 0.03);
    R.head2.render(t, T.head2);
    R.sub2.render(t, T.sub2, Infinity, 0.03);

    // --- Phone + live site
    const ph = ctx.phone;
    if (wp < 1) {
      ph.set({ cx: pg.cx + offA(F.main), cy: pg.cy, h: pg.h });
      const k = ph.k;
      const maxScroll = Math.max(0, 940 - (ph.screenH - 84));
      ph.content({
        bt: t - T.build, skelOn: 1 - ease.std(prog(t, 0, 0.25)),
        scroll: maxScroll * ease.inOut(prog(t, ...T.scroll)), live: t >= T.live ? 1 : 0, pingT: t - T.live,
      });
    } else ph.set({ cx: 0, cy: 0, h: pg.h, opacity: 0 });

    // --- Loader → peek spot → Orobi
    const d = L.describe.think;
    const pk = ease.emph(prog(t, ...T.peek));
    const wv = lerp(d.w, g.peek.w, pk);
    const k = wv / 1200;
    const fromG = atCentre(d.cx, d.cy, d.w);
    const gx = lerp(fromG.x, g.peek.cx, pk), gy = lerp(fromG.y, g.peek.ground, pk);
    const m = ease.std(prog(t, ...T.morph));
    const h = hop(t, T.hop[0], T.hop[1], 70, { antic: 0.08 });
    const gz = saccades(t, [[T.glance, L.V ? -9 : 9, 1], [T.glance + 0.5, L.V ? -6 : 7, -2]]);
    ctx.o = {
      x: gx + offA(F.main), y: gy, w: wv, theme: 'dark', z: 6,
      loop: 1 - m, loopWidth: lerp(90, 150, m), loopDraw: 1, dashOn: 1 - m,
      head: loaderHead(t + Describe.design, DT.toLoop + 0.05),
      body: m, marks: ease.emph(prog(t, T.morph[0] + 0.12, T.morph[1] + 0.25)),
      eyes: keys(t, [[T.eyes, 0.08], [T.eyes + 0.09, 1.1], [T.eyes + 0.2, 1]]),
      gx: gz.x, gy: gz.y, lift: h.lift, sx: h.sx, sy: h.sy, shadow: m,
    };

    // --- Agent panel
    const arrive = ease.emph(prog(t, T.whip[0] + 0.1, T.whip[1] + 0.25));
    css(R.panel, { transform: `scale(${r2(R.sc0 * (0.94 + 0.06 * arrive))})` });
    const last = R.rows.length - 1;
    const allDone = t >= T.rows + last * T.rowGap;
    R.status.textContent = allDone ? 'Done' : 'Working…';
    R.rows.forEach((r, i) => {
      const t0 = T.rows + i * T.rowGap;
      const e = ease.emph(prog(t, t0, t0 + 0.4));
      css(r.row, { opacity: String(r2(clamp(prog(t, t0, t0 + 0.15)))), transform: `translateY(${r2((1 - e) * 22)}px)` });
      const spinning = !r.done && t >= t0 && t < t0 + T.spin;
      css(r.spin.el, { display: spinning ? '' : 'none' });
      if (spinning) r.spin.set(loaderHead(t, t0) + 30);
      const c = ease.back(prog(t, t0 + T.spin, t0 + T.spin + 0.3), 2.2);
      css(r.chk, { opacity: !r.done && t >= t0 + T.spin ? '1' : '0', transform: `scale(${r2(0.4 + 0.6 * c)})` });
      if (r.done) {
        const pop = ease.back(prog(t, t0 + 0.05, t0 + 0.45), 2.4);
        css(r.ic, { transform: `scale(${r2(0.3 + 0.7 * pop)})` });
        const pt = t - (t0 + 0.3);
        css(r.row, { boxShadow: pt > 0 && pt < 0.8 ? `0 0 0 ${r2(ease.emph(pt / 0.8) * 14)}px rgba(61,220,151,${r2(0.22 * (1 - pt / 0.8))})` : 'none' });
      }
    });
  },
};
