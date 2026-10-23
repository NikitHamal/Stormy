// Brand tokens: the only place a colour, face or type size is written down. Acts import these;
// scripts/lint-film.mjs fails any hex colour that is not declared here.
//
// Oro's own brand (core/designsystem OroBrand.kt and the app's dark theme). Its magenta and peach
// sit inside cinetic's banned regions; they are the brand's identity, recorded in BRIEF.md.
import type React from 'react';
import { safeInsets } from '../lib/safe';
import { H, W } from '../timeline';

export const C = {
  // Film roles (the starter's components read these): ink = type on this dark stage, paper = stage.
  ink: '#EEF1F6', // type on the ink-navy stage
  paper: '#090C17', // cinetic:brand-supplied ink-navy (the stage, tinted toward Oro's ink #1A1F4A; never pure black)
  stage2: '#10142A', // cinetic:brand-supplied ink-navy (the stage's raised layer)
  mute: '#8A93A3', // secondary text, 6.4:1 on the stage
  white: '#FFFFFF',
  // The one accent and what it means: "Orobi is doing it" / "just made by Orobi".
  accent: '#F800F8', // cinetic:brand-supplied magenta (Orobi's markings, the app's working colour)
  // Orobi
  peach: '#FDDFC6', // cinetic:brand-supplied peach (Orobi's body on dark; mascot only)
  orobiInk: '#1A1F4A', // cinetic:brand-supplied ink (Orobi's eyes on dark, its body on light)
  // The app's dark theme (config palette.dark), for the product UI
  app: '#0B0D12',
  surface: '#12151C',
  surface2: '#181C25',
  surface3: '#222834',
  outline: '#262C38',
  text: '#E8ECF2',
  muted: '#8A93A3',
  ok: '#3DDC97', // the app's success green: checks and +lines only
  bad: '#FF6B6B', // errors in the problem pile only (npm ERR!, failed): the old way
  // The app's light theme (the site after the flip)
  lbg: '#F7F8FA',
  lsurface: '#FFFFFF',
  ltext: '#0E1117',
  lmuted: '#5A6373',
  loutline: '#E2E5EA',
  // The street photographs on Mira's site: night city in cool blues and slate (flat fills, no gradients)
  sky1: '#1B2A4A',
  sky2: '#24365C',
  sky3: '#3A4E78',
  bld1: '#0E1424',
  bld2: '#162038',
  bld3: '#22304F',
  lamp: '#F2E27A', // window and street light: a clear pale yellow, never orange or cream
  teal: '#2FB7A8',
  steel: '#9FB3D1',
};

export const FONT = {
  text: '"Poppins", system-ui, sans-serif', // Oro's UI face (brand-supplied); typeStyle and measure.ts read it
  mono: '"JetBrains Mono Variable", "JetBrains Mono", ui-monospace, monospace',
};

/** Faces the FontGate waits for before anything renders or measures (one entry per weight in use). */
export const FACES = ['400 16px "Poppins"', '500 16px "Poppins"', '600 16px "Poppins"', '400 16px "JetBrains Mono Variable"'];

/** Layout unit: 1 at 1080p, scales every size with the short side of the frame (16:9, 9:16, 1:1). */
export const U = Math.min(W, H) / 1080;

// One token per role, reused everywhere (size in px at 1080p).
export const TYPE = {
  display: { size: 112, weight: 600, track: -0.035, lead: 1.0 }, // statements
  hero: { size: 220, weight: 600, track: -0.04, lead: 1.0 }, // the wordmark in the lockup
  line: { size: 76, weight: 500, track: -0.02, lead: 1.1 }, // fragment chains in open space
  secondary: { size: 48, weight: 500, track: -0.01, lead: 1.15 }, // the end line, URLs
  ui: { size: 26, weight: 500, track: 0, lead: 1.35 }, // product UI at 1x (camera scale lifts it)
};

/** CSS for a type role at a size (px). Tabular figures always, so numbers never jitter in width. */
export const typeStyle = (role: keyof typeof TYPE, size = TYPE[role].size * U): React.CSSProperties => ({
  fontFamily: FONT.text,
  fontSize: size,
  fontWeight: TYPE[role].weight,
  letterSpacing: `${TYPE[role].track}em`,
  lineHeight: TYPE[role].lead,
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'pre',
});

export const safeArea = (w: number, h: number) => {
  const i = safeInsets(undefined, w, h);
  return { x: Math.max(i.left, i.right), y: Math.max(i.top, i.bottom) };
};
/** The film's safe margins; text stays inside them (scripts/layout-audit.sh checks). */
export const SAFE = safeArea(W, H);
