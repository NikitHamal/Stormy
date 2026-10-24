// 01 — Orobi (Main Theme). D major, 100 BPM.
// The story of the app in two and a half minutes: a spark in the dark (bells draw the motif like
// the loader drawing itself), Orobi waking (piano states the theme), the work finding its groove,
// a lift, and the anthem — the moment the site goes live. A quiet bridge, then home.
import { Song, I } from '../engine/song.mjs';
import { THEME, MOTIF, ANSWER, score, chord, deg, stretch } from './theory.mjs';

export default function build() {
  const s = new Song({ title: 'Orobi (Main Theme)', bpm: 100, bars: 64, seed: 101 });
  s.track('pad', { gain: 0.16, reverb: 0.35, duck: 0.35, width: 1.4, hp: 170 });
  s.track('strings', { gain: 0.13, reverb: 0.4, width: 1.3 });
  s.track('piano', { gain: 0.32, reverb: 0.3, delay: 0.08 });
  s.track('bells', { gain: 0.16, reverb: 0.5, delay: 0.25 });
  s.track('lead', { gain: 0.15, reverb: 0.25, delay: 0.2 });
  s.track('harm', { gain: 0.09, reverb: 0.3, delay: 0.2 });
  s.track('pluck', { gain: 0.12, reverb: 0.25, delay: 0.18, duck: 0.3 });
  s.track('bass', { gain: 0.26, reverb: 0, duck: 0.45, lp: 2500 });
  s.track('drums', { gain: 0.5, reverb: 0.08 });
  s.track('fx', { gain: 0.25, reverb: 0.4 });

  // Chords per bar of the eight-bar theme, and the build.
  const THEME_CH = [['D', 'maj9'], ['A', 'add9'], ['B', 'm9'], ['G', 'maj9'], ['D', 'maj9'], ['A', 'sus2'], ['G', 'maj9'], ['D', 'add9']];
  const BUILD_CH = [['E', 'm9'], ['G', 'maj9'], ['A', 'sus4'], ['A', 'maj'], ['E', 'm9'], ['G', 'maj9'], ['A', 'sus4'], ['A', '7']];
  const BRIDGE_CH = [['B', 'm9'], ['G', 'maj9'], ['D', 'add9'], ['A', 'sus4']];
  const bassOf = (r) => r + '2';

  const padBars = (from, list, o = {}) => list.forEach(([r, q], i) => s.chord('pad', I.pad, s.bar(from + i), chord(r, 3, q), 4, { spread: 0.8, ...o }));
  const strBars = (from, list, oct = 4) => list.forEach(([r, q], i) => s.chord('strings', I.strings, s.bar(from + i), chord(r, oct, q === 'maj9' ? 'maj7' : q).slice(0, 3), 4, { spread: 0.6 }));
  const bassBars = (from, list, rhythm) => list.forEach(([r], i) => {
    const b = s.bar(from + i), n = bassOf(r);
    if (rhythm === 'whole') s.play('bass', I.bass, b, n, 3.8, { cut: 500 });
    else if (rhythm === 'pulse') [0, 1.5, 2, 3.5].forEach((o) => s.play('bass', I.bass, b + o, n, 0.45, { cut: 900 }));
    else for (let k = 0; k < 8; k++) s.play('bass', I.bass, b + k / 2, k % 4 === 3 ? n.replace(/\d/, (d) => +d + 1) : n, 0.42, { cut: 1100 });
  });
  const arpBars = (from, list, oct = 4) => list.forEach(([r, q], i) => {
    const notes = chord(r, oct, q);
    const pat = [0, 1, 2, 3 % notes.length, 2, 1, 2, 3 % notes.length];
    pat.forEach((p, k) => s.play('pluck', I.pluck, s.bar(from + i) + k / 2, notes[p], 0.45, { pan: k % 2 ? 0.35 : -0.35, bright: 0.7 }));
  });

  // ---------------------------------------------------------------- 0–8 Intro: a spark
  s.mark(0, 'Intro');
  padBars(0, THEME_CH, { attack: 2.4, bright: 0.25 });
  s.seq('bells', I.bell, s.bar(2), score('D5', stretch(MOTIF, 2)), { decay: 2.2 });
  s.seq('bells', I.bell, s.bar(6), score('D6', ANSWER), { decay: 1.6, pan: 0.3 });
  s.play('fx', I.swell, s.bar(6), null, 8);

  // ---------------------------------------------------------------- 8–16 Verse: Orobi wakes
  s.mark(8, 'Verse');
  padBars(8, THEME_CH, { bright: 0.35 });
  s.seq('piano', I.piano, s.bar(8), score('D5', THEME), { vel: 0.7 });
  THEME_CH.forEach(([r, q], i) => s.chord('piano', I.piano, s.bar(8 + i), chord(r, 3, q).slice(0, 3), 1.8, { vel: 0.4, strum: 0.04 }));
  bassBars(8, THEME_CH, 'whole');
  s.drums('drums', 12, 4, { kick: 'x.......x.......', shaker: '..o...o...o...o.' });

  // ---------------------------------------------------------------- 16–24 Verse 2: the groove
  s.mark(16, 'Groove');
  padBars(16, THEME_CH, { bright: 0.45 });
  arpBars(16, THEME_CH);
  s.seq('piano', I.piano, s.bar(16), score('D5', THEME, { shift: 0 }), { vel: 0.62 });
  s.seq('bells', I.bell, s.bar(20), score('D6', MOTIF), { decay: 1.2, pan: -0.4 });
  bassBars(16, THEME_CH, 'pulse');
  s.drums('drums', 16, 8, { kick: 'x......xx.......', snare: '....x.......x...', hat: 'x.o.x.o.x.o.x.o.', shaker: '..o...o...o...o.' });

  // ---------------------------------------------------------------- 24–32 Build: lift
  s.mark(24, 'Build');
  padBars(24, BUILD_CH, { bright: 0.55 });
  strBars(24, BUILD_CH, 4);
  arpBars(24, BUILD_CH, 4);
  s.seq('piano', I.piano, s.bar(24), score('D5', [[2, 1], [3, 1], [5, 2], [4, 1], [5, 1], [6, 2], [2, 1], [3, 1], [5, 2], [5, 1], [6, 1], [7, 2],
    [2, 1], [3, 1], [5, 2], [4, 1], [5, 1], [6, 2], [5, 1], [6, 1], [7, 1], [8, 1], [9, 4]]), { vel: 0.65 });
  bassBars(24, BUILD_CH, 'pulse');
  s.drums('drums', 24, 6, { kick: 'x.......x.......', snare: '....x.......x...', hat: 'x.x.x.x.x.x.x.x.' });
  s.drums('drums', 30, 1, { kick: 'x.......x.......', snare: '....x...x.x.xxxx', hat: 'xxxxxxxxxxxxxxxx' });
  s.drums('drums', 31, 1, { snare: 'x.x.x.x.xxxxxxxx', tom: '........x.x.x.x.' }, { tom: { gain: 0.7 } });
  s.play('fx', I.riser, s.bar(30), null, 8);

  // ---------------------------------------------------------------- 32–48 Anthem: it goes live
  s.mark(32, 'Anthem');
  s.play('fx', I.impact, s.bar(32), null, 4);
  s.play('drums', I.ride, s.bar(32), null, 4, { crash: true });
  s.play('drums', I.ride, s.bar(40), null, 4, { crash: true });
  for (const from of [32, 40]) {
    padBars(from, THEME_CH, { bright: 0.85, attack: 0.05 });
    strBars(from, THEME_CH, 4);
    arpBars(from, THEME_CH, 5);
    bassBars(from, THEME_CH, 'eighths');
    s.drums('drums', from, 8, { kick: 'x...x...x...x...', clap: '....x.......x...', hat: '..x...x...x...x.', hat2: '..............x.' });
  }
  s.seq('lead', I.lead, s.bar(32), score('D5', THEME), { bright: 3600 });
  s.seq('lead', I.lead, s.bar(40), score('D5', THEME), { bright: 4200 });
  s.seq('harm', I.lead, s.bar(40), score('D5', THEME, { shift: 2 }), { bright: 2600, pan: 0.4 });
  s.seq('bells', I.bell, s.bar(32), score('D6', THEME), { decay: 0.9, pan: 0.5 });

  // ---------------------------------------------------------------- 48–52 Bridge: a breath
  s.mark(48, 'Bridge');
  padBars(48, BRIDGE_CH, { bright: 0.2, attack: 1.2 });
  s.seq('piano', I.piano, s.bar(48), score('D4', stretch(MOTIF, 2)), { vel: 0.55 });
  s.seq('piano', I.piano, s.bar(50), score('D5', stretch(ANSWER, 2)), { vel: 0.5 });
  BRIDGE_CH.forEach(([r, q], i) => s.chord('piano', I.piano, s.bar(48 + i), chord(r, 3, q).slice(0, 3), 3.6, { vel: 0.35, strum: 0.08 }));
  s.play('fx', I.riser, s.bar(50), null, 8);

  // ---------------------------------------------------------------- 52–60 Final anthem
  s.mark(52, 'Final anthem');
  s.play('fx', I.impact, s.bar(52), null, 4);
  s.play('drums', I.ride, s.bar(52), null, 4, { crash: true });
  padBars(52, THEME_CH, { bright: 0.95, attack: 0.05 });
  strBars(52, THEME_CH, 5);
  arpBars(52, THEME_CH, 5);
  bassBars(52, THEME_CH, 'eighths');
  s.drums('drums', 52, 8, { kick: 'x...x...x...x...', clap: '....x.......x...', hat: 'x.x.x.x.x.x.x.x.', hat2: '..............x.' });
  s.seq('lead', I.lead, s.bar(52), score('D5', THEME), { bright: 4600 });
  s.seq('harm', I.lead, s.bar(52), score('D4', THEME), { bright: 2400, pan: -0.4 });
  s.seq('bells', I.bell, s.bar(52), score('D6', THEME, { shift: 2 }), { decay: 0.9, pan: 0.5 });

  // ---------------------------------------------------------------- 60–64 Outro: home
  s.mark(60, 'Outro');
  s.chord('pad', I.pad, s.bar(60), chord('D', 3, 'maj9'), 16, { attack: 0.3, release: 4, bright: 0.4, spread: 0.9 });
  s.chord('strings', I.strings, s.bar(60), ['D3', 'A3', 'F#4', 'D5'], 15, { release: 3, spread: 0.7 });
  s.play('bass', I.bass, s.bar(60), 'D2', 8, { cut: 300 });
  s.seq('bells', I.bell, s.bar(60), score('D6', MOTIF), { decay: 2.4 });
  s.seq('bells', I.bell, s.bar(62), 'D6:0.5 F#6:0.5 A6:3', { decay: 3.2, pan: 0.2 });
  s.play('drums', I.ride, s.bar(60), null, 4, { crash: true, gain: 0.6 });
  return s;
}
