// 02 — Infinity Loop. Lo-fi beat in F, 80 BPM, swung.
// The sound of working together late: warm electric piano on a ii–V–I–vi loop, vinyl dust,
// a lazy boom-bap, and Orobi's motif half-whispered on a nylon pluck, never quite the same twice.
// Built to loop forever under a long session; it never asks for attention.
import { Song, I } from '../engine/song.mjs';
import { MOTIF, ANSWER, score, stretch } from './theory.mjs';

export default function build() {
  const s = new Song({ title: 'Infinity Loop', bpm: 80, bars: 44, swing: 0.55, seed: 202, tail: 5 });
  s.track('keys', { gain: 0.28, reverb: 0.25, lp: 4200, hp: 120 });
  s.track('pluck', { gain: 0.2, reverb: 0.3, delay: 0.25 });
  s.track('bell', { gain: 0.1, reverb: 0.45, delay: 0.3 });
  s.track('bass', { gain: 0.32, reverb: 0, duck: 0.25, lp: 1800 });
  s.track('drums', { gain: 0.55, reverb: 0.06, lp: 9000 });
  s.track('vinyl', { gain: 0.35, reverb: 0 });
  s.track('pad', { gain: 0.08, reverb: 0.4, hp: 200, width: 1.5 });

  // Gm9 – C13 – Fmaj9 – Dm9, voiced close and warm.
  const LOOP = [
    { bass: 'G2', v: ['Bb3', 'D4', 'F4', 'A4'] },
    { bass: 'C3', v: ['Bb3', 'E4', 'A4', 'D5'] },
    { bass: 'F2', v: ['A3', 'C4', 'E4', 'G4'] },
    { bass: 'D3', v: ['C4', 'F4', 'A4', 'E5'] },
  ];
  const keysLoop = (from, bars, o = {}) => {
    for (let b = 0; b < bars; b++) {
      const c = LOOP[b % 4], t = s.bar(from + b);
      // Two hits per bar, the second anticipated: a pianist leaning on the beat.
      s.chord('keys', I.keys, t, c.v, 2.4, { vel: 0.62, strum: 0.025, trem: true, spread: 0.5, ...o });
      s.chord('keys', I.keys, t + 2.75, c.v.slice(1), 1.1, { vel: 0.45, strum: 0.02, trem: true, spread: 0.5, ...o });
    }
  };
  const bassLoop = (from, bars) => {
    for (let b = 0; b < bars; b++) {
      const c = LOOP[b % 4], t = s.bar(from + b);
      s.play('bass', I.bass808, t, c.bass, 1.6, { decay: 0.8 });
      s.play('bass', I.bass808, t + 2.5, c.bass, 0.9, { decay: 0.5 });
      if (b % 2) s.play('bass', I.bass808, t + 3.5, c.bass.replace(/\d/, (d) => +d + 1), 0.4, { decay: 0.3 });
    }
  };
  const beat = (from, bars, full = true) => s.drums('drums', from, bars, full
    ? { kick: 'x......x..x.....', snare: '....x.......x..o', hat: 'x.oxx.o.x.oxx.o.', rim: '..........o.....' }
    : { kick: 'x.........x.....', hat: 'x...x...x...x...' }, { hat: { gain: 0.7 }, snare: { gain: 0.9 }, rim: { gain: 0.5 } });

  s.play('vinyl', I.vinyl, 0, null, s.bars * 4 + 6);

  s.mark(0, 'Intro');
  keysLoop(0, 4, { vel: 0.5 });
  s.mark(4, 'Loop A');
  keysLoop(4, 8); bassLoop(4, 8); beat(4, 8);
  s.mark(12, 'Loop B — the motif');
  keysLoop(12, 8); bassLoop(12, 8); beat(12, 8);
  // The motif in F, lazy and behind the beat.
  s.seq('pluck', I.pluck, s.bar(12) + 0.5, score('F4', MOTIF), { vel: 0.8, bright: 0.5 });
  s.seq('pluck', I.pluck, s.bar(14) + 0.5, score('F4', ANSWER), { vel: 0.75, bright: 0.5 });
  s.seq('pluck', I.pluck, s.bar(16) + 0.5, score('F4', [[3, 0.5], [5, 0.5], [6, 1], [8, 0.5], [6, 0.5], [5, 0.5], [3, 0.5]]), { vel: 0.8, bright: 0.5 });
  s.seq('pluck', I.pluck, s.bar(18) + 0.5, score('F4', [[2, 0.5], [3, 0.5], [2, 0.5], [1, 0.5], [6, 2]].map(([d, b], i) => [i === 4 ? -1 : d, b])), { vel: 0.7, bright: 0.45 });
  s.mark(20, 'Rain break');
  keysLoop(20, 4, { vel: 0.5 });
  for (let b = 0; b < 4; b++) s.chord('pad', I.pad, s.bar(20 + b), LOOP[b].v, 4, { attack: 1.2, bright: 0.2, spread: 0.8 });
  beat(22, 2, false);
  s.seq('bell', I.bell, s.bar(21), score('F5', stretch(MOTIF, 2)), { decay: 2 });
  s.mark(24, 'Loop A again');
  keysLoop(24, 8); bassLoop(24, 8); beat(24, 8);
  s.seq('bell', I.bell, s.bar(24), score('F5', MOTIF), { decay: 1.2, pan: 0.4 });
  s.seq('pluck', I.pluck, s.bar(26) + 0.5, score('F4', ANSWER), { vel: 0.7 });
  s.seq('pluck', I.pluck, s.bar(28) + 0.25, score('F4', [[5, 0.5], [6, 0.5], [8, 0.75], [9, 0.25], [8, 0.5], [6, 0.5], [5, 1]]), { vel: 0.75 });
  s.seq('pluck', I.pluck, s.bar(30) + 0.5, score('F4', [[3, 0.5], [2, 0.5], [1, 3]]), { vel: 0.7 });
  s.mark(32, 'Loop C');
  keysLoop(32, 8); bassLoop(32, 8); beat(32, 8);
  s.seq('pluck', I.pluck, s.bar(32) + 0.5, score('F4', MOTIF), { vel: 0.75 });
  s.seq('bell', I.bell, s.bar(34) + 0.5, score('F5', ANSWER), { decay: 1.4, pan: -0.4 });
  s.seq('pluck', I.pluck, s.bar(36) + 0.5, score('F4', MOTIF), { vel: 0.7 });
  s.mark(40, 'Fade');
  keysLoop(40, 4, { vel: 0.45 }); beat(40, 2, false);
  s.seq('bell', I.bell, s.bar(42), score('F5', [[1, 0.5], [3, 0.5], [5, 3]]), { decay: 3 });
  return s;
}
