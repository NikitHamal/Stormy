import React from 'react';
import { useCurrentFrame } from 'remotion';
import { C } from '../brand/tokens';
import { ASK, GALLERY, MODELS, PROJECT, SITE } from '../data';
import { E, hitPulse, lmix, mix, prog, SPR, spr } from '../lib/anim';
import { camera, type Shot } from '../lib/camera';
import { mixColor } from '../lib/color';
import { ACT, CUE, f60, H, W } from '../timeline';
import { AppScreen, Composer, ModelChip, PAGE, Site, Touch, UIBlock, UserBubble } from '../ui/kit';
import { PreviewPill, siteShot } from './Live';
import { CopyLine, dimFor, Stage } from './shared';

// ACT 5 - change anything. The live site glides into a window on the right while the chat slides in
// on the left: the user asked for bigger photos and Orobi answered with a UI block
// (generative UI). One slider drives every dependent number (dn-linked-control-counters): columns
// 4 -> 2, the photo width 290 -> 600 px, and the preview re-lays out live. Then "Light" is tapped;
// the theme flips in the next act, mid-flight.

/** The app (left) and the site window (right) in the split. */
// 16:9 splits side by side; 9:16 stacks the chat over the site; 1:1 splits at a smaller scale.
const TALL = H > W;
export const S_SPLIT_APP: Shot = TALL ? { ax: 360, ay: 470, sx: W / 2, sy: H * 0.27, k: 0.98 } : { ax: 360, ay: 480, sx: W * 0.26, sy: H * 0.5, k: W >= 1920 ? 1.05 : 0.7 };
export const SITE_WIN = TALL ? { x: (W - PAGE.w * 0.7) / 2, y: H * 0.55, k: 0.7 } : { x: W * 0.49, y: H * 0.12, k: W >= 1920 ? 0.68 : 0.36 };
export const siteSplitShot: Shot = { ax: 0, ay: 0, sx: SITE_WIN.x, sy: SITE_WIN.y, k: SITE_WIN.k };

/** Columns at absolute frame F (fractional while the thumb travels). */
export const colsAt = (F: number) => mix(GALLERY.colsFrom, GALLERY.colsTo, prog(F, CUE.drag, CUE.dragEnd, E.smooth));
/** "Light" chosen: 0 -> 1 on the tap. */
export const litAt = (F: number) => prog(F, CUE.tapLight, CUE.tapLight + f60(8), E.ui);

const BLOCK_Y = 440;

export const Change: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.change.from;
  const into = prog(F, ACT.change.from - 1, ACT.change.from + f60(54), E.smooth);
  // the site: from the live act's full-frame shot into its window
  const from = siteShot(ACT.change.from);
  const win: Shot = {
    ax: mix(from.ax, 0, into),
    ay: mix(from.ay, 0, into),
    sx: mix(from.sx, SITE_WIN.x, into),
    sy: mix(from.sy, SITE_WIN.y, into),
    k: lmix(from.k, SITE_WIN.k, into),
  };
  const appIn = prog(F, ACT.change.from + f60(4), ACT.change.from + f60(54), E.glide);
  const app: Shot = TALL ? { ...S_SPLIT_APP, sy: S_SPLIT_APP.sy - (1 - appIn) * 900 } : { ...S_SPLIT_APP, sx: S_SPLIT_APP.sx - (1 - appIn) * 1150 };
  const block = spr(F, CUE.block + f60(6), SPR.firm);
  const cols = colsAt(F);
  const lit = litAt(F);
  // the cursor: in to the thumb 8 f before the drag, rides it, then to "Light"
  const thumb0 = { x: 40 + 32 + 536, y: BLOCK_Y + 166 }; // the thumb's centre in app px (UIBlock layout)
  const thumbT = (GALLERY.colsFrom - cols) / (GALLERY.colsFrom - GALLERY.colsTo);
  const thumb = { x: thumb0.x - 520 * thumbT, y: thumb0.y };
  const light = { x: 40 + 467, y: BLOCK_Y + 305 }; // the Light segment's centre
  const toLight = prog(F, CUE.dragEnd + f60(10), CUE.tapLight, E.glide);
  const cur = { x: mix(thumb.x, light.x, toLight), y: mix(thumb.y, light.y, toLight) };
  const curIn = prog(F, CUE.drag - f60(24), CUE.drag - f60(8), E.out);
  const screen = { x: app.sx + (cur.x - app.ax) * app.k, y: app.sy + (cur.y - app.ay) * app.k };
  const press = F >= CUE.drag - f60(2) && F < CUE.dragEnd + f60(4) ? 1 : hitPulse(F - CUE.tapLight, 2, 5);
  const dim = dimFor(F, ['live', 'change'], 0.5); // 'live' is still leaving on the seam

  return (
    <Stage bg={mixColor(C.app, C.paper, into)}>
      <div style={{ position: 'absolute', inset: 0, opacity: dim }}>
        {!TALL && (
        <div style={{ ...camera(win), borderRadius: 28 / win.k, overflow: 'hidden', width: PAGE.w, height: 1700, boxShadow: '0 40px 120px rgba(0,0,0,.45)' }}>
          <Site theme="dark" cols={cols} data={SITE} />
        </div>
        )}
        <div style={camera(app)}>
          <AppScreen title={PROJECT} composer={<Composer text="" model={<ModelChip name={MODELS[0]} />} />}>
            <UserBubble text={ASK} x={240} y={300} w={456} />
            <div style={{ position: 'absolute', left: 0, top: 0, opacity: block, transform: `translate(40px, ${BLOCK_Y + (1 - block) * 40}px)` }}>
              <UIBlock title={GALLERY.title} cols={cols} colsFrom={GALLERY.colsFrom} colsTo={GALLERY.colsTo} widthFor={GALLERY.widthFor} lit={lit} thumb={press} />
            </div>
          </AppScreen>
        </div>
        {TALL && (
        <div style={{ ...camera(win), borderRadius: 28 / win.k, overflow: 'hidden', width: PAGE.w, height: 1700, boxShadow: '0 40px 120px rgba(0,0,0,.45)' }}>
          <Site theme="dark" cols={cols} data={SITE} />
        </div>
        )}
        {curIn > 0 && <Touch x={screen.x} y={screen.y} press={press} o={curIn * (1 - prog(F, CUE.tapLight + f60(12), CUE.tapLight + f60(24), E.smooth))} />}
      </div>
      <PreviewPill F={F} x={mix(W / 2, SITE_WIN.x + (PAGE.w * SITE_WIN.k) / 2, into)} />
      <CopyLine id="change" F={F} role="display" />
    </Stage>
  );
};
void C;
