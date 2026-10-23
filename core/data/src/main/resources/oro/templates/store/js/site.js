// Kiln & Co — catalogue from Oro's built-in backend (/api/products in db.json), basket in localStorage.
(function () {
  'use strict';

  var money = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', maximumFractionDigits: 0 });
  var FREE_SHIPPING = 60;
  var KEY = 'kiln-basket';
  var products = [];
  var category = 'All';
  var basket = read();

  function byId(id) { return document.getElementById(id); }
  function read() { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; } }
  function write() { localStorage.setItem(KEY, JSON.stringify(basket)); }
  function find(id) { return products.find(function (p) { return String(p.id) === String(id); }); }
  function piece(p) { return '<span class="piece ' + Oro.esc(p.art) + '" style="--tone:' + Oro.esc(p.tone) + '"></span>'; }

  function renderFilters() {
    var cats = ['All'].concat(products.map(function (p) { return p.category; }).filter(function (c, i, a) { return a.indexOf(c) === i; }));
    byId('filters').innerHTML = cats.map(function (c) {
      return '<button class="chip" aria-pressed="' + (c === category) + '" data-cat="' + Oro.esc(c) + '">' + Oro.esc(c) + '</button>';
    }).join('');
  }

  function sorted(list) {
    var mode = byId('sort').value;
    var copy = list.slice();
    if (mode === 'price-asc') copy.sort(function (a, b) { return a.price - b.price; });
    if (mode === 'price-desc') copy.sort(function (a, b) { return b.price - a.price; });
    if (mode === 'rating') copy.sort(function (a, b) { return b.rating - a.rating; });
    return copy;
  }

  function renderProducts() {
    var list = sorted(products.filter(function (p) { return category === 'All' || p.category === category; }));
    byId('no-products').hidden = list.length > 0;
    byId('products').innerHTML = list.map(function (p) {
      var sold = p.stock <= 0;
      var flag = sold ? '<span class="flag sold">Sold out</span>' : p.new ? '<span class="flag">New</span>' : p.stock <= 5 ? '<span class="flag">Only ' + p.stock + ' left</span>' : '';
      return '<article class="product reveal">' +
        '<div class="product-art">' + flag + piece(p) + '</div>' +
        '<h3>' + Oro.esc(p.name) + '</h3><p class="glaze">' + Oro.esc(p.glaze) + ' · ★ ' + p.rating + '</p>' +
        '<div class="buy"><span class="price">' + money.format(p.price) + '</span>' +
        '<button class="btn btn-sm ' + (sold ? 'btn-outline' : 'btn-primary') + '" data-add="' + p.id + '"' + (sold ? ' disabled' : '') + '>' +
        (sold ? 'Sold out' : 'Add to basket') + '</button></div></article>';
    }).join('');
    Oro.reveal(byId('products'));
  }

  function renderBasket() {
    var ids = Object.keys(basket).filter(function (id) { return basket[id] > 0 && find(id); });
    var count = ids.reduce(function (n, id) { return n + basket[id]; }, 0);
    var total = ids.reduce(function (t, id) { return t + basket[id] * find(id).price; }, 0);
    var badge = byId('cart-count');
    if (badge.textContent !== String(count)) { badge.classList.remove('bump'); void badge.offsetWidth; badge.classList.add('bump'); }
    badge.textContent = count;
    byId('open-cart').setAttribute('aria-label', 'Open basket, ' + count + (count === 1 ? ' item' : ' items'));
    byId('cart-lines').innerHTML = ids.map(function (id) {
      var p = find(id);
      return '<li class="line"><span class="thumb">' + piece(p) + '</span><div><strong>' + Oro.esc(p.name) + '</strong>' +
        '<span class="qty"><button aria-label="One fewer ' + Oro.esc(p.name) + '" data-dec="' + id + '">−</button>' +
        '<span aria-live="polite">' + basket[id] + '</span>' +
        '<button aria-label="One more ' + Oro.esc(p.name) + '" data-inc="' + id + '">+</button></span></div>' +
        '<span class="price">' + money.format(basket[id] * p.price) + '</span></li>';
    }).join('');
    byId('cart-empty').hidden = ids.length > 0;
    byId('cart-foot').hidden = ids.length === 0;
    byId('cart-total').textContent = money.format(total);
    byId('shipping-note').textContent = total >= FREE_SHIPPING ? 'Free delivery included.' : 'Add ' + money.format(FREE_SHIPPING - total) + ' more for free delivery.';
  }

  function openCart(open) {
    byId('cart').classList.toggle('is-open', open);
    byId('cart').setAttribute('aria-hidden', String(!open));
    byId('scrim').hidden = !open;
    if (open) byId('close-cart').focus();
  }

  document.addEventListener('click', function (e) {
    var add = e.target.closest('[data-add]');
    if (add) {
      var p = find(add.getAttribute('data-add'));
      var inBasket = basket[p.id] || 0;
      if (inBasket >= p.stock) { Oro.toast('That’s every ' + p.name + ' we have.', 'error'); return; }
      basket[p.id] = inBasket + 1; write(); renderBasket();
      Oro.toast(p.name + ' added to your basket', 'success');
      return;
    }
    var inc = e.target.closest('[data-inc]'), dec = e.target.closest('[data-dec]');
    if (inc || dec) {
      var id = (inc || dec).getAttribute(inc ? 'data-inc' : 'data-dec');
      var max = find(id).stock;
      basket[id] = Math.max(0, Math.min(max, (basket[id] || 0) + (inc ? 1 : -1)));
      if (!basket[id]) delete basket[id];
      write(); renderBasket();
      return;
    }
    var chip = e.target.closest('[data-cat]');
    if (chip) { category = chip.getAttribute('data-cat'); renderFilters(); renderProducts(); }
  });
  byId('sort').addEventListener('change', renderProducts);
  byId('open-cart').addEventListener('click', function () { openCart(true); });
  byId('close-cart').addEventListener('click', function () { openCart(false); });
  byId('scrim').addEventListener('click', function () { openCart(false); });
  byId('cart-browse').addEventListener('click', function () { openCart(false); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') openCart(false); });
  byId('checkout').addEventListener('click', function () {
    Oro.toast('Checkout is where your payment provider goes — Stripe or similar.', 'success');
  });

  async function load() {
    byId('shop-error').hidden = true;
    byId('products').innerHTML = Oro.skeleton(4, 320);
    try {
      products = await Oro.api.get('/products');
      renderFilters(); renderProducts(); renderBasket();
    } catch (e) {
      byId('products').innerHTML = '';
      byId('shop-error').hidden = false;
    }
  }
  byId('retry').addEventListener('click', load);
  load();
})();
