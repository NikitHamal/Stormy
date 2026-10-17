// Core math, easing and DOM helpers. Everything here is pure: no clocks, no randomness.

export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, k) => a + (b - a) * k;
/** Normalised progress of t through [a, b], clamped to 0..1. */
export const prog = (t, a, b) => (b === a ? (t >= b ? 1 : 0) : clamp((t - a) / (b - a)));

// ---- cubic-bezier easing (same maths as CSS) ------------------------------
export function bezier(x1, y1, x2, y2) {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  const sx = (u) => ((ax * u + bx) * u + cx) * u;
  const sy = (u) => ((ay * u + by) * u + cy) * u;
  const dx = (u) => (3 * ax * u + 2 * bx) * u + cx;
  return (x) => {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    let u = x;
    for (let i = 0; i < 8; i++) {
      const e = sx(u) - x;
      if (Math.abs(e) < 1e-6) break;
      const d = dx(u);
      if (Math.abs(d) < 1e-6) break;
      u -= e / d;
    }
    let lo = 0, hi = 1;
    if (Math.abs(sx(u) - x) > 1e-5) {
      u = x;
      for (let i = 0; i < 30; i++) { const v = sx(u); if (v < x) lo = u; else hi = u; u = (lo + hi) / 2; }
    }
    return sy(u);
  };
}

export const ease = {
  linear: (x) => clamp(x),
  /** Material "emphasized decelerate": entrances. */
  emph: bezier(0.05, 0.7, 0.1, 1),
  /** Material "emphasized accelerate": exits. */
  emphIn: bezier(0.3, 0, 0.8, 0.15),
  /** Material "standard". */
  std: bezier(0.2, 0, 0, 1),
  inOut: bezier(0.65, 0, 0.35, 1),
  in: bezier(0.4, 0, 1, 1),
  /** Back-out: small overshoot for things that land. */
  back: (x, s = 1.55) => { x = clamp(x) - 1; return 1 + x * x * ((s + 1) * x + s); },
  smooth: (x) => { x = clamp(x); return x * x * (3 - 2 * x); },
};

/** Tween helper: value at time t moving from→to over [a,b] with an easing fn. */
export const tw = (t, a, b, from, to, fn = ease.emph) => lerp(from, to, fn(prog(t, a, b)));

/** Damped spring impulse: 0 at t<=0, peaks then decays to 0. Used for squash recoveries. */
export function wobble(t, freq = 3.2, decay = 6.5) {
  if (t <= 0) return 0;
  return Math.exp(-decay * t) * Math.cos(2 * Math.PI * freq * t);
}

/** Keyframed scalar with per-segment easing. keys: [[t, v, easeFnIntoThisKey?], ...] sorted. */
export function keys(t, ks, defaultEase = ease.std) {
  if (t <= ks[0][0]) return ks[0][1];
  for (let i = 1; i < ks.length; i++) {
    const [t1, v1, e] = ks[i];
    const [t0, v0] = ks[i - 1];
    if (t <= t1) return lerp(v0, v1, (e || defaultEase)(prog(t, t0, t1)));
  }
  return ks[ks.length - 1][1];
}

/**
 * Saccadic gaze: list of [time, gx, gy]. Each change is a quick ~150ms jump then a hold.
 * Returns {x, y}.
 */
export function saccades(t, list, dur = 0.15) {
  let x = 0, y = 0;
  for (const [ts, gx, gy] of list) {
    if (t < ts) break;
    const k = ease.std(prog(t, ts, ts + dur));
    x = lerp(x, gx, k);
    y = lerp(y, gy, k);
  }
  return { x, y };
}

/** Eye openness from a list of blink times (each ~100ms squash to 0.1). */
export function blinks(t, times, dur = 0.11) {
  let open = 1;
  for (const bt of times) {
    if (t >= bt && t <= bt + dur) {
      const k = prog(t, bt, bt + dur);
      const c = k < 0.4 ? k / 0.4 : k < 0.55 ? 1 : 1 - (k - 0.55) / 0.45;
      open = Math.min(open, 1 - 0.9 * ease.smooth(c));
    }
  }
  return open;
}

// ---- DOM helpers -----------------------------------------------------------
export const SVGNS = 'http://www.w3.org/2000/svg';

export function el(tag, attrs = {}, parent, html) {
  const isSvg = ['svg', 'g', 'path', 'ellipse', 'circle', 'rect', 'clipPath', 'defs', 'filter',
    'feGaussianBlur', 'line', 'polyline', 'mask', 'use', 'text', 'tspan', 'linearGradient', 'stop'].includes(tag);
  const n = isSvg ? document.createElementNS(SVGNS, tag) : document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'style' && typeof v === 'object') Object.assign(n.style, v);
    else if (k === 'class') n.setAttribute('class', v);
    else n.setAttribute(k, v);
  }
  if (html != null) n.innerHTML = html;
  if (parent) parent.appendChild(n);
  return n;
}

/** Absolute-positioned div. */
export const box = (parent, cls = '', style = {}) => el('div', { class: 'abs ' + cls, style }, parent);

/** Set style only when changed (keeps renderAt cheap). */
export function css(n, props) {
  const c = n.__c || (n.__c = {});
  for (const k in props) {
    const v = props[k];
    if (c[k] !== v) { c[k] = v; n.style[k] = v; }
  }
}
export function attr(n, props) {
  const c = n.__a || (n.__a = {});
  for (const k in props) {
    const v = props[k];
    if (c[k] !== v) { c[k] = v; n.setAttribute(k, v); }
  }
}
export const show = (n, on) => css(n, { display: on ? '' : 'none' });
export const px = (v) => `${Math.round(v * 100) / 100}px`;
export const r2 = (v) => Math.round(v * 1000) / 1000;

/**
 * Masked kinetic headline: each word rises out of its own clip box.
 * Returns {root, set(tIn, tOut)} where progress values are 0..1 per word handled by caller.
 */
export function maskedWords(parent, text, cls) {
  const root = el('div', { class: 'abs words ' + cls }, parent);
  const words = text.split(' ').map((w) => {
    const m = el('span', { class: 'wmask' }, root);
    const i = el('span', { class: 'winner' }, m);
    i.textContent = w;
    return i;
  });
  // Interleave real spaces so wrapping & measuring behave like text.
  const masks = [...root.children];
  masks.forEach((m, i) => { if (i < masks.length - 1) m.after(document.createTextNode(' ')); });
  return {
    root, words,
    /** inT: entrance time, outT: exit time (or Infinity), stagger per word. */
    render(t, inT, outT = Infinity, stagger = 0.06, dIn = 0.6, dOut = 0.38) {
      words.forEach((w, i) => {
        const a = ease.emph(prog(t, inT + i * stagger, inT + i * stagger + dIn));
        const b = ease.emphIn(prog(t, outT + i * stagger * 0.6, outT + i * stagger * 0.6 + dOut));
        const y = (1 - a) * 135 - b * 135;
        css(w, { transform: `translateY(${r2(y)}%)`, opacity: String(r2(Math.min(1, a * 1.4) * (1 - b * 0.6))) });
      });
      show(root, t >= inT - 0.01 && t <= outT + dOut + words.length * stagger + 0.05);
    },
  };
}

/**
 * Hop with anticipation, stretch in the air and squash on landing.
 * Returns {lift, sx, sy} — lift in caller units (multiply by height h).
 */
export function hop(t, t0, t1, h, { antic = 0.12, squash = 0.15, stretch = 0.08 } = {}) {
  let lift = 0, sx = 1, sy = 1;
  if (t >= t0 - antic && t < t0) {
    const k = ease.smooth(prog(t, t0 - antic, t0));
    sy = 1 - 0.08 * k; sx = 1 + 0.06 * k;
  } else if (t >= t0 && t < t1) {
    const u = prog(t, t0, t1);
    lift = h * 4 * u * (1 - u);
    const st = stretch * Math.abs(1 - 2 * u);
    const k = ease.smooth(prog(t, t0, t0 + 0.07));
    sy = lerp(0.92, 1 + st, k); sx = lerp(1.06, 1 - st * 0.7, k);
  } else if (t >= t1) {
    const w = wobble(t - t1, 2.6, 7.5);
    sy = 1 - squash * w; sx = 1 + squash * 0.8 * w;
  }
  return { lift, sx, sy };
}

/** 0→1→0 bump with zero velocity at both ends (for camera drifts). */
export const bump = (p) => (1 - Math.cos(2 * Math.PI * clamp(p))) / 2;

/** Orobi placement by visual centre → ground-anchor state fields. */
export const atCentre = (cx, cy, w) => ({ x: cx, y: cy + (1148 - 810) * (w / 1200), w });
