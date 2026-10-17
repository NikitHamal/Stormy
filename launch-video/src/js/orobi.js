// Orobi — the mascot — plus the infinity loader and the sparkle glyph.
// Geometry is brand-locked (see README "Brand rules"). Do not edit the path data.
import { el, attr, css, clamp, r2, px } from './core.js';

export const GEO = {
  viewBox: '200 400 1200 820',
  body: 'M810,710C760,635 650,503 520,503C370,503 256,660 256,810C256,990 380,1148 510,1148C640,1148 740,1050 810,933C880,1040 980,1148 1100,1148C1250,1148 1345,1020 1345,880C1345,760 1290,650 1190,590C1205,570 1200,520 1160,495C1140,478 1115,472 1085,472C1060,472 1040,480 1030,487L995,520C910,560 850,640 810,710ZM743,825C690,745 600,631 520,631C435,631 377,730 377,835C377,940 440,1017 520,1017C600,1017 690,905 743,825ZM880,817C940,720 1000,650 1030,632C1065,645 1110,650 1160,630C1215,690 1241,770 1241,835C1241,940 1180,1017 1115,1017C1030,1017 940,940 880,817Z',
  markL: 'M553,400L553,503C515,555 487,600 477,637L470,660L420,760L390,930C400,1010 440,1090 505,1148L505,1200L317,1200L317,925Q317,850 256,815L256,803Q317,770 317,690L317,400Z',
  markR: 'M1020,400L1020,470C1070,520 1105,580 1127,642L1150,670L1190,950L1205,965C1195,1050 1160,1110 1097,1148L1097,1200L1285,1200L1285,935Q1285,850 1340,820L1340,800Q1285,770 1285,680L1285,400Z',
  eyes: [[717, 634], [885, 634]],
  eyeRx: 11.5, eyeRy: 14.5, glint: { r: 3.6, dx: 3.4, dy: -5.2 },
  shadow: { cx: 800, cy: 1185, rx: 400, ry: 24, opacity: 0.1 },
  loaderViewBox: '250 500 1110 650',
  loader: 'M810 822C880 700 980 568 1105 568C1230 568 1295 700 1295 830C1295 960 1220 1082 1105 1082C980 1082 880 940 810 822C740 700 640 567 520 567C400 567 317 700 317 825C317 950 400 1082 520 1082C640 1082 740 940 810 822Z',
  // Gaze limits (the right eye sits near the head edge).
  gaze: { x: 10, up: 2.5, down: 6 },
  // Anchor used for positioning/squash: bottom centre of the body.
  ground: [800, 1148],
  // Visual centre of the body (for centring Orobi on a point).
  centre: [800, 810],
  loopSeconds: 1.6,
};

let uid = 0;

/**
 * Animated Orobi. Positioned by its ground anchor in stage pixels.
 * state = { x, y, w, theme, body, loop, loopWidth, loopDraw, head, dashOn,
 *           marks, eyes, gx, gy, sx, sy, rot, lift, shadow, opacity, z }
 */
export function createOrobi(parent, P) {
  const id = `orobi${uid++}`;
  const wrap = el('div', { class: 'abs orobi' }, parent);
  const svg = el('svg', { viewBox: GEO.viewBox, overflow: 'visible' }, wrap);
  const defs = el('defs', {}, svg);
  const clip = el('clipPath', { id: `${id}-clip` }, defs);
  el('path', { d: GEO.body, 'clip-rule': 'evenodd' }, clip);
  const blur = el('filter', { id: `${id}-soft`, x: '-20%', y: '-200%', width: '140%', height: '500%' }, defs);
  el('feGaussianBlur', { stdDeviation: '10' }, blur);

  const shadow = el('ellipse', { cx: GEO.shadow.cx, cy: GEO.shadow.cy, rx: GEO.shadow.rx, ry: GEO.shadow.ry, filter: `url(#${id}-soft)` }, svg);
  const rig = el('g', {}, svg); // squash/stretch + hop
  const loopG = el('g', {}, rig);
  const track = el('path', { d: GEO.loader, fill: 'none', stroke: P.mascot.magenta, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', pathLength: '100' }, loopG);
  const dash = el('path', { d: GEO.loader, fill: 'none', stroke: P.mascot.magenta, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', pathLength: '100' }, loopG);
  const bodyG = el('g', {}, rig);
  const body = el('path', { d: GEO.body, 'fill-rule': 'evenodd' }, bodyG);
  const marks = el('g', { 'clip-path': `url(#${id}-clip)` }, bodyG);
  const mL = el('path', { d: GEO.markL, fill: P.mascot.magenta }, marks);
  const mR = el('path', { d: GEO.markR, fill: P.mascot.magenta }, marks);
  const eyesG = el('g', {}, bodyG);
  const eyes = GEO.eyes.map(([cx, cy]) => {
    const g = el('g', {}, eyesG);
    const e = el('ellipse', { cx, cy, rx: GEO.eyeRx, ry: GEO.eyeRy }, g);
    const c = el('circle', { cx: cx + GEO.glint.dx, cy: cy + GEO.glint.dy, r: GEO.glint.r, fill: '#FFFFFF' }, g);
    return { g, e, c, cx, cy };
  });

  function set(s) {
    const k = s.w / 1200;
    const [gx0, gy0] = GEO.ground;
    css(wrap, {
      width: px(s.w), height: px(820 * k),
      transform: `translate(${r2(s.x - (gx0 - 200) * k)}px, ${r2(s.y - (gy0 - 400) * k)}px)`,
      opacity: String(r2(s.opacity ?? 1)),
      zIndex: String(s.z ?? 10),
      display: (s.opacity ?? 1) <= 0.001 ? 'none' : '',
    });
    const dark = (s.theme || 'dark') === 'dark';
    const bodyCol = dark ? P.mascot.peach : P.mascot.ink;
    const eyeCol = dark ? P.mascot.ink : P.mascot.peach;
    const lift = s.lift || 0, sx = s.sx ?? 1, sy = s.sy ?? 1, rot = s.rot || 0;
    attr(rig, { transform: `translate(${gx0} ${r2(gy0 - lift)}) rotate(${r2(rot)}) scale(${r2(sx)} ${r2(sy)}) translate(${-gx0} ${-gy0})` });

    // Ground shadow shrinks & fades with lift.
    const sh = (s.shadow ?? 1) * clamp(1 - lift / 520, 0.15, 1);
    attr(shadow, {
      fill: dark ? '#000000' : P.mascot.ink,
      opacity: r2(GEO.shadow.opacity * sh * (dark ? 3.2 : 1)),
      rx: r2(GEO.shadow.rx * (0.55 + 0.45 * clamp(1 - lift / 400, 0, 1)) * sx),
    });

    // Body
    const b = clamp(s.body ?? 1);
    attr(body, { fill: bodyCol });
    css(bodyG, { opacity: String(r2(b)) });
    // Markings slide in from the sides (clip stays put).
    const m = clamp(s.marks ?? 1);
    attr(mL, { transform: `translate(${r2(-(1 - m) * 260)} 0)` });
    attr(mR, { transform: `translate(${r2((1 - m) * 260)} 0)` });
    // Eyes: gaze is clamped to brand limits; blink squashes vertically about the eye centre.
    const gx = clamp(s.gx || 0, -GEO.gaze.x, GEO.gaze.x);
    const gy = clamp(s.gy || 0, -GEO.gaze.up, GEO.gaze.down);
    const open = clamp(s.eyes ?? 1, 0.06, 1.15);
    for (const E of eyes) {
      attr(E.e, { fill: eyeCol });
      attr(E.g, { transform: `translate(${r2(E.cx + gx)} ${r2(E.cy + gy)}) scale(1 ${r2(open)}) translate(${-E.cx} ${-E.cy})` });
      attr(E.c, { opacity: r2(clamp((open - 0.35) / 0.4)) });
    }

    // Loop (infinity loader)
    const L = clamp(s.loop ?? 0);
    css(loopG, { opacity: String(r2(L)), display: L <= 0.001 ? 'none' : '' });
    if (L > 0.001) {
      const lw = s.loopWidth ?? 90;
      const draw = clamp(s.loopDraw ?? 1);
      attr(track, {
        'stroke-width': r2(lw), 'stroke-opacity': '0.2',
        'stroke-dasharray': draw >= 1 ? 'none' : `${r2(draw * 100)} 200`,
      });
      // Dash: 24% of length, head at position `head` (0..∞, wraps every 100).
      const head = s.head ?? 0;
      const on = clamp(s.dashOn ?? 1);
      const len = 24 * on;
      attr(dash, {
        'stroke-width': r2(lw), 'stroke-dasharray': `${r2(Math.max(len, 0.001))} ${r2(100 - len)}`,
        'stroke-dashoffset': r2(-(((head - len) % 100) + 100) % 100),
        opacity: on <= 0.001 ? '0' : '1',
      });
    }
  }
  return { el: wrap, set };
}

/** Dash head position (0..100 per loop) for a loader that started drawing at t0.
 *  Starts fast and settles to one loop per 1.6s — continuous velocity, no hitch. */
export function loaderHead(t, t0 = 0) {
  const x = Math.max(0, t - t0);
  const base = 100 / GEO.loopSeconds;
  const extra = 140, tau = 0.35;
  return base * x + extra * tau * (1 - Math.exp(-x / tau));
}

/** Small standalone loader (used in tool rows). Returns {el, set(head, opacity)} */
export function miniLoader(parent, P, size) {
  const svg = el('svg', { viewBox: GEO.loaderViewBox, class: 'mini-loader', width: size, height: size * 650 / 1110 }, parent);
  el('path', { d: GEO.loader, fill: 'none', stroke: P.mascot.magenta, 'stroke-opacity': '0.2', 'stroke-width': '90', 'stroke-linecap': 'round' , pathLength: '100'}, svg);
  const d = el('path', { d: GEO.loader, fill: 'none', stroke: P.mascot.magenta, 'stroke-width': '90', 'stroke-linecap': 'round', pathLength: '100', 'stroke-dasharray': '24 76' }, svg);
  return {
    el: svg,
    set(head) { attr(d, { 'stroke-dashoffset': r2(-(((head - 24) % 100) + 100) % 100) }); },
  };
}

/** Static Orobi markup (avatar). */
export function staticOrobi(parent, P, w, theme = 'dark') {
  const id = `orobiS${uid++}`;
  const dark = theme === 'dark';
  const svg = el('svg', { viewBox: GEO.viewBox, width: w, height: w * 820 / 1200 }, parent);
  svg.innerHTML = `<defs><clipPath id="${id}"><path d="${GEO.body}" clip-rule="evenodd"/></clipPath></defs>
    <path d="${GEO.body}" fill-rule="evenodd" fill="${dark ? P.mascot.peach : P.mascot.ink}"/>
    <g clip-path="url(#${id})"><path d="${GEO.markL}" fill="${P.mascot.magenta}"/><path d="${GEO.markR}" fill="${P.mascot.magenta}"/></g>
    ${GEO.eyes.map(([x, y]) => `<ellipse cx="${x}" cy="${y}" rx="${GEO.eyeRx}" ry="${GEO.eyeRy}" fill="${dark ? P.mascot.ink : P.mascot.peach}"/><circle cx="${x + GEO.glint.dx}" cy="${y + GEO.glint.dy}" r="${GEO.glint.r}" fill="#fff"/>`).join('')}`;
  return svg;
}

/** Four-point sparkle: four quadratic curves pinched to 15% of the radius. */
export function sparklePath(r = 50) {
  const p = 0.15 * r;
  return `M0,${-r}Q${p},${-p} ${r},0Q${p},${p} 0,${r}Q${-p},${p} ${-r},0Q${-p},${-p} 0,${-r}Z`;
}
