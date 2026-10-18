import React from 'react';
import { AbsoluteFill } from 'remotion';
import { Orobi, Loop } from './brand/Mark';
import { C, typeStyle, U } from './brand/tokens';
import { FontGate } from './lib/FontGate';
import { H, W } from './timeline';

// Style stills: the mark at 16 px, at full size and across sizes (npm run stills).
export const MarkStill: React.FC<{ size: number; surface: 'paper' | 'ink' }> = ({ size }) => (
  <AbsoluteFill style={{ background: C.paper }}>
    <Orobi x={size === 16 ? 8 : W / 2} y={size === 16 ? 8.4 : H / 2} w={size === 16 ? 14 : size} id="still" />
  </AbsoluteFill>
);

export const MarkSizes: React.FC = () => (
  <AbsoluteFill style={{ background: C.paper }}>
    {[16, 24, 32, 48, 64, 128, 256].map((s, i, all) => {
      const x0 = 160 + all.slice(0, i).reduce((a, b) => a + b + 60, 0);
      return <Orobi key={s} x={x0 + s / 2} y={H / 2} w={s * U} id={`s${s}`} />;
    })}
    <Loop x={W / 2} y={H * 0.78} w={260 * U} />
  </AbsoluteFill>
);

export const Lockup: React.FC = () => (
  <FontGate>
    <AbsoluteFill style={{ background: C.paper }}>
      <Orobi x={W / 2 - 260} y={H / 2} w={300} id="lock" />
      <div style={{ ...typeStyle('hero'), color: C.peach, position: 'absolute', left: W / 2 - 60, top: H / 2 - 120 }}>Oro</div>
    </AbsoluteFill>
  </FontGate>
);
