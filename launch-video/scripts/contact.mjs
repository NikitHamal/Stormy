// QA helper: tile a folder of stills into contact sheets (for eyeballing a whole sweep at once).
//   node scripts/contact.mjs --dir out/qa --format landscape --cols 4
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import ffmpeg from 'ffmpeg-static';
import { ROOT, arg } from './lib.mjs';
const dir = path.join(ROOT, arg('dir', 'out/qa'));
const fmt = arg('format', 'landscape');
const cols = Number(arg('cols', 4)), rows = Number(arg('rows', 3));
const files = fs.readdirSync(dir).filter((f) => f.startsWith(fmt + '-') && f.endsWith('.png')).sort();
const per = cols * rows;
const tw = fmt === 'landscape' ? 640 : 300;
for (let p = 0; p * per < files.length; p++) {
  const chunk = files.slice(p * per, (p + 1) * per);
  const tmp = fs.mkdtempSync(path.join(dir, '_c'));
  chunk.forEach((f, i) => fs.copyFileSync(path.join(dir, f), path.join(tmp, `${String(i).padStart(3, '0')}.png`)));
  const out = path.join(dir, `contact-${fmt}-${p}.jpg`);
  spawnSync(ffmpeg, ['-y', '-loglevel', 'error', '-framerate', '1', '-i', path.join(tmp, '%03d.png'),
    '-vf', `scale=${tw}:-1,tile=${cols}x${rows}:padding=6:color=0x444444`, '-frames:v', '1', out], { stdio: 'inherit' });
  fs.rmSync(tmp, { recursive: true });
  console.log('wrote', path.relative(ROOT, out), chunk[0], '…', chunk.at(-1));
}
