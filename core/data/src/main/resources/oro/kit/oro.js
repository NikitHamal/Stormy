/* Oro UI kit runtime: reveal-on-scroll, sticky nav, mobile menu, ripples, toasts, modals,
   theme toggle, count-up, tilt, tabs, app shell, and for real apps: Oro.api (the built-in
   /api backend), Oro.auth (accounts), Oro.table (sortable, searchable, paged), Oro.chart (SVG
   bar/line). Load with <script src="oro-ui/oro.js" defer></script>. */
(function () {
  'use strict';
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

  function esc(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function initReveal(root = document) {
    const els = $$('.reveal, .reveal-scale', root);
    if (reduce || !('IntersectionObserver' in window)) { els.forEach((e) => e.classList.add('is-in')); return; }
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target); } });
    }, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' });
    els.forEach((el) => {
      const sibs = el.parentElement ? Array.from(el.parentElement.children).filter((c) => c.classList.contains('reveal') || c.classList.contains('reveal-scale')) : [];
      const i = Math.max(0, sibs.indexOf(el));
      if (!el.style.transitionDelay) el.style.transitionDelay = Math.min(i, 6) * 70 + 'ms';
      io.observe(el);
    });
  }

  function initNav() {
    const nav = document.querySelector('.nav');
    if (!nav) return;
    const onScroll = () => nav.classList.toggle('is-scrolled', scrollY > 8);
    addEventListener('scroll', onScroll, { passive: true }); onScroll();
    const toggle = nav.querySelector('.nav-toggle');
    if (toggle) {
      toggle.setAttribute('aria-expanded', 'false');
      toggle.addEventListener('click', () => {
        const open = nav.classList.toggle('is-open');
        toggle.setAttribute('aria-expanded', String(open));
      });
      $$('.nav-links a', nav).forEach((a) => a.addEventListener('click', () => { nav.classList.remove('is-open'); toggle.setAttribute('aria-expanded', 'false'); }));
    }
  }

  function initRipple() {
    document.addEventListener('pointerdown', (e) => {
      const btn = e.target.closest('.btn');
      if (!btn || reduce || btn.disabled) return;
      const r = btn.getBoundingClientRect(), size = Math.max(r.width, r.height);
      const s = document.createElement('span');
      s.className = 'ripple';
      s.style.cssText = `width:${size}px;height:${size}px;left:${e.clientX - r.left - size / 2}px;top:${e.clientY - r.top - size / 2}px`;
      btn.appendChild(s); s.addEventListener('animationend', () => s.remove());
    });
  }

  let region;
  function toast(message, type = 'info', ms = 4000) {
    if (!region) {
      region = document.createElement('div'); region.className = 'toast-region';
      region.setAttribute('role', 'status'); region.setAttribute('aria-live', 'polite');
      document.body.appendChild(region);
    }
    const t = document.createElement('div');
    t.className = 'toast' + (type !== 'info' ? ' toast-' + type : '');
    t.innerHTML = esc(message);
    region.appendChild(t);
    let timer = setTimeout(close, ms);
    t.addEventListener('mouseenter', () => clearTimeout(timer));
    t.addEventListener('mouseleave', () => { timer = setTimeout(close, 1500); });
    function close() { t.classList.add('is-leaving'); t.addEventListener('animationend', () => t.remove(), { once: true }); }
    return close;
  }

  function confirmDialog({ title = 'Are you sure?', body = '', confirm = 'Confirm', cancel = 'Cancel', danger = false } = {}) {
    return new Promise((resolve) => {
      const d = document.createElement('dialog'); d.className = 'modal';
      d.innerHTML = `<div class="modal-body"><h3>${esc(title)}</h3><p>${esc(body)}</p></div>
        <div class="modal-actions"><button class="btn btn-ghost" value="no">${esc(cancel)}</button>
        <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" value="yes">${esc(confirm)}</button></div>`;
      document.body.appendChild(d);
      d.addEventListener('click', (e) => {
        const v = e.target.closest('button')?.value;
        if (v || e.target === d) { d.close(); resolve(v === 'yes'); }
      });
      d.addEventListener('close', () => d.remove());
      d.showModal();
    });
  }

  function initTheme() {
    const saved = localStorage.getItem('oro:theme');
    if (saved) document.documentElement.dataset.theme = saved;
    $$('[data-theme-toggle]').forEach((b) => b.addEventListener('click', () => {
      const cur = document.documentElement.dataset.theme || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
      const next = cur === 'dark' ? 'light' : 'dark';
      const apply = () => { document.documentElement.dataset.theme = next; localStorage.setItem('oro:theme', next); };
      document.startViewTransition && !reduce ? document.startViewTransition(apply) : apply();
    }));
  }

  function initCountUp() {
    const els = $$('[data-count]');
    if (!els.length) return;
    const run = (el) => {
      const end = parseFloat(el.dataset.count), dec = (el.dataset.count.split('.')[1] || '').length;
      const prefix = el.dataset.prefix || '', suffix = el.dataset.suffix || '';
      if (reduce) { el.textContent = prefix + end.toLocaleString(undefined, { minimumFractionDigits: dec }) + suffix; return; }
      const t0 = performance.now(), dur = 1600;
      const step = (now) => {
        const p = Math.min(1, (now - t0) / dur), v = end * (1 - Math.pow(1 - p, 4));
        el.textContent = prefix + v.toLocaleString(undefined, { minimumFractionDigits: dec, maximumFractionDigits: dec }) + suffix;
        if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    };
    const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { run(e.target); io.unobserve(e.target); } }), { threshold: 0.5 });
    els.forEach((el) => io.observe(el));
  }

  function initTilt() {
    if (reduce || matchMedia('(hover: none)').matches) return;
    $$('[data-tilt]').forEach((el) => {
      el.style.transition = 'transform .4s var(--ease-out)';
      el.addEventListener('pointermove', (e) => {
        const r = el.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        el.style.transform = `perspective(900px) rotateY(${x * 8}deg) rotateX(${-y * 8}deg)`;
      });
      el.addEventListener('pointerleave', () => { el.style.transform = ''; });
    });
  }

  function setBusy(btn, busy) {
    if (!btn) return;
    btn.classList.toggle('is-loading', busy); btn.disabled = busy; btn.setAttribute('aria-busy', String(busy));
  }

  function skeleton(n = 3, h = 72) {
    return Array.from({ length: n }, () => `<div class="skeleton" style="height:${h}px;margin-bottom:12px"></div>`).join('');
  }

  // ---------------------------------------------------------------- tabs, shell

  function initTabs(root = document) {
    $$('[role=tablist]', root).forEach((list) => {
      const tabs = $$('[role=tab]', list);
      const select = (t, focus) => {
        tabs.forEach((x) => {
          const on = x === t;
          x.setAttribute('aria-selected', String(on)); x.tabIndex = on ? 0 : -1;
          const panel = x.getAttribute('aria-controls') && document.getElementById(x.getAttribute('aria-controls'));
          if (panel) panel.hidden = !on;
        });
        if (focus) t.focus();
        list.dispatchEvent(new CustomEvent('oro:tab', { detail: { tab: t } }));
      };
      tabs.forEach((t, i) => {
        t.addEventListener('click', () => select(t));
        t.addEventListener('keydown', (e) => {
          const d = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
          if (d) { e.preventDefault(); select(tabs[(i + d + tabs.length) % tabs.length], true); }
        });
      });
      select(tabs.find((t) => t.getAttribute('aria-selected') === 'true') || tabs[0]);
    });
  }

  function initShell() {
    const shell = document.querySelector('.app-shell');
    if (!shell) return;
    $$('[data-sidebar-toggle]').forEach((b) => b.addEventListener('click', () => {
      const open = shell.classList.toggle('is-open'); b.setAttribute('aria-expanded', String(open));
    }));
    $$('.sidebar-link', shell).forEach((a) => {
      if (a.getAttribute('href') && location.pathname.endsWith(a.getAttribute('href'))) a.setAttribute('aria-current', 'page');
      a.addEventListener('click', () => shell.classList.remove('is-open'));
    });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') shell.classList.remove('is-open'); });
  }

  // ---------------------------------------------------------------- backend

  const TOKEN = 'oro:token', USER = 'oro:user';
  async function request(method, path, body) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const token = localStorage.getItem(TOKEN);
    if (token) headers.Authorization = 'Bearer ' + token;
    const res = await fetch('/api' + (path.startsWith('/') ? path : '/' + path), {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body)
    });
    const data = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) {
      const err = new Error((data && data.error) || 'Request failed (' + res.status + ')');
      err.status = res.status; throw err;
    }
    const total = res.headers.get('X-Total-Count');
    if (Array.isArray(data) && total !== null) data.total = Number(total);
    return data;
  }
  const api = {
    get: (p) => request('GET', p), post: (p, b) => request('POST', p, b || {}),
    put: (p, b) => request('PUT', p, b || {}), patch: (p, b) => request('PATCH', p, b || {}),
    del: (p) => request('DELETE', p),
    upload: (file) => new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => request('POST', '/uploads', { name: file.name, dataUrl: r.result }).then(resolve, reject);
      r.onerror = () => reject(r.error); r.readAsDataURL(file);
    })
  };

  const auth = {
    get user() { try { return JSON.parse(localStorage.getItem(USER)); } catch (_) { return null; } },
    get signedIn() { return !!localStorage.getItem(TOKEN); },
    async register(fields) { return keep(await api.post('/auth/register', fields)); },
    async login(fields) { return keep(await api.post('/auth/login', fields)); },
    async logout() { try { await api.post('/auth/logout'); } catch (_) {} localStorage.removeItem(TOKEN); localStorage.removeItem(USER); },
    async me() {
      try { const u = await api.get('/auth/me'); localStorage.setItem(USER, JSON.stringify(u)); return u; }
      catch (e) { if (e.status === 401) { localStorage.removeItem(TOKEN); localStorage.removeItem(USER); } return null; }
    },
    /** Sends signed-out visitors to the login page; resolves to the user. */
    async require(loginPage = 'login.html') {
      const u = auth.signedIn ? await auth.me() : null;
      if (!u) location.replace(loginPage + '?next=' + encodeURIComponent(location.pathname + location.search));
      return u;
    }
  };
  function keep(res) { localStorage.setItem(TOKEN, res.token); localStorage.setItem(USER, JSON.stringify(res.user)); return res.user; }

  // ---------------------------------------------------------------- data table

  /**
   * Oro.table(el, { columns: [{ key, label, num, format(v, row) }], rows, pageSize: 10,
   *   search: inputEl, empty: 'No orders yet', onRow(row) }) -> { update(rows) }
   */
  function table(el, opts) {
    const o = Object.assign({ pageSize: 10, empty: 'Nothing here yet' }, opts);
    let rows = o.rows || [], sort = null, dir = 1, page = 1, q = '';
    const fmt = (c, r) => (c.format ? c.format(r[c.key], r) : r[c.key] ?? '');
    function view() {
      let v = q ? rows.filter((r) => o.columns.some((c) => String(fmt(c, r)).toLowerCase().includes(q))) : rows.slice();
      if (sort) v.sort((a, b) => { const x = a[sort], y = b[sort]; return (x > y ? 1 : x < y ? -1 : 0) * dir; });
      return v;
    }
    function render() {
      const v = view(), pages = Math.max(1, Math.ceil(v.length / o.pageSize));
      page = Math.min(page, pages);
      const slice = v.slice((page - 1) * o.pageSize, page * o.pageSize);
      const head = o.columns.map((c) => `<th class="${c.num ? 'num' : ''}"${sort === c.key ? ` aria-sort="${dir > 0 ? 'ascending' : 'descending'}"` : c.sortable === false ? '' : ' aria-sort="none"'}>` +
        (c.sortable === false ? esc(c.label) : `<button type="button" data-key="${esc(c.key)}">${esc(c.label)}</button>`) + '</th>').join('');
      const body = slice.length ? slice.map((r, i) => '<tr data-i="' + i + '">' + o.columns.map((c) => `<td class="${c.num ? 'num' : ''}">${esc(fmt(c, r))}</td>`).join('') + '</tr>').join('')
        : `<tr><td colspan="${o.columns.length}"><div class="empty">${esc(q ? 'No matches for “' + q + '”' : o.empty)}</div></td></tr>`;
      el.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>` +
        (pages > 1 ? `<div class="pagination"><span>${v.length} rows · page ${page} of ${pages}</span><span class="row">` +
          `<button class="btn btn-sm btn-outline" data-p="-1"${page === 1 ? ' disabled' : ''}>Previous</button>` +
          `<button class="btn btn-sm btn-outline" data-p="1"${page === pages ? ' disabled' : ''}>Next</button></span></div>` : '');
      el._slice = slice;
    }
    el.addEventListener('click', (e) => {
      const k = e.target.closest('th button')?.dataset.key;
      if (k) { dir = sort === k ? -dir : 1; sort = k; render(); return; }
      const p = e.target.closest('[data-p]')?.dataset.p;
      if (p) { page += Number(p); render(); return; }
      const tr = e.target.closest('tbody tr[data-i]');
      if (tr && o.onRow) o.onRow(el._slice[Number(tr.dataset.i)]);
    });
    if (o.search) o.search.addEventListener('input', () => { q = o.search.value.trim().toLowerCase(); page = 1; render(); });
    render();
    return { update(next) { rows = next || []; render(); } };
  }

  // ---------------------------------------------------------------- chart

  /** Oro.chart(el, { type: 'bar' | 'line', labels: [...], values: [...], format: (v) => '$' + v }) */
  function chart(el, { type = 'bar', labels = [], values = [], format = (v) => v.toLocaleString() } = {}) {
    const W = 600, H = 240, P = { t: 12, r: 8, b: 28, l: 44 };
    const max = Math.max(1, ...values), w = W - P.l - P.r, h = H - P.t - P.b, n = Math.max(1, values.length);
    const x = (i) => P.l + (type === 'bar' ? (i + 0.5) * (w / n) : (n === 1 ? w / 2 : (i * w) / (n - 1)));
    const y = (v) => P.t + h - (v / max) * h;
    const ticks = [0, 0.5, 1].map((f) => `<line class="axis" x1="${P.l}" x2="${W - P.r}" y1="${y(max * f)}" y2="${y(max * f)}"/>` +
      `<text class="label" x="${P.l - 8}" y="${y(max * f) + 4}" text-anchor="end">${esc(format(Math.round(max * f)))}</text>`).join('');
    const step = Math.ceil(n / 8);
    const xl = labels.map((l, i) => (i % step ? '' : `<text class="label" x="${x(i)}" y="${H - 8}" text-anchor="middle">${esc(l)}</text>`)).join('');
    let marks;
    if (type === 'line') {
      const pts = values.map((v, i) => `${x(i)},${y(v)}`).join(' ');
      marks = `<polygon class="area" points="${x(0)},${y(0)} ${pts} ${x(n - 1)},${y(0)}"/><polyline class="line" points="${pts}"/>` +
        values.map((v, i) => `<circle class="dot" cx="${x(i)}" cy="${y(v)}" r="3.5"><title>${esc(labels[i] ?? '')}: ${esc(format(v))}</title></circle>`).join('');
    } else {
      const bw = Math.min(48, (w / n) * 0.62);
      marks = values.map((v, i) => `<rect class="bar" x="${x(i) - bw / 2}" y="${y(v)}" width="${bw}" height="${Math.max(0, y(0) - y(v))}" rx="6"><title>${esc(labels[i] ?? '')}: ${esc(format(v))}</title></rect>`).join('');
    }
    el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img" aria-label="${esc(type)} chart">${ticks}${marks}${xl}</svg>`;
    if (!reduce) el.querySelector('svg').animate([{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], { duration: 420, easing: 'cubic-bezier(.2,0,0,1)' });
  }

  window.Oro = { toast, confirm: confirmDialog, esc, setBusy, skeleton, reveal: initReveal, api, auth, table, chart, tabs: initTabs };
  const boot = () => { initReveal(); initNav(); initRipple(); initTheme(); initCountUp(); initTilt(); initTabs(); initShell(); };
  document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', boot) : boot();
})();
