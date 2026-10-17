// Preview UI: scrubber + play/pause. Real-time playback lives ONLY here; renders never load this.
export function mountPreview(comp, renderAt) {
  const stage = document.getElementById('stage');
  document.body.style.overflow = 'hidden';
  const fit = () => {
    const s = Math.min(innerWidth / comp.W, (innerHeight - 60) / comp.H);
    stage.style.transformOrigin = '0 0';
    stage.style.transform = `translate(${(innerWidth - comp.W * s) / 2}px, 0) scale(${s})`;
  };
  addEventListener('resize', fit); fit();
  const bar = document.createElement('div');
  bar.className = 'pv';
  bar.innerHTML = `<button id="pv-play">Play</button><input id="pv-t" type="range" min="0" max="${comp.duration}" step="${1 / comp.fps}" value="0">
    <span id="pv-lab" style="width:150px;font-variant-numeric:tabular-nums"></span>
    <select id="pv-fmt">${Object.keys(comp.cfg.formats).map((f) => `<option ${f === comp.format ? 'selected' : ''}>${f}</option>`).join('')}</select>
    ${comp.timeline.map((s) => `<button data-t="${s.start}">${s.id}</button>`).join('')}`;
  document.body.appendChild(bar);
  const range = bar.querySelector('#pv-t'), lab = bar.querySelector('#pv-lab'), play = bar.querySelector('#pv-play');
  let t = Number(new URLSearchParams(location.search).get('t') || 0), playing = false, last = 0;
  const draw = () => { const r = renderAt(t); range.value = t; lab.textContent = `${t.toFixed(3)}s  ${r.scene}`; };
  range.oninput = () => { t = Number(range.value); draw(); };
  bar.querySelectorAll('[data-t]').forEach((b) => (b.onclick = () => { t = Number(b.dataset.t); draw(); }));
  bar.querySelector('#pv-fmt').onchange = (e) => { const u = new URL(location.href); u.searchParams.set('format', e.target.value); location.href = u; };
  const loop = (now) => {
    if (!playing) return;
    t += (now - last) / 1000; last = now;
    if (t >= comp.duration) t = 0;
    draw(); requestAnimationFrame(loop);
  };
  play.onclick = () => { playing = !playing; play.textContent = playing ? 'Pause' : 'Play'; if (playing) { last = performance.now(); requestAnimationFrame(loop); } };
  addEventListener('keydown', (e) => {
    if (e.key === ' ') { e.preventDefault(); play.click(); }
    if (e.key === 'ArrowRight') { t = Math.min(comp.duration, t + 1 / comp.fps); draw(); }
    if (e.key === 'ArrowLeft') { t = Math.max(0, t - 1 / comp.fps); draw(); }
  });
  draw();
}
