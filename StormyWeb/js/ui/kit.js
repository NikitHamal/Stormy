// Small UI kit: element helpers, sheets, snackbar, segmented, switches. Mirrors the Compose components.
export const $ = (sel, root = document) => root.querySelector(sel);
export const h = (html) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };
export const ic = (name, cls = '') => `<span class="ic ${cls}">${name}</span>`;
export const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

let host = null;
export const setHost = (el) => { host = el; };

/** ModalBottomSheet: returns { el, close }. `content` is HTML for the body. */
export function sheet(content, { onClose } = {}) {
  const scrim = h('<div class="scrim"></div>');
  const el = h(`<div class="sheet" role="dialog"><div class="handle"></div><div class="body">${content}</div></div>`);
  host.append(scrim, el);
  requestAnimationFrame(() => { scrim.classList.add('open'); el.classList.add('open'); });
  const close = () => {
    scrim.classList.remove('open'); el.classList.remove('open');
    setTimeout(() => { scrim.remove(); el.remove(); onClose?.(); }, 320);
  };
  scrim.onclick = close;
  el.querySelectorAll('[data-close]').forEach((b) => (b.onclick = close));
  return { el, close };
}

/** FormSheet: icon + title + content + Cancel / Confirm. */
export function formSheet({ title, icon = 'edit', body, confirm = 'Save', destructive = false, onConfirm }) {
  const wide = confirm.length > 12 ? 'wide' : '';
  const s = sheet(`<div class="sheet-head"><span class="sheet-mark">${ic(icon)}</span><span class="t-title-l-e" style="flex:1">${esc(title)}</span><button class="iconbtn dim" data-close>${ic('close')}</button></div>
    ${body}<div class="sheet-actions" style="padding:24px 0 0"><button class="btn text" data-close>Cancel</button><button class="btn ${destructive ? 'danger' : 'filled'} ${wide}" data-ok>${esc(confirm)}</button></div>`);
  s.el.querySelector('[data-ok]').onclick = () => { onConfirm?.(s.el); s.close(); };
  return s;
}

/** ActionSheet: a list of actions, each a card. */
export function actionSheet({ title, subtitle, actions }) {
  const s = sheet(`<div class="t-title-m-e">${esc(title)}</div>${subtitle ? `<div class="t-body-s muted" style="margin-bottom:12px">${esc(subtitle)}</div>` : '<div style="height:12px"></div>'}
    ${actions.map((a, i) => `<button class="action ${a.destructive ? 'destructive' : ''}" data-i="${i}">${ic(a.icon)}<span><div class="t-body-l-e">${esc(a.label)}</div>${a.description ? `<div class="t-body-s muted">${esc(a.description)}</div>` : ''}</span></button>`).join('')}
    <button class="btn text block" data-close>Cancel</button>`);
  s.el.querySelectorAll('[data-i]').forEach((b) => (b.onclick = () => { actions[+b.dataset.i].onClick?.(); s.close(); }));
  return s;
}

export function snackbar(text) {
  const el = h(`<div class="snackbar">${esc(text)}</div>`);
  host.append(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 2600);
}

/** SegmentedPicker markup; wire with bindSegmented. */
export const segmented = (label, options, selected, id) => `${label ? `<div class="seg-label t-label-s-e">${esc(label)}</div>` : ''}
  <div class="seg" data-seg="${id}">${options.map((o) => `<button class="${o === selected ? 'on' : ''}" data-v="${esc(o)}">${esc(o)}</button>`).join('')}</div>`;
export function bindSegmented(root, id, onChange) {
  root.querySelectorAll(`[data-seg="${id}"] button`).forEach((b) => (b.onclick = () => {
    b.parentElement.querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
    onChange?.(b.dataset.v);
  }));
}
export const sw = (on, id) => `<span class="switch ${on ? 'on' : ''}" data-sw="${id}"></span>`;
export function bindSwitches(root, onChange) {
  root.querySelectorAll('[data-sw]').forEach((s) => (s.closest('.row') || s).onclick = () => { s.classList.toggle('on'); onChange?.(s.dataset.sw, s.classList.contains('on')); });
}
export const field = (label, value = '', { type = 'text', support = '', id = '', area = false, placeholder = '' } = {}) =>
  `<div class="field" style="margin-bottom:12px">${area ? `<textarea rows="3" data-f="${id}" placeholder="${esc(placeholder)}">${esc(value)}</textarea>` : `<input type="${type}" value="${esc(value)}" data-f="${id}" placeholder="${esc(placeholder)}">`}<label>${esc(label)}</label>${support ? `<div class="support">${esc(support)}</div>` : ''}</div>`;
