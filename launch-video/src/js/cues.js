// The sound cue sheet, derived from the same beat tables the picture uses.
// Pure: no DOM. scripts/audio.mjs imports it in Node; nothing in the composition depends on it.
// Every cue is { t: seconds in the film, kind, pan: -1..1, ...params }. Retiming a scene in
// config.json moves its cues with it (local beat × duration / design), exactly as renderAt does.
import wake, { T as WT } from './scenes/wake.js';
import describe, { T as DT } from './scenes/describe.js';
import live, { T as LT } from './scenes/live.js';
import models, { T as MT } from './scenes/models.js';
import endcard, { T as ET } from './scenes/endcard.js';

const MODS = { wake, describe, live, models, endcard };

/** Scene start times and a local→global mapper per scene id. */
export function timeline(cfg) {
  let acc = 0;
  const map = {};
  for (const sc of cfg.scenes) {
    const mod = MODS[sc.id];
    const dur = sc.duration ?? mod.design;
    const start = acc;
    map[sc.id] = { start, dur, at: (lt) => start + (lt / mod.design) * dur };
    acc += dur;
  }
  return { scenes: map, total: cfg.meta.duration ?? acc };
}

export function cues(cfg) {
  const { scenes: S, total } = timeline(cfg);
  const c = [];
  const add = (t, kind, o = {}) => c.push({ t, kind, pan: 0, ...o });
  const sc = (id) => cfg.scenes.find((s) => s.id === id) || {};

  // --- Wake: the loader draws, morphs into Orobi, who hops, lands and wakes.
  const w = S.wake.at;
  add(w(WT.drawStart), 'swell', { dur: w(WT.morph) - w(WT.drawStart) });
  add(w(WT.morph), 'bloom');
  add(w(WT.takeoff), 'hopUp', { gain: 0.8 });
  add(w(WT.land), 'land', { gain: 0.9 });
  add(w(WT.wake), 'chirp');
  WT.glances.forEach(([t, dx]) => add(w(t), 'glance', { pan: dx > 0 ? 0.35 : -0.35 }));

  // --- Describe: headline, pill, typing, send, gulp, phone, tokens.
  const d = S.describe.at;
  add(d(DT.head), 'swish', { gain: 0.6 });
  add(d(DT.pill[0]), 'uiPop');
  const prompt = sc('describe').prompt || '';
  for (let i = 0; i < prompt.length; i++) {
    const t = d(DT.type[0] + ((DT.type[1] - DT.type[0]) * i) / prompt.length);
    add(t, 'key', { seed: i, space: prompt[i] === ' ', pan: -0.25 + (0.5 * i) / Math.max(1, prompt.length - 1) });
  }
  add(d(DT.send), 'tap');
  add(d(DT.send) + 0.02, 'sendWhoosh');
  add(d(DT.gulp[1]), 'gulp');
  add(d(DT.toLoop), 'whirr', { dur: d(DT.think[1]) - d(DT.toLoop) });
  add(d(DT.phone[0]), 'rise', { dur: d(DT.phone[1]) - d(DT.phone[0]) });
  (sc('describe').tokens || []).forEach((_, i) => {
    const t0 = DT.tokens + i * DT.tokenGap;
    add(d(t0), 'tokenOut', { i, pan: i % 2 ? 0.4 : -0.4 });
    add(d(t0 + DT.tokenFly), 'tokenIn', { i, pan: (((i * 37) % 5) - 2) * 0.12 });
  });

  // --- Live: the site builds, Orobi pops out and peeks, the whip pan, the tool run.
  const l = S.live.at;
  add(l(LT.head), 'swish', { gain: 0.6 });
  for (let i = 0; i < 4; i++) add(l(LT.build + 0.12 + i * 0.13), 'blockPop', { i, pan: 0.15 });
  add(l(LT.morph[0]), 'bloomSmall', { pan: 0.45 });
  add(l(LT.hop[0]), 'hopUp', { gain: 0.6, pan: 0.45 });
  add(l(LT.hop[1]), 'land', { gain: 0.55, pan: 0.45 });
  add(l(LT.live), 'livePing', { pan: 0.1 });
  add(l(LT.eyes), 'chirp', { gain: 0.7, pan: 0.45 });
  add(l(LT.scroll[0]), 'scroll', { dur: l(LT.scroll[1]) - l(LT.scroll[0]) });
  add(l(LT.whip[0]), 'whip', { dur: l(LT.whip[1]) - l(LT.whip[0]) });
  add(l(LT.head2), 'swish', { gain: 0.5 });
  const rows = sc('live').toolRun || [];
  rows.forEach((r, i) => {
    const t0 = LT.rows + i * LT.rowGap;
    add(l(t0), 'rowIn', { i });
    if (r.icon === 'done') add(l(t0 + 0.05), 'success');
    else add(l(t0 + LT.spin), 'check', { i });
  });

  // --- Models: match cut into light, Orobi drops in, providers spring out, hop away.
  const m = S.models.at;
  add(m(MT.expand[0]), 'matchCut', { dur: m(MT.expand[1]) - m(MT.expand[0]) });
  add(m(MT.drop[0]), 'fall', { dur: m(MT.drop[1]) - m(MT.drop[0]) });
  add(m(MT.drop[1]), 'land', { gain: 1 });
  MT.head.forEach((t) => add(m(t), 'swish', { gain: 0.45 }));
  const nChips = (sc('models').free || []).length + (sc('models').byo || []).length;
  for (let i = 0; i < nChips; i++) add(m(MT.chips + 0.08 + i * 0.045), 'chip', { i, pan: ((i % 3) - 1) * 0.5 });
  add(m(MT.out), 'suck', { dur: 0.35 });
  add(m(MT.takeoff), 'hopUp', { gain: 0.9 });

  // --- End card: iris impact on the cut, wordmark, sparkles, tagline, platform.
  const e = S.endcard.at;
  add(e(ET.iris[0]) - 1.0, 'riser', { dur: 1.0 });
  add(e(ET.iris[0]), 'impact');
  const word = sc('endcard').wordmark || 'Oro';
  [...word].forEach((_, i) => add(e(ET.word + i * 0.07), 'letter', { i, pan: (i - (word.length - 1) / 2) * 0.25 }));
  add(e(ET.word), 'logoChime');
  for (let i = 0; i < 4; i++) add(e(ET.spark + i * 0.07), 'twinkle', { i, pan: [-0.4, 0.5, 0.2, -0.55][i] });
  add(e(ET.tag), 'swish', { gain: 0.35 });
  add(e(ET.plat), 'uiTick');
  add(e(ET.blink), 'blink');

  c.sort((a, b) => a.t - b.t);
  return { cues: c, total, scenes: S };
}
