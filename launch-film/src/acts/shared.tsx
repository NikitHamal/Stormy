import React from 'react';
import { AbsoluteFill } from 'remotion';
import { C, SAFE, TYPE, typeStyle, U } from '../brand/tokens';
import { Chain } from '../fx/Chain';
import { Words } from '../fx/Words';
import { clamp, E, pace, prog } from '../lib/anim';
import { mixColor } from '../lib/color';
import { measureTracked } from '../lib/measure';
import { chainOf, copy, f60, H, W } from '../timeline';



/** The stage every act draws on. */
export const Stage: React.FC<{ children: React.ReactNode; bg?: string }> = ({ children, bg = C.paper }) => (
  <AbsoluteFill style={{ background: bg, overflow: 'hidden' }}>{children}</AbsoluteFill>
);

/** Copy sits on the lower third, centred: one eye line for the whole film. */
export const EYE = H > W ? H * 0.7 : H * 0.79; // 9:16 keeps copy above the feed's caption zone

/** How much a COPY entry (or chain) is on screen at absolute frame F: 0..1, eased in and out. */
export const onScreen = (F: number, ids: string[]) => {
  let v = 0;
  for (const id of ids) {
    const list = id.includes('*') ? chainOf(id.replace('*', '')) : [copy(id)];
    for (const c of list) {
      const a = prog(F, c.in - f60(10), c.in + f60(10), E.smooth);
      const b = 1 - prog(F, c.out, c.out + f60(pace.exitDur + 10), E.smooth);
      v = Math.max(v, Math.min(a, b));
    }
  }
  return clamp(v);
};

/**
 * Text and the product take turns: while copy is read, the product steps back (dims), so the
 * words sit on the stage, never on a busy UI. Multiply the product layer's opacity by this.
 */
export const dimFor = (F: number, ids: string[], depth = 0.62) => 1 - depth * onScreen(F, ids);

/** The role's size, shrunk only if the text would leave the safe width (narrow frames). */
export const fitTo = (text: string, size: number, t: { weight: number; track: number }) => {
  const w = measureTracked(text, size, t.weight, t.track) - t.track * size;
  return Math.min(size, (size * (W - 2 * SAFE.x)) / Math.max(1, w));
};

/** A single COPY line on the eye line, centred, revealed by Words with the film's pace. */
export const CopyLine: React.FC<{ id: string; F: number; role?: keyof typeof TYPE; y?: number; color?: string; accent?: boolean }> = ({ id, F, role = 'line', y = EYE, color = C.ink, accent = true }) => {
  const c = copy(id);
  const t = TYPE[role];
  const size = fitTo(c.text, t.size * U, t);
  const w = measureTracked(c.text, size, t.weight, t.track) - t.track * size;
  const relax = accent ? prog(F, c.resolved, c.resolved + f60(pace.accentDur), E.smooth) : 1;
  return (
    <div style={{ position: 'absolute', left: 0, top: 0, transform: `translate(${(W / 2 - w / 2).toFixed(3)}px, ${(y - (size * t.lead) / 2).toFixed(3)}px)` }}>
      <Words text={c.text} id={c.id} frame={F} start={c.in} out={c.out >= 1e9 ? undefined : c.out} size={size} style={{ ...typeStyle(role, size), color: mixColor(C.accent, color, relax) }} />
    </div>
  );
};

/** A chain on the eye line (fx/Chain), centred. */
export const CopyChain: React.FC<{ chain: string; F: number; role?: keyof typeof TYPE; y?: number }> = ({ chain, F, role = 'line', y = EYE }) => {
  const t = TYPE[role];
  const size = fitTo(chainOf(chain).map((c) => c.text).join(' '), t.size * U, t);
  return <Chain chain={chain} frame={F} from={0} role={role} size={size} top={y - (size * t.lead) / 2} />;
};
