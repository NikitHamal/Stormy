import React from 'react';
import { C } from './tokens';

// Orobi, Oro's mark (brand-supplied; geometry from the app, OroBrand.kt / launch-video orobi.js):
// an infinity loop with a head, the same stroke that draws the app's loader. The film's device is
// that loop (`LOOP`): born from the collapsing pile, it carries every shot and becomes Orobi.
export const GEO = {
  viewBox: { x: 200, y: 400, w: 1200, h: 820 },
  body: 'M810,710C760,635 650,503 520,503C370,503 256,660 256,810C256,990 380,1148 510,1148C640,1148 740,1050 810,933C880,1040 980,1148 1100,1148C1250,1148 1345,1020 1345,880C1345,760 1290,650 1190,590C1205,570 1200,520 1160,495C1140,478 1115,472 1085,472C1060,472 1040,480 1030,487L995,520C910,560 850,640 810,710ZM743,825C690,745 600,631 520,631C435,631 377,730 377,835C377,940 440,1017 520,1017C600,1017 690,905 743,825ZM880,817C940,720 1000,650 1030,632C1065,645 1110,650 1160,630C1215,690 1241,770 1241,835C1241,940 1180,1017 1115,1017C1030,1017 940,940 880,817Z',
  markL: 'M553,400L553,503C515,555 487,600 477,637L470,660L420,760L390,930C400,1010 440,1090 505,1148L505,1200L317,1200L317,925Q317,850 256,815L256,803Q317,770 317,690L317,400Z',
  markR: 'M1020,400L1020,470C1070,520 1105,580 1127,642L1150,670L1190,950L1205,965C1195,1050 1160,1110 1097,1148L1097,1200L1285,1200L1285,935Q1285,850 1340,820L1340,800Q1285,770 1285,680L1285,400Z',
  eyes: [[717, 634], [885, 634]] as const,
  eyeRx: 11.5,
  eyeRy: 14.5,
  // The loader: one closed infinity stroke through the body's two lobes.
  loop: 'M810 822C880 700 980 568 1105 568C1230 568 1295 700 1295 830C1295 960 1220 1082 1105 1082C980 1082 880 940 810 822C740 700 640 567 520 567C400 567 317 700 317 825C317 950 400 1082 520 1082C640 1082 740 940 810 822Z',
  // The loop's visual centre and size in artwork units: the device is placed by this centre.
  loopCx: 806,
  loopCy: 825,
  loopW: 978, // 317 .. 1295
  stroke: 64, // the loader's stroke weight in artwork units
};

/**
 * The device: Orobi's infinity loop as a stroke. Placed by its centre at (x, y) with width w px.
 * draw: 0..1 of the stroke drawn (the birth); spin: 0..1 phase of the travelling dash (working);
 * dash: 0..1 how much of it is the working dash (0 = a whole calm loop).
 */
export const Loop: React.FC<{
  x: number;
  y: number;
  w: number;
  draw?: number;
  spin?: number;
  dash?: number;
  color?: string;
  track?: string;
  weight?: number; // stroke weight multiplier
  o?: number;
}> = ({ x, y, w, draw = 1, spin = 0, dash = 0, color = C.accent, track, weight = 1, o = 1 }) => {
  const k = w / GEO.loopW;
  const vb = GEO.viewBox;
  const sw = GEO.stroke * weight;
  // A working loader shows a 30% segment travelling round a faint track; a calm loop is whole.
  const seg = 100 - 70 * dash;
  return (
    <svg
      width={vb.w}
      height={vb.h}
      viewBox={`${vb.x} ${vb.y} ${vb.w} ${vb.h}`}
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        overflow: 'visible',
        opacity: o,
        transformOrigin: '0 0',
        transform: `translate(${x - (GEO.loopCx - vb.x) * k}px, ${y - (GEO.loopCy - vb.y) * k}px) scale(${Math.max(1e-4, k)})`,
      }}
    >
      {track && dash > 0.001 && <path d={GEO.loop} fill="none" stroke={track} strokeWidth={sw} strokeLinecap="round" opacity={dash} />}
      <path
        d={GEO.loop}
        fill="none"
        stroke={color}
        strokeWidth={sw}
        strokeLinecap="round"
        strokeLinejoin="round"
        pathLength={100}
        strokeDasharray={draw >= 1 && dash <= 0.001 ? undefined : `${Math.max(0.001, Math.min(seg, 100 * draw))} ${draw < 1 ? 100 : Math.max(0.001, 100 - seg)}`}
        strokeDashoffset={-100 * spin}
      />
    </svg>
  );
};

/**
 * Orobi: body (peach on dark), magenta markings clipped to it, ink eyes. Placed by the loop centre,
 * so the device can become Orobi in place. body/marks/eyes are 0..1 reveals; blink 0..1 closes the eyes.
 */
export const Orobi: React.FC<{
  x: number;
  y: number;
  w: number; // width of the loop part in px (the same w as the Loop it grows from)
  body?: number;
  marks?: number;
  eyes?: number;
  blink?: number;
  gx?: number; // gaze offset in artwork units
  gy?: number;
  id?: string;
  bodyColor?: string;
  eyeColor?: string;
}> = ({ x, y, w, body = 1, marks = 1, eyes = 1, blink = 0, gx = 0, gy = 0, id = 'orobi', bodyColor = C.peach, eyeColor = C.orobiInk }) => {
  const k = w / GEO.loopW;
  const vb = GEO.viewBox;
  return (
    <svg
      width={vb.w}
      height={vb.h}
      viewBox={`${vb.x} ${vb.y} ${vb.w} ${vb.h}`}
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        overflow: 'visible',
        transformOrigin: '0 0',
        transform: `translate(${x - (GEO.loopCx - vb.x) * k}px, ${y - (GEO.loopCy - vb.y) * k}px) scale(${Math.max(1e-4, k)})`,
      }}
    >
      <defs>
        <clipPath id={`${id}-clip`}>
          <path d={GEO.body} clipRule="evenodd" />
        </clipPath>
      </defs>
      <path d={GEO.body} fillRule="evenodd" fill={bodyColor} opacity={body} />
      <g clipPath={`url(#${id}-clip)`} opacity={marks}>
        <path d={GEO.markL} fill={C.accent} />
        <path d={GEO.markR} fill={C.accent} />
      </g>
      {GEO.eyes.map(([cx, cy], i) => (
        <ellipse key={i} cx={cx + gx} cy={cy + gy} rx={GEO.eyeRx} ry={Math.max(0.6, GEO.eyeRy * (1 - blink))} fill={eyeColor} opacity={eyes} />
      ))}
    </svg>
  );
};

/** Transform that places an element's top-left at (x, y) scaled by k (transform only: no relayout, no snapping). */
export const place = (x: number, y: number, k = 1): React.CSSProperties => ({
  position: 'absolute',
  left: 0,
  top: 0,
  transformOrigin: '0 0',
  transform: `translate(${x}px, ${y}px) scale(${Math.max(1e-4, k)})`,
});
