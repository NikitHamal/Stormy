// Procedural score + sound design for the launch film. No samples: every sound is synthesised here,
// deterministically (seeded PRNG), from the cue sheet in src/js/cues.js -- the same beat tables
// the picture animates from, so a retimed scene moves its sounds with it.
//
//   node scripts/audio.mjs            -> out/oro-launch-audio.wav (48 kHz stereo, -14 LUFS, -1 dBTP)
//   node scripts/audio.mjs --stems    -> also out/audio-stems/{music,sfx}.wav for mixing checks
//
// Signal flow: music bus (pad, bass, arp, drums) is side-chained by the kick and ducked under the
// SFX bus; both feed a stereo reverb and a ping-pong delay; the sum is soft-saturated, then ffmpeg's
// two-pass loudnorm sets integrated loudness and true peak.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import ffmpeg from 'ffmpeg-static';
import { ROOT, config, arg } from './lib.mjs';

const SR = 48000;
const cfg = config();
const { cues: CUES, total } = await import(pathToFileURL(path.join(ROOT, 'src/js/cues.js')).href).then((m) => m.cues(cfg));
const N = Math.round(total * SR);
const TAU = Math.PI * 2;

// ------------------------------------------------------------------ primitives
function rng(seed) { // mulberry32
  let a = seed >>> 0;
  return () => { a = (a + 0x6d2b79f5) >>> 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const noiseRng = rng(0x5eed);
const hz = (midi) => 440 * 2 ** ((midi - 69) / 12);
const NOTE = (n) => { // "D4", "F#3", "Bb2"
  const m = /^([A-G])([#b]?)(-?\d)$/.exec(n); const base = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 }[m[1]];
  return 12 * (Number(m[3]) + 1) + base + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0);
};
const expGlide = (a, b, u) => a * (b / a) ** Math.min(1, Math.max(0, u));
const env = (tt, a, d) => (tt < a ? tt / a : Math.exp(-(tt - a) / d)); // attack then exponential decay

class Bus { constructor() { this.L = new Float32Array(N); this.R = new Float32Array(N); } }
const music = new Bus(), sfx = new Bus(), verbSend = new Bus(), delaySend = new Bus();

/** Render a mono voice into a bus with constant-power pan; every voice gets 1 ms in / 4 ms out fades. */
function voice(bus, t0, len, fn, { pan = 0, gain = 1, verb = 0, delay = 0 } = {}) {
  const s0 = Math.round(t0 * SR), n = Math.round(len * SR);
  const p = Math.max(-1, Math.min(1, pan));
  const gl = Math.cos(((p + 1) * Math.PI) / 4) * gain, gr = Math.sin(((p + 1) * Math.PI) / 4) * gain;
  const fi = 0.001 * SR, fo = 0.004 * SR;
  for (let k = 0; k < n; k++) {
    const i = s0 + k;
    if (i < 0) continue;
    if (i >= N) break;
    let v = fn(k / SR, k);
    if (k < fi) v *= k / fi;
    if (n - k < fo) v *= (n - k) / fo;
    const l = v * gl, r = v * gr;
    bus.L[i] += l; bus.R[i] += r;
    if (verb) { verbSend.L[i] += l * verb; verbSend.R[i] += r * verb; }
    if (delay) { delaySend.L[i] += l * delay; delaySend.R[i] += r * delay; }
  }
}

/** Topology-preserving state-variable filter; mode 'lp' | 'bp' | 'hp'. Cutoff may change per sample. */
function svf(mode = 'lp') {
  let ic1 = 0, ic2 = 0, lastFc = -1, g = 0;
  return (x, fc, q = 0.707) => {
    if (fc !== lastFc) { g = Math.tan((Math.PI * Math.min(fc, SR * 0.45)) / SR); lastFc = fc; }
    const k = 1 / q, a1 = 1 / (1 + g * (g + k)), a2 = g * a1, a3 = g * a2;
    const v3 = x - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
    ic1 = 2 * v1 - ic1; ic2 = 2 * v2 - ic2;
    return mode === 'lp' ? v2 : mode === 'bp' ? v1 * k : x - k * v1 - v2;
  };
}

/** PolyBLEP saw, phase in [0,1). */
function saw(ph, dt) {
  let v = 2 * ph - 1;
  if (ph < dt) { const x = ph / dt; v -= x + x - x * x - 1; } else if (ph > 1 - dt) { const x = (ph - 1) / dt; v -= x * x + x + x + 1; }
  return v;
}

/** Phase accumulator for a time-varying frequency. */
function osc() { let ph = 0; return (f) => { ph += f / SR; ph -= Math.floor(ph); return ph; }; }

// ------------------------------------------------------------------ instruments
/** FM bell / pluck: carrier + modulator at `ratio`, index decaying. */
function fmTone(f, { ratio = 2, index = 2, idxDecay = 0.15, decay = 0.4, attack = 0.002 } = {}) {
  const c = osc(), m = osc();
  return (tt) => {
    const mod = Math.sin(TAU * m(f * ratio)) * index * Math.exp(-tt / idxDecay);
    return Math.sin(TAU * c(f) + mod) * env(tt, attack, decay);
  };
}

function bell(bus, t, midi, o = {}) {
  // Two FM pairs, the second slightly inharmonic, so a held bell shimmers instead of reading as a
  // sine test tone; high bells are tamed, since above ~1.5 kHz a pure partial gets piercing fast.
  const f = hz(midi), decay = o.decay ?? 0.7, len = decay * 4;
  const a = fmTone(f, { ratio: 3.5, index: 1.6, idxDecay: 0.3, decay, attack: 0.003 });
  const b = fmTone(f * 2.756, { ratio: 1.41, index: 0.8, idxDecay: 0.12, decay: decay * 0.3 });
  const c = fmTone(f * 1.003, { ratio: 1, index: 0.3, idxDecay: 0.5, decay: decay * 0.8 });
  const tame = f > 1500 ? 0.7 : 1;
  voice(bus, t, len, (tt) => (0.55 * a(tt) + 0.2 * b(tt) + 0.25 * c(tt) * Math.sin(TAU * 4.5 * tt + 1)) * tame, { verb: 0.45, delay: 0.12, ...o });
}

function pluck(bus, t, midi, o = {}) {
  const f = hz(midi), lp = svf('lp');
  const tone = fmTone(f, { ratio: 1, index: 2.4, idxDecay: 0.07, decay: o.decay ?? 0.22 });
  voice(bus, t, (o.decay ?? 0.22) * 6, (tt) => lp(tone(tt), 900 + 5000 * Math.exp(-tt / 0.08)), { verb: 0.25, delay: 0.3, ...o });
}

function padChord(t0, t1, notes, { gain = 0.03, bright = 1 } = {}) {
  const r = rng(Math.round(t0 * 1000));
  notes.forEach((n, j) => {
    const f = hz(NOTE(n));
    const oscs = [osc(), osc(), osc()], det = [-0.07, 0.0, 0.08].map((c) => 2 ** (c / 12));
    oscs.forEach(() => r()); // advance for stable phases
    const lp = svf('lp');
    const len = t1 - t0 + 0.9;
    voice(music, t0, len, (tt) => {
      let s = 0;
      for (let q = 0; q < 3; q++) s += saw(oscs[q](f * det[q]), (f * det[q]) / SR);
      const a = Math.min(1, tt / 0.35) * (tt > t1 - t0 ? Math.exp(-(tt - (t1 - t0)) / 0.3) : 1);
      const cut = (500 + 2200 * bright) * (1 + 0.15 * Math.sin(TAU * 0.3 * (t0 + tt) + j));
      return lp(s / 3, cut, 0.8) * a;
    }, { pan: (j % 2 ? 1 : -1) * (0.15 + 0.12 * j), gain, verb: 0.3 });
  });
}

function bass(t, dur, midi, { gain = 0.16 } = {}) {
  const f = hz(midi), o1 = osc(), o2 = osc(), lp = svf('lp');
  voice(music, t, dur + 0.12, (tt) => {
    const a = Math.min(1, tt / 0.004) * Math.exp(-tt / (dur * 0.9 + 0.05)) * (tt > dur ? Math.exp(-(tt - dur) / 0.03) : 1);
    const s = 0.65 * Math.sin(TAU * o1(f)) + 0.35 * saw(o2(f), f / SR);
    return Math.tanh(1.6 * lp(s, 240 + 900 * Math.exp(-tt / 0.06))) * a;
  }, { gain });
}

const kickTimes = [];
function kick(t, gain = 0.55) {
  kickTimes.push(t);
  const o = osc();
  voice(music, t, 0.5, (tt) => {
    const f = expGlide(160, 46, tt / 0.09);
    const body = Math.sin(TAU * o(f)) * Math.exp(-tt / 0.22);
    const click = (noiseRng() * 2 - 1) * Math.exp(-tt / 0.003) * 0.25;
    return Math.tanh(1.8 * (body + click));
  }, { gain });
}
function clap(t, gain = 0.2) {
  const bp = svf('bp'), r = rng(Math.round(t * 977));
  voice(music, t, 0.35, (tt) => {
    const bursts = [0, 0.009, 0.019].reduce((s, o) => s + (tt >= o ? Math.exp(-(tt - o) / 0.006) : 0), 0) + 0.6 * Math.exp(-tt / 0.09);
    return bp(r() * 2 - 1, 1500, 1.2) * bursts;
  }, { gain, verb: 0.25 });
}
function hat(t, gain = 0.06, open = false, pan = 0.2) {
  const hp = svf('hp'), r = rng(Math.round(t * 1913));
  voice(music, t, open ? 0.25 : 0.06, (tt) => hp(r() * 2 - 1, 7500, 0.9) * Math.exp(-tt / (open ? 0.07 : 0.018)), { gain, pan });
}
function snareRoll(t0, t1) {
  let t = t0, step = 0.125;
  while (t < t1) { clap(t, 0.06 + 0.12 * ((t - t0) / (t1 - t0))); t += step; step = Math.max(0.0625, step * 0.93); }
}

// ------------------------------------------------------------------ the score
// 120 BPM, bars start at 0.5 s + 2n, so the 2.5 s and 12.5 s cuts land on downbeats and
// 6.0 s / 10.0 s on beat three. D major, warm and bright, resolving on the end card.
const BEAT = 0.5;
const CH = {
  Dmaj9: ['D3', 'A3', 'C#4', 'E4', 'F#4'], Bm9: ['B2', 'F#3', 'A3', 'C#4', 'D4'], Gmaj9: ['G2', 'D3', 'F#3', 'A3', 'B3'],
  Asus: ['A2', 'E3', 'A3', 'B3', 'D4'], Em9: ['E2', 'B2', 'D3', 'F#3', 'G3'], A7sus: ['A2', 'E3', 'G3', 'B3', 'D4'],
  Dend: ['D2', 'A2', 'F#3', 'C#4', 'E4', 'A4'],
};
const PROG = [ // [start, end, chord, bassNote, brightness]
  [0.1, 2.5, 'Dmaj9', 'D2', 0.25], [2.5, 4.5, 'Bm9', 'B1', 0.45], [4.5, 6.0, 'Gmaj9', 'G1', 0.55],
  [6.0, 8.0, 'Asus', 'A1', 0.8], [8.0, 9.0, 'Dmaj9', 'D2', 0.9], [9.0, 10.0, 'Bm9', 'B1', 0.9],
  [10.0, 11.0, 'Gmaj9', 'G1', 1.1], [11.0, 11.5, 'Em9', 'E2', 1.1], [11.5, 12.5, 'A7sus', 'A1', 1.25],
  [12.5, total, 'Dend', 'D1', 1.0],
];
PROG.forEach(([a, b, ch, , br], i) => padChord(a, b, CH[ch], { gain: i === 0 ? 0.022 : i === PROG.length - 1 ? 0.03 : 0.026, bright: br }));

for (let t = 0.5; t < total - 1e-9; t += BEAT / 2) {
  const beat = Math.round((t - 0.5) / BEAT * 2) / 2; // in beats from the grid origin
  const onBeat = Number.isInteger(beat), bar = Math.floor(beat / 4), inBar = beat - bar * 4;
  const section = t < 2.5 ? 'intro' : t < 6 ? 'describe' : t < 10 ? 'live' : t < 12.5 ? 'models' : 'end';
  const chord = PROG.find(([a, b]) => t >= a && t < b) || PROG[PROG.length - 1];
  const root = NOTE(chord[3]);
  const whipGap = t >= 7.85 && t < 8.3; // drums drop out under the whip pan
  if (section === 'describe') {
    if (onBeat) kick(t, 0.38);
    if (!onBeat) hat(t, 0.035);
    if (onBeat) bass(t, BEAT * 0.8, root, { gain: 0.11 });
  } else if (section === 'live' && !whipGap) {
    if (onBeat) kick(t, 0.5);
    if (onBeat && (inBar === 1 || inBar === 3)) clap(t, 0.17);
    hat(t, onBeat ? 0.03 : 0.055, inBar === 1.5 || inBar === 3.5, onBeat ? 0.25 : -0.2);
    hat(t + BEAT / 4, 0.022, false, 0.3);
    bass(t, BEAT * 0.42, root + (onBeat ? 0 : 12), { gain: 0.12 });
  } else if (section === 'models') {
    if (t < 11.5) {
      if (onBeat && inBar % 2 === 0) kick(t, 0.42);
      if (onBeat && inBar % 2 === 1) clap(t, 0.12);
      if (!onBeat) hat(t, 0.04, false, -0.15);
      if (onBeat) bass(t, BEAT * 0.9, root, { gain: 0.1 });
    } else if (t < 12.4 && onBeat) bass(t, BEAT * 0.4, root, { gain: 0.09 });
  }
}
snareRoll(11.5, 12.42);

// Arpeggios: 8ths in describe, 16ths in live, bright 8ths in models. Pluck into the delay.
function arp(t0, t1, step, oct, gain) {
  for (let t = t0, i = 0; t < t1 - 1e-6; t += step, i++) {
    if (t >= 7.85 && t < 8.3) continue;
    const chord = PROG.find(([a, b]) => t >= a && t < b);
    const notes = CH[chord[2]].slice(1).map(NOTE);
    const pat = [0, 2, 1, 3, 2, 1, 3, 0];
    pluck(music, t, notes[pat[i % pat.length] % notes.length] + 12 * oct, { gain, pan: (i % 2 ? 0.35 : -0.35), decay: 0.16 });
  }
}
arp(0.5, 2.5, BEAT, 1, 0.025);
arp(2.5, 6.0, BEAT / 2, 1, 0.04);
arp(6.0, 10.0, BEAT / 4, 1, 0.035);
arp(10.0, 12.4, BEAT / 2, 2, 0.03);

// ------------------------------------------------------------------ sound design
function noiseSweep(bus, t, len, f0, f1, { q = 1.4, shape = (u) => Math.sin(Math.PI * u), panFrom = 0, panTo = 0, gain = 0.3, verb = 0.2, mode = 'bp' } = {}) {
  const f = svf(mode), r = rng(Math.round(t * 7919));
  const n = Math.round(len * SR);
  // Pan sweeps are rendered as two half-voices so position can move with time.
  const L = new Float32Array(n);
  for (let k = 0; k < n; k++) { const u = k / n; L[k] = f(r() * 2 - 1, expGlide(f0, f1, u), q) * shape(u); }
  const s0 = Math.round(t * SR);
  for (let k = 0; k < n; k++) {
    const i = s0 + k; if (i < 0 || i >= N) continue;
    const u = k / n, p = panFrom + (panTo - panFrom) * u;
    const fade = Math.min(1, k / 48, (n - k) / 192);
    const gl = Math.cos(((p + 1) * Math.PI) / 4) * gain * fade, gr = Math.sin(((p + 1) * Math.PI) / 4) * gain * fade;
    bus.L[i] += L[k] * gl; bus.R[i] += L[k] * gr;
    verbSend.L[i] += L[k] * gl * verb; verbSend.R[i] += L[k] * gr * verb;
  }
}

function blip(t, f0, f1, len, gain, pan = 0, verb = 0.1, decay = len / 3) {
  const o = osc();
  voice(sfx, t, len, (tt) => Math.sin(TAU * o(expGlide(f0, f1, tt / len))) * env(tt, 0.002, decay), { gain, pan, verb });
}

const PENT = ['D5', 'E5', 'F#5', 'A5', 'B5', 'D6', 'E6', 'F#6', 'A6', 'B6'].map(NOTE);

const SFX = {
  swell(c) {
    // The loader drawing itself: a soft rising cluster and wind, opening into the bloom.
    const o1 = osc(), o2 = osc(), o3 = osc(), lp = svf('lp');
    voice(sfx, c.t, c.dur + 0.4, (tt) => {
      const u = tt / c.dur, a = Math.min(1, u) ** 2 * (tt > c.dur ? Math.exp(-(tt - c.dur) / 0.12) : 1);
      const s = Math.sin(TAU * o1(hz(NOTE('D5')) * (1 + 0.004 * Math.sin(TAU * 5 * tt)))) + 0.6 * Math.sin(TAU * o2(hz(NOTE('A5')))) + 0.4 * Math.sin(TAU * o3(hz(NOTE('E6'))));
      return lp(s, 600 + 4000 * Math.min(1, u)) * a * 0.33;
    }, { gain: 0.32, verb: 0.5 });
    noiseSweep(sfx, c.t, c.dur + 0.1, 400, 5200, { q: 0.9, shape: (u) => u ** 2.2 * (1 - u) * 4, gain: 0.12, verb: 0.4 });
  },
  bloom(c, scale = 1) {
    ['D6', 'F#6', 'A6', 'C#7', 'E7'].forEach((n, i) => bell(sfx, c.t + i * 0.035, NOTE(n), { gain: 0.11 * scale, decay: 0.7, pan: -0.5 + i * 0.25 }));
    noiseSweep(sfx, c.t - 0.05, 0.6, 6000, 12000, { mode: 'hp', q: 0.7, shape: (u) => Math.exp(-u * 4) * Math.min(1, u * 20), gain: 0.07 * scale, verb: 0.6 });
  },
  bloomSmall(c) { SFX.bloom(c, 0.55); },
  hopUp(c) {
    const o = osc();
    voice(sfx, c.t, 0.26, (tt) => Math.sin(TAU * o(expGlide(260, 680, tt / 0.13) * (1 + 0.03 * Math.sin(TAU * 28 * tt)))) * env(tt, 0.004, 0.07), { gain: 0.3 * (c.gain ?? 1), pan: c.pan, verb: 0.15 });
  },
  land(c) {
    const o = osc(), lp = svf('lp'), r = rng(Math.round(c.t * 31));
    voice(sfx, c.t, 0.35, (tt) => Math.tanh(1.4 * Math.sin(TAU * o(expGlide(150, 52, tt / 0.07)))) * env(tt, 0.001, 0.09) + lp(r() * 2 - 1, 500) * env(tt, 0.001, 0.025) * 0.6, { gain: 0.5 * (c.gain ?? 1), pan: c.pan, verb: 0.08 });
  },
  chirp(c) {
    // Orobi wakes: a two-note "bwip".
    const g = 0.22 * (c.gain ?? 1);
    blip(c.t, 1050, 1500, 0.08, g, c.pan, 0.2, 0.03);
    blip(c.t + 0.075, 1400, 2100, 0.11, g * 0.9, c.pan, 0.25, 0.04);
  },
  glance(c) { blip(c.t, 3200, 2600, 0.03, 0.05, c.pan, 0.05, 0.007); },
  swish(c) { noiseSweep(sfx, c.t, 0.32, 1400, 7000, { q: 1.1, shape: (u) => Math.sin(Math.PI * u) ** 1.5, gain: 0.09 * (c.gain ?? 1), verb: 0.15 }); },
  uiPop(c) {
    const o = osc();
    voice(sfx, c.t, 0.2, (tt) => Math.sin(TAU * o(expGlide(380, 920, tt / 0.05))) * env(tt, 0.002, 0.045), { gain: 0.3, verb: 0.15 });
  },
  uiTick(c) { blip(c.t, 2400, 2300, 0.05, 0.08, c.pan, 0.1, 0.012); },
  key(c) {
    // A soft keyboard tap: a bright click and a low "thock", varied per key.
    const r = rng(1000 + c.seed), bp = svf('bp'), o = osc();
    const fc = 2600 + r() * 1800, thock = (c.space ? 150 : 230) + r() * 60, g = 0.13 + r() * 0.05;
    voice(sfx, c.t + r() * 0.006, 0.06, (tt) => bp(r() * 2 - 1, fc, 2) * env(tt, 0.0005, 0.004) * 1.6 + Math.sin(TAU * o(thock)) * env(tt, 0.001, c.space ? 0.02 : 0.012), { gain: g, pan: c.pan });
  },
  tap(c) {
    blip(c.t, 1300, 650, 0.06, 0.28, 0, 0.1, 0.018);
    noiseSweep(sfx, c.t, 0.03, 3000, 2000, { q: 1.5, shape: (u) => 1 - u, gain: 0.08, verb: 0 });
  },
  sendWhoosh(c) {
    noiseSweep(sfx, c.t, 0.42, 350, 3500, { q: 1.2, shape: (u) => Math.sin(Math.PI * u) * (1 - u * 0.4), gain: 0.2, verb: 0.2 });
    blip(c.t, 280, 900, 0.3, 0.06, 0, 0.3, 0.12);
  },
  gulp(c) {
    const o = osc(), bp = svf('bp');
    voice(sfx, c.t - 0.06, 0.3, (tt) => {
      const f = expGlide(560, 150, tt / 0.13);
      const s = Math.sin(TAU * o(f));
      return (s + 0.4 * bp(s, f * 3, 3)) * env(tt, 0.006, 0.07);
    }, { gain: 0.45, verb: 0.15 });
    SFX.land({ t: c.t + 0.05, gain: 0.35, pan: 0 });
  },
  whirr(c) {
    const o = osc(), lp = svf('lp');
    voice(sfx, c.t, c.dur + 0.2, (tt) => lp(saw(o(92), 92 / SR), 700) * (0.6 + 0.4 * Math.sin(TAU * 17 * tt)) * Math.min(1, tt / 0.08) * Math.exp(-tt / 0.5), { gain: 0.05, verb: 0.2 });
  },
  rise(c) {
    noiseSweep(sfx, c.t, c.dur + 0.15, 180, 1400, { mode: 'lp', q: 0.9, shape: (u) => Math.sin(Math.PI * Math.min(1, u * 1.2)) ** 1.2, gain: 0.32, verb: 0.15 });
    const o = osc();
    voice(sfx, c.t, c.dur + 0.1, (tt) => Math.sin(TAU * o(expGlide(55, 95, tt / c.dur))) * Math.sin(Math.PI * Math.min(1, tt / (c.dur + 0.1))), { gain: 0.18 });
  },
  tokenOut(c) { blip(c.t, 700, 1500, 0.06, 0.06, c.pan, 0.1, 0.02); },
  tokenIn(c) { bell(sfx, c.t, PENT[c.i % PENT.length], { gain: 0.09, decay: 0.28, pan: c.pan }); },
  blockPop(c) { blip(c.t, 520 + c.i * 140, 320 + c.i * 90, 0.08, 0.12, c.pan, 0.12, 0.025); },
  livePing(c) {
    bell(sfx, c.t, NOTE('A5'), { gain: 0.16, decay: 0.8, pan: c.pan });
    bell(sfx, c.t + 0.07, NOTE('E6'), { gain: 0.13, decay: 0.9, pan: c.pan + 0.1 });
  },
  scroll(c) { noiseSweep(sfx, c.t, c.dur, 700, 1800, { q: 1, shape: (u) => Math.sin(Math.PI * u), gain: 0.05, verb: 0.1 }); },
  whip(c) {
    // The camera whips right, so the world -- and the sound -- rushes right to left.
    const len = c.dur + 0.28;
    noiseSweep(sfx, c.t - 0.04, len, 260, 5200, { q: 1.1, shape: (u) => Math.sin(Math.PI * Math.min(1, u * 1.15)) ** 2, panFrom: 0.85, panTo: -0.85, gain: 0.55, verb: 0.25 });
    noiseSweep(sfx, c.t + c.dur * 0.35, 0.3, 5000, 900, { q: 0.8, shape: (u) => (1 - u) ** 2, panFrom: -0.4, panTo: -0.9, gain: 0.18, verb: 0.3 });
  },
  rowIn(c) { blip(c.t, 900, 860, 0.05, 0.07, 0, 0.1, 0.012); },
  check(c) {
    const f = 1500 * 2 ** ((c.i * 2) / 12);
    blip(c.t, f, f * 1.02, 0.05, 0.11, 0.05, 0.15, 0.012);
    blip(c.t + 0.045, f * 1.335, f * 1.34, 0.08, 0.1, 0.05, 0.2, 0.02);
  },
  success(c) {
    ['D6', 'F#6', 'A6', 'D7'].forEach((n, i) => bell(sfx, c.t + i * 0.05, NOTE(n), { gain: 0.13 - i * 0.018, decay: 0.8, pan: -0.15 + i * 0.1 }));
  },
  matchCut(c) {
    noiseSweep(sfx, c.t, c.dur + 0.25, 900, 9000, { mode: 'hp', q: 0.7, shape: (u) => Math.sin(Math.PI * Math.min(1, u * 1.3)), gain: 0.16, verb: 0.5 });
    SFX.bloom({ t: c.t + c.dur * 0.8 }, 0.5);
  },
  fall(c) { blip(c.t, 1900, 650, c.dur + 0.02, 0.07, 0, 0.15, c.dur); },
  chip(c) { pluck(sfx, c.t, PENT[c.i % PENT.length], { gain: 0.1, decay: 0.2, pan: c.pan }); },
  suck(c) {
    noiseSweep(sfx, c.t, c.dur, 4200, 500, { q: 1, shape: (u) => u ** 1.6 * Math.min(1, (1 - u) * 12), gain: 0.2, verb: 0.15 });
  },
  riser(c) {
    noiseSweep(sfx, c.t, c.dur, 500, 9000, { q: 0.8, shape: (u) => u ** 2.4 * Math.min(1, (1 - u) * 40), gain: 0.22, verb: 0.35 });
    const o = osc(), lp = svf('lp');
    voice(sfx, c.t, c.dur, (tt) => lp(saw(o(expGlide(110, 440, tt / c.dur)), 300 / SR), 300 + 5000 * (tt / c.dur) ** 2) * (tt / c.dur) ** 2, { gain: 0.06, verb: 0.3 });
  },
  impact(c) {
    // The end-card hit: a sub drop, a body thud and a dark noise burst into a big room.
    const o = osc(), o2 = osc(), lp = svf('lp'), r = rng(4242);
    voice(sfx, c.t, 2.4, (tt) => {
      const sub = Math.sin(TAU * o(expGlide(95, 34, tt / 0.55))) * env(tt, 0.002, 0.75);
      const thud = Math.tanh(2 * Math.sin(TAU * o2(expGlide(220, 70, tt / 0.06)))) * env(tt, 0.001, 0.08);
      const burst = lp(r() * 2 - 1, 2400 * Math.exp(-tt / 0.15) + 200) * env(tt, 0.001, 0.22);
      return 0.9 * sub + 0.5 * thud + 0.45 * burst;
    }, { gain: 0.8, verb: 0.35 });
  },
  letter(c) { pluck(sfx, c.t, [NOTE('D5'), NOTE('F#5'), NOTE('A5'), NOTE('D6')][c.i % 4], { gain: 0.08, decay: 0.25, pan: c.pan }); },
  logoChime(c) {
    ['D5', 'A5', 'F#6', 'A6'].forEach((n, i) => bell(sfx, c.t + i * 0.02, NOTE(n), { gain: 0.1 - i * 0.012, decay: 1.15, pan: -0.3 + i * 0.2 }));
  },
  twinkle(c) { bell(sfx, c.t, NOTE(['E7', 'A7', 'F#7', 'D7'][c.i % 4]), { gain: 0.05, decay: 0.3, pan: c.pan }); },
  blink(c) { blip(c.t, 2800, 2200, 0.025, 0.03, 0, 0.05, 0.006); },
};

for (const c of CUES) {
  const f = SFX[c.kind];
  if (!f) throw new Error(`No sound for cue kind "${c.kind}"`);
  f(c);
}

// ------------------------------------------------------------------ effects + mix
function pingPong(bus, time = 0.375, fb = 0.32) {
  const d = Math.round(time * SR), outL = new Float32Array(N), outR = new Float32Array(N);
  const lpL = svf('lp'), lpR = svf('lp');
  for (let i = 0; i < N; i++) {
    const inL = bus.L[i] + bus.R[i];
    const fl = i >= d ? outR[i - d] : 0, fr = i >= d ? outL[i - d] : 0;
    outL[i] = lpL(inL * 0.5 + fl * fb, 3500);
    outR[i] = lpR(fr * fb, 3500);
  }
  return { L: outL, R: outR };
}

function reverb(bus) { // Freeverb-style: 8 damped combs + 4 allpasses per side, stereo spread
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((x) => Math.round((x * SR) / 44100));
  const aps = [556, 441, 341, 225].map((x) => Math.round((x * SR) / 44100));
  const run = (input, spread) => {
    const out = new Float32Array(N);
    const cb = combs.map((l) => ({ b: new Float32Array(l + spread), i: 0, s: 0 }));
    const ab = aps.map((l) => ({ b: new Float32Array(l + spread), i: 0 }));
    const fb = 0.84, damp = 0.3;
    for (let n = 0; n < N; n++) {
      const x = input[n] * 0.015;
      let y = 0;
      for (const c of cb) { const o = c.b[c.i]; c.s = o * (1 - damp) + c.s * damp; c.b[c.i] = x + c.s * fb; c.i = (c.i + 1) % c.b.length; y += o; }
      for (const a of ab) { const o = a.b[a.i]; const v = -y + o; a.b[a.i] = y + o * 0.5; a.i = (a.i + 1) % a.b.length; y = v; }
      out[n] = y;
    }
    return out;
  };
  return { L: run(bus.L, 0), R: run(bus.R, 23) };
}

// Kick side-chain on the music bed (pads/bass/arp pump gently under the kick).
const pump = new Float32Array(N).fill(1);
for (const t of kickTimes) {
  const s0 = Math.round(t * SR);
  for (let k = 0; k < 0.22 * SR && s0 + k < N; k++) pump[s0 + k] = Math.min(pump[s0 + k], 1 - 0.28 * Math.exp(-k / (0.07 * SR)));
}
// Duck the music under sound effects (envelope follower on the SFX bus).
const duck = new Float32Array(N);
{
  let e = 0; const att = Math.exp(-1 / (0.004 * SR)), rel = Math.exp(-1 / (0.18 * SR));
  for (let i = 0; i < N; i++) {
    const x = Math.max(Math.abs(sfx.L[i]), Math.abs(sfx.R[i]));
    e = x > e ? att * e + (1 - att) * x : rel * e + (1 - rel) * x;
    duck[i] = 1 - 0.42 * Math.min(1, e / 0.25);
  }
}
const dl = pingPong(delaySend);
for (let i = 0; i < N; i++) { verbSend.L[i] += dl.L[i] * 0.3; verbSend.R[i] += dl.R[i] * 0.3; }
const rv = reverb(verbSend);

const outL = new Float32Array(N), outR = new Float32Array(N);
const MUSIC = 0.8; // bed sits ~2 dB under the effects: the picture's beats should read first
const fadeOut = 0.45 * SR, fadeIn = 0.01 * SR;
for (let i = 0; i < N; i++) {
  const mg = duck[i];
  let l = (music.L[i] * pump[i]) * mg * MUSIC + sfx.L[i] + dl.L[i] * 0.45 + rv.L[i];
  let r = (music.R[i] * pump[i]) * mg * MUSIC + sfx.R[i] + dl.R[i] * 0.45 + rv.R[i];
  const f = Math.min(1, i / fadeIn, (N - i) / fadeOut);
  outL[i] = Math.tanh(l * 1.15) * f;
  outR[i] = Math.tanh(r * 1.15) * f;
}

// ------------------------------------------------------------------ write + loudness
function writeWav(file, L, R) {
  const n = L.length, buf = Buffer.alloc(44 + n * 8);
  buf.write('RIFF', 0); buf.writeUInt32LE(36 + n * 8, 4); buf.write('WAVE', 8); buf.write('fmt ', 12);
  buf.writeUInt32LE(16, 16); buf.writeUInt16LE(3, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
  buf.writeUInt32LE(SR * 8, 28); buf.writeUInt16LE(8, 32); buf.writeUInt16LE(32, 34); buf.write('data', 36); buf.writeUInt32LE(n * 8, 40);
  for (let i = 0; i < n; i++) { buf.writeFloatLE(L[i], 44 + i * 8); buf.writeFloatLE(R[i], 48 + i * 8); }
  fs.writeFileSync(file, buf);
}

const outDir = path.join(ROOT, 'out');
fs.mkdirSync(outDir, { recursive: true });
const raw = path.join(outDir, '.audio-raw.wav');
writeWav(raw, outL, outR);
if (arg('stems')) {
  fs.mkdirSync(path.join(outDir, 'audio-stems'), { recursive: true });
  writeWav(path.join(outDir, 'audio-stems', 'music.wav'), music.L, music.R);
  writeWav(path.join(outDir, 'audio-stems', 'sfx.wav'), sfx.L, sfx.R);
}

const target = { I: -14, TP: -1, LRA: 11 };
const pass1 = spawnSync(ffmpeg, ['-hide_banner', '-i', raw, '-af', `loudnorm=I=${target.I}:TP=${target.TP}:LRA=${target.LRA}:print_format=json`, '-f', 'null', '-'], { encoding: 'utf8' });
const m = JSON.parse(pass1.stderr.slice(pass1.stderr.lastIndexOf('{'), pass1.stderr.lastIndexOf('}') + 1));
const final = path.join(outDir, 'oro-launch-audio.wav');
const ln = `loudnorm=I=${target.I}:TP=${target.TP}:LRA=${target.LRA}:measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}:measured_thresh=${m.input_thresh}:offset=${m.target_offset}:linear=true`;
const r2 = spawnSync(ffmpeg, ['-y', '-hide_banner', '-loglevel', 'error', '-i', raw, '-af', `${ln},aresample=${SR}`, '-ar', String(SR), '-c:a', 'pcm_s24le', final], { stdio: 'inherit' });
if (r2.status !== 0) throw new Error('loudnorm pass failed');
fs.unlinkSync(raw);
const check = spawnSync(ffmpeg, ['-hide_banner', '-i', final, '-af', 'ebur128=peak=true', '-f', 'null', '-'], { encoding: 'utf8' }).stderr;
const I = /I:\s+(-?[\d.]+) LUFS/.exec(check.slice(check.lastIndexOf('Summary')))?.[1];
const TP = /Peak:\s+(-?[\d.]+) dBFS/.exec(check.slice(check.lastIndexOf('Summary')))?.[1];
console.log(`audio: ${CUES.length} cues, ${total}s → ${path.relative(ROOT, final)}  (${I} LUFS integrated, ${TP} dBTP)`);
