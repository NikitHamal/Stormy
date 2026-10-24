// Format-specific geometry (stage pixels). Scenes never hard-code positions; they read these.
// Landscape and vertical share the same scenes, only the layout differs.
export function layout(W, H) {
  const V = H > W;
  const pad = V ? 90 : 150;
  return {
    V, W, H, pad,
    wake: V ? { cx: 540, cy: 900, w: 760 } : { cx: 960, cy: 540, w: 640 },
    describe: V
      ? { orobi: { cx: 540, cy: 900, w: 520 }, pill: { cx: 540, cy: 1250, w: 920, h: 120 },
          head: { x: 0, y: 300, w: W, align: 'center', size: 136 }, think: { cx: 540, cy: 372, w: 320 } }
      : { orobi: { cx: 1300, cy: 410, w: 380 }, pill: { cx: 1300, cy: 690, w: 780, h: 104 },
          head: { x: pad, y: 380, w: 760, align: 'left', size: 132 }, think: { cx: 760, cy: 560, w: 360 } },
    // Vertical: clear of the top ~8% and bottom ~15% that Reels/Shorts/TikTok cover with UI.
    phone: V ? { cx: 540, cy: 1062, h: 1100 } : { cx: 1330, cy: 540, h: 900 },
    live: V
      ? { head: { x: 0, y: 206, w: W, align: 'center', size: 116 }, peek: { cx: 892, ground: 1612, w: 300 },
          panel: { cx: 540, cy: 1090, w: 960 } }
      : { head: { x: pad, y: 390, w: 760, align: 'left', size: 116 }, peek: { cx: 1070, ground: 978, w: 280 },
          panel: { cx: 1370, cy: 540, w: 760 } },
    models: V
      ? { head: { x: 0, y: 220, w: W, align: 'center', size: 136 }, c: { x: 540, y: 935 }, w: 380,
          rings: [{ rx: 300, ry: 170 }, { rx: 390, ry: 300 }],
          // Vertical lays providers out as two labelled groups instead of an orbit:
          // an orbit around a 380px Orobi on a 1080px frame cannot keep 10 chips apart.
          grid: { top: 1146, rowH: 84, groupGap: 40, label: 50, colW: [330, 300] } }
      : { head: { x: pad, y: 300, w: 760, align: 'left', size: 132 }, c: { x: 1380, y: 540 }, w: 320,
          rings: [{ rx: 320, ry: 128 }, { rx: 430, ry: 250 }] },
    end: V
      ? { orobi: { cx: 540, cy: 800, w: 540 }, word: { cx: 540, cy: 1160, size: 250 }, tag: { cy: 1350, size: 50 }, plat: { cy: 1478 } }
      : { orobi: { cx: 700, cy: 455, w: 380 }, word: { x: 940, cy: 455, size: 250 }, tag: { cy: 690, size: 46 }, plat: { cy: 800 } },
  };
}
