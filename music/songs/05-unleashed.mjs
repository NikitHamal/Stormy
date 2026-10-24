// 05 — Unleashed. Anthem in D -> E, 132 BPM.
// Ship it. Driving drums, the full theme on a big lead, strings carrying it higher, and for the
// last chorus the whole song lifts a whole step — from D, home, to E: the same tune, further.
import { Song, I } from '../engine/song.mjs';
import { THEME, MOTIF, ANSWER, score, chord, transpose } from './theory.mjs';

export default function build() {
  const s = new Song({ title: 'Unleashed', bpm: 132, bars: 60, seed: 505 });
  s.track('pad', { gain: 0.15, reverb: 0.3, duck: 0.6, hp: 180, width: 1.5 });
  s.track('strings', { gain: 0.13, reverb: 0.35, width: 1.3 });
  s.track('lead', { gain: 0.17, reverb: 0.22, delay: 0.2 });
  s.track('harm', { gain: 0.09, reverb: 0.25, delay: 0.2 });
  s.track('arp', { gain: 0.12, reverb: 0.2, delay: 0.2, duck: 0.4 });
  s.track('bells', { gain: 0.12, reverb: 0.4, delay: 0.25 });
  s.track('bass', { gain: 0.3, duck: 0.5, lp: 2400 });
  s.track('drums', { gain: 0.55, reverb: 0.07 });
  s.track('fx', { gain: 0.24, reverb: 0.4 });

  const T = (k) => (n) => transpose(n, k);
  const prog = (key) => {
    const up = T(key === 'E' ? 2 : 0);
    const map = (list) => list.map(([r, q]) => [up(r + '3').replace(/\d/, ''), q]);
    return {
      verse: map([['D', 'maj'], ['B', 'min'], ['G', 'maj'], ['A', 'sus4']]),
      theme: map([['D', 'add9'], ['A', 'maj'], ['B', 'm7'], ['G', 'maj9'], ['D', 'add9'], ['A', 'sus2'], ['G', 'maj9'], ['A', 'maj']]),
    };
  };
  const low = (r) => r.replace(/\d/, '') + '2';
  const stack = (from, list, bars, { pad = true, str = true, arp = true, bass = '8ths', bright = 0.8 } = {}) => {
    for (let b = 0; b < bars; b++) {
      const [r, q] = list[b % list.length], t = s.bar(from + b);
      if (pad) s.chord('pad', I.pad, t, chord(r, 3, q), 4, { attack: 0.02, release: 0.4, bright, spread: 0.9 });
      if (str) s.chord('strings', I.strings, t, chord(r, 4, q === 'maj9' ? 'maj7' : q).slice(0, 3), 4, { spread: 0.6 });
      if (arp) { const n = chord(r, 4, q); for (let k = 0; k < 8; k++) s.play('arp', I.arp, t + k / 2, n[[0, 1, 2, 1, 0, 2, 1, 2][k] % n.length], 0.4, { pan: k % 2 ? 0.35 : -0.35, cut: 6000 }); }
      if (bass === '8ths') for (let k = 0; k < 8; k++) s.play('bass', I.bass, t + k / 2, k % 2 ? low(r).replace(/\d/, (d) => +d + 1) : low(r), 0.4, { cut: 1200 });
      if (bass === 'whole') s.play('bass', I.bass, t, low(r), 3.8, { cut: 600 });
    }
  };
  const drive = (from, bars) => s.drums('drums', from, bars, { kick: 'x...x...x...x...', snare: '....x.......x...', hat: 'xoxoxoxoxoxoxoxo', hat2: '..............x.' }, { hat: { gain: 0.8 } });
  const fill = (bar) => s.drums('drums', bar, 1, { kick: 'x.......x.......', snare: '....x...xxxxxxxx', tom: '........x.x.x.x.' }, { tom: { gain: 0.8 } });

  const D = prog('D'), E = prog('E');
  s.mark(0, 'Countdown');
  stack(0, D.verse, 4, { pad: true, str: false, arp: true, bass: 'whole', bright: 0.4 });
  s.drums('drums', 2, 2, { kick: 'x...x...x...x...', hat: '..x...x...x...x.' });
  s.play('fx', I.riser, s.bar(2), null, 8);
  s.mark(4, 'Verse');
  stack(4, D.verse, 8, { str: false });
  drive(4, 7); fill(11);
  s.seq('lead', I.lead, s.bar(4), score('D5', [...MOTIF, ...ANSWER, ...MOTIF, [5, 1], [6, 1], [5, 2]]), { bright: 3000 });
  s.seq('lead', I.lead, s.bar(8), score('D5', [...MOTIF, ...ANSWER, [3, 0.5], [5, 0.5], [8, 1], [9, 0.5], [8, 0.5], [6, 0.5], [5, 0.5], [5, 4]]), { bright: 3200 });
  for (const [from, label] of [[12, 'Anthem'], [28, 'Anthem II']]) {
    s.mark(from, label);
    s.play('fx', I.impact, s.bar(from), null, 4); s.play('drums', I.ride, s.bar(from), null, 4, { crash: true });
    stack(from, D.theme, 16); drive(from, 15); fill(from + 15);
    s.seq('lead', I.lead, s.bar(from), score('D5', [...THEME, ...THEME]), { bright: 4400 });
    if (from === 28) s.seq('harm', I.lead, s.bar(from), score('D5', [...THEME, ...THEME], { shift: 2 }), { bright: 2800, pan: 0.4 });
    s.seq('bells', I.bell, s.bar(from + 8), score('D6', THEME), { decay: 0.8, pan: -0.4 });
  }
  s.mark(44, 'Lift');
  // Breakdown into the key change: the pre-dominant B7 points at E.
  s.chord('pad', I.pad, s.bar(44), chord('B', 3, '7'), 8, { attack: 0.4, bright: 0.6, spread: 0.9 });
  s.chord('strings', I.strings, s.bar(44), ['B3', 'D#4', 'A4'], 8, { attack: 0.6 });
  s.seq('bells', I.bell, s.bar(44), score('E5', MOTIF), { decay: 1.4 });
  s.drums('drums', 45, 1, { snare: 'x.x.x.x.xxxxxxxx', tom: '........x.x.x.x.' });
  s.play('fx', I.riser, s.bar(44), null, 8);
  s.mark(46, 'Anthem in E');
  s.play('fx', I.impact, s.bar(46), null, 4); s.play('drums', I.ride, s.bar(46), null, 4, { crash: true });
  stack(46, E.theme, 10, { bright: 1 }); drive(46, 10);
  s.seq('lead', I.lead, s.bar(46), score('E5', THEME), { bright: 5000 });
  s.seq('harm', I.lead, s.bar(46), score('E4', THEME), { bright: 2600, pan: -0.4 });
  s.seq('strings', I.strings, s.bar(46), score('E5', THEME, { shift: 2 }), { pan: 0.4 });
  s.seq('lead', I.lead, s.bar(54), score('E5', [[5, 1], [6, 1], [8, 2]]), { bright: 5000 });
  s.mark(56, 'Landing');
  s.play('fx', I.impact, s.bar(56), null, 4);
  s.chord('pad', I.pad, s.bar(56), chord('E', 3, 'add9'), 14, { attack: 0.02, release: 4, bright: 0.8, spread: 0.9 });
  s.chord('strings', I.strings, s.bar(56), ['E3', 'B3', 'G#4', 'E5'], 13, { release: 3.5, spread: 0.7 });
  s.play('bass', I.bass, s.bar(56), 'E2', 10, { cut: 300 });
  s.play('drums', I.ride, s.bar(56), null, 4, { crash: true });
  s.play('drums', I.kick, s.bar(56), null, 1);
  s.seq('bells', I.bell, s.bar(56), score('E6', ANSWER), { decay: 3 });
  return s;
}
