// Onboarding: three pages, then Get started (OnboardingScreen.kt).
import { h, ic } from '../ui/kit.js';
import { orobi } from '../ui/orobi.js';
import { navigate } from '../app.js';

const PAGES = [
  { title: 'Meet Orobi', body: 'Your coding agent. Describe a website and Orobi builds it, right on your phone.' },
  { title: 'Describe it. See it live.', body: 'Orobi reads, writes and edits your project files while the live preview updates.' },
  { title: 'Any model. No key.', body: 'Free models are built in, some run fully on your phone. Or bring your own provider.' },
];

export default {
  render() {
    let i = 0;
    const el = h(`<div><div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:32px;text-align:center;gap:16px">
      <div data-art></div><div class="t-headline-s" data-t></div><div class="t-body-m muted" data-b style="max-width:300px"></div></div>
      <div style="display:flex;justify-content:center;gap:6px;padding:12px" data-dots></div>
      <div style="display:flex;gap:12px;padding:16px 24px 28px"><button class="btn text" data-skip>Skip</button><span style="flex:1"></span><button class="btn filled" data-next>Next</button></div></div>`);
    const draw = () => {
      el.querySelector('[data-art]').innerHTML = orobi(i === 0 ? 200 : 150);
      el.querySelector('[data-t]').textContent = PAGES[i].title;
      el.querySelector('[data-b]').textContent = PAGES[i].body;
      el.querySelector('[data-dots]').innerHTML = PAGES.map((_, k) => `<i style="width:${k === i ? 24 : 8}px;height:8px;border-radius:4px;background:var(${k === i ? '--primary' : '--outline-variant'});transition:width .3s"></i>`).join('');
      el.querySelector('[data-next]').innerHTML = i === PAGES.length - 1 ? `Get started ${ic('arrow_forward', 's')}` : 'Next';
    };
    el.querySelector('[data-next]').onclick = () => (i < PAGES.length - 1 ? (i++, draw()) : navigate('home', {}, { replace: true }));
    el.querySelector('[data-skip]').onclick = () => navigate('home', {}, { replace: true });
    draw();
    return el;
  },
};
