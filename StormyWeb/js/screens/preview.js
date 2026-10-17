// Preview (PreviewActivity.kt): the project page in a frame, toolbar, console with errors.
import { h, ic } from '../ui/kit.js';
import { back } from '../app.js';

export default { render() {
  const el = h(`<div><div class="topbar"><button class="iconbtn" data-back>${ic('close')}</button><span class="title t-title-m">Mara Ilves — Photographer</span>
      <button class="iconbtn dim">${ic('smartphone')}</button><button class="iconbtn dim">${ic('ads_click')}</button><button class="iconbtn dim">${ic('refresh')}</button></div>
    <div style="flex:1;background:#F7F8FA;color:#0E1117;overflow:auto;padding:16px;font-family:var(--font)">
      <div style="display:flex;justify-content:space-between;align-items:center;font:700 12px var(--font);letter-spacing:.2em">MARA ILVES <span style="background:#0E1117;color:#fff;border-radius:99px;padding:6px 14px;letter-spacing:0;font-weight:600">Book</span></div>
      <div style="margin-top:16px;height:260px;border-radius:18px;background:linear-gradient(180deg,#2B3039 60%,#171A21 60%);position:relative;overflow:hidden">
        <div style="position:absolute;right:60px;top:60px;width:90px;height:90px;border-radius:50%;background:#E9ECF1"></div>
        <div style="position:absolute;left:16px;bottom:16px;color:#fff"><div style="font:600 40px/1 var(--font);letter-spacing:-.045em">Light, kept.</div><div style="font:400 12px var(--font);opacity:.8;margin-top:6px">Portraits, weddings and editorial work.</div></div></div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:16px">${['#D9DDE3', '#BCC1C9', '#1D2129', '#EEF0F3'].map((c) => `<div style="height:140px;border-radius:12px;background:${c}"></div>`).join('')}</div></div>
    <div style="max-height:160px;background:var(--sc-low);border-top:1px solid var(--outline-variant)"><div class="row" style="min-height:40px;padding:6px 16px">${ic('terminal', 's')}<span class="grow t-label-l-e">Console</span><span class="t-label-m err">1 error</span></div>
      <div class="mono" style="font-size:11px;padding:0 16px 12px;color:var(--error)">Uncaught TypeError: menu is null (app.js:12)</div>
      <div class="mono" style="font-size:11px;padding:0 16px 12px;color:var(--on-surface-variant)">Orobi will see this on its next run.</div></div></div>`);
  el.querySelector('[data-back]').onclick = back;
  return el;
} };
