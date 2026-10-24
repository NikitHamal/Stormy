// Core DSP: buffers, notes, oscillators, filters, envelopes. Deterministic (seeded PRNG only).
export const SR = 48000;
export const TAU = Math.PI * 2;

export function rng(seed) { // mulberry32
  let a = seed >>> 0;
  return () => { a = (a + 0x6d2b79f5) >>> 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const NOTE_BASE = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
/** "F#4" -> MIDI number. Numbers pass through. */
export function midi(n) {
  if (typeof n === 'number') return n;
  const m = /^([A-G])([#b]?)(-?\d)$/.exec(n);
  if (!m) throw new Error('bad note ' + n);
  return 12 * (+m[3] + 1) + NOTE_BASE[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0);
}
export const hz = (m) => 440 * 2 ** ((midi(m) - 69) / 12);
export const db = (d) => 10 ** (d / 20);
export const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
export const expGlide = (a, b, u) => a * (b / a) ** clamp(u);

/** A stereo buffer. */
export class Bus {
  constructor(n) { this.n = n; this.L = new Float32Array(n); this.R = new Float32Array(n); }
}

/** Phase accumulator. */
export function osc() { let ph = 0; return (f) => { ph += f / SR; ph -= Math.floor(ph); return ph; }; }
export function saw(ph, dt) { // PolyBLEP
  let v = 2 * ph - 1;
  if (ph < dt) { const x = ph / dt; v -= x + x - x * x - 1; } else if (ph > 1 - dt) { const x = (ph - 1) / dt; v -= x * x + x + x + 1; }
  return v;
}
export function square(ph, dt, pw = 0.5) { return saw(ph, dt) - saw((ph + 1 - pw) % 1, dt); }
export const tri = (ph) => 1 - 4 * Math.abs(ph - 0.5);

/** TPT state-variable filter. mode 'lp' | 'bp' | 'hp'. */
export function svf(mode = 'lp') {
  let ic1 = 0, ic2 = 0, lastFc = -1, g = 0;
  return (x, fc, q = 0.707) => {
    if (fc !== lastFc) { g = Math.tan((Math.PI * Math.min(Math.max(fc, 10), SR * 0.45)) / SR); lastFc = fc; }
    const k = 1 / q, a1 = 1 / (1 + g * (g + k)), a2 = g * a1, a3 = g * a2;
    const v3 = x - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
    ic1 = 2 * v1 - ic1; ic2 = 2 * v2 - ic2;
    return mode === 'lp' ? v2 : mode === 'bp' ? v1 * k : x - k * v1 - v2;
  };
}
export function onepole(fc) { const a = Math.exp(-TAU * fc / SR); let z = 0; return (x) => (z = x * (1 - a) + z * a); }

/** ADSR value at time t for a note held `hold` seconds. */
export function adsr(t, hold, a, d, s, r) {
  let v;
  if (t < a) v = t / a;
  else if (t < a + d) v = 1 - (1 - s) * ((t - a) / d);
  else v = s;
  if (t > hold) {
    const atRelease = hold < a ? hold / a : hold < a + d ? 1 - (1 - s) * ((hold - a) / d) : s;
    v = atRelease * Math.exp(-(t - hold) / Math.max(1e-4, r / 4.6));
  }
  return v;
}

/**
 * Render one mono voice into a bus (constant-power pan, 1 ms in / 5 ms out
 * fades so nothing clicks), with optional sends.
 */
export function voice(bus, t0, len, fn, { pan = 0, gain = 1, sends = [] } = {}) {
  const s0 = Math.round(t0 * SR), n = Math.round(len * SR);
  const p = clamp(pan, -1, 1);
  const gl = Math.cos(((p + 1) * Math.PI) / 4) * gain, gr = Math.sin(((p + 1) * Math.PI) / 4) * gain;
  const fi = 0.001 * SR, fo = 0.005 * SR;
  for (let k = 0; k < n; k++) {
    const i = s0 + k;
    if (i < 0) continue;
    if (i >= bus.n) break;
    let v = fn(k / SR, k);
    if (k < fi) v *= k / fi;
    if (n - k < fo) v *= (n - k) / fo;
    bus.L[i] += v * gl; bus.R[i] += v * gr;
    for (const [sb, amt] of sends) { sb.L[i] += v * gl * amt; sb.R[i] += v * gr * amt; }
  }
}
