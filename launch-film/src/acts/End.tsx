import React from 'react';
import { useCurrentFrame } from 'remotion';
import { Loop, Orobi } from '../brand/Mark';
import { C, TYPE, typeStyle, U } from '../brand/tokens';
import { Words } from '../fx/Words';
import { SITE } from '../data';
import { clamp, E, hitPulse, lmix, mix, prog } from '../lib/anim';
import { camera, type Shot } from '../lib/camera';
import { measureTracked } from '../lib/measure';
import { ACT, copy, CUE, f60, H, W } from '../timeline';
import { PAGE, Site } from '../ui/kit';
import { CopyLine, Stage } from './shared';

// ACT 9 - the lockup (le-stage-clears-to-brand). The crash lands inside the published site, light,
// still rushing (1.76x -> rest). Then the site drops away, accelerating, and uncovers the loop that
// was waiting behind it the whole act: the device grows into Orobi, slides into the lockup as the
// name locks beside it (one clean hit), and the one line arrives after the name has resolved.

export const LOOP_END = { x: W / 2, y: H * 0.44, w: 300 };
const REST: Shot = { ax: PAGE.w / 2, ay: 470, sx: W / 2, sy: H / 2, k: 1.34 };
const HERO = TYPE.hero;
const GAP = 44;

export const lockupLayout = () => {
  const size = HERO.size * U;
  const wordW = measureTracked(copy('wordmark').text, size, HERO.weight, HERO.track) - HERO.track * size;
  const orobiW = LOOP_END.w * (1089 / 978);
  const total = orobiW + GAP + wordW;
  const left = W / 2 - total / 2;
  return { size, wordW, orobiX: left + orobiW / 2, wordX: left + orobiW + GAP, fit: Math.min(1, (W - 2 * 120) / total) };
};

export const End: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.end.from;
  // interior: the site opens already moving and decays to rest
  const rush = prog(F, ACT.end.from - 1, ACT.end.from + f60(40), E.outSoft);
  const site: Shot = { ...REST, k: REST.k * lmix(1.76, 1, rush) };
  const drop = prog(F, CUE.clear, CUE.clear + f60(44), E.exit);
  const lay = lockupLayout();
  // the device becomes Orobi, then slides into the lockup
  const body = prog(F, CUE.grow, CUE.grow + f60(24), E.out);
  const eyes = prog(F, CUE.grow + f60(16), CUE.grow + f60(24), E.smooth);
  const slide = prog(F, CUE.grow + f60(6), CUE.lockup, E.inOut);
  const ox = mix(LOOP_END.x, lay.orobiX, slide);
  const blink = hitPulse(F - (CUE.lineIn + f60(50)), 3, 4);
  const push = lmix(1, 1.12, prog(F, CUE.grow, ACT.end.from + ACT.end.dur, E.linear));
  const wm = copy('wordmark');
  const top = LOOP_END.y - (lay.size * HERO.lead) / 2;
  return (
    <Stage>
      <div style={{ position: 'absolute', inset: 0, transformOrigin: `${W / 2}px ${LOOP_END.y}px`, transform: `scale(${push * lay.fit})` }}>
        <div style={{ position: 'absolute', left: 0, top: 0, opacity: 1 - body }}>
          <Loop x={ox} y={LOOP_END.y} w={LOOP_END.w} dash={Math.sin(Math.PI * prog(F, CUE.uncover, CUE.uncover + f60(28), E.smooth))} spin={prog(F, CUE.uncover, CUE.uncover + f60(28), E.smooth)} track={C.surface3} />
        </div>
        {body > 0 && <Orobi x={ox} y={LOOP_END.y} w={LOOP_END.w} body={body} marks={body} eyes={eyes} blink={blink} id="end" />}
        <div style={{ position: 'absolute', left: 0, top: 0, transform: `translate(${lay.wordX}px, ${top}px)` }}>
          <Words text={wm.text} id={wm.id} frame={F} start={wm.in} size={lay.size} style={{ ...typeStyle('hero', lay.size), color: C.peach }} />
        </div>
      </div>
      <CopyLine id="endline" F={F} role="secondary" y={LOOP_END.y + 230} color={C.ink} />
      {drop < 1 && (
        <div style={{ position: 'absolute', inset: 0, transform: `translateY(${(drop * 1150).toFixed(2)}px)`, background: C.lbg, opacity: 1 - clamp((drop - 0.9) / 0.1) }}>
          <div style={camera(site)}>
            <Site theme="light" cols={2} data={SITE} />
          </div>
        </div>
      )}
    </Stage>
  );
};
