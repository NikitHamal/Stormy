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
/** Cards are drawn at this scale: a pile that fills the frame, not a row of chips. */
export const CARD_K = Math.min(1.75, (W / 1920) * 1.75 + (W < H ? 0.4 : 0.3));
export const POSE = PILE.map((c, k) => {
  // a heap that closes in on the centre: the first cards near the middle (frame 0 is composed),
  // later ones spiralling out to the edges on the golden angle, so the pile covers the frame
  const ang = k * 2.39996 + 0.6;
  const rr = 0.18 + 0.82 * Math.sqrt(k / (PILE.length - 1));
  const w = c.w * CARD_K;
  const cx = W / 2 + Math.cos(ang) * rr * W * 0.43;
  const cy = H * 0.44 + Math.sin(ang) * rr * H * 0.43;
  return { x: Math.min(W - w + 60, Math.max(-60, cx - w / 2)), y: Math.min(H - 120, Math.max(-20, cy - 70)), rot: rand(`r${k}`) * 7 - 3.5 };
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
        const iy = mix(0, to.y - (p.y + 70), implode);
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
