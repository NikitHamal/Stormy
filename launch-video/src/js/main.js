// Composition entry. Builds the stage for a format and exposes window.renderAt(t).
// Every frame is a pure function of t: no clocks, no CSS transitions/animations, no randomness.
import { box, css, attr, r2, px, clamp } from './core.js';
import { layout } from './layout.js';
import { createOrobi } from './orobi.js';
import { createPhone } from './phone.js';
import wake from './scenes/wake.js';
import describe from './scenes/describe.js';
import live from './scenes/live.js';
import models from './scenes/models.js';
import endcard from './scenes/endcard.js';

const SCENES = { wake, describe, live, models, endcard };
const params = new URLSearchParams(location.search);

async function boot() {
  const cfg = await (await fetch('../config.json', { cache: 'no-store' })).json();
  const format = params.get('format') || 'landscape';
  const { width: W, height: H } = cfg.formats[format];
  const P = cfg.palette;
  const root = document.documentElement;
  for (const [grp, cols] of Object.entries(P)) for (const [k, v] of Object.entries(cols)) root.style.setProperty(`--${grp}-${k}`, v);
  root.style.setProperty('--font-ui', `'${cfg.fonts.ui}'`);
  root.style.setProperty('--font-code', `'${cfg.fonts.code}'`);

  await Promise.all([400, 500, 600, 700].map((w) => document.fonts.load(`${w} 40px '${cfg.fonts.ui}'`)));
  await Promise.all([400, 500].map((w) => document.fonts.load(`${w} 40px '${cfg.fonts.code}'`)));
  await document.fonts.ready;

  const stage = document.getElementById('stage');
  css(stage, { width: px(W), height: px(H) });
  const world = box(stage, 'world', { width: px(W), height: px(H) });
  const factors = { bg: 0.35, type: 0.82, main: 1, fore: 1.18 };
  const layers = {};
  for (const k of Object.keys(factors)) layers[k] = box(world, 'layer layer-' + k, { width: px(W), height: px(H) });
  const dots = box(layers.bg, 'dots', { left: px(-W), top: px(-H), width: px(W * 3), height: px(H * 3) });

  const L = layout(W, H);
  stage.classList.add(L.V ? 'fmt-v' : 'fmt-l');
  const ctx = { cfg, W, H, P, L, layers, factors, stage, shared: {}, cam: {}, o: {}, bg: P.dark.bg };
  ctx.phone = createPhone(layers.main, P, cfg.scenes.find((s) => s.id === 'live')?.site || {}, {
    preview: cfg.scenes.find((s) => s.id === 'live')?.previewLabel || 'Preview',
    live: cfg.scenes.find((s) => s.id === 'live')?.liveLabel || 'Live',
  });
  ctx.orobi = createOrobi(layers.main, P);

  // Timeline from config (scene order + durations).
  let acc = 0;
  const timeline = cfg.scenes.map((sc) => {
    const mod = SCENES[sc.id];
    if (!mod) throw new Error(`Unknown scene id "${sc.id}" in config.json`);
    const dur = sc.duration ?? mod.design;
    const item = { sc, mod, start: acc, dur, R: null };
    acc += dur;
    return item;
  });
  for (const it of timeline) it.R = it.mod.build(ctx, it.sc) || { roots: [] };
  const total = cfg.meta.duration ?? acc;
  const whip = document.getElementById('whip-blur');

  function renderAt(t) {
    t = clamp(t, 0, total - 1e-6);
    let i = timeline.findIndex((it) => t < it.start + it.dur);
    if (i < 0) i = timeline.length - 1;
    const cur = timeline[i];
    // Local time in the scene's *design* seconds (scene durations in config retime proportionally).
    const lt = ((t - cur.start) / cur.dur) * cur.mod.design;
    const under = i > 0 && cur.mod.underlay && lt < cur.mod.underlay ? timeline[i - 1] : null;

    ctx.cam = { x: W / 2, y: H / 2, s: 1, blur: 0 };
    ctx.o = { opacity: 0, x: 0, y: 0, w: 100 };
    ctx.bg = P.dark.bg;
    ctx.phone.set({ cx: 0, cy: 0, h: 100, opacity: 0 });
    for (const it of timeline) for (const n of it.R.roots) css(n, { display: it === cur || it === under ? '' : 'none' });
    if (under) under.mod.render(under.mod.design, ctx, under.sc, under.R);
    cur.mod.render(lt, ctx, cur.sc, cur.R);

    // Camera with per-layer parallax.
    const { x, y, s, blur } = ctx.cam;
    for (const [k, f] of Object.entries(factors)) {
      const sf = 1 + (s - 1) * f;
      const cx = W / 2 + (x - W / 2) * f, cy = H / 2 + (y - H / 2) * f;
      css(layers[k], { transform: `translate(${r2(W / 2)}px, ${r2(H / 2)}px) scale(${r2(sf)}) translate(${r2(-cx)}px, ${r2(-cy)}px)` });
    }
    attr(whip, { stdDeviation: `${r2(blur)} ${r2(blur * 0.04)}` });
    css(world, { filter: blur > 0.5 ? 'url(#whip)' : 'none' });
    css(stage, { background: ctx.bg });
    const light = ctx.bg.toLowerCase() === P.light.bg.toLowerCase();
    css(dots, { opacity: light ? '0.5' : '1', backgroundImage: `radial-gradient(circle, ${light ? 'rgba(14,17,23,.10)' : 'rgba(138,147,163,.085)'} 1.3px, transparent 1.6px)` });
    ctx.orobi.set(ctx.o);
    return { scene: cur.sc.id, lt };
  }

  window.renderAt = renderAt;
  window.__comp = { W, H, fps: cfg.meta.fps, duration: total, format, cfg, timeline: timeline.map((t) => ({ id: t.sc.id, start: t.start, dur: t.dur })) };
  renderAt(0);
  window.__ready = true;
  if (params.get('preview')) import('./preview.js').then((m) => m.mountPreview(window.__comp, renderAt));
}

boot().catch((e) => { console.error(e); window.__error = String(e && e.stack || e); });
