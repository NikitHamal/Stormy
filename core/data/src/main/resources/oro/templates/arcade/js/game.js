// Starhop — a small canvas arcade game: dodge rocks, catch stars, speed ramps up.
(function () {
  'use strict';

  var canvas = document.getElementById('game');
  var ctx = canvas.getContext('2d');
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var $ = function (id) { return document.getElementById(id); };
  var BEST = 'starhop-best';

  var W = 0, H = 0, dpr = 1;
  var state = 'start';
  var player, rocks, stars, sparks, bg, score, lives, speed, spawn, last, shake, invuln;
  var keys = { left: false, right: false };
  var pointerX = null;

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = canvas.clientWidth; H = canvas.clientHeight;
    canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (player) player.y = H - Math.max(90, H * 0.14);
  }

  function makeBackground() {
    bg = [];
    for (var i = 0; i < 90; i++) bg.push({ x: Math.random() * W, y: Math.random() * H, r: Math.random() * 1.6 + 0.3, s: Math.random() * 0.6 + 0.2 });
  }

  function reset() {
    player = { x: W / 2, y: H - Math.max(90, H * 0.14), r: 18, vx: 0 };
    rocks = []; stars = []; sparks = [];
    score = 0; lives = 3; speed = 180; spawn = 0; shake = 0; invuln = 0;
    updateHud();
  }

  function updateHud() {
    $('score').textContent = Math.floor(score);
    var pips = $('lives').children;
    for (var i = 0; i < pips.length; i++) pips[i].classList.toggle('lost', i >= lives);
    $('lives').setAttribute('aria-label', lives + (lives === 1 ? ' life' : ' lives'));
  }

  function show(id) {
    ['start', 'paused', 'over'].forEach(function (s) { $(s).hidden = s !== id; });
    $('pause').hidden = id !== null;
  }

  function start() { reset(); state = 'play'; show(null); last = performance.now(); requestAnimationFrame(loop); }
  function pause() { if (state !== 'play') return; state = 'paused'; show('paused'); $('resume').focus(); }
  function resume() { if (state !== 'paused') return; state = 'play'; show(null); last = performance.now(); requestAnimationFrame(loop); }

  function gameOver() {
    state = 'over';
    var best = Number(localStorage.getItem(BEST) || 0);
    var final = Math.floor(score);
    var record = final > best;
    if (record) localStorage.setItem(BEST, String(final));
    $('final').textContent = final;
    $('final-note').textContent = record ? 'New best score!' : 'Best: ' + best;
    show('over');
    $('again').focus();
  }

  function burst(x, y, color, n) {
    if (reduce) n = Math.min(n, 6);
    for (var i = 0; i < n; i++) {
      var a = Math.random() * Math.PI * 2, v = 60 + Math.random() * 220;
      sparks.push({ x: x, y: y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 0.6 + Math.random() * 0.4, color: color });
    }
  }

  function step(dt) {
    score += dt * 10;
    speed = 180 + score * 1.4;

    // Move: a held key or a dragging finger.
    var target = pointerX;
    if (keys.left) target = player.x - 400;
    if (keys.right) target = player.x + 400;
    if (target !== null) player.vx += (target - player.x) * 18 * dt;
    player.vx *= Math.pow(0.0008, dt);
    player.x = Math.max(player.r, Math.min(W - player.r, player.x + player.vx * dt));

    spawn -= dt;
    if (spawn <= 0) {
      spawn = Math.max(0.28, 0.9 - score / 400);
      var r = 14 + Math.random() * 22;
      rocks.push({ x: r + Math.random() * (W - 2 * r), y: -r, r: r, spin: Math.random() * 6, rot: 0, vy: speed * (0.8 + Math.random() * 0.5) });
      if (Math.random() < 0.35) stars.push({ x: 20 + Math.random() * (W - 40), y: -20, vy: speed * 0.8, t: 0 });
    }

    rocks.forEach(function (k) { k.y += k.vy * dt; k.rot += k.spin * dt; });
    stars.forEach(function (s) { s.y += s.vy * dt; s.t += dt; });
    sparks.forEach(function (p) { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.96; p.vy *= 0.96; p.life -= dt; });
    bg.forEach(function (b) { b.y += b.s * speed * 0.12 * dt; if (b.y > H) { b.y = 0; b.x = Math.random() * W; } });

    if (invuln > 0) invuln -= dt;
    rocks = rocks.filter(function (k) {
      var hit = invuln <= 0 && Math.hypot(k.x - player.x, k.y - player.y) < k.r + player.r * 0.8;
      if (hit) {
        lives--; invuln = 1.2; shake = reduce ? 0 : 0.35;
        burst(player.x, player.y, '#ff5c8a', 26);
        if (navigator.vibrate) navigator.vibrate(60);
        updateHud();
        if (lives <= 0) gameOver();
        return false;
      }
      return k.y < H + k.r;
    });
    stars = stars.filter(function (s) {
      if (Math.hypot(s.x - player.x, s.y - player.y) < player.r + 14) {
        score += 25; burst(s.x, s.y, '#ffe066', 14); updateHud();
        return false;
      }
      return s.y < H + 20;
    });
    sparks = sparks.filter(function (p) { return p.life > 0; });
    if (shake > 0) shake -= dt;
    $('score').textContent = Math.floor(score);
  }

  function drawStar(x, y, r, t) {
    ctx.save(); ctx.translate(x, y); ctx.rotate(t * 2);
    ctx.beginPath();
    for (var i = 0; i < 10; i++) {
      var a = (i / 10) * Math.PI * 2, rr = i % 2 ? r * 0.45 : r;
      ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
    }
    ctx.closePath(); ctx.fillStyle = '#ffe066'; ctx.shadowColor = '#ffd23f'; ctx.shadowBlur = 18; ctx.fill();
    ctx.restore();
  }

  function draw() {
    ctx.save();
    if (shake > 0) ctx.translate((Math.random() - 0.5) * 10, (Math.random() - 0.5) * 10);
    var g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, '#0b0420'); g.addColorStop(1, '#2a0b4d');
    ctx.fillStyle = g; ctx.fillRect(-20, -20, W + 40, H + 40);

    ctx.fillStyle = 'rgba(255,255,255,.8)';
    bg.forEach(function (b) { ctx.globalAlpha = b.s; ctx.beginPath(); ctx.arc(b.x, b.y, b.r, 0, Math.PI * 2); ctx.fill(); });
    ctx.globalAlpha = 1;

    stars.forEach(function (s) { drawStar(s.x, s.y, 13, s.t); });

    rocks.forEach(function (k) {
      ctx.save(); ctx.translate(k.x, k.y); ctx.rotate(k.rot);
      ctx.beginPath();
      for (var i = 0; i < 9; i++) {
        var a = (i / 9) * Math.PI * 2, rr = k.r * (0.82 + ((i * 37) % 7) / 30);
        ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
      }
      ctx.closePath();
      var rg = ctx.createRadialGradient(-k.r * 0.3, -k.r * 0.3, 2, 0, 0, k.r);
      rg.addColorStop(0, '#8e83b8'); rg.addColorStop(1, '#3b315e');
      ctx.fillStyle = rg; ctx.fill();
      ctx.restore();
    });

    // The ship: a glowing triangle that blinks while invulnerable.
    if (state !== 'start' && !(invuln > 0 && Math.floor(invuln * 10) % 2)) {
      ctx.save(); ctx.translate(player.x, player.y); ctx.rotate(player.vx * 0.0012);
      ctx.shadowColor = '#c17bff'; ctx.shadowBlur = 24;
      var sg = ctx.createLinearGradient(0, -22, 0, 20);
      sg.addColorStop(0, '#f2d4ff'); sg.addColorStop(1, '#a855f7');
      ctx.fillStyle = sg;
      ctx.beginPath(); ctx.moveTo(0, -24); ctx.lineTo(18, 18); ctx.lineTo(0, 10); ctx.lineTo(-18, 18); ctx.closePath(); ctx.fill();
      ctx.fillStyle = 'rgba(255,170,90,' + (0.5 + Math.random() * 0.5) + ')';
      ctx.beginPath(); ctx.moveTo(-7, 16); ctx.lineTo(0, 30 + Math.random() * 8); ctx.lineTo(7, 16); ctx.fill();
      ctx.restore();
    }

    sparks.forEach(function (p) {
      ctx.globalAlpha = Math.max(0, p.life); ctx.fillStyle = p.color;
      ctx.fillRect(p.x - 2, p.y - 2, 4, 4);
    });
    ctx.globalAlpha = 1;
    ctx.restore();
  }

  function loop(now) {
    if (state !== 'play') { draw(); return; }
    var dt = Math.min(0.033, (now - last) / 1000);
    last = now;
    step(dt);
    draw();
    if (state === 'play') requestAnimationFrame(loop);
  }

  // Input: keyboard, pointer drag, and pausing when the tab hides.
  window.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowLeft' || e.key === 'a') keys.left = true;
    if (e.key === 'ArrowRight' || e.key === 'd') keys.right = true;
    if (e.key === ' ' || e.key === 'Escape' || e.key === 'p') { e.preventDefault(); state === 'play' ? pause() : resume(); }
  });
  window.addEventListener('keyup', function (e) {
    if (e.key === 'ArrowLeft' || e.key === 'a') keys.left = false;
    if (e.key === 'ArrowRight' || e.key === 'd') keys.right = false;
  });
  canvas.addEventListener('pointerdown', function (e) { pointerX = e.clientX; });
  canvas.addEventListener('pointermove', function (e) { if (e.buttons || e.pointerType === 'touch') pointerX = e.clientX; });
  window.addEventListener('pointerup', function () { pointerX = null; });
  document.addEventListener('visibilitychange', function () { if (document.hidden) pause(); });
  window.addEventListener('resize', function () { resize(); makeBackground(); if (state !== 'play') draw(); });

  $('play').addEventListener('click', start);
  $('again').addEventListener('click', start);
  $('resume').addEventListener('click', resume);
  $('pause').addEventListener('click', pause);

  resize(); makeBackground(); reset(); draw();
  var best = Number(localStorage.getItem(BEST) || 0);
  if (best) $('best-start').textContent = 'Best: ' + best;
  $('play').focus();
})();
