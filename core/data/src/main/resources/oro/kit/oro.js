/* Oro UI kit runtime: reveal-on-scroll, sticky nav, mobile menu, ripples, toasts, modals,
   theme toggle, count-up, tilt. Load with <script src="oro-ui/oro.js" defer></script>. */
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

  window.Oro = { toast, confirm: confirmDialog, esc, setBusy, skeleton, reveal: initReveal };
  const boot = () => { initReveal(); initNav(); initRipple(); initTheme(); initCountUp(); initTilt(); };
  document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', boot) : boot();
})();
