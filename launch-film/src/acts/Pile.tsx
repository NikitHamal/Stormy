import React from 'react';
import { useCurrentFrame } from 'remotion';
import { C } from '../brand/tokens';
import { PILE } from '../data';
import { clamp, E, lmix, mix, prog, rand, SPR, spr } from '../lib/anim';
import { ACT, CUE, f60, H, W } from '../timeline';
import { PileCard } from '../ui/kit';
import { CopyChain, dimFor, Stage } from './shared';

// ACT 1 - the problem pile-up (oh-pile-up-freeze-turn). Frame 0 already holds three toolchain
// windows; thirteen more pile in, faster and faster (gap_k = 26 * 0.88^k), each rising from +40 px
// with a slight tilt, while the camera breathes in on E.in. "A website" / "takes all this." builds
// over it. At peak density the picture freezes for 15 f of true silence; the turn (Act 2) implodes
// the pile into one point on the bar-3 downbeat.

const L = (abs: number) => abs - ACT.pile.from;

/** Arrival frame (absolute) of card k: three already on screen at frame 0, then the accelerating pile. */
export const ARRIVE: number[] = (() => {
  const out = [-30, -24, -18];
  let t = 18;
  for (let k = 0; out.length < PILE.length; k++) {
    out.push(Math.round(t));
    t += 26 * 0.88 ** k;
  }
  return out;
})();
if (ARRIVE[ARRIVE.length - 1] >= CUE.freeze - f60(6)) throw new Error('Pile: the last card must land before the freeze');

/** Each card's resting pose (frame px, its top-left), seeded so a re-render is identical. */
/** Cards are drawn at this scale: a pile that fills the frame above the copy band. */
export const CARD_K = W < H ? 1.05 : (W / 1920) * 1.5;
/**
 * Each card's resting centre, as an offset from the frame's centre line (16:9 px), in mirrored
 * pairs: the pile builds outward symmetrically and stays above the eye line, so the copy below it
 * reads on clear stage. Frame 0 is a balanced triangle (cards 0-2).
 */
const SLOTS: [number, number][] = [
  [0, 420], [-400, 300], [400, 300],
  [-640, 470], [640, 470], [-250, 560], [250, 560],
  [-700, 150], [700, 150], [0, 140],
  [-480, 660], [480, 660], [-860, 620], [860, 620],
  [0, 660], [0, 470],
];
export const POSE = PILE.map((c, k) => {
  const [dx, cy] = SLOTS[k];
  const w = c.w * CARD_K;
  const sx = W / 1920;
  const sy = W < H ? (H * 0.62) / 1080 : H / 1080;
  return { x: W / 2 + dx * sx - w / 2, y: cy * sy - 70 * CARD_K, rot: (k === 0 ? 0 : (k % 2 ? -1 : 1) * (1 + rand(`r${k}`) * 2.2)) };
});

/** The pile at absolute frame F, with an optional implosion (0..1) toward a point. */
export const PileLayer: React.FC<{ F: number; implode?: number; to?: { x: number; y: number } }> = ({ F, implode = 0, to = { x: W / 2, y: H * 0.45 } }) => {
  // Freeze: from CUE.freeze the pile is held exactly (designed freeze, <= 15 f, on a silence).
  const Fp = Math.min(F, CUE.freeze);
  const cam = lmix(1, 1.045, prog(Fp, 0, CUE.freeze, E.in));
  return (
    <div style={{ position: 'absolute', inset: 0, transformOrigin: `${W / 2}px ${H / 2}px`, transform: `scale(${cam})` }}>
      {PILE.map((c, k) => {
        const a = ARRIVE[k];
        if (Fp < a - 1) return null;
        const s = spr(Fp, a, SPR.firm);
        const o = prog(Fp, a, a + f60(8), E.smooth);
        const p = POSE[k];
        // implosion: every card falls into the point on E.in, shrinking in log space
        const ix = mix(0, to.x - (p.x + (c.w * CARD_K) / 2), implode);
        const iy = mix(0, to.y - (p.y + 70 * CARD_K), implode);
        const k2 = lmix(1, 0.02, implode);
        return (
          <div key={k} style={{ position: 'absolute', left: 0, top: 0, opacity: o * (1 - clamp((implode - 0.55) / 0.45)), transformOrigin: '0 0', transform: `translate(${p.x + ix}px, ${p.y + iy + (1 - s) * 40}px) rotate(${p.rot * (1 - implode)}deg) scale(${k2 * CARD_K})` }}>
            <PileCard {...c} />
          </div>
        );
      })}
    </div>
  );
};

export const Pile: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.pile.from;
  return (
    <Stage>
      <div style={{ position: 'absolute', inset: 0, opacity: dimFor(F, ['hook*'], 0.45) }}>
        <PileLayer F={F} />
      </div>
      <CopyChain chain="hook" F={F} role="display" />
    </Stage>
  );
};
void L;
void C;
