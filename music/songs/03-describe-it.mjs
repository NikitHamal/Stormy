// 03 — Describe It. Synth-pop in A, 122 BPM.
// The spark: you type one sentence and something starts building. Bouncy arps are keystrokes,
// the side-chained chords breathe with the kick like a live preview refreshing, and the hook is
// Orobi's motif sped up and grinning.
import { Song, I } from '../engine/song.mjs';
import { MOTIF, ANSWER, score, chord } from './theory.mjs';

export default function build() {
  const s = new Song({ title: 'Describe It', bpm: 122, bars: 52, seed: 303 });
  s.track('arp', { gain: 0.14, reverb: 0.2, delay: 0.22, duck: 0.4 });
  s.track('chords', { gain: 0.15, reverb: 0.25, duck: 0.65, hp: 200, width: 1.5 });
  s.track('lead', { gain: 0.17, reverb: 0.22, delay: 0.25 });
  s.track('bells', { gain: 0.13, reverb: 0.4, delay: 0.25 });
  s.track('bass', { gain: 0.3, duck: 0.55, lp: 2200 });
  s.track('drums', { gain: 0.5, reverb: 0.06 });
  s.track('fx', { gain: 0.22, reverb: 0.4 });

  const VERSE = [['A', 'add9'], ['E', 'sus4'], ['F#', 'm7'], ['D', 'maj9']];
  const PRE = [['B', 'm7'], ['D', 'maj7'], ['E', 'sus4'], ['E', 'maj']];
  const CHORUS = [['D', 'maj9'], ['E', 'add9'], ['C#', 'm7'], ['F#', 'm9'], ['D', 'maj9'], ['E', 'add9'], ['A', 'add9'], ['A', 'add9']];
  const low = (r) => r + '2';

  const arpBars = (from, list, bars, cut = 5200) => {
    for (let b = 0; b < bars; b++) {
      const [r, q] = list[b % list.length], n = chord(r, 4, q);
      const order = [0, 2, 1, 3, 2, 0, 3, 1, 0, 2, 1, 3, 2, 3, 1, 2];
      order.forEach((o, k) => s.play('arp', I.arp, s.bar(from + b) + k / 4, n[o % n.length], 0.22, { pan: k % 2 ? 0.4 : -0.4, cut, decay: 0.09 }));
    }
  };
  const chordBars = (from, list, bars, pump = true) => {
    for (let b = 0; b < bars; b++) {
      const [r, q] = list[b % list.length];
      if (pump) [0, 1, 2, 3].forEach((k) => s.chord('chords', I.pad, s.bar(from + b) + k, chord(r, 3, q), 0.9, { attack: 0.01, release: 0.25, bright: 0.8, spread: 0.9 }));
      else s.chord('chords', I.pad, s.bar(from + b), chord(r, 3, q), 4, { attack: 0.4, bright: 0.5, spread: 0.9 });
    }
  };
  const bassBars = (from, list, bars) => {
    for (let b = 0; b < bars; b++) {
      const n = low(list[b % list.length][0]);
      for (let k = 0; k < 4; k++) s.play('bass', I.bass, s.bar(from + b) + k + 0.5, n, 0.42, { cut: 1300 });
    }
  };
  // The hook: the motif in sixteenths with a bounce on the top note.
  const HOOK = [[1, 0.5], [3, 0.5], [5, 0.75], [6, 0.25], [5, 0.5], [3, 0.5], [2, 0.5], [3, 0.5]];
  const HOOK2 = [[1, 0.5], [2, 0.5], [3, 0.5], [5, 0.5], [6, 0.75], [5, 0.25], [3, 1]];
  const HOOK3 = [[4, 0.5], [6, 0.5], [8, 0.75], [9, 0.25], [8, 0.5], [6, 0.5], [5, 0.5], [6, 0.5]];
  const HOOK4 = [[5, 1], [3, 0.5], [2, 0.5], [1, 2]];
  const hook = (from, oct = 'A4', o = {}) => [HOOK, HOOK2, HOOK, HOOK2, HOOK3, HOOK2, HOOK, HOOK4].forEach((p, i) => s.seq('lead', I.lead, s.bar(from + i), score(oct, p), { bright: 4200, ...o }));
  const fourFloor = (from, bars, full) => s.drums('drums', from, bars, full
    ? { kick: 'x...x...x...x...', clap: '....x.......x...', hat: '..x...x...x...x.', hat2: '..............x.', shaker: 'o.o.o.o.o.o.o.o.' }
    : { kick: 'x...x...x...x...', hat: '..x...x...x...x.' }, { shaker: { gain: 0.6 } });

  s.mark(0, 'Intro'); arpBars(0, VERSE, 4, 1400); s.play('fx', I.swell, s.bar(2), null, 8);
  s.mark(4, 'Verse'); arpBars(4, VERSE, 8); chordBars(4, VERSE, 8, false); bassBars(4, VERSE, 8); fourFloor(4, 8, false);
  s.seq('bells', I.bell, s.bar(8), score('A5', MOTIF), { decay: 1 });
  s.mark(12, 'Pre'); arpBars(12, PRE, 4, 7000); chordBars(12, PRE, 4, false); bassBars(12, PRE, 4);
  s.drums('drums', 12, 3, { kick: 'x...x...x...x...', snare: '....x.......x...', hat: 'x.x.x.x.x.x.x.x.' });
  s.drums('drums', 15, 1, { snare: 'x.x.x.x.xxxxxxxx' });
  s.play('fx', I.riser, s.bar(13), null, 12);
  for (const [from, extra] of [[16, false], [36, true]]) {
    s.mark(from, 'Chorus');
    s.play('fx', I.impact, s.bar(from), null, 4); s.play('drums', I.ride, s.bar(from), null, 4, { crash: true });
    arpBars(from, CHORUS, 8, 7500); chordBars(from, CHORUS, 8, true); bassBars(from, CHORUS, 8); fourFloor(from, 8, true);
    hook(from);
    if (extra) { s.seq('lead', I.lead, s.bar(from), score('A4', [...HOOK, ...HOOK2, ...HOOK, ...HOOK2, ...HOOK3, ...HOOK2, ...HOOK, ...HOOK4], { shift: 2 }), { bright: 3000, pan: 0.35, gain: 0.6 }); s.seq('bells', I.bell, s.bar(from), score('A5', [...HOOK, ...HOOK2]), { decay: 0.8 }); }
  }
  s.mark(24, 'Post-chorus'); arpBars(24, VERSE, 4, 3000); chordBars(24, VERSE, 4, false);
  s.seq('bells', I.bell, s.bar(24), score('A5', [...HOOK, ...HOOK2, ...HOOK, ...HOOK4]), { decay: 1.1 });
  s.drums('drums', 24, 4, { kick: 'x.......x.......', hat: '..x...x...x...x.' });
  s.mark(28, 'Verse 2'); arpBars(28, VERSE, 4); chordBars(28, VERSE, 4, false); bassBars(28, VERSE, 4); fourFloor(28, 4, true);
  s.seq('lead', I.lead, s.bar(28), score('A4', [...MOTIF, ...ANSWER, ...MOTIF]), { bright: 2800 });
  arpBars(32, PRE, 4, 7000); chordBars(32, PRE, 4, false); bassBars(32, PRE, 4);
  s.drums('drums', 32, 3, { kick: 'x...x...x...x...', snare: '....x.......x...', hat: 'xxxxxxxxxxxxxxxx' });
  s.drums('drums', 35, 1, { snare: 'xxxxxxxxxxxxxxxx', tom: '........x.x.x.x.' });
  s.play('fx', I.riser, s.bar(33), null, 12);
  s.mark(44, 'Outro'); arpBars(44, VERSE, 6, 2600); chordBars(44, VERSE, 6, false); fourFloor(44, 4, false);
  s.seq('bells', I.bell, s.bar(46), score('A5', [...HOOK, ...HOOK4]), { decay: 1.6 });
  s.chord('chords', I.pad, s.bar(50), chord('A', 3, 'add9'), 8, { attack: 0.1, release: 3, bright: 0.5, spread: 0.9 });
  s.seq('bells', I.bell, s.bar(50), 'A5:0.5 C#6:0.5 E6:3', { decay: 3 });
  return s;
}
