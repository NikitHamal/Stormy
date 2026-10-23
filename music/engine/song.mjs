// Song: tempo grid, tracks with sends and side-chain, a tiny score language, and the mix/master.
import { SR, Bus, svf, clamp, rng } from './core.mjs';
import * as I from './instruments.mjs';

export class Song {
  constructor({ title, bpm, bars, beatsPerBar = 4, swing = 0, tail = 4, seed = 1 }) {
    Object.assign(this, { title, bpm, bars, beatsPerBar, swing, seed });
    this.spb = 60 / bpm;
    this.length = bars * beatsPerBar * this.spb + tail;
    this.n = Math.ceil(this.length * SR);
    this.tracks = {};
    this.verb = new Bus(this.n);
    this.echo = new Bus(this.n);
    this.kicks = [];
    this.markers = [];
    this.rand = rng(seed);
  }

  /** Beat number (0-based, may be fractional) to seconds, with swing on off-16ths. */
  t(beat) {
    const frac = beat - Math.floor(beat * 2) / 2;
    const swung = Math.abs(frac - 0.25) < 1e-6 ? beat + this.swing * 0.25 * 0.5 : beat;
    return swung * this.spb;
  }
  bar(n) { return n * this.beatsPerBar; }
  mark(bar, label) { this.markers.push({ bar, time: this.t(this.bar(bar)), label }); }

  track(name, { gain = 1, reverb = 0.15, delay = 0, duck = 0, hp = 0, lp = 0, width = 1 } = {}) {
    if (!this.tracks[name]) {
      const bus = new Bus(this.n);
      this.tracks[name] = { bus, sends: [], gain, duck, hp, lp, width, seed: this.seed };
      if (reverb) this.tracks[name].sends.push([this.verb, reverb]);
      if (delay) this.tracks[name].sends.push([this.echo, delay]);
    }
    return this.tracks[name];
  }

  /** Play one instrument note. beat/dur in beats. */
  play(trackName, inst, beat, note, dur = 1, o = {}) {
    const tr = this.tracks[trackName] || this.track(trackName);
    const human = o.human ?? 0.006;
    const jitter = human ? (this.rand() - 0.5) * human : 0;
    const t = Math.max(0, this.t(beat) + jitter);
    if (inst === I.kick && trackName !== '_ghost') this.kicks.push(t);
    inst(tr, t, note, dur * this.spb, { ...o, vel: (o.vel ?? 0.8) * (1 - (o.human === 0 ? 0 : this.rand() * 0.08)) });
  }

  chord(trackName, inst, beat, notes, dur, o = {}) {
    notes.forEach((nt, i) => this.play(trackName, inst, beat + (o.strum ?? 0) * i, nt, dur, { ...o, pan: o.spread ? (i / Math.max(1, notes.length - 1) - 0.5) * o.spread : o.pan }));
  }

  /**
   * Score language: whitespace-separated tokens `NOTE:beats`, `r:beats` (rest),
   * `[A3,C#4,E4]:beats` (chord), optional `@vel`. Beats default to the previous token's.
   * Returns the beat after the last token.
   */
  seq(trackName, inst, beat, score, o = {}) {
    let b = beat, dur = 1;
    for (const tok of score.trim().split(/\s+/)) {
      const m = /^(\[[^\]]+\]|[A-Gr][#b]?-?\d?)(?::([\d.\/]+))?(?:@([\d.]+))?$/.exec(tok);
      if (!m) throw new Error('bad token ' + tok);
      if (m[2]) dur = m[2].includes('/') ? m[2].split('/').reduce((a, c) => a / +c) : +m[2];
      const vel = m[3] ? +m[3] : o.vel;
      const legato = o.legato ?? 0.95;
      if (m[1] === 'r') { b += dur; continue; }
      if (m[1].startsWith('[')) this.chord(trackName, inst, b, m[1].slice(1, -1).split(','), dur * legato, { ...o, vel });
      else this.play(trackName, inst, b, m[1], dur * legato, { ...o, vel });
      b += dur;
    }
    return b;
  }

  /** Repeat a drum pattern: map of instrument -> string of x/o/. per 16th (x = hit, o = soft). */
  drums(trackName, startBar, bars, pattern, o = {}) {
    for (let bar = 0; bar < bars; bar++) {
      for (const [name, pat] of Object.entries(pattern)) {
        const inst = I[name.replace(/\d+$/, '')];
        const steps = pat.replace(/\s/g, '');
        const per = (this.beatsPerBar * 4) / steps.length;
        for (let s = 0; s < steps.length; s++) {
          const c = steps[s];
          if (c === '.' || c === '-') continue;
          const vel = c === 'x' ? 1 : c === 'X' ? 1.15 : 0.55;
          this.play(trackName, inst, this.bar(startBar + bar) + (s * per) / 4, null, 0.25, { ...o, ...(o[name] || {}), gain: (o[name]?.gain ?? o.gain ?? 1) * vel, open: name === 'hat2' || o[name]?.open });
        }
      }
    }
  }

  // ---------------------------------------------------------------- mix
  render() {
    const N = this.n, L = new Float32Array(N), R = new Float32Array(N);
    // Side-chain envelope from every kick.
    const duckEnv = new Float32Array(N).fill(0);
    for (const t of this.kicks) {
      const s0 = Math.round(t * SR);
      for (let k = 0; k < 0.28 * SR && s0 + k < N; k++) duckEnv[s0 + k] = Math.max(duckEnv[s0 + k], Math.exp(-k / (0.09 * SR)));
    }
    for (const tr of Object.values(this.tracks)) {
      const hpL = tr.hp ? svf('hp') : null, hpR = tr.hp ? svf('hp') : null, lpL = tr.lp ? svf('lp') : null, lpR = tr.lp ? svf('lp') : null;
      for (let i = 0; i < N; i++) {
        let l = tr.bus.L[i], r = tr.bus.R[i];
        if (hpL) { l = hpL(l, tr.hp); r = hpR(r, tr.hp); }
        if (lpL) { l = lpL(l, tr.lp); r = lpR(r, tr.lp); }
        if (tr.width !== 1) { const m = (l + r) / 2, sd = (l - r) / 2 * tr.width; l = m + sd; r = m - sd; }
        const g = tr.gain * (1 - tr.duck * duckEnv[i]);
        L[i] += l * g; R[i] += r * g;
      }
    }
    const echo = pingPong(this.echo, this.spb * 0.75, 0.38);
    for (let i = 0; i < N; i++) { this.verb.L[i] += echo.L[i] * 0.25; this.verb.R[i] += echo.R[i] * 0.25; }
    const rv = reverb(this.verb, 0.86);
    for (let i = 0; i < N; i++) { L[i] += echo.L[i] * 0.55 + rv.L[i]; R[i] += echo.R[i] * 0.55 + rv.R[i]; }
    return master(L, R);
  }
}

function pingPong(bus, time, fb) {
  const N = bus.n, d = Math.round(time * SR), oL = new Float32Array(N), oR = new Float32Array(N);
  const lpL = svf('lp'), lpR = svf('lp'), hpL = svf('hp'), hpR = svf('hp');
  for (let i = 0; i < N; i++) {
    const inp = (bus.L[i] + bus.R[i]) * 0.5;
    const fl = i >= d ? oR[i - d] : 0, fr = i >= d ? oL[i - d] : 0;
    oL[i] = hpL(lpL(inp + fl * fb, 4200), 250);
    oR[i] = hpR(lpR(fr * fb, 4200), 250);
  }
  return { L: oL, R: oR };
}

/** Freeverb-style stereo reverb with 25 ms pre-delay. */
function reverb(bus, room = 0.85) {
  const N = bus.n;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((x) => Math.round((x * SR) / 44100));
  const aps = [556, 441, 341, 225].map((x) => Math.round((x * SR) / 44100));
  const pre = Math.round(0.025 * SR);
  const run = (input, spread) => {
    const out = new Float32Array(N);
    const cb = combs.map((l) => ({ b: new Float32Array(l + spread), i: 0, s: 0 }));
    const ab = aps.map((l) => ({ b: new Float32Array(l + spread), i: 0 }));
    const hp = svf('hp');
    for (let n = 0; n < N; n++) {
      const x = hp(n >= pre ? input[n - pre] : 0, 180) * 0.012;
      let y = 0;
      for (const c of cb) { const o = c.b[c.i]; c.s = o * 0.72 + c.s * 0.28; c.b[c.i] = x + c.s * room; if (++c.i >= c.b.length) c.i = 0; y += o; }
      for (const a of ab) { const o = a.b[a.i]; const vv = -y + o; a.b[a.i] = y + o * 0.5; if (++a.i >= a.b.length) a.i = 0; y = vv; }
      out[n] = y;
    }
    return out;
  };
  return { L: run(bus.L, 0), R: run(bus.R, 23) };
}

/** Master: DC/rumble high-pass, glue compression, gentle air shelf, soft clip, tail fade. */
function master(L, R) {
  const N = L.length;
  const hpL = svf('hp'), hpR = svf('hp'), airL = svf('hp'), airR = svf('hp'), subL = svf('lp'), subR = svf('lp');
  let env = 0;
  const att = Math.exp(-1 / (0.01 * SR)), rel = Math.exp(-1 / (0.15 * SR));
  const thr = 0.25, ratio = 2.2;
  const fade = 2.5 * SR;
  for (let i = 0; i < N; i++) {
    let l = hpL(L[i], 34), r = hpR(R[i], 34);
    // Sub shelf down ~4 dB below 70 Hz (phones cannot play it; it only eats headroom),
    // and a touch of air above 8 kHz.
    l -= subL(l, 75) * 0.55; r -= subR(r, 75) * 0.55;
    l += airL(l, 8000) * 0.22; r += airR(r, 8000) * 0.22;
    const x = Math.max(Math.abs(l), Math.abs(r));
    env = x > env ? att * env + (1 - att) * x : rel * env + (1 - rel) * x;
    const g = env > thr ? (thr + (env - thr) / ratio) / env : 1;
    const f = Math.min(1, (N - i) / fade, i / (0.004 * SR));
    L[i] = Math.tanh(l * g * 1.1) * f; R[i] = Math.tanh(r * g * 1.1) * f;
  }
  return { L, R };
}

export { I };
