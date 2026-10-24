import React from 'react';
import { C, FONT, typeStyle } from '../brand/tokens';
import { Loop } from '../brand/Mark';
import { clamp, rand } from '../lib/anim';
import { mixColor } from '../lib/color';

// Oro's product UI, drawn from the app's own components (dark theme from config palette.dark,
// ToolCard rows, the composer, DeploySheet). Static layout; acts animate wrappers by transform.

export const ui = (size: number, weight = 500, color: string = C.text): React.CSSProperties => ({
  fontFamily: FONT.text,
  fontSize: size,
  fontWeight: weight,
  color,
  lineHeight: 1.3,
  whiteSpace: 'pre',
  fontVariantNumeric: 'tabular-nums',
});

const abs = (x: number, y: number, w?: number, h?: number): React.CSSProperties => ({
  position: 'absolute',
  left: 0,
  top: 0,
  width: w,
  height: h,
  transform: `translate(${x}px, ${y}px)`,
});

/** Line glyphs for the product chrome (drawn, not stock icons): 24-unit box. */
export const Glyph: React.FC<{ name: string; size: number; color?: string; sw?: number }> = ({ name, size, color = C.muted, sw = 2 }) => {
  const p: Record<string, React.ReactNode> = {
    back: <path d="M15 5 L8 12 L15 19" />,
    read: <><path d="M5 4 H14 L19 9 V20 H5 Z" /><path d="M14 4 V9 H19" /></>,
    write: <><path d="M5 19 L5.5 15 L15 5.5 L18.5 9 L9 18.5 Z" /><path d="M13 7.5 L16.5 11" /></>,
    eye: <><path d="M2.5 12 C5 7 8.5 5 12 5 C15.5 5 19 7 21.5 12 C19 17 15.5 19 12 19 C8.5 19 5 17 2.5 12 Z" /><circle cx="12" cy="12" r="3" /></>,
    check: <path d="M5 12.5 L10 17 L19 7" />,
    chev: <path d="M9 6 L15 12 L9 18" />,
    photo: <><rect x="3.5" y="5" width="17" height="14" rx="2.5" /><path d="M3.5 16 L9 11 L14 15.5 L16.5 13.5 L20.5 17" /></>,
    mic: <><rect x="9" y="3.5" width="6" height="11" rx="3" /><path d="M5.5 11.5 C5.5 15.5 8.5 18 12 18 C15.5 18 18.5 15.5 18.5 11.5 M12 18 V21" /></>,
    tune: <><path d="M4 7 H14 M18 7 H20 M4 17 H8 M12 17 H20" /><circle cx="16" cy="7" r="2" /><circle cx="10" cy="17" r="2" /></>,
    globe: <><circle cx="12" cy="12" r="8.5" /><path d="M3.5 12 H20.5 M12 3.5 C9 7 9 17 12 20.5 C15 17 15 7 12 3.5" /></>,
    up: <path d="M12 19 V6 M6.5 11.5 L12 6 L17.5 11.5" />,
    dot: <circle cx="12" cy="12" r="3" fill={color} />,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ display: 'block' }}>
      {p[name]}
    </svg>
  );
};

// ------------------------------------------------------------------ app chrome

export const APP = { w: 720, h: 1480, r: 64, top: 0 };
/** World y positions inside the app screen (the layout every act frames). */
export const APPY = { bar: 64, tabs: 160, chat: 270, composer: 1236 };

/** The app screen: dark rounded glass with the top bar and the Chat/Editor tabs. Children are the chat. */
/** The phone around the app: a thin bezel and the status bar, so the screen reads as a phone at a glance. */
export const BEZEL = 16;
export const AppScreen: React.FC<{ title: string; children?: React.ReactNode; composer?: React.ReactNode }> = ({ title, children, composer }) => (
  <>
  <div style={{ ...abs(-BEZEL, -BEZEL, APP.w + 2 * BEZEL, APP.h + 2 * BEZEL), borderRadius: APP.r + BEZEL, background: C.surface2, border: `2px solid ${C.surface3}`, boxShadow: '0 40px 120px rgba(0,0,0,.5)', boxSizing: 'border-box' }} />
  <div style={{ ...abs(0, 0, APP.w, APP.h), borderRadius: APP.r, background: C.app, overflow: 'hidden' }}>
    <div data-ui style={{ ...abs(44, 16), ...ui(22, 600, C.text) }}>9:41</div>
    <div style={{ ...abs(APP.w / 2 - 60, 14, 120, 30), borderRadius: 15, background: C.paper }} />
    <div style={{ ...abs(APP.w - 120, 22, 76, 18), display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center' }}>
      <div style={{ width: 22, height: 12, borderRadius: 3, background: C.text, opacity: 0.85 }} />
      <div style={{ width: 12, height: 12, borderRadius: 6, background: C.text, opacity: 0.85 }} />
      <div style={{ width: 30, height: 14, borderRadius: 4, border: `2px solid ${C.text}`, opacity: 0.85, boxSizing: 'border-box' }} />
    </div>
    <div style={{ ...abs(28, APPY.bar) }}><Glyph name="back" size={40} color={C.text} sw={2.4} /></div>
    <div data-ui style={{ ...abs(88, APPY.bar - 2), ...ui(34, 600) }}>{title}</div>
    <div style={{ ...abs(28, APPY.tabs, 664, 76), display: 'flex', gap: 12 }}>
      <div data-ui style={{ flex: '0 0 auto', width: 300, height: 76, borderRadius: 38, background: C.text, ...ui(28, 600, C.app), display: 'flex', alignItems: 'center', justifyContent: 'center' }}>Chat</div>
      <div data-ui style={{ flex: '0 0 auto', width: 300, height: 76, borderRadius: 38, background: C.surface2, ...ui(28, 500, C.muted), display: 'flex', alignItems: 'center', justifyContent: 'center' }}>Editor</div>
    </div>
    {children}
    {composer}
  </div>
  </>
);

/** The composer: input, photo + tune buttons, the model chip, mic, and the send button that holds the loop. */
export const COMPOSER = { x: 24, y: APPY.composer, w: 672, h: 212, send: { x: 24 + 672 - 70, y: APPY.composer + 160, d: 84 } };
export const Composer: React.FC<{ text: string; caret?: boolean; placeholder?: string; model: React.ReactNode; sendLit?: number; textOpacity?: number }> = ({ text, caret, placeholder = 'Describe a website…', model, sendLit = 0, textOpacity = 1 }) => {
  const { x, y, w, h, send } = COMPOSER;
  return (
    <div style={{ ...abs(x, y, w, h), borderRadius: 40, background: C.surface, border: `2px solid ${C.outline}` }}>
      <div data-ui style={{ ...abs(28, 26, w - 56), ...ui(28, 500, text ? C.text : C.muted), whiteSpace: 'nowrap', overflow: 'hidden', opacity: textOpacity }}>
        {text || placeholder}
        {caret && <span style={{ display: 'inline-block', width: 3, height: 32, background: C.accent, marginLeft: 3, transform: 'translateY(6px)' }} />}
      </div>
      <div style={{ ...abs(26, 124) }}><Glyph name="photo" size={40} /></div>
      <div style={{ ...abs(90, 124) }}><Glyph name="tune" size={40} /></div>
      <div style={{ ...abs(154, 116) }}>{model}</div>
      <div style={{ ...abs(w - 170, 124) }}><Glyph name="mic" size={40} /></div>
      <div style={{ ...abs(send.x - x - send.d / 2, send.y - y - send.d / 2, send.d, send.d), borderRadius: send.d / 2, background: mixColor(C.surface3, C.text, sendLit) }} />
    </div>
  );
};

/** The model chip in the composer. `name` is the model; the loop icon is drawn by the act (the device). */
export const ModelChip: React.FC<{ name: string; w?: number }> = ({ name, w = 300 }) => (
  <div style={{ width: w, height: 58, borderRadius: 29, background: C.surface2, display: 'flex', alignItems: 'center', paddingLeft: 58, boxSizing: 'border-box' }}>
    <div data-ui style={{ ...ui(24, 500, C.text), overflow: 'hidden', textOverflow: 'ellipsis' }}>{name}</div>
  </div>
);

/** The user's message bubble. */
export const UserBubble: React.FC<{ text: string; x: number; y: number; w: number }> = ({ text, x, y, w }) => (
  <div style={{ ...abs(x, y, w), borderRadius: 32, background: C.text, padding: '22px 28px', boxSizing: 'border-box' }}>
    <div data-ui style={{ ...ui(26, 500, C.app), whiteSpace: 'normal' }}>{text}</div>
  </div>
);

// ------------------------------------------------------------------ tool rows

export const ROW = { h: 72, gap: 6, tile: 48 };
/**
 * One tool row as the app draws it (ToolCard): a neutral tile with the glyph (or the working loop,
 * drawn by the act), the verb in normal weight, the file in semibold, +N in green, a chevron.
 * done: 0..1 (the check arrives: the tile glyph swaps to a check, +N fades in).
 */
export const ToolRow: React.FC<{ verb: string; file: string; add?: number; icon: string; done: number; ink?: number }> = ({ verb, file, add, icon, done, ink = 1 }) => (
  <div style={{ position: 'relative', width: 640, height: ROW.h, display: 'flex', alignItems: 'center' }}>
    <div style={{ width: ROW.tile, height: ROW.tile, borderRadius: 14, background: C.surface2, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: '0 0 auto' }}>
      {done >= 0.5 ? <Glyph name="check" size={28} color={C.ok} sw={2.6} /> : done < 0 ? <Glyph name={icon} size={26} /> : null}
    </div>
    <div data-ui style={{ ...ui(28, 400, C.muted), marginLeft: 22 }}>{verb}</div>
    <div data-ui style={{ ...ui(28, 600, mixColor(C.muted, C.text, ink)), marginLeft: 10 }}>{file}</div>
    <div style={{ flex: 1 }} />
    {add ? <div data-ui style={{ ...ui(26, 600, C.ok), opacity: clamp(done * 2 - 1) }}>+{add}</div> : null}
    <div style={{ marginLeft: 10, opacity: clamp(done * 2 - 1) }}><Glyph name="chev" size={26} color={C.outline} /></div>
  </div>
);

// ------------------------------------------------------------------ Mira's site

export const PAGE = { w: 1440, nav: 120, heroH: 640, tile: 24 };

type Theme = { bg: string; surface: string; text: string; muted: string; line: string };
export const THEME: Record<'dark' | 'light', Theme> = {
  dark: { bg: C.app, surface: C.surface, text: C.text, muted: C.muted, line: C.outline },
  light: { bg: C.lbg, surface: C.lsurface, text: C.ltext, muted: C.lmuted, line: C.loutline },
};

/** A night street photograph, as flat fills (no gradients): sky, a row of buildings, lit windows, a figure. */
export const Photo: React.FC<{ seed: number; w: number; h: number; radius?: number }> = ({ seed, w, h, radius = 18 }) => {
  const r = (k: number | string) => rand(`${seed}:${k}`);
  const skies = [C.sky1, C.sky2, C.sky3];
  const blds = [C.bld1, C.bld2, C.bld3];
  const n = 5 + Math.floor(r(0) * 3);
  const items: React.ReactNode[] = [];
  let x = -10;
  for (let i = 0; i < n; i++) {
    const bw = 60 + r(10 + i) * 120;
    const bh = 140 + r(20 + i) * 240;
    const fill = blds[Math.floor(r(30 + i) * blds.length)];
    items.push(<rect key={`b${i}`} x={x} y={400 - bh} width={bw} height={bh + 10} fill={fill} />);
    for (let wy = 400 - bh + 22; wy < 380; wy += 34) {
      for (let wx = x + 14; wx < x + bw - 16; wx += 26) {
        if (r(`${i}:${wx}:${wy}`) > 0.72) items.push(<rect key={`w${i}${wx}${wy}`} x={wx} y={wy} width={10} height={14} rx={2} fill={C.lamp} opacity={0.85} />);
      }
    }
    x += bw + 4;
  }
  const fx = 120 + r(50) * 340;
  return (
    <svg width={w} height={h} viewBox="0 0 600 400" preserveAspectRatio="xMidYMid slice" style={{ display: 'block', borderRadius: radius }}>
      <rect width="600" height="400" fill={skies[Math.floor(r(1) * skies.length)]} />
      <circle cx={80 + r(2) * 440} cy={70 + r(3) * 60} r={18 + r(4) * 14} fill={C.steel} opacity={0.55} />
      {items}
      <rect y="360" width="600" height="40" fill={C.bld1} />
      <rect x={fx} y="300" width="16" height="64" rx="8" fill={C.bld1} />
      <circle cx={fx + 8} cy="292" r="11" fill={C.bld1} />
      <rect x={fx + 120} y="250" width="6" height="112" fill={C.bld2} />
      <circle cx={fx + 123} cy="248" r="10" fill={C.lamp} />
    </svg>
  );
};

/**
 * Mira's portfolio at desktop width. reveal (0..1 per section) drives the live build; cols and
 * theme come from the UI block. Height grows with the gallery.
 */
export const siteHeight = (cols: number) => PAGE.nav + PAGE.heroH + Math.ceil(8 / cols) * (GAL_H(cols) + PAGE.tile) + 220;
const GAL_H = (cols: number) => Math.round(((PAGE.w - 192 - PAGE.tile * (cols - 1)) / cols) * 0.68);

export const Site: React.FC<{
  theme: 'dark' | 'light';
  cols: number; // may be fractional during a drag: the layout uses the nearest whole count
  reveal?: { nav?: number; hero?: number; tiles?: number[]; foot?: number };
  data: { name: string; nav: string[]; title: string[]; sub: string };
}> = ({ theme, cols, reveal = {}, data }) => {
  const T = THEME[theme];
  const c = Math.max(1, Math.round(cols));
  const tw = Math.round((PAGE.w - 192 - PAGE.tile * (c - 1)) / c);
  const th = GAL_H(c);
  const rv = (v: number | undefined) => (v === undefined ? 1 : clamp(v));
  const sec = (v: number | undefined): React.CSSProperties => ({ opacity: rv(v), transform: `translateY(${(1 - rv(v)) * 40}px)` });
  const tiles = reveal.tiles;
  return (
    <div style={{ position: 'relative', width: PAGE.w, height: siteHeight(c), background: T.bg, overflow: 'hidden' }}>
      <div style={abs(96, 0, PAGE.w - 192, PAGE.nav)}><div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', ...sec(reveal.nav) }}>
        <div data-ui style={ui(30, 600, T.text)}>{data.name}</div>
        <div style={{ flex: 1 }} />
        {data.nav.map((n) => (
          <div key={n} data-ui style={{ ...ui(24, 500, T.muted), marginLeft: 44 }}>{n}</div>
        ))}
      </div></div>
      <div style={abs(96, PAGE.nav + 70, PAGE.w - 192)}><div style={{ ...sec(reveal.hero), display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        {data.title.map((t) => (
          <div key={t} data-ui style={{ ...typeStyle('hero', 150), color: T.text }}>{t}</div>
        ))}
        <div data-ui style={{ ...ui(30, 400, T.muted), marginTop: 30 }}>{data.sub}</div>
        <div style={{ marginTop: 44, width: 236, height: 72, borderRadius: 36, background: T.text, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div data-ui style={ui(24, 600, T.bg)}>View work</div>
        </div>
      </div></div>
      {Array.from({ length: 8 }).map((_, i) => {
        const col = i % c;
        const row = Math.floor(i / c);
        const v = tiles ? rv(tiles[i]) : 1;
        return (
          <div key={i} style={{ ...abs(96 + col * (tw + PAGE.tile), PAGE.nav + PAGE.heroH + row * (th + PAGE.tile), tw, th), opacity: v }}>
            <div style={{ transform: `translateY(${(1 - v) * 36}px) scale(${0.96 + 0.04 * v})`, transformOrigin: '50% 100%' }}>
              <Photo seed={i + 3} w={tw} h={th} />
            </div>
          </div>
        );
      })}
      <div style={abs(96, siteHeight(c) - 150, PAGE.w - 192, 60)}>
        <div style={sec(reveal.foot)}>
          <div style={{ height: 2, background: T.line }} />
          <div data-ui style={{ ...ui(24, 500, T.muted), marginTop: 38 }}>{`© ${data.name}`}</div>
        </div>
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ the UI block (A2UI)

/** Orobi's generative-UI answer: a panel with a columns slider (and the photo width it drives) and a theme choice. */
export const UIBlock: React.FC<{ title: string; cols: number; colsFrom: number; colsTo: number; widthFor: (c: number) => number; lit: number; thumb: number }> = ({ title, cols, colsFrom, colsTo, widthFor, lit, thumb }) => {
  const c = Math.round(cols);
  const t = clamp((colsFrom - cols) / (colsFrom - colsTo));
  const track = 520;
  return (
    <div style={{ width: 640, borderRadius: 32, background: C.surface, border: `2px solid ${C.outline}`, padding: 32, boxSizing: 'border-box' }}>
      <div data-ui style={ui(30, 600)}>{title}</div>
      <div style={{ display: 'flex', marginTop: 24, alignItems: 'baseline' }}>
        <div data-ui style={ui(26, 400, C.muted)}>Columns</div>
        <div style={{ flex: 1 }} />
        <div data-ui style={ui(30, 600)}>{String(c)}</div>
      </div>
      <div style={{ position: 'relative', height: 44, marginTop: 10 }}>
        <div style={{ ...abs(0, 18, track, 8), borderRadius: 4, background: C.surface3 }} />
        <div style={{ ...abs(0, 18, track * (1 - t) + 16, 8), borderRadius: 4, background: C.text }} />
        <div style={{ ...abs(track * (1 - t) - 2, 4, 36, 36), borderRadius: 18, background: C.text, outline: `${(6 * thumb).toFixed(2)}px solid ${mixColor(C.surface, C.text, 0.18)}` }} />
      </div>
      <div style={{ display: 'flex', marginTop: 18, alignItems: 'baseline' }}>
        <div data-ui style={ui(26, 400, C.muted)}>Photo width</div>
        <div style={{ flex: 1 }} />
        <div data-ui style={ui(30, 600)}>{`${widthFor(c)} px`}</div>
      </div>
      <div style={{ display: 'flex', gap: 12, marginTop: 28 }}>
        {(['Dark', 'Light'] as const).map((m, i) => {
          const on = i === 0 ? 1 - lit : lit;
          return (
            <div key={m} style={{ flex: 1, height: 64, borderRadius: 32, background: mixColor(C.surface2, C.text, on), display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div data-ui style={ui(26, 600, mixColor(C.muted, C.app, on))}>{m}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ publish sheet

export const PublishSheet: React.FC<{ target: string; url: string; steps: string[]; step: number; done: number }> = ({ target, url, steps, step, done }) => (
  <div style={{ width: 720, minHeight: 700, borderRadius: '48px 48px 0 0', background: C.surface, borderTop: `2px solid ${C.outline}`, padding: '40px 40px 60px', boxSizing: 'border-box' }}>
    <div style={{ width: 80, height: 8, borderRadius: 4, background: C.surface3, margin: '-18px auto 30px' }} />
    <div data-ui style={ui(34, 600)}>{`Publish to ${target}`}</div>
    <div style={{ marginTop: 26 }}>
      {steps.map((s, i) => {
        const st = i < step ? 1 : i === step ? 0.5 : 0;
        return (
          <div key={s} style={{ display: 'flex', alignItems: 'center', height: 62 }}>
            <div style={{ width: 40, height: 40, borderRadius: 20, background: C.surface2, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {st >= 1 ? <Glyph name="check" size={24} color={C.ok} sw={2.8} /> : null}
            </div>
            <div data-ui style={{ ...ui(28, st >= 1 ? 500 : 400, st > 0 ? C.text : C.muted), marginLeft: 20 }}>{s}</div>
          </div>
        );
      })}
    </div>
    <div style={{ marginTop: 24, height: 96, borderRadius: 28, background: mixColor(C.surface2, C.text, done), display: 'flex', alignItems: 'center', padding: '0 28px' }}>
      <Glyph name="globe" size={36} color={mixColor(C.muted, C.app, done)} />
      <div data-ui style={{ ...ui(28, 600, mixColor(C.muted, C.app, done)), marginLeft: 18 }}>{url}</div>
    </div>
  </div>
);

// ------------------------------------------------------------------ the problem pile

export const PileCard: React.FC<{ title: string; body: string; tone: 'bad' | 'mute' | 'plain'; w: number }> = ({ title, body, tone, w }) => (
  <div style={{ width: w, borderRadius: 22, background: C.surface, border: `2px solid ${C.outline}`, boxShadow: '0 30px 80px rgba(0,0,0,.35)', overflow: 'hidden' }}>
    <div style={{ height: 52, background: C.surface2, display: 'flex', alignItems: 'center', padding: '0 20px', gap: 10 }}>
      {[0, 1, 2].map((i) => <div key={i} style={{ width: 14, height: 14, borderRadius: 7, background: C.surface3 }} />)}
      <div data-ui style={{ ...ui(22, 500, C.muted), marginLeft: 10 }}>{title}</div>
    </div>
    <div data-ui style={{ padding: '22px 24px 26px', ...(tone === 'bad' ? { ...ui(26, 400, C.bad), fontFamily: FONT.mono } : ui(26, 500, tone === 'mute' ? C.muted : C.text)) }}>{body}</div>
  </div>
);

/** A touch on a phone: a soft disc that tightens on press (no desktop arrow on a phone). */
export const Touch: React.FC<{ x: number; y: number; press?: number; o?: number }> = ({ x, y, press = 0, o = 1 }) => (
  <div style={{ position: 'absolute', left: 0, top: 0, opacity: o, transform: `translate(${x - 34}px, ${y - 34}px) scale(${1 - 0.18 * press})`, transformOrigin: '34px 34px' }}>
    <div style={{ width: 68, height: 68, borderRadius: 34, background: C.ink, opacity: 0.3, border: `2px solid ${C.ink}` }} />
  </div>
);

export { Loop };
