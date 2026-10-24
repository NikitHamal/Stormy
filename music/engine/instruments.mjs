// Instruments. Each is play(track, t, note, dur, opts): track = { bus, sends, seed }.
// All synthesis: supersaw pads, FM electric piano, additive piano, Karplus-Strong plucks,
// analog-style bass/lead, FM bells, string ensemble, and a full drum kit from noise and sines.
import { SR, TAU, osc, saw, square, tri, svf, onepole, adsr, voice, hz, midi, rng, expGlide, clamp } from './core.mjs';

const v = (track, t, len, fn, o = {}) => voice(track.bus, t, len, fn, { pan: o.pan ?? 0, gain: o.gain ?? 1, sends: track.sends });

/** Wide supersaw pad: 5 detuned saws per note, slow filter, soft attack. */
export function pad(track, t, note, dur, o = {}) {
  const f = hz(note), lp = svf('lp'), r = rng((midi(note) * 977 + Math.round(t * 100)) >>> 0);
  const det = [-0.11, -0.05, 0, 0.05, 0.11].map((c) => 2 ** (c / 12)), oscs = det.map(() => osc());
  const ph0 = det.map(() => r()); let first = true;
  const a = o.attack ?? 0.8, rel = o.release ?? 1.6, bright = o.bright ?? 0.5;
  v(track, t, dur + rel, (tt) => {
    if (first) { first = false; }
    let s = 0;
    for (let i = 0; i < 5; i++) s += saw((oscs[i](f * det[i]) + ph0[i]) % 1, (f * det[i]) / SR);
    const e = adsr(tt, dur, a, 0.6, 0.85, rel);
    const cut = 300 + (900 + 4000 * bright) * (0.55 + 0.45 * Math.sin(TAU * 0.13 * (t + tt))) * e;
    return lp(s / 5, cut, 0.9) * e;
  }, o);
}

/** Bowed string ensemble: 3 saws, vibrato fades in, warm low-pass. */
export function strings(track, t, note, dur, o = {}) {
  const f = hz(note), lp = svf('lp'), hp = svf('hp'), oscs = [osc(), osc(), osc()], det = [-0.06, 0, 0.07].map((c) => 2 ** (c / 12));
  const rel = o.release ?? 0.9;
  v(track, t, dur + rel, (tt) => {
    const vib = 1 + 0.004 * Math.sin(TAU * 5.2 * tt) * clamp(tt / 0.6);
    let s = 0; for (let i = 0; i < 3; i++) s += saw(oscs[i](f * det[i] * vib), f / SR);
    const e = adsr(tt, dur, o.attack ?? 0.35, 0.3, 0.9, rel);
    return hp(lp(s / 3, 1800 + 1600 * (o.bright ?? 0.4), 0.7), 180) * e;
  }, o);
}

/** FM electric piano (tine + bell bark), with stereo tremolo when o.trem is set. */
export function keys(track, t, note, dur, o = {}) {
  const f = hz(note), c = osc(), m = osc(), tine = osc(), vel = o.vel ?? 0.8;
  const rel = 0.35;
  v(track, t, dur + rel + 0.8, (tt) => {
    const idx = (1.6 + 1.6 * vel) * Math.exp(-tt / 0.35);
    const mod = Math.sin(TAU * m(f)) * idx;
    const body = Math.sin(TAU * c(f) + mod);
    const bark = Math.sin(TAU * tine(f * 14.02)) * 0.12 * vel * Math.exp(-tt / 0.03);
    const e = Math.exp(-tt / (1.4 + 0.6 * (60 - Math.min(60, midi(note) - 40)) / 60)) * (tt > dur ? Math.exp(-(tt - dur) / rel) : 1);
    const trem = o.trem ? 1 - 0.25 * (0.5 + 0.5 * Math.sin(TAU * 4.5 * (t + tt))) : 1;
    return (body + bark) * e * vel * trem * Math.min(1, tt / 0.003);
  }, o);
}

/** Piano-like: inharmonic additive partials, each decaying at its own rate, plus a hammer thump. */
export function piano(track, t, note, dur, o = {}) {
  const f = hz(note), mm = midi(note), vel = o.vel ?? 0.7, B = 0.00045;
  const parts = [1, 2, 3, 4, 5, 6, 7].map((n) => ({ f: f * n * Math.sqrt(1 + B * n * n), a: (1 / n ** 1.15) * (n === 1 ? 1 : 0.6 + 0.4 * vel), d: (3.2 - mm / 40) / n ** 0.7, o: osc() }));
  const hammer = svf('bp'), r = rng(mm * 31 + 7);
  const rel = 0.25;
  v(track, t, Math.min(dur + 2.5, 7), (tt) => {
    let s = 0;
    for (const p of parts) { if (p.f > 16000) continue; s += Math.sin(TAU * p.o(p.f)) * p.a * Math.exp(-tt / Math.max(0.15, p.d)); }
    const damp = tt > dur ? Math.exp(-(tt - dur) / rel) : 1;
    const th = hammer(r() * 2 - 1, f * 3, 1.5) * Math.exp(-tt / 0.012) * 0.6;
    return (s * 0.55 + th) * vel * damp * Math.min(1, tt / 0.002);
  }, o);
}

/** Karplus-Strong plucked string (guitar / harp-like). */
export function pluck(track, t, note, dur, o = {}) {
  const f = hz(note), N = Math.max(2, Math.round(SR / f)), buf = new Float32Array(N), r = rng(midi(note) * 131 + Math.round(t * 997));
  const bright = o.bright ?? 0.6;
  for (let i = 0; i < N; i++) buf[i] = r() * 2 - 1;
  let idx = 0, last = 0; const lp = onepole(2000 + 6000 * bright);
  const decay = o.decay ?? 0.996;
  v(track, t, Math.min(dur + 1.8, 5), (tt) => {
    const cur = buf[idx], next = buf[(idx + 1) % N];
    const out = (cur + next) * 0.5 * decay;
    buf[idx] = out; idx = (idx + 1) % N;
    last = lp(cur);
    return last * (tt > dur + 0.4 ? Math.exp(-(tt - dur - 0.4) / 0.25) : 1) * (o.vel ?? 0.8);
  }, o);
}

/** Filtered synth pluck / arp: square + saw, fast filter envelope. */
export function arp(track, t, note, dur, o = {}) {
  const f = hz(note), a = osc(), b = osc(), lp = svf('lp');
  v(track, t, dur + 0.25, (tt) => {
    const s = 0.6 * square(a(f), f / SR, 0.35) + 0.4 * saw(b(f * 1.005), f / SR);
    const env = Math.exp(-tt / (o.decay ?? 0.12));
    return lp(s, 400 + (o.cut ?? 5200) * env, 1.6) * Math.min(1, tt / 0.002) * (tt > dur ? Math.exp(-(tt - dur) / 0.04) : 1) * (0.25 + 0.75 * env);
  }, o);
}

/** FM bell / celesta. */
export function bell(track, t, note, dur, o = {}) {
  const f = hz(note), c = osc(), m = osc(), c2 = osc(), m2 = osc(), dec = o.decay ?? 1.4;
  v(track, t, dec * 4, (tt) => {
    const a = Math.sin(TAU * c(f) + Math.sin(TAU * m(f * 3.5)) * 1.8 * Math.exp(-tt / 0.4));
    const b = Math.sin(TAU * c2(f * 2.0) + Math.sin(TAU * m2(f * 1.41)) * 0.8 * Math.exp(-tt / 0.15));
    return (0.75 * a + 0.25 * b * Math.exp(-tt / (dec * 0.3))) * Math.exp(-tt / dec) * Math.min(1, tt / 0.002) * (f > 1500 ? 0.7 : 1);
  }, o);
}

/** Lead: two saws + square sub, vibrato after the attack, optional glide from o.from. */
export function lead(track, t, note, dur, o = {}) {
  const f1 = hz(note), f0 = o.from ? hz(o.from) : f1, a = osc(), b = osc(), c = osc(), lp = svf('lp');
  const rel = o.release ?? 0.18;
  v(track, t, dur + rel, (tt) => {
    const f = expGlide(f0, f1, tt / 0.06) * (1 + 0.006 * Math.sin(TAU * 5.6 * tt) * clamp((tt - 0.18) / 0.3));
    const s = 0.45 * saw(a(f), f / SR) + 0.35 * saw(b(f * 1.007), f / SR) + 0.2 * square(c(f / 2), f / SR / 2);
    const e = adsr(tt, dur, 0.012, 0.25, 0.75, rel);
    return lp(s, 900 + (o.bright ?? 3200) * (0.4 + 0.6 * e), 1.1) * e;
  }, o);
}

/** Bass: sub sine + filtered saw. */
export function bass(track, t, note, dur, o = {}) {
  const f = hz(note), a = osc(), b = osc(), lp = svf('lp');
  v(track, t, dur + 0.08, (tt) => {
    const e = adsr(tt, dur, 0.004, 0.2, 0.7, 0.06);
    const s = 0.7 * Math.sin(TAU * a(f)) + 0.45 * saw(b(f), f / SR);
    return Math.tanh(1.4 * lp(s, 180 + (o.cut ?? 700) * Math.exp(-tt / 0.09), 0.9)) * e;
  }, o);
}

/** 808: pitched sine with a drop, saturated, long tail. */
export function bass808(track, t, note, dur, o = {}) {
  const f = hz(note), a = osc();
  v(track, t, dur + 0.3, (tt) => {
    const fr = f * (1 + 1.4 * Math.exp(-tt / 0.03));
    return Math.tanh(1.8 * Math.sin(TAU * a(fr))) * Math.exp(-tt / (o.decay ?? 0.9)) * (tt > dur ? Math.exp(-(tt - dur) / 0.08) : 1);
  }, o);
}

// ------------------------------------------------------------------ drums
let noiseSeed = 1;
const noise = () => { const r = rng(noiseSeed++ * 7919); return () => r() * 2 - 1; };

export function kick(track, t, _n, _d, o = {}) {
  const a = osc(), nz = noise(), punch = o.punch ?? 1;
  v(track, t, 0.6, (tt) => {
    const f = expGlide(o.hi ?? 170, o.lo ?? 56, tt / 0.065);
    const body = Math.sin(TAU * a(f)) * Math.exp(-tt / (o.decay ?? 0.26));
    const click = nz() * Math.exp(-tt / 0.0025) * 0.3 * punch;
    return Math.tanh(1.9 * (body + click));
  }, o);
}
export function snare(track, t, _n, _d, o = {}) {
  const a = osc(), b = osc(), nz = noise(), bp = svf('bp'), hp = svf('hp');
  v(track, t, 0.45, (tt) => {
    const tone = (Math.sin(TAU * a(185)) + 0.5 * Math.sin(TAU * b(330))) * Math.exp(-tt / 0.06) * 0.5;
    const n = hp(bp(nz(), 3800, 0.7), 900) * Math.exp(-tt / (o.decay ?? 0.13));
    return (tone + n * 1.1) * 0.8;
  }, o);
}
export function clap(track, t, _n, _d, o = {}) {
  const nz = noise(), bp = svf('bp');
  v(track, t, 0.4, (tt) => {
    const bursts = [0, 0.008, 0.017, 0.026].reduce((s, d) => s + (tt >= d ? Math.exp(-(tt - d) / 0.005) : 0), 0) + 0.7 * Math.exp(-tt / 0.1);
    return bp(nz(), 1400, 1.1) * bursts * 0.9;
  }, o);
}
export function hat(track, t, _n, _d, o = {}) {
  const nz = noise(), hp = svf('hp'), open = o.open;
  v(track, t, open ? 0.4 : 0.08, (tt) => hp(nz(), 7800, 0.8) * Math.exp(-tt / (open ? 0.11 : 0.022)) * 0.5, o);
}
export function shaker(track, t, _n, _d, o = {}) {
  const nz = noise(), bp = svf('bp');
  v(track, t, 0.12, (tt) => bp(nz(), 6500, 1.2) * Math.sin(Math.PI * clamp(tt / 0.07)) * 0.35, o);
}
export function rim(track, t, _n, _d, o = {}) {
  const a = osc(), nz = noise(), bp = svf('bp');
  v(track, t, 0.08, (tt) => (Math.sin(TAU * a(1700)) * 0.6 + bp(nz(), 2500, 2) * 0.5) * Math.exp(-tt / 0.012), o);
}
export function ride(track, t, _n, _d, o = {}) {
  const nz = noise(), bp = svf('bp'), m = [osc(), osc(), osc()];
  v(track, t, o.crash ? 2.6 : 1.2, (tt) => {
    const metal = Math.sin(TAU * m[0](3100) + Math.sin(TAU * m[1](4630)) * 2) * 0.3;
    return (bp(nz(), o.crash ? 6000 : 9000, 0.6) + metal) * Math.exp(-tt / (o.crash ? 0.9 : 0.35)) * 0.35;
  }, o);
}
export function tom(track, t, note, _d, o = {}) {
  const a = osc(), f = hz(note ?? o.note ?? 'A2');
  v(track, t, 0.5, (tt) => Math.tanh(1.5 * Math.sin(TAU * a(expGlide(f * 1.5, f, tt / 0.05)))) * Math.exp(-tt / 0.22), o);
}

// ------------------------------------------------------------------ fx
export function riser(track, t, _n, dur, o = {}) {
  const nz = noise(), bp = svf('bp'), a = osc();
  v(track, t, dur, (tt) => {
    const u = tt / dur;
    return (bp(nz(), expGlide(400, 9000, u), 1.2) * 0.8 + 0.15 * saw(a(expGlide(110, 880, u)), 880 / SR)) * u ** 2.2;
  }, o);
}
export function swell(track, t, _n, dur, o = {}) {
  const nz = noise(), lp = svf('lp');
  v(track, t, dur, (tt) => lp(nz(), expGlide(200, 6000, tt / dur), 0.8) * (tt / dur) ** 2 * 0.7, o);
}
export function impact(track, t, _n, _d, o = {}) {
  const a = osc(), nz = noise(), lp = svf('lp');
  v(track, t, 2.6, (tt) => 0.9 * Math.sin(TAU * a(expGlide(90, 32, tt / 0.6))) * Math.exp(-tt / 0.8) + lp(nz(), 2500 * Math.exp(-tt / 0.2) + 150) * Math.exp(-tt / 0.3) * 0.5, o);
}
/** Vinyl crackle + hiss for the lo-fi track. */
export function vinyl(track, t, _n, dur, o = {}) {
  const r = rng(4242), lp = svf('lp');
  v(track, t, dur, () => {
    const pop = r() < 0.0007 ? (r() * 2 - 1) * 0.9 : 0;
    return lp((r() * 2 - 1) * 0.05 + pop, 4000) * 0.6;
  }, o);
}
