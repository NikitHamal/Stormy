import React from 'react';
import { useCurrentFrame } from 'remotion';
import { Loop } from '../brand/Mark';
import { C } from '../brand/tokens';
import { MODELS, PROJECT, PROMPT, ROWS } from '../data';
import { arrive, E, lmix, mix, prog } from '../lib/anim';
import { camera, lc, toScreen } from '../lib/camera';
import { ACT, CUE, f60, H, W } from '../timeline';
import { AppScreen, Composer, ModelChip, ROW, ToolRow, UserBubble } from '../ui/kit';
import { LOOP_IN_BUTTON, S_ROWS, S_TYPE, SEND } from './app';
import { LiveSite, PILL, PreviewPill } from './Live';
import { CopyChain, dimFor, Stage } from './shared';

// ACT 3 - build. Send: the prompt lifts into the chat as the user's bubble and the loop leaves the
// send button for the first tool row's tile, where it spins (mi-spinner-to-check). Each row
// arrives (ui-row-cascade), its loop resolves to a green check as the file lands, and the loop
// relays down to the next row. On the last row the loop is the seed of the iris
// (tr-iris-from-glyph): the live site opens inside it, filling the frame on the bar-8 downbeat.

/** Row k: arrival and check frames (absolute). One step every 30 f. */
export const ROW_AT = ROWS.map((_, k) => ({ a: CUE.row1 + f60(30) * k, d: CUE.row1 + f60(30) * k + f60(24) }));
export const ROW_Y = (k: number) => 430 + k * (ROW.h + ROW.gap);
export const TILE = (k: number) => ({ x: 40 + ROW.tile / 2, y: ROW_Y(k) + ROW.h / 2 });
const IRIS = { from: CUE.iris, to: CUE.open };

export const Build: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.build.from;
  const pull = prog(F, CUE.send, CUE.send + f60(90), E.cam);
  // after the pull the camera keeps breathing in on the rows, so the reads are alive
  const drift = 1 + 0.06 * prog(F, CUE.send + f60(60), CUE.iris, E.linear);
  const shot0 = lc(S_TYPE, S_ROWS, pull);
  const shot = { ...shot0, k: shot0.k * drift };

  // the bubble lifts from the composer into the chat
  const lift = prog(F, CUE.send, CUE.send + f60(50), E.smooth);
  const bubble = { x: 140, y: mix(1262, 296, lift), w: 556 };

  // the device: send button -> row k tile while row k works -> next row; on the last row, the iris seed
  const k = ROW_AT.findIndex((r) => F < r.d);
  const cur = k < 0 ? ROWS.length - 1 : k;
  const prevPos = cur === 0 ? SEND : TILE(cur - 1);
  const start = cur === 0 ? CUE.send + f60(2) : ROW_AT[cur - 1].d;
  const hop = prog(F, start, start + f60(12), E.out);
  const at = { x: mix(prevPos.x, TILE(cur).x, hop), y: mix(prevPos.y, TILE(cur).y, hop) };
  const working = F >= ROW_AT[cur].a && F < ROW_AT[cur].d;
  const lastDone = F >= ROW_AT[ROWS.length - 1].d;
  const loopW = lmix(LOOP_IN_BUTTON, 40, cur === 0 ? hop : 1);
  const scr = toScreen(shot, at);

  // iris: radius in log space at a constant ratio, centre drifting to the frame centre
  const ir = prog(F, IRIS.from, IRIS.to, E.linear);
  const R = Math.hypot(W, H);
  const radius = F < IRIS.from ? 0 : lmix(10, R, ir);
  const cx = mix(scr.x, W / 2, prog(F, IRIS.from, IRIS.to, E.smooth));
  const cy = mix(scr.y, H / 2, prog(F, IRIS.from, IRIS.to, E.smooth));

  return (
    <Stage>
      <div style={{ ...camera(shot), opacity: dimFor(F, ['build*'], 0.35) }}>
        <AppScreen
          title={PROJECT}
          composer={<Composer text={PROMPT} textOpacity={1 - prog(F, CUE.send, CUE.send + f60(12), E.smooth)} model={<ModelChip name={MODELS[0]} />} sendLit={1 - prog(F, CUE.send, CUE.send + f60(10), E.smooth)} />}
        >
          <div style={{ opacity: prog(F, CUE.send, CUE.send + f60(10), E.smooth) }}>
            <UserBubble text={PROMPT} {...bubble} />
          </div>
          {ROWS.map((r, i) => {
            const t = ROW_AT[i];
            if (F < t.a - 1) return null;
            const p = prog(F, t.a, t.a + f60(12), E.out);
            const done = F >= t.d ? 1 : F >= t.a ? 0 : -1;
            const ink = prog(F, t.a + f60(16), t.a + f60(28), E.smooth);
            return (
              <div key={i} style={{ position: 'absolute', left: 0, top: 0, opacity: p, transform: `translate(40px, ${ROW_Y(i) + (1 - arrive(p)) * 10}px)` }}>
                <ToolRow verb={r.verb} file={r.file} add={r.add} icon={r.icon} done={done} ink={ink} />
              </div>
            );
          })}
        </AppScreen>
      </div>
      {F < IRIS.to && (
        <Loop x={scr.x} y={scr.y} w={loopW * shot.k * (lastDone ? 1 : 1)} dash={working ? 1 : 0} spin={(F % 48) / 48} track={working ? C.surface3 : undefined} o={1 - prog(F, IRIS.from, IRIS.from + f60(8), E.smooth)} />
      )}
      {radius > 0 && (
        <>
        <div style={{ position: 'absolute', left: 0, top: 0, width: 2 * radius, height: 2 * radius, borderRadius: radius, border: `3px solid ${C.accent}`, opacity: 1 - prog(F, IRIS.to - f60(6), IRIS.to, E.smooth), transform: `translate(${(cx - radius).toFixed(2)}px, ${(cy - radius).toFixed(2)}px)`, boxSizing: 'border-box' }} />
        <div style={{ position: 'absolute', inset: 0, clipPath: `circle(${radius.toFixed(2)}px at ${cx.toFixed(2)}px ${cy.toFixed(2)}px)`, background: C.app }}>
          <LiveSite F={F} />
          <PreviewPill F={F} o={prog(F, IRIS.to - f60(8), IRIS.to, E.smooth)} />
        </div>
        </>
      )}
      <CopyChain chain="build" F={F} role="display" />
    </Stage>
  );
};
void PILL;
