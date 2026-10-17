// npm run render [-- --format vertical] [--fps 30] [--from 0 --to 15] [--workers 2]
// Seeks every frame with window.renderAt(t) in headless Chromium, screenshots it and streams the
// PNGs straight into ffmpeg's stdin (no frames on disk, bounded memory).
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import ffmpegPath from 'ffmpeg-static';
import { chromium } from 'playwright';
import { ROOT, config, openComposition, arg } from './lib.mjs';

const cfg = config();
const format = arg('format', 'landscape');
const fps = Number(arg('fps', cfg.meta.fps));
const from = Number(arg('from', 0));
const to = Number(arg('to', cfg.meta.duration));
const workers = Math.max(1, Number(arg('workers', cfg.encode.workers || 1)));
const out = path.join(ROOT, arg('out', cfg.formats[format].out));
const E = cfg.encode;
fs.mkdirSync(path.dirname(out), { recursive: true });

const total = Math.round((to - from) * fps);
const browser = await chromium.launch({ args: ['--disable-gpu', '--font-render-hinting=none', '--disable-lcd-text'] });
const pages = [];
for (let i = 0; i < workers; i++) pages.push(await openComposition(format, { browser }));
const { W, H } = pages[0].comp;

const ff = spawn(ffmpegPath, [
  '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'png', '-i', '-',
  '-c:v', E.codec, '-preset', E.preset, '-crf', String(E.crf), ...(E.tune ? ['-tune', E.tune] : []),
  '-pix_fmt', E.pixFmt, '-r', String(fps), '-movflags', '+faststart',
  '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
  '-metadata', `title=${cfg.meta.title}`, out,
], { stdio: ['pipe', 'inherit', 'inherit'] });
const ffDone = new Promise((res, rej) => ff.on('close', (c) => (c === 0 ? res() : rej(new Error('ffmpeg exited ' + c)))));
const write = (buf) => new Promise((res) => (ff.stdin.write(buf) ? res() : ff.stdin.once('drain', res)));

console.log(`Rendering ${format} ${W}x${H} @ ${fps}fps, ${total} frames, ${workers} worker(s) → ${path.relative(ROOT, out)}`);
const t0 = Date.now();
const ready = new Map();
let next = 0, written = 0;
const waiters = [];
const wakeAll = () => { while (waiters.length) waiters.shift()(); };
async function worker(p) {
  while (true) {
    // Bounded look-ahead keeps memory flat.
    while (next - written > workers * 3) await new Promise((r) => waiters.push(r));
    const i = next++;
    if (i >= total) return;
    // Sample each frame at its start time (frame i covers [i/fps, (i+1)/fps)).
    ready.set(i, await p.frame(from + i / fps));
    wakeAll();
  }
}
async function writer() {
  while (written < total) {
    if (ready.has(written)) {
      const buf = ready.get(written); ready.delete(written);
      await write(buf);
      written++;
      wakeAll();
      if (written % fps === 0 || written === total) {
        const el = (Date.now() - t0) / 1000;
        process.stdout.write(`\r  ${written}/${total} frames  ${(written / el).toFixed(1)} fps  eta ${((total - written) / (written / el)).toFixed(0)}s   `);
      }
    } else await new Promise((r) => setTimeout(r, 5));
  }
}
await Promise.all([...pages.map(worker), writer()]);
ff.stdin.end();
await ffDone;
for (const p of pages) await p.close();
await browser.close();
const mb = fs.statSync(out).size / 1e6;
console.log(`\nDone in ${((Date.now() - t0) / 1000).toFixed(1)}s — ${path.relative(ROOT, out)} (${mb.toFixed(2)} MB)`);
