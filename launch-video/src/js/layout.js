// Format-specific geometry (stage pixels). Scenes never hard-code positions; they read these.
// Landscape and vertical share the same scenes, only the layout differs.
export function layout(W, H) {
  const V = H > W;
  const pad = V ? 90 : 150;
  return {
    V, W, H, pad,
    wake: V ? { cx: 540, cy: 900, w: 760 } : { cx: 960, cy: 540, w: 640 },
    describe: V
      ? { orobi: { cx: 540, cy: 860, w: 520 }, pill: { cx: 540, cy: 1190, w: 940, h: 112 },
          head: { x: 0, y: 250, w: W, align: 'center', size: 128 }, think: { cx: 540, cy: 380, w: 340 } }
      : { orobi: { cx: 1300, cy: 410, w: 380 }, pill: { cx: 1300, cy: 690, w: 780, h: 104 },
          head: { x: pad, y: 380, w: 760, align: 'left', size: 132 }, think: { cx: 760, cy: 560, w: 360 } },
    phone: V ? { cx: 540, cy: 1190, h: 1240 } : { cx: 1330, cy: 540, h: 900 },
    live: V
      ? { head: { x: 0, y: 150, w: W, align: 'center', size: 112 }, peek: { cx: 900, ground: 1790, w: 300 },
          panel: { cx: 540, cy: 1130, w: 960 } }
      : { head: { x: pad, y: 390, w: 760, align: 'left', size: 116 }, peek: { cx: 1070, ground: 978, w: 280 },
          panel: { cx: 1370, cy: 540, w: 760 } },
    models: V
      ? { head: { x: 0, y: 190, w: W, align: 'center', size: 132 }, c: { x: 540, y: 1250 }, w: 420,
          rings: [{ rx: 300, ry: 170 }, { rx: 390, ry: 300 }] }
      : { head: { x: pad, y: 300, w: 760, align: 'left', size: 132 }, c: { x: 1380, y: 540 }, w: 320,
          rings: [{ rx: 320, ry: 128 }, { rx: 430, ry: 250 }] },
    end: V
      ? { orobi: { cx: 540, cy: 820, w: 540 }, word: { cx: 540, cy: 1180, size: 240 }, tag: { cy: 1360, size: 46 }, plat: { cy: 1480 } }
      : { orobi: { cx: 700, cy: 455, w: 380 }, word: { x: 940, cy: 455, size: 250 }, tag: { cy: 690, size: 46 }, plat: { cy: 800 } },
  };
}
