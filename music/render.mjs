// Render the Oro soundtrack: node render.mjs [track-number ...]  ->  out/*.wav, *.mp3, *.png
// Each song module builds a Song (score + mix); this renders it, writes a float WAV, then masters
// with ffmpeg's two-pass loudnorm to -14 LUFS integrated / -1 dBTP and exports WAV (24-bit) + MP3 320k
// + a spectrogram for checking the mix without ears.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import ffmpeg from 'ffmpeg-static';
import { SR } from './engine/core.mjs';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(ROOT, 'out');
fs.mkdirSync(OUT, { recursive: true });
const pick = process.argv.slice(2);
const files = fs.readdirSync(path.join(ROOT, 'songs')).filter((f) => /^\d\d-.*\.mjs$/.test(f)).sort()
  .filter((f) => !pick.length || pick.some((p) => f.startsWith(p.padStart(2, '0'))));

function writeWav(file, L, R) {
  const n = L.length, buf = Buffer.alloc(44 + n * 8);
  buf.write('RIFF', 0); buf.writeUInt32LE(36 + n * 8, 4); buf.write('WAVEfmt ', 8);
  buf.writeUInt32LE(16, 16); buf.writeUInt16LE(3, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
  buf.writeUInt32LE(SR * 8, 28); buf.writeUInt16LE(8, 32); buf.writeUInt16LE(32, 34); buf.write('data', 36); buf.writeUInt32LE(n * 8, 40);
  for (let i = 0; i < n; i++) { buf.writeFloatLE(L[i], 44 + i * 8); buf.writeFloatLE(R[i], 48 + i * 8); }
  fs.writeFileSync(file, buf);
}
const ff = (args) => spawnSync(ffmpeg, ['-hide_banner', ...args], { encoding: 'utf8', maxBuffer: 1 << 26 });

const report = [];
for (const f of files) {
  const t0 = Date.now();
  const { default: build } = await import(pathToFileURL(path.join(ROOT, 'songs', f)).href);
  const song = build();
  const { L, R } = song.render();
  const base = f.replace(/\.mjs$/, '');
  const raw = path.join(OUT, `.${base}.raw.wav`);
  writeWav(raw, L, R);
  const p1 = ff(['-i', raw, '-af', 'loudnorm=I=-14:TP=-1:LRA=11:print_format=json', '-f', 'null', '-']).stderr;
  const m = JSON.parse(p1.slice(p1.lastIndexOf('{'), p1.lastIndexOf('}') + 1));
  const ln = `loudnorm=I=-14:TP=-1:LRA=11:measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}:measured_thresh=${m.input_thresh}:offset=${m.target_offset}:linear=true`;
  const wav = path.join(OUT, `${base}.wav`), mp3 = path.join(OUT, `${base}.mp3`);
  const meta = ['-metadata', `title=${song.title}`, '-metadata', 'artist=Orobi', '-metadata', 'album=Oro — Original Soundtrack', '-metadata', `track=${parseInt(base)}`];
  ff(['-y', '-loglevel', 'error', '-i', raw, '-af', `${ln},aresample=${SR}`, '-ar', String(SR), '-c:a', 'pcm_s24le', ...meta, wav]);
  // MP3 with the album cover embedded (out/cover.png, made from cover.svg).
  const cover = path.join(OUT, 'cover.png');
  const art = fs.existsSync(cover) ? ['-i', cover, '-map', '0:a', '-map', '1:v', '-c:v', 'mjpeg', '-disposition:v', 'attached_pic', '-metadata:s:v', 'title=Cover'] : [];
  ff(['-y', '-loglevel', 'error', '-i', wav, ...art, '-c:a', 'libmp3lame', '-b:a', '320k', '-id3v2_version', '3', ...meta, mp3]);
  ff(['-y', '-loglevel', 'error', '-i', wav, '-lavfi', 'showspectrumpic=s=1600x400:legend=1:scale=log:fscale=log:start=30:stop=18000', path.join(OUT, `${base}.png`)]);
  fs.unlinkSync(raw);
  const s = ff(['-i', wav, '-af', 'ebur128=peak=true', '-f', 'null', '-']).stderr;
  const sum = s.slice(s.lastIndexOf('Summary'));
  const I = /I:\s+(-?[\d.]+) LUFS/.exec(sum)?.[1], TP = /Peak:\s+(-?[\d.]+) dBFS/.exec(sum)?.[1], LRA = /LRA:\s+([\d.]+) LU/.exec(sum)?.[1];
  const dur = (L.length / SR).toFixed(1);
  report.push({ file: base, title: song.title, seconds: +dur, lufs: +I, truePeak: +TP, lra: +LRA, markers: song.markers });
  console.log(`${base}: ${song.title} — ${dur}s, ${I} LUFS, ${TP} dBTP, LRA ${LRA} (${((Date.now() - t0) / 1000).toFixed(1)}s)`);
}
const reportFile = path.join(OUT, 'report.json');
const prev = fs.existsSync(reportFile) ? JSON.parse(fs.readFileSync(reportFile, 'utf8')) : [];
const merged = [...prev.filter((p) => !report.some((r) => r.file === p.file)), ...report].sort((a, b) => a.file.localeCompare(b.file));
fs.writeFileSync(reportFile, JSON.stringify(merged, null, 2));
