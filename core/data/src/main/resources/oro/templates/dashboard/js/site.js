// Roastery dashboard — data from Oro's built-in backend (/api/orders, stored in db.json).
(function () {
  'use strict';

  var money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
  var DAY = 86400000;
  var orders = [];
  var table = null;

  function byId(id) { return document.getElementById(id); }
  function paid(list) { return list.filter(function (o) { return o.status === 'Paid'; }); }
  function sum(list) { return list.reduce(function (a, o) { return a + Number(o.total || 0); }, 0); }
  function newest() { return orders.reduce(function (m, o) { return Math.max(m, Date.parse(o.date)); }, 0) || Date.now(); }

  // Change against the previous four weeks: relative for amounts, in points for rates.
  function delta(el, now, before, opts) {
    opts = opts || {};
    if (!opts.points && !before) { el.textContent = ''; return; }
    var up = now >= before;
    var size = opts.points ? Math.abs(now - before).toFixed(1) + ' pts' : Math.abs(((now - before) / before) * 100).toFixed(1) + '%';
    el.textContent = (up ? '▲ ' : '▼ ') + size + ' vs last 4 wks';
    el.classList.toggle('is-down', opts.inverse ? up : !up);
  }

  function renderStats() {
    var end = newest();
    var recent = orders.filter(function (o) { return Date.parse(o.date) > end - 28 * DAY; });
    var prior = orders.filter(function (o) { var t = Date.parse(o.date); return t <= end - 28 * DAY && t > end - 56 * DAY; });
    var rev = sum(paid(recent)), revPrior = sum(paid(prior));
    byId('s-revenue').textContent = money.format(rev);
    delta(byId('d-revenue'), rev, revPrior);
    byId('s-orders').textContent = recent.length;
    delta(byId('d-orders'), recent.length, prior.length);
    var aov = recent.length ? rev / Math.max(1, paid(recent).length) : 0;
    var aovPrior = prior.length ? revPrior / Math.max(1, paid(prior).length) : 0;
    byId('s-aov').textContent = money.format(aov);
    delta(byId('d-aov'), aov, aovPrior);
    var refunds = recent.length ? recent.filter(function (o) { return o.status === 'Refunded'; }).length / recent.length * 100 : 0;
    var refundsPrior = prior.length ? prior.filter(function (o) { return o.status === 'Refunded'; }).length / prior.length * 100 : 0;
    byId('s-refunds').textContent = refunds.toFixed(1) + '%';
    delta(byId('d-refunds'), refunds, refundsPrior, { points: true, inverse: true });
  }

  function renderCharts() {
    var end = newest();
    var labels = [], values = [];
    for (var w = 11; w >= 0; w--) {
      var to = end - w * 7 * DAY, from = to - 7 * DAY;
      labels.push(new Date(from + DAY).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }));
      values.push(sum(paid(orders.filter(function (o) { var t = Date.parse(o.date); return t > from && t <= to; }))));
    }
    Oro.chart(byId('chart-revenue'), { type: 'line', labels: labels, values: values, format: function (v) { return money.format(v); } });

    var totals = {};
    paid(orders).forEach(function (o) { totals[o.product] = (totals[o.product] || 0) + Number(o.total); });
    var top = Object.keys(totals).map(function (k) { return { name: k, total: totals[k] }; })
      .sort(function (a, b) { return b.total - a.total; }).slice(0, 5);
    var max = top.length ? top[0].total : 1;
    byId('top-products').innerHTML = top.length ? top.map(function (p) {
      return '<li><div class="top-row"><strong>' + Oro.esc(p.name) + '</strong><span>' + money.format(p.total) + '</span></div>' +
        '<div class="meter"><span style="width:' + Math.round((p.total / max) * 100) + '%"></span></div></li>';
    }).join('') : '<li class="empty">No sales yet</li>';
  }

  function renderTable() {
    var rows = orders.map(function (o) {
      return { id: '#' + o.id, customer: o.customer, product: o.product, total: Number(o.total), status: o.status, date: o.date };
    });
    if (table) { table.update(rows); return; }
    table = Oro.table(byId('orders-table'), {
      columns: [
        { key: 'id', label: 'Order' },
        { key: 'customer', label: 'Customer' },
        { key: 'product', label: 'Product' },
        { key: 'status', label: 'Status' },
        { key: 'date', label: 'Date', format: function (v) { return new Date(v).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }); } },
        { key: 'total', label: 'Total', num: true, format: function (v) { return money.format(v); } }
      ],
      rows: rows,
      pageSize: 8,
      search: byId('order-search'),
      empty: 'No orders yet — add your first one.'
    });
  }

  function render() { renderStats(); renderCharts(); renderTable(); }

  async function load() {
    byId('load-error').hidden = true;
    try {
      orders = await Oro.api.get('/orders?_sort=date&_order=desc');
      render();
    } catch (e) {
      byId('load-error').hidden = false;
    }
  }
  byId('retry').addEventListener('click', load);

  // New order dialog.
  var dialog = byId('order-dialog');
  var form = byId('order-form');
  byId('new-order').addEventListener('click', function () {
    form.reset();
    byId('o-error').hidden = true;
    dialog.showModal();
    byId('o-customer').focus();
  });
  byId('o-cancel').addEventListener('click', function () { dialog.close(); });
  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    var customer = byId('o-customer').value.trim();
    byId('o-error').hidden = !!customer;
    if (!customer) { byId('o-customer').focus(); return; }
    var select = byId('o-product');
    var qty = Math.max(1, Number(byId('o-qty').value) || 1);
    var price = Number(select.options[select.selectedIndex].getAttribute('data-price'));
    var save = byId('o-save');
    Oro.setBusy(save, true);
    try {
      var created = await Oro.api.post('/orders', {
        customer: customer, product: select.value, qty: qty, total: price * qty,
        status: 'Paid', date: new Date().toISOString().slice(0, 10)
      });
      orders.unshift(created);
      render();
      dialog.close();
      Oro.toast('Order saved', 'success');
    } catch (err) {
      Oro.toast(err.message || 'Couldn’t save the order', 'error');
    } finally {
      Oro.setBusy(save, false);
    }
  });

  load();
})();
