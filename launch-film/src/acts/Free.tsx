import React from 'react';
import { useCurrentFrame } from 'remotion';
import { Loop } from '../brand/Mark';
import { C } from '../brand/tokens';
import { MODELS, PROJECT } from '../data';
import { E, fd, hitPulse, prog } from '../lib/anim';
import { camera, type Shot, toScreen } from '../lib/camera';
import { ACT, CUE, f60, H, W } from '../timeline';
import { AppScreen, Composer, COMPOSER, ModelChip } from '../ui/kit';
import { CopyChain, dimFor, Stage } from './shared';

// ACT 7 - free models, no key. A close-up on the composer's model chip: the loop sits in the
// chip's icon slot and the model swaps on the peak of a kick (tr-motion-masked-swap), so each change
// hides inside the motion: free, keyless models from the app's own picker.

const CHIP = { x: COMPOSER.x + 154, y: COMPOSER.y + 116, w: 360, h: 58 };
// the whole composer, centred on the frame's axis: the chip sits in it as the app draws it
export const S_CHIP: Shot = { ax: COMPOSER.x + COMPOSER.w / 2, ay: COMPOSER.y + COMPOSER.h / 2, sx: W / 2, sy: H * 0.42, k: Math.min(1.65, (W * 0.92) / COMPOSER.w) };
const SWAPS = [CUE.swap1, CUE.swap2, CUE.swap3];

export const Free: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.free.from;
  // the shot opens moving (continuing the flight's forward rush) and comes to rest
  const settle = prog(F, ACT.free.from - 1, ACT.free.from + f60(36), E.out);
  // after it settles the camera keeps creeping in, so the reads between swaps stay alive
  const creep = 1 + 0.07 * prog(F, ACT.free.from + f60(36), ACT.free.from + ACT.free.dur, E.linear);
  const shot: Shot = { ...S_CHIP, sy: S_CHIP.sy - (1 - settle) * 300, k: S_CHIP.k * (1 + 0.25 * (1 - settle)) * creep };
  const idx = SWAPS.filter((s) => fd(F) >= s).length;
  const kick = SWAPS.reduce((a, s) => Math.max(a, hitPulse(F - s + 2, 2, 3)), 0);
  const slot = toScreen(shot, { x: CHIP.x + 29, y: CHIP.y + CHIP.h / 2 });
  return (
    <Stage>
      <div style={{ position: 'absolute', inset: 0, opacity: dimFor(F, ['free*'], 0.5) }}>
        <div style={camera(shot)}>
          <AppScreen
            title={PROJECT}
            composer={
              <Composer
                text=""
                model={
                  <div style={{ perspective: 900, transformOrigin: '50% 50%' }}>
                    <div style={{ transform: `rotateY(${(7 * kick).toFixed(3)}deg) translateX(${(3 * kick).toFixed(2)}px)`, boxShadow: kick > 0.01 ? '0 18px 40px rgba(0,0,0,.18)' : undefined, borderRadius: 29 }}>
                      <ModelChip name={MODELS[idx]} w={CHIP.w} />
                    </div>
                  </div>
                }
              />
            }
          />
        </div>
        <Loop x={slot.x} y={slot.y} w={34 * shot.k} dash={0} />
      </div>
      <CopyChain chain="free" F={F} role="display" />
    </Stage>
  );
};
void C;
