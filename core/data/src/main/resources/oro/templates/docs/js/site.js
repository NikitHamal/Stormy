// Lumen Docs — copy buttons, search, scroll spy, mobile navigation and the live demo chart.
(function () {
  'use strict';

  // Copy code blocks.
  document.querySelectorAll('.copy').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var code = btn.closest('.code').querySelector('code').textContent;
      var done = function () {
        btn.textContent = 'Copied';
        setTimeout(function () { btn.textContent = 'Copy'; }, 1400);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(code).then(done, function () { if (window.Oro) Oro.toast('Copy failed — select the text instead', 'error'); });
      } else {
        done();
      }
    });
  });

  // Filter the sidebar as you type; "/" focuses search.
  var search = document.getElementById('doc-search');
  var links = Array.prototype.slice.call(document.querySelectorAll('.doc-link'));
  var none = document.getElementById('no-results');
  search.addEventListener('input', function () {
    var q = search.value.trim().toLowerCase();
    var shown = 0;
    links.forEach(function (a) {
      var target = document.querySelector(a.getAttribute('href'));
      var section = target ? target.textContent + ' ' + (target.nextElementSibling ? target.nextElementSibling.textContent : '') : '';
      var match = !q || (a.textContent + ' ' + section).toLowerCase().indexOf(q) >= 0;
      a.hidden = !match;
      if (match) shown++;
    });
    none.hidden = shown > 0;
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === '/' && document.activeElement !== search) { e.preventDefault(); search.focus(); }
    if (e.key === 'Escape') toggleNav(false);
  });

  // Scroll spy for the sidebar and "on this page".
  var headings = Array.prototype.slice.call(document.querySelectorAll('.prose h1[id], .prose h2[id]'));
  var spyLinks = Array.prototype.slice.call(document.querySelectorAll('.doc-link, .toc a'));
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var id = '#' + entry.target.id;
        spyLinks.forEach(function (a) { a.classList.toggle('is-active', a.getAttribute('href') === id); });
      });
    }, { rootMargin: '-72px 0px -70% 0px' });
    headings.forEach(function (h) { io.observe(h); });
  }

  // Mobile navigation drawer.
  var nav = document.getElementById('sidebar');
  var menu = document.getElementById('menu-btn');
  var scrim = document.getElementById('scrim');
  function toggleNav(open) {
    nav.classList.toggle('is-open', open);
    menu.setAttribute('aria-expanded', String(open));
    scrim.hidden = !open;
  }
  menu.addEventListener('click', function () { toggleNav(!nav.classList.contains('is-open')); });
  scrim.addEventListener('click', function () { toggleNav(false); });
  links.forEach(function (a) { a.addEventListener('click', function () { toggleNav(false); }); });

  // The example from "Your first chart", drawn live.
  if (window.Oro) {
    Oro.chart(document.getElementById('demo-chart'), {
      type: 'bar', labels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'], values: [12, 19, 8, 24, 16],
      format: function (v) { return v + ' orders'; }
    });
  }
})();
