// The sound events of the film, computed from the picture code (scripts/export-cues.ts writes
// out/cues.json; scripts/audio/score.py voices each with the kit). Rules: a visible cause for every
// sound; impacts on contact, pops at 50%, settles at 97%, whoosh apex on the velocity peak; one soft
// `text` per fragment; the lockup is a stop-down and ONE clean element.
import { DOCK, LOOP0 } from './acts/Turn';
import { ARRIVE, POSE } from './acts/Pile';
import { ROW_AT } from './acts/Build';
import { colsAt } from './acts/Change';
import { TYPE, U } from './brand/tokens';
import { PILE, PROMPT } from './data';
import { chainResolves } from './fx/Chain';
import { E, SPR } from './lib/anim';
import { hit, peak, settleOf } from './lib/sync';
import { ACT, copy, CUE, f60, FPS, TOTAL, W } from './timeline';

export type Kind =
  | 'tick' | 'tock' | 'hit' | 'land' | 'pop' | 'whoosh' | 'riser'
  | 'drop' | 'key' | 'click' | 'snap' | 'swell' | 'bell' | 'suck' | 'text';

export type SoundEvent = {
  f: number;
  kind: Kind;
  weight?: number;
  pan?: number;
  pitch?: number | string;
  apexFrac?: number;
  dur?: number;
  variant?: string;
  id?: string;
};

const panX = (x: number) => Math.max(-0.8, Math.min(0.8, ((x - W / 2) / (W / 2)) * 0.8));
const actOf = (f: number) => Object.entries(ACT).find(([, a]) => f >= a.from && f < a.from + a.dur)?.[0] ?? 'tail';
const DISPLAY = TYPE.display.size * U;

/** The fractional frame each prompt character appears on (the inverse of Turn's eased typewriter). */
const keyFrames = () => {
  const n = PROMPT.length;
  // Turn types round(n * t): character i appears where n * t reaches i - 0.5
  return Array.from({ length: n }, (_, i) => CUE.typeFrom + ((i + 0.5) / n) * (CUE.typeTo - CUE.typeFrom));
};

export default function cues(): SoundEvent[] {
  const ev: SoundEvent[] = [];
  const text = (chain: string) => chainResolves(chain, { size: DISPLAY }).forEach((f, i) => ev.push({ f, kind: 'text', weight: 0.75, id: `${chain}${i + 1}` }));

  // 1 pile: one crisp tick per arriving card, each a little louder (the pile thickens); the freeze is a silence (score.json)
  ARRIVE.forEach((a, k) => {
    if (a < 0) return;
    ev.push({ f: a + f60(4), kind: 'tick', variant: 'crisp', weight: 0.45 + 0.04 * k, pan: panX(POSE[k].x + PILE[k].w / 2), id: `card${k}` });
  });
  text('hook');

  // 2 turn: the implosion's suck ends where the point is; the loop's motif on its birth; text; the dock; typing
  ev.push({ f: CUE.implode + f60(18), kind: 'suck', dur: f60(18), apexFrac: 0.85, weight: 0.9, id: 'implode' });
  ev.push({ f: CUE.born, kind: 'bell', variant: 'motif', weight: 0.7, id: 'loop-born' });
  text('turn');
  ev.push({ f: settleOf(DOCK.from, DOCK.to, DOCK.ease), kind: 'land', variant: 'light', weight: 0.6, pan: panX(LOOP0.x + 260), id: 'dock' });
  keyFrames().forEach((f, i) => {
    const ch = PROMPT[i];
    const prev = PROMPT[i - 1];
    ev.push({ f, kind: 'key', variant: ch === ' ' ? 'space' : i === 0 || prev === ' ' ? 'word' : undefined, id: `key${i}` });
  });

  // 3 build: the send tap; each row's check pops, climbing (progress motif); the iris' whoosh into the drop
  ev.push({ f: CUE.send, kind: 'click', variant: 'confirm', id: 'send' });
  text('build');
  ROW_AT.forEach((r, i) => ev.push({ f: r.d, kind: 'bell', weight: 0.6, id: `check${i + 1}` }));
  ev.push({ f: peak(CUE.iris, CUE.open, E.linear) - 2, kind: 'whoosh', weight: 0.9, pan: 0, dur: Math.max(f60(18), CUE.open - CUE.iris), apexFrac: 0.9, id: 'iris' });

  // 4 live: the drop lands on the reveal (score.json drops + payoff); the gallery tiles land, light
  // the gallery's cascade is carried by the drop: no per-tile sounds under it
  ev.push({ f: settleOf(copy('live').in, copy('live').resolved, E.out), kind: 'text', weight: 0.75, id: 'live' });

  // 5 change: the block lands; each column step locks with a crisp tick; the "Light" tap
  ev.push({ f: hit(CUE.block + f60(6), SPR.firm, 0.5), kind: 'pop', weight: 0.7, id: 'block' });
  ev.push({ f: settleOf(copy('change').in, copy('change').resolved, E.out), kind: 'text', weight: 0.75, id: 'change' });
  for (let F = CUE.drag; F <= CUE.dragEnd + 1; F++) {
    if (Math.round(colsAt(F)) !== Math.round(colsAt(F - 1))) ev.push({ f: F, kind: 'tick', variant: 'crisp', weight: 0.8, id: `cols${Math.round(colsAt(F))}` });
  }
  ev.push({ f: CUE.tapLight, kind: 'click', variant: 'confirm', id: 'light' });

  // 6 fly: one whoosh as long as the flight, its apex on the fastest frames before the cut; the flip ticks
  ev.push({ f: ACT.fly.from + ACT.fly.dur - f60(4), kind: 'whoosh', weight: 1.1, pan: 0, dur: ACT.fly.dur - f60(10), apexFrac: 0.95, id: 'runway' });
  ev.push({ f: CUE.flip, kind: 'tick', variant: 'crisp', weight: 0.9, id: 'flip' });

  // 7 free: one crisp tick per swap; text
  text('free');
  [CUE.swap1, CUE.swap2, CUE.swap3].forEach((s, i) => ev.push({ f: s, kind: 'tick', variant: 'crisp', weight: 0.7, id: `swap${i + 1}` }));

  // 8 ship: the publish tap, each step's check (continuing the climb), the URL pop, text, the crash
  ev.push({ f: CUE.publish, kind: 'click', variant: 'confirm', id: 'publish' });
  [CUE.build2, CUE.done].forEach((s, i) => ev.push({ f: s, kind: 'bell', weight: 0.6, id: `check${6 + i}` }));
  ev.push({ f: CUE.url, kind: 'pop', weight: 0.6, id: 'url' });
  ev.push({ f: settleOf(copy('ship').in, copy('ship').resolved, E.out), kind: 'text', weight: 0.75, id: 'ship' });
  ev.push({ f: ACT.ship.from + ACT.ship.dur - 2, kind: 'whoosh', weight: 1.2, pan: 0, dur: f60(30), apexFrac: 0.93, id: 'crash' });
  ev.push({ f: ACT.end.from, kind: 'hit', weight: 0.9, id: 'crash-in' });

  // 9 end: the uncovered loop turns once (a soft tock), then stop-down;
  ev.push({ f: CUE.uncover, kind: 'tock', weight: 0.6, id: 'loop-turn' });
  // ONE hit as the wordmark becomes readable; the line's soft text sound
  const wm = copy('wordmark');
  ev.push({ f: settleOf(wm.in, wm.in + f60(26), E.out), kind: 'hit', weight: 1, pan: 0.1, id: 'lockup' });
  ev.push({ f: settleOf(copy('endline').in, copy('endline').resolved, E.out), kind: 'text', weight: 0.6, id: 'endline' });

  for (const e of ev) {
    if (!(e.f >= 0 && e.f < TOTAL)) throw new Error(`sync: ${e.id} at f=${e.f} is outside the film (0..${TOTAL - 1})`);
    e.id = `${actOf(e.f)}:${e.id}`;
  }
  void FPS;
  return ev.sort((a, b) => a.f - b.f);
}
