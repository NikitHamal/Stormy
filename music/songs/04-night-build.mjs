// 04 — Night Build. Ambient in B minor -> D major, 68 BPM.
// Late at night the screen dims and Orobi keeps working. Wide pads breathe, a soft heartbeat
// keeps time, the piano remembers the motif slowly, in minor. Near the end the harmony turns to
// D major — the relative major, the same notes seen from the other side — and it is morning.
import { Song, I } from '../engine/song.mjs';
import { MOTIF, ANSWER, score, stretch, chord } from './theory.mjs';

export default function build() {
  const s = new Song({ title: 'Night Build', bpm: 68, bars: 40, seed: 404, tail: 7 });
  s.track('pad', { gain: 0.17, reverb: 0.55, hp: 140, width: 1.15 });
  s.track('strings', { gain: 0.1, reverb: 0.55, width: 1.1 });
  s.track('piano', { gain: 0.3, reverb: 0.45, delay: 0.12 });
  s.track('bells', { gain: 0.1, reverb: 0.6, delay: 0.35 });
  s.track('sub', { gain: 0.26, lp: 400 });
  s.track('beat', { gain: 0.32, reverb: 0.15, lp: 6000 });
  s.track('air', { gain: 0.12, reverb: 0.6 });

  // Two bars per chord. Night (Bm) for 24 bars, a turn, then dawn (D).
  const NIGHT = [['B', 'madd9', 'B2'], ['G', 'maj9', 'G2'], ['D', 'add9', 'F#2'], ['E', 'm9', 'E2']];
  const DAWN = [['D', 'maj9', 'D2'], ['A', 'sus2', 'A2'], ['G', 'maj9', 'G2'], ['D', 'add9', 'D2']];
  const bed = (from, list, cycles, o = {}) => {
    for (let c = 0; c < cycles; c++) list.forEach(([r, q, b], i) => {
      const at = s.bar(from + c * 8 + i * 2);
      s.chord('pad', I.pad, at, chord(r, 3, q), 8, { attack: 2.6, release: 3.5, bright: o.bright ?? 0.25, spread: 0.9 });
      s.play('sub', I.bass, at, b, 7.6, { cut: 200 });
      if (o.strings) s.chord('strings', I.strings, at, chord(r, 4, q === 'maj9' ? 'maj7' : q).slice(0, 3), 7.8, { attack: 1.6, release: 2, spread: 0.6 });
    });
  };
  const heartbeat = (from, bars) => s.drums('beat', from, bars, { kick: 'x..x............', rim: '........o.......', shaker: '....o.......o...' }, { kick: { gain: 0.8, decay: 0.45, hi: 90, lo: 40, punch: 0.2 }, rim: { gain: 0.35 }, shaker: { gain: 0.35 } });

  s.play('air', I.swell, 0, null, 10);
  s.mark(0, 'Dusk');
  bed(0, NIGHT, 3);
  // The motif in B minor's colours: same shape, from the sixth degree of D (B).
  s.seq('piano', I.piano, s.bar(4), score('D4', stretch(MOTIF.map(([d, b]) => [d + 5, b]), 2)), { vel: 0.45 });
  s.seq('bells', I.bell, s.bar(9), score('D5', stretch([[6, 1], [8, 1], [10, 2]], 1)), { decay: 3.2, pan: -0.5 });
  s.mark(8, 'Working');
  heartbeat(8, 16);
  s.seq('piano', I.piano, s.bar(12), score('D4', stretch(ANSWER.map(([d, b]) => [d + 5, b]), 2)), { vel: 0.45 });
  s.seq('piano', I.piano, s.bar(16), score('D5', [[6, 1.5], [5, 0.5], [3, 2], [2, 1.5], [3, 0.5], [6, 4], [8, 1.5], [7, 0.5], [6, 2], [5, 4]]), { vel: 0.42 });
  s.seq('bells', I.bell, s.bar(20), score('D6', stretch(MOTIF.map(([d, b]) => [d + 5, b]), 1)), { decay: 2.4, pan: 0.5 });
  s.mark(24, 'The turn');
  bed(24, DAWN, 2, { bright: 0.5, strings: true });
  heartbeat(24, 12);
  s.play('air', I.swell, s.bar(22), null, 9);
  s.mark(24, 'Dawn');
  // Now the motif in D major, as itself, finally.
  s.seq('piano', I.piano, s.bar(24), score('D5', stretch(MOTIF, 2)), { vel: 0.55 });
  s.seq('piano', I.piano, s.bar(28), score('D5', stretch(ANSWER, 2)), { vel: 0.5 });
  s.seq('bells', I.bell, s.bar(32), score('D6', MOTIF), { decay: 2.6 });
  s.seq('piano', I.piano, s.bar(34), score('D4', [[1, 1], [3, 1], [5, 2], [8, 4]]), { vel: 0.45 });
  s.mark(36, 'Morning');
  s.chord('pad', I.pad, s.bar(36), chord('D', 3, 'maj9'), 14, { attack: 1.4, release: 5, bright: 0.45, spread: 0.9 });
  s.chord('strings', I.strings, s.bar(36), ['D3', 'A3', 'F#4'], 13, { release: 4, spread: 0.6 });
  s.play('sub', I.bass, s.bar(36), 'D2', 12, { cut: 200 });
  s.seq('bells', I.bell, s.bar(37), 'D6:1 F#6:1 A6:6', { decay: 4 });
  return s;
}
