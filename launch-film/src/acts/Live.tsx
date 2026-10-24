import React from 'react';
import { useCurrentFrame } from 'remotion';
import { Loop } from '../brand/Mark';
import { C } from '../brand/tokens';
import { SITE } from '../data';
import { E, prog } from '../lib/anim';
import { camera, type Shot } from '../lib/camera';
import { ACT, CUE, f60, H, W } from '../timeline';
import { PAGE, Site } from '../ui/kit';
import { CopyLine, dimFor, Stage } from './shared';

// ACT 4 - live. The iris (Act 3) opens on Mira's site already building: the nav, the title, then
// the gallery cascading in, 4 f apart, while the camera cruises down the page. "live." lands under
// it. The loop rides in the preview's chrome pill (top centre), so the device never leaves.

/** The camera on the site during the live act (page coordinates). */
export const siteShot = (F: number): Shot => ({
  ax: PAGE.w / 2,
  ay: 300 + 140 * prog(F, CUE.open - f60(24), ACT.live.from + ACT.live.dur, E.linear),
  sx: W / 2,
  sy: H * 0.36,
  k: Math.min(1.18, (W / PAGE.w) * 1.12),
});

export const revealAt = (F: number) => ({
  nav: prog(F, CUE.open - f60(14), CUE.open + f60(6), E.out),
  hero: prog(F, CUE.open - f60(4), CUE.open + f60(22), E.out),
  tiles: Array.from({ length: SITE.photos }, (_, i) => prog(F, CUE.open + f60(26 + 4 * i), CUE.open + f60(26 + 4 * i + 16), E.out)),
  foot: prog(F, CUE.open + f60(70), CUE.open + f60(90), E.out),
});

/** The preview chrome: a pill at the top centre with the loop (the device) and the site's address. */
export const PILL = { x: W / 2, y: 70, w: 360, h: 64 };
export const PreviewPill: React.FC<{ F: number; light?: number; o?: number; x?: number; y?: number }> = ({ F, o = 1, x = PILL.x, y = PILL.y }) => (
  <div style={{ position: 'absolute', left: 0, top: 0, opacity: o, transform: `translate(${x - PILL.w / 2}px, ${y - PILL.h / 2}px)` }}>
    <div style={{ width: PILL.w, height: PILL.h, borderRadius: PILL.h / 2, background: C.surface, border: `2px solid ${C.outline}`, boxShadow: '0 20px 60px rgba(0,0,0,.30)' }} />
    <Loop x={44} y={PILL.h / 2} w={46} dash={1} spin={((F % 96) / 96)} track={C.surface3} />
    <div data-ui style={{ position: 'absolute', left: 84, top: 15, fontFamily: '"Poppins"', fontSize: 24, fontWeight: 500, color: C.text, whiteSpace: 'pre' }}>
      {SITE.name.split(' ')[0].toLowerCase()}.preview
    </div>
  </div>
);

export const LiveSite: React.FC<{ F: number }> = ({ F }) => (
  <div style={camera(siteShot(F))}>
    <Site theme="dark" cols={4} reveal={revealAt(F)} data={SITE} />
  </div>
);

export const Live: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.live.from;
  return (
    <Stage bg={C.app}>
      <div style={{ position: 'absolute', inset: 0, opacity: dimFor(F, ['live'], 0.5) }}>
        <LiveSite F={F} />
      </div>
      <PreviewPill F={F} />
      <CopyLine id="live" F={F} role="display" />
    </Stage>
  );
};
