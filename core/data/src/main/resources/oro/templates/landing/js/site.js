// Paperline — page behaviour. Navigation, theme, reveals and count-ups come from oro-ui/oro.js.
(function () {
  'use strict';

  // Monthly / yearly pricing.
  var options = document.querySelectorAll('.billing-opt');
  options.forEach(function (btn) {
    btn.addEventListener('click', function () {
      var period = btn.getAttribute('data-period');
      options.forEach(function (o) { o.setAttribute('aria-pressed', String(o === btn)); });
      document.querySelectorAll('.amount').forEach(function (el) {
        var value = Number(el.getAttribute('data-' + period));
        el.textContent = '$' + (Number.isInteger(value) ? value : value.toFixed(2));
      });
    });
  });

  // Sign-up form: inline validation and a busy state, no alerts.
  var form = document.querySelector('.cta-form');
  if (form) {
    var input = form.querySelector('input');
    var error = document.getElementById('cta-error');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.value.trim());
      error.hidden = ok;
      input.setAttribute('aria-invalid', String(!ok));
      if (!ok) { input.focus(); return; }
      var btn = form.querySelector('button');
      if (window.Oro) Oro.setBusy(btn, true);
      setTimeout(function () {
        if (window.Oro) { Oro.setBusy(btn, false); Oro.toast('Check your inbox — your workspace is ready.', 'success'); }
        form.reset();
      }, 900);
    });
  }
})();
