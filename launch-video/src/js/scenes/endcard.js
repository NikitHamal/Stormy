// Scene 5 — end card. A dark iris opens from Orobi mid-hop (ink → peach match cut), Orobi lands
// beside the wordmark with sparkles; tagline + platform label; holds perfectly still at the end.
import { el, box, css, show, ease, tw, prog, keys, saccades, blinks, lerp, clamp, r2, px, maskedWords, atCentre } from '../core.js';
import { sparklePath } from '../orobi.js';
import Models, { handoff, T as MT } from './models.js';
const CUT = () => Models.design - MT.takeoff; // seconds Orobi has been airborne at the cut

export const T = { iris: [0, 0.55], word: 0.55, tag: 0.9, plat: 1.15, spark: 0.68, glance: 1.0, blink: 1.32 };

const SPARKS = [
  // [dx, dy] in units of Orobi width from its centre, radius (units of w), colour key, rotation
  [-0.36, -0.5, 0.075, 'magenta', -12], [0.5, -0.44, 0.05, 'apricot', 8], [0.2, -0.66, 0.026, 'magenta', 0], [-0.52, -0.16, 0.034, 'apricot', 18],
];

export default {
  id: 'endcard',
  design: 2.5,
  build(ctx, sc) {
    const { L, layers, P, W } = ctx;
    const e = L.end;
    const bg = box(layers.bg, 'scene-end iris');
    const type = box(layers.type, 'scene-end');
    const fore = box(layers.fore, 'scene-end');
    const wordClip = box(type, 'word-clip');
    const word = box(wordClip, 'wordmark');
    const letters = [...sc.wordmark].map((ch) => { const s = el('span', {}, word); s.textContent = ch; return s; });
    if (L.V) css(word, { left: '0', width: px(W), textAlign: 'center' });
    if (L.V) css(wordClip, { left: '0', width: px(W), top: px(e.word.cy - e.word.size * 0.62), height: px(e.word.size * 1.2), fontSize: px(e.word.size), textAlign: 'center' });
    else {
      const ob = e.orobi.cx + (1345 - 800) * (e.orobi.w / 1200);
      css(wordClip, { left: px(ob - 6), width: px(900), top: px(e.word.cy - e.word.size * 0.62), height: px(e.word.size * 1.2), fontSize: px(e.word.size), });
      css(word, { left: px(e.word.x - ob + 6) });
    }
    const tag = maskedWords(type, sc.tagline, 'tagline');
    css(tag.root, { left: '0', width: px(W), top: px(e.tag.cy - e.tag.size * 0.65), fontSize: px(e.tag.size), textAlign: 'center' });
    const plat = box(type, 'platform', { left: '0', width: px(W), top: px(e.plat.cy - 24) });
    plat.innerHTML = `<span>${sc.platform}</span>`;
    const sparks = SPARKS.map(([dx, dy, rr, col, rot]) => {
      const s = el('svg', { class: 'abs spark', viewBox: '-50 -50 100 100', overflow: 'visible' }, fore);
      el('path', { d: sparklePath(50), fill: P.mascot[col] }, s);
      return { s, dx, dy, rr, rot };
    });
    return { roots: [bg, type, fore], bg, word, letters, tag, plat, sparks };
  },
  render(t, ctx, sc, R) {
    const { L, W, H, P } = ctx;
    const e = L.end;

    // --- Iris: dark opens from where Orobi is at the cut, over the light frame.
    const ho = handoff(t + CUT(), L);
    const irisC = handoff(CUT(), L);
    const k = irisC.w / 1200;
    const ic = { x: irisC.x, y: irisC.y - 338 * k };
    const ir = ease.emph(prog(t, ...T.iris));
    const maxR = Math.hypot(Math.max(ic.x, W - ic.x), Math.max(ic.y, H - ic.y)) + 10;
    ctx.bg = ir >= 1 ? P.dark.bg : P.light.bg;
    css(R.bg, {
      left: '0', top: '0', width: px(W), height: px(H), background: P.dark.bg,
      clipPath: `circle(${r2(lerp(210, maxR, ir))}px at ${r2(ic.x)}px ${r2(ic.y)}px)`,
      display: ir >= 1 ? 'none' : '',
    });

    // --- Orobi lands next to the wordmark
    const gz = saccades(t, [[T.glance, L.V ? 0 : 9, L.V ? 6 : 1]]);
    ctx.o = {
      ...ho, theme: 'dark', body: 1, marks: 1, loop: 0,
      eyes: blinks(t, [T.blink]), gx: gz.x, gy: gz.y,
    };

    // --- Wordmark: letters slide out from behind Orobi (landscape) / rise (vertical)
    const wa = ease.emph(prog(t, T.word, T.word + 0.75));
    css(R.word, { transform: L.V ? `translateY(${r2((1 - wa) * 100)}%)` : `translateX(${r2((1 - wa) * -100)}%)` });
    R.letters.forEach((s, i) => {
      const a = prog(t, T.word + i * 0.07, T.word + i * 0.07 + 0.35);
      css(s, { opacity: String(r2(ease.std(a))) });
    });
    R.tag.render(t, T.tag, Infinity, 0.045, 0.65);
    const pa = ease.emph(prog(t, T.plat, T.plat + 0.5));
    css(R.plat, { opacity: String(r2(pa)), transform: `translateY(${r2((1 - pa) * 14)}px)` });

    // --- Sparkles burst on landing, then settle (static by ~1.4s)
    const oc = atCentre(e.orobi.cx, e.orobi.cy, e.orobi.w);
    R.sparks.forEach((s, i) => {
      const t0 = T.spark + i * 0.07;
      const a = ease.back(prog(t, t0, t0 + 0.5), 2.0);
      const sz = s.rr * e.orobi.w * 2;
      const x = e.orobi.cx + s.dx * e.orobi.w, y = e.orobi.cy + s.dy * e.orobi.w;
      const fly = 1 - ease.emph(prog(t, t0, t0 + 0.5));
      css(s.s, {
        left: px(x - sz / 2 - fly * s.dx * 60), top: px(y - sz / 2 - fly * s.dy * 60), width: px(sz), height: px(sz),
        transform: `rotate(${r2(s.rot + (1 - a) * 90)}deg) scale(${r2(Math.max(0, a))})`,
        display: t < t0 ? 'none' : '',
      });
    });
  },
};
