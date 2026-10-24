import React from 'react';
import { useCurrentFrame } from 'remotion';
import { Loop } from '../brand/Mark';
import { C } from '../brand/tokens';
import { MODELS, PROJECT, PROMPT } from '../data';
import { clamp, E, fd, hitPulse, lmix, mix, prog } from '../lib/anim';
import { camera, lc, toScreen } from '../lib/camera';
import { ACT, CUE, f60, H, W } from '../timeline';
import { AppScreen, Composer, ModelChip } from '../ui/kit';
import { LOOP_IN_BUTTON, S_REST, S_TYPE, SEND } from './app';
import { PileLayer } from './Pile';
import { CopyChain, dimFor, Stage } from './shared';

// ACT 2 - the turn. The pile implodes into one point on the downbeat (E.in), the point draws itself
// into Orobi's infinity loop (the device's birth: the signature move), "Or" / "one sentence." is
// read in the quiet, then the app rises and the loop glides into the composer's send button. The
// prompt types (product UI) under a push-in.

/** Where the loop is born and waits (screen px). */
export const LOOP0 = { x: W / 2, y: H * 0.41, w: Math.min(560, W * 0.5) };
/** The loop's glide into the send button: from CUE.rise to CUE.dock. */
export const DOCK = { from: CUE.rise, to: CUE.dock + f60(12), ease: E.glide };

export const typedAt = (F: number) => {
  const n = PROMPT.length;
  const t = clamp((F - CUE.typeFrom) / (CUE.typeTo - CUE.typeFrom));
  return PROMPT.slice(0, fd(n * t)); // even typing, about 24 characters a second
};

export const Turn: React.FC = () => {
  const f = useCurrentFrame();
  const F = f + ACT.turn.from;

  // implosion: everything falls into the point where the loop is born
  const imp = prog(F, CUE.implode - 1, CUE.implode + f60(18), E.in);

  // the device: seed dot, then the stroke draws itself, then it waits, turns once, and docks
  const draw = prog(F, CUE.born, CUE.born + f60(40), E.smooth); // the stroke draws itself evenly: a signature move, never a pop
  const turn = prog(F, CUE.spin, CUE.spin + f60(30), E.smooth);
  const dash = Math.sin(Math.PI * turn);
  // alive while it waits: a slow push in (log space) and a gentle breath, until it docks
  const breathe = lmix(1, 1.14, prog(F, CUE.born, CUE.rise, E.linear)) * (1 + 0.012 * Math.sin(((F - CUE.born) / 60) * Math.PI));

  // the app rises from below on E.out, with the camera at rest framing the composer
  const up = prog(F, CUE.rise - f60(6), CUE.rise + f60(30), E.out);
  const push = prog(F, CUE.typeFrom, CUE.typeTo + f60(6), E.cam);
  const shot = lc(S_REST, S_TYPE, push);
  const appShot = { ...shot, sy: shot.sy + (1 - up) * 820 };

  const dock = prog(F, DOCK.from, DOCK.to, DOCK.ease);
  const send = toScreen(appShot, SEND);
  const lx = mix(LOOP0.x, send.x, dock);
  const ly = mix(LOOP0.y, send.y, dock);
  const lw = lmix(LOOP0.w * breathe, LOOP_IN_BUTTON * shot.k, dock);
  const landed = F >= DOCK.to;
  const pulse = hitPulse(F - DOCK.to, 2, 5);

  const typed = typedAt(F);
  const caret = F >= CUE.typeFrom - f60(30) && Math.floor((F - CUE.typeFrom) / 18) % 2 === 0;
  const dim = dimFor(F, ['turn*'], 0.5);

  return (
    <Stage>
      {imp < 1 && <PileLayer F={F} implode={imp} to={LOOP0} />}
      {up > 0 && (
        <div style={{ ...camera(appShot), opacity: dim }}>
          <AppScreen title={PROJECT} composer={<Composer text={typed} caret={caret && typed.length < PROMPT.length + 1} model={<ModelChip name={MODELS[0]} />} sendLit={landed ? 1 : 0} />} />
        </div>
      )}
      {F >= CUE.born - 1 && (
        <div style={{ position: 'absolute', inset: 0, transform: landed ? undefined : undefined }}>
          <Loop x={lx} y={ly} w={lw * (1 + 0.06 * pulse)} draw={Math.max(0.02, draw)} dash={dash * 0.8} spin={turn} track={C.surface3} color={landed ? C.accent : C.accent} />
        </div>
      )}
      <CopyChain chain="turn" F={F} role="display" />
    </Stage>
  );
};
