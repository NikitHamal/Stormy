package com.oro.app.data.ai.skills

/** Starter files for the built-in landing-page skill: small, valid, accessible, mobile-first. */
internal object StarterFiles {
    const val INDEX = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Brand — One line about it</title>
  <meta name="description" content="One sentence that says what this is and who it is for.">
  <meta name="theme-color" content="#0E1117">
  <script>document.documentElement.dataset.theme = localStorage.theme || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');</script>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <a class="skip" href="#main">Skip to content</a>
  <header class="nav">
    <a class="logo" href="#">Brand</a>
    <button class="menu-btn" type="button" aria-expanded="false" aria-controls="menu" aria-label="Open menu">☰</button>
    <nav id="menu" class="menu" aria-label="Main">
      <a href="#features">Features</a>
      <a href="#contact">Contact</a>
      <button class="theme-btn" type="button" aria-label="Toggle dark mode">◐</button>
    </nav>
  </header>
  <main id="main">
    <section class="hero">
      <p class="eyebrow">New</p>
      <h1>A clear promise in a few words.</h1>
      <p class="lead">One or two sentences that explain the value, in the visitor's words.</p>
      <div class="actions"><a class="btn" href="#contact">Get started</a><a class="btn ghost" href="#features">Learn more</a></div>
    </section>
    <section id="features" class="features" aria-labelledby="features-title">
      <h2 id="features-title">Why it works</h2>
      <div class="cards">
        <article class="card"><h3>Fast</h3><p>A short benefit, not a feature list.</p></article>
        <article class="card"><h3>Simple</h3><p>Another benefit the visitor cares about.</p></article>
        <article class="card"><h3>Friendly</h3><p>A third reason, in plain language.</p></article>
      </div>
    </section>
    <section id="contact" class="cta">
      <h2>Ready when you are.</h2>
      <a class="btn" href="mailto:hello@example.com">Say hello</a>
    </section>
  </main>
  <footer class="footer"><p>© <span id="year"></span> Brand</p></footer>
  <script src="app.js" defer></script>
</body>
</html>
"""

    const val CSS = """:root {
  --bg: #F7F8FA; --surface: #FFFFFF; --text: #0E1117; --muted: #5A6273; --accent: #3B5BDB; --border: #E2E5EA;
  --radius: 16px; --max: 1080px;
}
[data-theme="dark"] { --bg: #0B0D12; --surface: #12151C; --text: #E8ECF2; --muted: #9AA3B2; --accent: #8EA6FF; --border: #262C38; }
*, *::before, *::after { box-sizing: border-box; }
body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; background: var(--bg); color: var(--text); line-height: 1.6; }
img { max-width: 100%; height: auto; display: block; }
a { color: inherit; }
.skip { position: absolute; left: -999px; } .skip:focus { left: 1rem; top: 1rem; z-index: 99; }
.nav { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  padding: .75rem 1.25rem; background: color-mix(in srgb, var(--bg) 88%, transparent); backdrop-filter: blur(8px); border-bottom: 1px solid var(--border); }
.logo { font-weight: 700; text-decoration: none; }
.menu { display: none; flex-direction: column; gap: .5rem; position: absolute; top: 100%; left: 0; right: 0; padding: 1rem 1.25rem; background: var(--surface); border-bottom: 1px solid var(--border); }
.menu.open { display: flex; }
.menu a { text-decoration: none; padding: .5rem 0; }
.menu-btn, .theme-btn { min-width: 44px; min-height: 44px; border: 0; background: none; color: inherit; font-size: 1.25rem; cursor: pointer; }
main { max-width: var(--max); margin: 0 auto; padding: 0 1.25rem; }
.hero { padding: clamp(3rem, 10vw, 7rem) 0; }
.eyebrow { display: inline-block; font-size: .8rem; font-weight: 600; letter-spacing: .12em; text-transform: uppercase; color: var(--accent); margin: 0; }
h1 { font-size: clamp(2.2rem, 1.2rem + 5vw, 4rem); line-height: 1.05; letter-spacing: -0.03em; margin: .5rem 0 1rem; }
.lead { font-size: 1.15rem; color: var(--muted); max-width: 55ch; }
.actions { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 1.5rem; }
.btn { display: inline-flex; align-items: center; min-height: 44px; padding: 0 1.25rem; border-radius: 999px; background: var(--accent); color: #fff; font-weight: 600; text-decoration: none; transition: transform .15s; }
.btn:active { transform: scale(.97); }
.btn.ghost { background: transparent; color: var(--text); box-shadow: inset 0 0 0 1.5px var(--border); }
.features { padding: 3rem 0; }
.cards { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
.card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 1.25rem; }
.card h3 { margin-top: 0; }
.cta { margin: 3rem 0; padding: 2.5rem 1.5rem; border-radius: 24px; background: var(--text); color: var(--bg); text-align: center; }
.cta .btn { background: var(--bg); color: var(--text); }
.footer { text-align: center; color: var(--muted); padding: 2rem 1rem; font-size: .9rem; }
a:focus-visible, button:focus-visible { outline: 3px solid var(--accent); outline-offset: 2px; }
@media (min-width: 720px) {
  .menu-btn { display: none; }
  .menu { display: flex; flex-direction: row; align-items: center; position: static; padding: 0; background: none; border: 0; gap: 1.5rem; }
}
@media (prefers-reduced-motion: reduce) { * { transition: none !important; scroll-behavior: auto !important; } }
"""

    const val JS = """// Mobile menu, theme toggle, footer year.
const menuBtn = document.querySelector('.menu-btn');
const menu = document.getElementById('menu');
menuBtn?.addEventListener('click', () => {
  const open = menu.classList.toggle('open');
  menuBtn.setAttribute('aria-expanded', String(open));
});
menu?.addEventListener('click', (e) => {
  if (e.target.closest('a')) { menu.classList.remove('open'); menuBtn?.setAttribute('aria-expanded', 'false'); }
});
document.querySelector('.theme-btn')?.addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.theme = next;
});
document.getElementById('year').textContent = new Date().getFullYear();
"""
}
