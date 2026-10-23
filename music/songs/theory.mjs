// Music theory helpers: keys, scale degrees, chords, and Orobi's leitmotif.
import { midi } from '../engine/core.mjs';

const MAJOR = [0, 2, 4, 5, 7, 9, 11];
const NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
export const name = (m) => NAMES[((m % 12) + 12) % 12] + (Math.floor(m / 12) - 1);

/** Degree (1-based, any octave; 8 = tonic an octave up) of the major key on `root` (e.g. 'D4'). */
export function deg(root, d) {
  const r = midi(root), i = d - 1, o = Math.floor(i / 7), s = ((i % 7) + 7) % 7;
  return name(r + 12 * o + MAJOR[s]);
}
export const transpose = (n, semis) => name(midi(n) + semis);

/** Chord symbol -> voicing around an octave, e.g. chord('D', 4, 'maj9'). */
const Q = { maj: [0, 4, 7], min: [0, 3, 7], sus2: [0, 2, 7], sus4: [0, 5, 7], maj7: [0, 4, 7, 11], m7: [0, 3, 7, 10], '7': [0, 4, 7, 10],
  add9: [0, 4, 7, 14], madd9: [0, 3, 7, 14], maj9: [0, 4, 11, 14], m9: [0, 3, 10, 14], '6': [0, 4, 7, 9], '13': [0, 4, 10, 14, 21] };
export function chord(root, octave, quality = 'maj') {
  const r = midi(root + octave);
  return Q[quality].map((x) => name(r + x));
}

/**
 * Orobi's leitmotif, as [degree, beats] in a major key. The rising triad is
 * the loader drawing itself (1-3-5); the turn back down (6-5-3-2) is the
 * blink; the answer climbs again and rests on the third: curiosity, then
 * warmth. Every track quotes it.
 */
export const MOTIF = [[1, 0.5], [3, 0.5], [5, 1], [6, 0.5], [5, 0.5], [3, 0.5], [2, 0.5]];
export const ANSWER = [[1, 0.5], [2, 0.5], [3, 0.5], [5, 0.5], [3, 2]];
/** The full eight-bar theme. */
export const THEME = [
  ...MOTIF, ...ANSWER,
  [3, 0.5], [5, 0.5], [8, 1], [9, 0.5], [8, 0.5], [6, 0.5], [5, 0.5],
  [6, 1], [5, 1], [3, 1.5], [2, 0.5],
  ...MOTIF,
  [1, 0.5], [2, 0.5], [3, 0.5], [6, 0.5], [5, 2],
  [4, 0.5], [6, 0.5], [8, 1], [7, 0.5], [6, 0.5], [5, 0.5], [4, 0.5],
  [5, 1], [2, 1], [1, 2],
];

/** [degree, beats] list -> score string in a key. `shift` adds degrees (3rds above = 2). */
export function score(root, phrase, { shift = 0, vel } = {}) {
  return phrase.map(([d, b]) => (d === 0 ? 'r' : deg(root, d + shift)) + ':' + b + (vel ? '@' + vel : '')).join(' ');
}
/** Same rhythm, every note's beats multiplied (augmentation). */
export const stretch = (phrase, k) => phrase.map(([d, b]) => [d, b * k]);
