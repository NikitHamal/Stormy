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

// The split: two windows of equal height on one centre line, the chat (with Orobi's UI block)
// and the live site, with equal margins either side. 9:16 stacks them; 1:1 scales the pair down.
const TALL = H > W;
type Rect = { x: number; y: number; w: number; h: number };
const CHAT_TOP = 262; // the app's chat area starts here (app px): the window shows the chat, not the chrome
export const SPLIT = (() => {
  if (TALL) {
    const app: Rect = { x: 80, y: 160, w: W - 160, h: 640 };
    const site: Rect = { x: 80, y: 160 + 640 + 40, w: W - 160, h: 440 }; // ends above the copy band
    return { app, site, kA: app.w / 720, kS: site.w / PAGE.w };
  }
  const s = W / 1920;
  const kA = 0.92 * s;
  const kS = 0.55 * s;
  const h = 640 * s;
  const gap = 56 * s;
  const aw = 720 * kA;
  const sw = PAGE.w * kS;
  const left = (W - (aw + gap + sw)) / 2;
  const top = H * 0.41 - h / 2;
  return { app: { x: left, y: top, w: aw, h }, site: { x: left + aw + gap, y: top, w: sw, h }, kA, kS };
})();

/** Columns at absolute frame F (fractional while the thumb travels). */
export const colsAt = (F: number) => mix(GALLERY.colsFrom, GALLERY.colsTo, prog(F, CUE.drag, CUE.dragEnd, E.smooth));
/** "Light" chosen: 0 -> 1 on the tap. */
export const litAt = (F: number) => prog(F, CUE.tapLight, CUE.tapLight + f60(8), E.ui);

const BLOCK_Y = 440;

export const Change: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.change.from;
  const into = prog(F, ACT.change.from - 1, ACT.change.from + f60(54), E.smooth);
  // the site: the live act's full frame closes into its window (rect and inner shot together)
  const from = siteShot(ACT.change.from);
  const R = SPLIT.site;
  const sr: Rect = { x: mix(0, R.x, into), y: mix(0, R.y, into), w: mix(W, R.w, into), h: mix(H, R.h, into) };
  const inner: Shot = { ax: mix(from.ax, 0, into), ay: mix(from.ay, 0, into), sx: mix(from.sx, 0, into), sy: mix(from.sy, 0, into), k: lmix(from.k, SPLIT.kS, into) };
  // the chat window slides in from its side
  const appIn = prog(F, ACT.change.from + f60(4), ACT.change.from + f60(54), E.glide);
  const A = SPLIT.app;
  const ar: Rect = TALL ? { ...A, y: A.y - (1 - appIn) * (A.y + A.h + 80) } : { ...A, x: A.x - (1 - appIn) * (A.x + A.w + 80) };
  const appShot: Shot = { ax: 0, ay: CHAT_TOP, sx: 0, sy: 0, k: SPLIT.kA };
  const block = spr(F, CUE.block + f60(6), SPR.firm);
  const cols = colsAt(F);
  const lit = litAt(F);
  // the touch: in to the thumb 8 f before the drag, rides it, then to "Light"
  const thumb0 = { x: 40 + 32 + 536, y: BLOCK_Y + 166 }; // the thumb's centre in app px (UIBlock layout)
  const thumbT = (GALLERY.colsFrom - cols) / (GALLERY.colsFrom - GALLERY.colsTo);
  const thumb = { x: thumb0.x - 520 * thumbT, y: thumb0.y };
  const light = { x: 40 + 467, y: BLOCK_Y + 305 }; // the Light segment's centre
  const toLight = prog(F, CUE.dragEnd + f60(10), CUE.tapLight, E.glide);
  const cur = { x: mix(thumb.x, light.x, toLight), y: mix(thumb.y, light.y, toLight) };
  const curIn = prog(F, CUE.drag - f60(24), CUE.drag - f60(8), E.out);
  const screen = { x: ar.x + cur.x * SPLIT.kA, y: ar.y + (cur.y - CHAT_TOP) * SPLIT.kA };
  const press = F >= CUE.drag - f60(2) && F < CUE.dragEnd + f60(4) ? 1 : hitPulse(F - CUE.tapLight, 2, 5);
  const dim = dimFor(F, ['live', 'change'], 0.5); // 'live' is still leaving on the seam
  const radius = 28 * into;
  const frame = (r: Rect, children: React.ReactNode, bg: string) => (
    <div style={{ position: 'absolute', left: 0, top: 0, width: r.w, height: r.h, transform: `translate(${r.x}px, ${r.y}px)`, overflow: 'hidden', borderRadius: radius, background: bg, boxShadow: into > 0.01 ? '0 40px 120px rgba(0,0,0,.45)' : undefined, outline: into > 0.01 ? `2px solid ${C.outline}` : undefined }}>
      {children}
    </div>
  );

  return (
    <Stage bg={mixColor(C.app, C.paper, into)}>
      <div style={{ position: 'absolute', inset: 0, opacity: dim }}>
        {frame(sr, <div style={camera(inner)}><Site theme="dark" cols={cols} data={SITE} /></div>, C.app)}
        {appIn > 0 && frame({ ...ar }, (
          <div style={camera(appShot)}>
            <AppScreen title={PROJECT} composer={<Composer text="" model={<ModelChip name={MODELS[0]} />} />}>
              <UserBubble text={ASK} x={240} y={300} w={456} />
              <div style={{ position: 'absolute', left: 0, top: 0, opacity: block, transform: `translate(40px, ${BLOCK_Y + (1 - block) * 40}px)` }}>
                <UIBlock title={GALLERY.title} cols={cols} colsFrom={GALLERY.colsFrom} colsTo={GALLERY.colsTo} widthFor={GALLERY.widthFor} lit={lit} thumb={press} />
              </div>
            </AppScreen>
          </div>
        ), C.app)}
        {curIn > 0 && <Touch x={screen.x} y={screen.y} press={press} o={curIn * (1 - prog(F, CUE.tapLight + f60(12), CUE.tapLight + f60(24), E.smooth))} />}
      </div>
      <PreviewPill F={F} x={mix(W / 2, R.x + R.w / 2, into)} y={mix(70, R.y - 46, into)} />
      <CopyLine id="change" F={F} role="display" />
    </Stage>
  );
};
void C;
