import React from 'react';
import { useCurrentFrame } from 'remotion';
import { Loop } from '../brand/Mark';
import { C } from '../brand/tokens';
import { MODELS, PROJECT, PUBLISH } from '../data';
import { clamp, E, fd, hitPulse, mix, prog } from '../lib/anim';
import { camera, type Shot, toScreen } from '../lib/camera';
import { ACT, CUE, f60, H, W } from '../timeline';
import { AppScreen, Composer, ModelChip, PublishSheet } from '../ui/kit';
import { CopyLine, dimFor, Stage } from './shared';

// ACT 8 - ship it. The publish sheet rises over the chat; the tap starts the loop spinning beside
// each step (spinner -> check), and on "Live" the loop settles into the address bar as the site's
// own icon. Then the address bar extrudes and the camera crashes through it (cam-crash-zoom-through):
// the next act opens inside the published site, still rushing forward.

export const S_SHIP: Shot = { ax: 360, ay: 1130, sx: W / 2, sy: H * 0.42, k: Math.min(1.6, (W * 0.96) / 720) };
const SHEET_Y = 830;
const STEP_Y = (i: number) => SHEET_Y + 40 - 18 + 8 + 30 + 44 + 26 + 31 + 62 * i; // step icon centres
const URL_Y = SHEET_Y + 40 - 18 + 8 + 30 + 44 + 26 + 186 + 24 + 48;
export const URLBAR = { x: 360, y: URL_Y };
const STEPS_AT = [CUE.publish, CUE.build2, CUE.done];

export const Ship: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.ship.from;
  const rise = prog(F, CUE.sheet - 1, CUE.sheet + f60(44), E.glide);
  const step = F < CUE.publish ? -1 : STEPS_AT.filter((s, i) => i > 0 && fd(F) >= s).length; // the working step
  const done = prog(F, CUE.done, CUE.done + f60(10), E.ui);
  const urlPop = hitPulse(F - CUE.url, 2, 5);
  // crash: the bar extrudes toward camera (E.in), then a cubic push into its face; cut at the act end
  const ext = prog(F, CUE.crash, CUE.crash + f60(12), E.in);
  const n = clamp((F - (CUE.crash + f60(4))) / f60(26));
  const crashK = Math.exp(0.64 * n ** 3) * (1 + 0.9 * n ** 3);
  const creep = 1 + 0.08 * prog(F, CUE.sheet, CUE.crash, E.linear); // a slow push through the reads
  const shot: Shot = { ...S_SHIP, ax: mix(S_SHIP.ax, URLBAR.x, n), ay: mix(S_SHIP.ay, URLBAR.y, n), k: S_SHIP.k * crashK * creep };
  const working = F >= CUE.publish && F < CUE.done;
  const iconAt = working ? { x: 60, y: STEP_Y(Math.max(0, step)) } : { x: 40 + 28 + 18, y: URL_Y };
  const hop = prog(F, CUE.done, CUE.done + f60(14), E.out);
  const pos = F < CUE.done ? iconAt : { x: mix(60, 40 + 28 + 18, hop), y: mix(STEP_Y(2), URL_Y, hop) };
  const scr = toScreen(shot, pos);
  return (
    <Stage>
      <div style={{ position: 'absolute', inset: 0, opacity: dimFor(F, ['ship'], 0.45) }}>
        <div style={camera(shot)}>
          <AppScreen title={PROJECT}>
            <Composer text="" model={<ModelChip name={MODELS[0]} />} />
            <div style={{ position: 'absolute', left: 0, top: 0, transform: `translateY(${SHEET_Y + (1 - rise) * 700}px)` }}>
              <div style={{ transformOrigin: `360px ${URL_Y - SHEET_Y}px`, transform: `scale(${1 + 0.03 * urlPop + 0.25 * ext})` }}>
                <PublishSheet target={PUBLISH.target} url={PUBLISH.url} steps={PUBLISH.steps} step={F < CUE.publish ? -1 : F >= CUE.done ? 3 : step} done={done} />
              </div>
            </div>
          </AppScreen>
        </div>
        {F >= CUE.publish && <Loop x={scr.x} y={scr.y} w={30 * shot.k} dash={working ? 1 : 0} spin={(F % 48) / 48} track={working ? C.surface3 : undefined} />}
      </div>
      <CopyLine id="ship" F={F} role="display" />
    </Stage>
  );
};
