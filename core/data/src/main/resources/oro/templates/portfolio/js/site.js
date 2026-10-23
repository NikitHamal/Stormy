// Maya Lin — project filters and the contact form. Nav, theme, reveals, count-ups: oro-ui/oro.js.
(function () {
  'use strict';

  var chips = document.querySelectorAll('.chip');
  var projects = document.querySelectorAll('.project');
  var empty = document.getElementById('work-empty');
  chips.forEach(function (chip) {
    chip.addEventListener('click', function () {
      var filter = chip.getAttribute('data-filter');
      chips.forEach(function (c) { c.setAttribute('aria-pressed', String(c === chip)); });
      var shown = 0;
      projects.forEach(function (p) {
        var match = filter === 'all' || p.getAttribute('data-tags').split(' ').indexOf(filter) >= 0;
        p.hidden = !match;
        if (match) shown++;
      });
      empty.hidden = shown > 0;
    });
  });

  var form = document.querySelector('.contact-form');
  if (form) {
    var error = document.getElementById('c-error');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var fields = Array.prototype.slice.call(form.querySelectorAll('input, textarea'));
      var bad = fields.filter(function (f) { return !f.value.trim() || (f.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.value.trim())); });
      fields.forEach(function (f) { f.setAttribute('aria-invalid', String(bad.indexOf(f) >= 0)); });
      error.hidden = bad.length === 0;
      if (bad.length) { bad[0].focus(); return; }
      var btn = form.querySelector('button');
      if (window.Oro) Oro.setBusy(btn, true);
      setTimeout(function () {
        if (window.Oro) { Oro.setBusy(btn, false); Oro.toast('Thanks — I’ll reply within two working days.', 'success'); }
        form.reset();
      }, 900);
    });
  }
})();
