// Field Notes — topic filters, search, newsletter form, reading progress.
(function () {
  'use strict';

  var posts = Array.prototype.slice.call(document.querySelectorAll('.post'));
  var chips = document.querySelectorAll('.chip');
  var search = document.getElementById('search');
  var empty = document.getElementById('no-posts');
  var topic = 'all';

  function apply() {
    var q = search ? search.value.trim().toLowerCase() : '';
    var shown = 0;
    posts.forEach(function (p) {
      var okTopic = topic === 'all' || p.getAttribute('data-topic') === topic;
      var okText = !q || p.textContent.toLowerCase().indexOf(q) >= 0;
      p.hidden = !(okTopic && okText);
      if (!p.hidden) shown++;
    });
    if (empty) empty.hidden = shown > 0;
  }
  chips.forEach(function (chip) {
    chip.addEventListener('click', function () {
      topic = chip.getAttribute('data-topic');
      chips.forEach(function (c) { c.setAttribute('aria-pressed', String(c === chip)); });
      apply();
    });
  });
  if (search) search.addEventListener('input', apply);

  var form = document.querySelector('.sub-form');
  if (form) {
    var input = form.querySelector('input');
    var error = document.getElementById('sub-error');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.value.trim());
      error.hidden = ok;
      input.setAttribute('aria-invalid', String(!ok));
      if (!ok) { input.focus(); return; }
      var btn = form.querySelector('button');
      if (window.Oro) Oro.setBusy(btn, true);
      setTimeout(function () {
        if (window.Oro) { Oro.setBusy(btn, false); Oro.toast('You’re in. See you on Sunday.', 'success'); }
        form.reset();
      }, 800);
    });
  }

  var bar = document.getElementById('progress-bar');
  if (bar) {
    var update = function () {
      var max = document.documentElement.scrollHeight - window.innerHeight;
      bar.style.width = (max > 0 ? Math.min(100, (window.scrollY / max) * 100) : 0) + '%';
    };
    window.addEventListener('scroll', update, { passive: true });
    update();
  }
})();
