// Phone frame + the photographer site Orobi "builds" inside it.
// Site is laid out in 360-unit design space and scaled to the screen.
import { el, box, css, show, ease, prog, r2, px, clamp } from './core.js';

const PHOTOS = {
  // Monochrome, graphic "photographs" — no stock imagery, fully vector.
  sea: (w, h) => `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice"><rect width="${w}" height="${h}" fill="#2B3039"/>
    <circle cx="${w * 0.68}" cy="${h * 0.42}" r="${h * 0.2}" fill="#E9ECF1"/>
    <rect y="${h * 0.6}" width="${w}" height="${h * 0.4}" fill="#171A21"/>
    ${[0, 1, 2, 3, 4].map((i) => `<rect x="${w * 0.68 - (36 - i * 6)}" y="${h * (0.64 + i * 0.06)}" width="${(36 - i * 6) * 2}" height="3" rx="1.5" fill="#E9ECF1" opacity="${0.55 - i * 0.09}"/>`).join('')}</svg>`,
  arch: (w, h) => `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice"><rect width="${w}" height="${h}" fill="#D9DDE3"/>
    <path d="M${w * 0.28},${h} V${h * 0.45} A${w * 0.22},${w * 0.22} 0 0 1 ${w * 0.72},${h * 0.45} V${h} Z" fill="#3A404C"/>
    <rect x="${w * 0.47}" y="${h * 0.66}" width="${w * 0.06}" height="${h * 0.22}" rx="${w * 0.03}" fill="#D9DDE3"/>
    <circle cx="${w * 0.5}" cy="${h * 0.62}" r="${w * 0.035}" fill="#D9DDE3"/></svg>`,
  dunes: (w, h) => `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice"><rect width="${w}" height="${h}" fill="#BCC1C9"/>
    <path d="M0,${h * 0.55} C${w * 0.3},${h * 0.35} ${w * 0.55},${h * 0.7} ${w},${h * 0.45} V${h} H0Z" fill="#6E7582"/>
    <path d="M0,${h * 0.8} C${w * 0.35},${h * 0.6} ${w * 0.6},${h * 0.95} ${w},${h * 0.72} V${h} H0Z" fill="#2E333C"/></svg>`,
  portrait: (w, h) => `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice"><rect width="${w}" height="${h}" fill="#1D2129"/>
    <rect x="0" y="0" width="${w * 0.42}" height="${h}" fill="#262B34"/>
    <circle cx="${w * 0.52}" cy="${h * 0.4}" r="${w * 0.16}" fill="#9AA2AF"/>
    <path d="M${w * 0.18},${h} C${w * 0.22},${h * 0.66} ${w * 0.82},${h * 0.66} ${w * 0.86},${h} Z" fill="#7A828F"/></svg>`,
  light: (w, h) => `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice"><rect width="${w}" height="${h}" fill="#EEF0F3"/>
    <path d="M${w * 0.15},0 L${w},${h * 0.85} L${w},0Z" fill="#A3AAB5"/>
    <path d="M${w * 0.42},0 L${w},${h * 0.5} L${w},0Z" fill="#5E6570"/>
    <rect x="${w * 0.12}" y="${h * 0.62}" width="${w * 0.3}" height="${h * 0.3}" rx="${w * 0.15}" fill="#2B3039"/></svg>`,
};

export function createPhone(parent, P, site, labels) {
  const D = P.dark, Lt = P.light;
  const root = box(parent, 'phone');
  const frame = box(root, 'phone-frame');
  const screen = box(frame, 'phone-screen');
  const inner = box(screen, 'phone-inner'); // 360-wide design space
  // Status bar + app chrome (dark, monochrome UI)
  const status = box(inner, 'ph-status', { left: '0', top: '0', width: '360px', height: '34px' });
  status.innerHTML = `<span class="ph-time">9:41</span><span class="ph-icons"><i></i><i></i><b></b></span>`;
  const chrome = box(inner, 'ph-chrome', { left: '0', top: '34px', width: '360px', height: '50px' });
  chrome.innerHTML = `<span class="ph-title">${labels.preview}</span><span class="ph-live"><i class="dot"></i>${labels.live}</span>`;
  const ping = chrome.querySelector('.dot');
  const camera = el('div', { class: 'abs ph-pip' }, chrome);
  const viewport = box(inner, 'ph-view', { left: '0', top: '84px', width: '360px', height: '1000px', background: Lt.bg });
  const page = box(viewport, 'ph-page', { left: '0', top: '0', width: '360px', height: '1400px' });

  // Site blocks (design units). Each block: [el, buildDelay]
  const B = [];
  const add = (cls, style, html, delay, kind = 'rise') => {
    const n = box(page, cls, style);
    if (html != null) n.innerHTML = html;
    B.push({ n, delay, kind });
    return n;
  };
  // A real landing page, top to bottom, in 360-unit design space.
  const ICON = {
    cam: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h3l2-2h6l2 2h3v11H4z"/><circle cx="12" cy="13" r="3.5"/></svg>',
    ring: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="14" r="5"/><circle cx="15" cy="14" r="5"/><path d="M9 6l3-3 3 3"/></svg>',
    mag: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 9h8M8 13h8M8 17h5"/></svg>',
  };
  add('st-nav', { left: '0', top: '0', width: '360px', height: '52px' },
    `<span class="st-logo"><i></i>${site.brand}</span><span class="st-links"><b>Work</b><b>About</b></span><span class="st-book">Book</span>`, 0.0);
  add('st-hero', { left: '12px', top: '60px', width: '336px', height: '300px' },
    `${PHOTOS.sea(336, 300)}<div class="st-hero-shade"></div>
     <div class="st-hero-copy"><span class="st-eyebrow">${site.kicker}</span><h1>${site.title}</h1>
     <p>Portraits, weddings and editorial work in natural light.</p>
     <div class="st-ctas"><span class="st-btn">${site.cta}</span><span class="st-btn ghost">View work</span></div></div>`, 0.1, 'photo');
  add('st-stats', { left: '20px', top: '374px', width: '320px', height: '44px' },
    `<span><b>12</b>years</span><span><b>300+</b>shoots</span><span><b>4.9</b>★ rating</span>`, 0.26);
  add('st-press', { left: '20px', top: '432px', width: '320px', height: '22px' },
    `<i>AS SEEN IN</i><b>VOGUE</b><b>Kinfolk</b><b>MONOCLE</b>`, 0.32);
  add('st-label', { left: '20px', top: '476px', width: '320px' }, `<span>Services</span><i></i>`, 0.36);
  [['cam', 'Portraits', 'from €350'], ['ring', 'Weddings', 'from €1,800'], ['mag', 'Editorial', 'on request']].forEach(([ic, t, price], i) =>
    add('st-card', { left: '20px', top: `${500 + i * 62}px`, width: '320px', height: '54px' },
      `<span class="st-ic">${ICON[ic]}</span><span class="st-card-t"><b>${t}</b><i>${['Studio or on location', 'Full day, two shooters', 'Brands and magazines'][i]}</i></span><span class="st-price">${price}</span>`,
      0.42 + i * 0.06));
  add('st-label', { left: '20px', top: '700px', width: '320px' }, `<span>${site.galleryLabel}</span><i></i>`, 0.6);
  const tiles = [PHOTOS.arch, PHOTOS.dunes, PHOTOS.portrait, PHOTOS.light];
  const caps = ['Alfama, dusk', 'Dunes, Comporta', 'Studio portrait', 'Morning light'];
  tiles.forEach((f, i) => add('st-photo st-tile', {
    left: `${20 + (i % 2) * 166}px`, top: `${724 + Math.floor(i / 2) * 178}px`, width: '154px', height: '168px',
  }, `${f(154, 140)}<span class="st-cap">${caps[i]}</span>`, 0.64 + i * 0.06, 'photo'));
  add('st-quote', { left: '20px', top: '1086px', width: '320px', height: '104px' },
    `<span class="st-stars">★★★★★</span><p>“Mara found the light in a grey Lisbon morning. The photos feel like us.”</p><span class="st-who"><i></i>Inês & Tom · Wedding, 2026</span>`, 0.86);
  add('st-band', { left: '12px', top: '1206px', width: '336px', height: '150px' },
    `<h2>Let’s work together.</h2><p>Booking spring and summer 2027.</p><span class="st-btn light">${site.cta} →</span><span class="st-mail">${site.email}</span>`, 0.92, 'pop');
  add('st-foot', { left: '20px', top: '1372px', width: '320px', height: '20px' },
    `<span>© 2026 ${site.brand}</span><span>Instagram · Email</span>`, 0.98);

  // Skeleton lines shown while "thinking" (before build)
  const skel = box(viewport, 'ph-skel', { left: '0', top: '0', width: '360px', height: '900px' });
  [[20, 22, 120], [20, 62, 320, 210], [20, 290, 220, 30], [20, 340, 150]].forEach(([x, y, w, h = 12]) =>
    box(skel, 'sk', { left: `${x}px`, top: `${y}px`, width: `${w}px`, height: `${h}px` }));

  return {
    root,
    /** Geometry: centre, height (px). Width follows a 19.5:9 handset. */
    set({ cx, cy, h, opacity = 1, rot = 0, scale = 1, z = 8 }) {
      const w = h * 0.462;
      const bez = Math.max(9, h * 0.016);
      css(root, {
        left: px(cx - w / 2), top: px(cy - h / 2), width: px(w), height: px(h),
        opacity: String(r2(opacity)), display: opacity <= 0.001 ? 'none' : '',
        transform: `rotate(${r2(rot)}deg) scale(${r2(scale)})`, zIndex: String(z),
      });
      css(frame, { borderRadius: px(h * 0.075), padding: px(bez) });
      css(screen, { borderRadius: px(h * 0.075 - bez) });
      const sw = w - bez * 2;
      css(inner, { transform: `scale(${r2(sw / 360)})` });
      css(camera, {});
      this.k = sw / 360;
      this.screenH = (h - bez * 2) / (sw / 360);
    },
    /**
     * bt = seconds since build started (negative = not yet).
     * skelOn = 0..1 skeleton shimmer visibility, scroll = design units, live = 0..1 live dot
     */
    content({ bt = -1, skelOn = 0, scroll = 0, live = 0, pingT = -1, chromeOn = 1 }) {
      css(viewport, { height: px(this.screenH - 84) });
      css(chrome, { opacity: String(r2(chromeOn)) });
      css(page, { transform: `translateY(${r2(-scroll)}px)` });
      for (const b of B) {
        const k = prog(bt, b.delay, b.delay + 0.5);
        const e = ease.emph(k);
        if (b.kind === 'photo') {
          // Masked reveal: clip from bottom, inner image settles from 1.12 scale.
          const img = b.n.firstChild;
          css(b.n, { clipPath: `inset(${r2((1 - e) * 100)}% 0 0 0 round 10px)`, opacity: k > 0 ? '1' : '0' });
          if (img) css(img, { transform: `scale(${r2(1.14 - 0.14 * e)})` });
        } else if (b.kind === 'pop') {
          const s = ease.back(prog(bt, b.delay, b.delay + 0.45));
          css(b.n, { transform: `scale(${r2(0.6 + 0.4 * s)})`, opacity: String(r2(clamp(k * 3))) });
        } else {
          css(b.n, { transform: `translateY(${r2((1 - e) * 18)}px)`, opacity: String(r2(clamp(k * 2.2))), clipPath: `inset(0 ${r2((1 - e) * 100)}% 0 0)` });
        }
      }
      css(skel, { opacity: String(r2(skelOn)), display: skelOn <= 0.001 ? 'none' : '' });
      if (skelOn > 0) {
        [...skel.children].forEach((s, i) => css(s, { opacity: String(r2(0.5 + 0.5 * Math.sin((bt + 10) * 6 - i * 0.9))) }));
      }
      css(ping, { background: live > 0.5 ? P.mascot.success : D.muted, boxShadow: pingT >= 0 && pingT < 0.7 ? `0 0 0 ${r2(ease.emph(pingT / 0.7) * 9)}px rgba(61,220,151,${r2(0.45 * (1 - pingT / 0.7))})` : 'none' });
    },
  };
}
