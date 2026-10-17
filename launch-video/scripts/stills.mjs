// Export key frames as PNG.
//   npm run stills                         -> config.stills for landscape into out/stills/
//   node scripts/stills.mjs --format vertical --times 1,2.5,9
//   node scripts/stills.mjs --every 0.5     -> a frame every 0.5s (QA sweep)
//   node scripts/stills.mjs --poster        -> out/poster.png at meta.poster
import fs from 'node:fs';
import path from 'node:path';
import { ROOT, openComposition, arg, config } from './lib.mjs';

const cfg = config();
const format = arg('format', 'landscape');
const dir = path.join(ROOT, arg('dir', 'out/stills'));
let times = cfg.stills;
if (arg('times')) times = String(arg('times')).split(',').map(Number);
if (arg('every')) { const s = Number(arg('every')); times = []; for (let t = 0; t < cfg.meta.duration - 1e-9; t += s) times.push(Math.round(t * 1000) / 1000); times.push(cfg.meta.duration - 1 / cfg.meta.fps); }
const poster = arg('poster');
const c = await openComposition(format);
if (poster) {
  fs.mkdirSync(path.join(ROOT, 'out'), { recursive: true });
  const f = path.join(ROOT, 'out', format === 'landscape' ? 'poster.png' : `poster-${format}.png`);
  fs.writeFileSync(f, await c.frame(cfg.meta.poster));
  console.log('wrote', path.relative(ROOT, f));
} else {
  fs.mkdirSync(dir, { recursive: true });
  for (const t of times) {
    const f = path.join(dir, `${format}-${t.toFixed(2).padStart(5, '0')}.png`);
    fs.writeFileSync(f, await c.frame(t));
  }
  console.log(`wrote ${times.length} stills to ${path.relative(ROOT, dir)}`);
}
await c.close();
