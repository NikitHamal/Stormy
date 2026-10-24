import React from 'react';
import { useCurrentFrame } from 'remotion';
import { C } from '../brand/tokens';
import { SITE } from '../data';
import { E, prog } from '../lib/anim';
import { mixColor } from '../lib/color';
import { ACT, CUE, f60, H, W } from '../timeline';
import { PAGE, Site, siteHeight } from '../ui/kit';
import { PreviewPill } from './Live';
import { Stage } from './shared';

// ACT 6 - the flyover (pd-page-runway-flyover + cl-theme-flip-in-motion). The whole site lies on a
// runway plane tilted 55 degrees and streams toward the viewer, faster and faster, its upper
// sections rising out of the horizon so the title arrives last. Mid-flight, the "Light" choice
// lands: the page and the stage flip to light top first over 6 f, and the motion never pauses.
// Nothing here is meant to be read; the act cuts on its fastest frame.

const PLANE = { w: 1.3 * PAGE.w, h: 2 * H, tilt: 50, k: 1.3 };
const PH = siteHeight(2) * 1.3;

/** Page travel inside the plane at local frame n: linear acceleration, then cruise into the cut. */
export const travel = (n: number) => (n <= 54 ? 3 * n + 0.1125 * n * n : 3 * 54 + 0.1125 * 54 * 54 + 18 * (n - 54));

export const Fly: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.fly.from;
  const y = -(PH - PLANE.h) + travel(f);
  // the flip: the light layer's mask edge runs top to bottom over 6 f, linear
  const X = -30 + 130 * prog(F, CUE.flip - f60(3), CUE.flip + f60(3), E.linear);
  const stage = mixColor(C.app, C.lbg, prog(F, CUE.flip - f60(3), CUE.flip + f60(3), E.linear));
  const page = (theme: 'dark' | 'light') => (
    <div style={{ position: 'absolute', left: 0, top: 0, transformOrigin: '0 0', transform: `translateY(${y.toFixed(2)}px) scale(${PLANE.k})` }}>
      <Site theme={theme} cols={2} data={SITE} />
    </div>
  );
  return (
    <Stage bg={stage}>
      <div style={{ position: 'absolute', inset: 0, perspective: 1200, perspectiveOrigin: '50% 50%' }}>
        <div
          style={{
            position: 'absolute',
            left: (W - PLANE.w) / 2,
            top: H - PLANE.h,
            width: PLANE.w,
            height: PLANE.h,
            overflow: 'hidden',
            transformOrigin: '50% 100%',
            transform: `rotateX(${PLANE.tilt}deg)`,
            WebkitMaskImage: 'linear-gradient(to top, #000 45%, transparent 95%)',
            maskImage: 'linear-gradient(to top, #000 45%, transparent 95%)',
          }}
        >
          {page('dark')}
          <div style={{ position: 'absolute', inset: 0, WebkitMaskImage: `linear-gradient(to bottom, #000 ${X}%, transparent ${X + 30}%)`, maskImage: `linear-gradient(to bottom, #000 ${X}%, transparent ${X + 30}%)` }}>
            {page('light')}
          </div>
        </div>
      </div>
      <PreviewPill F={F} />
    </Stage>
  );
};
