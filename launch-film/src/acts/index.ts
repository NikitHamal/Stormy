import type React from 'react';
import { ACT } from '../timeline';
import { Build } from './Build';
import { Change } from './Change';
import { End } from './End';
import { Fly } from './Fly';
import { Free } from './Free';
import { Live } from './Live';
import { Pile } from './Pile';
import { Ship } from './Ship';
import { Turn } from './Turn';

/** The acts in film order: the Nth entry is composition ActN (scripts/layout-audit.sh relies on it). */
export const ACTS: { key: keyof typeof ACT; Act: React.FC }[] = [
  { key: 'pile', Act: Pile },
  { key: 'turn', Act: Turn },
  { key: 'build', Act: Build },
  { key: 'live', Act: Live },
  { key: 'change', Act: Change },
  { key: 'fly', Act: Fly },
  { key: 'free', Act: Free },
  { key: 'ship', Act: Ship },
  { key: 'end', Act: End },
];
