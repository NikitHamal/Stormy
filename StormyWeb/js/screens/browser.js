// Browser (BrowserScreen.kt): Orobi's in-app browser — URL bar, tabs, the live page,
// the run bar with Pause/Stop, question/approval/secret cards, the human-check banner,
// the transcript sheet and the task composer. The demo runs are scripted, but they pause
// on the real interactions: a secret only you can type, an approval only you can give.
import { h, ic, esc, sheet, snackbar } from '../ui/kit.js';
import { loader } from '../ui/orobi.js';
import { state } from '../data.js';
import { navigate, back } from '../app.js';

const PAGES = {
  blank: { url: '', title: '', html: '' },
  search: {
    url: 'https://duckduckgo.com/?q=Aurion+X2+256+GB+price', title: 'Aurion X2 256 GB price',
    html: `<div class="wresult"><a class="wr-a" href="#">Aurion X2 — Official Store</a><div class="wr-u">aurion.example › x2</div><p>Buy the Aurion X2. 128 GB, 256 GB and 512 GB. Free delivery.</p></div>
      <div class="wresult"><a class="wr-a" href="#">Aurion X2 review — TechDaily</a><div class="wr-u">techdaily.example › reviews</div><p>The X2 keeps last year's price while doubling the base storage…</p></div>
      <div class="wresult"><a class="wr-a" href="#">Aurion X2 256GB price in India</a><div class="wr-u">pricecompare.example</div><p>Compare Aurion X2 256 GB prices across 12 stores.</p></div>`,
  },
  product: {
    url: 'https://aurion.example/x2', title: 'Aurion X2 — Official Store',
    html: `<div class="wph hero"></div><h2>Aurion X2</h2><div class="wprice">₹74,900 <s>₹82,900</s> <span class="wsave">Exchange offer −₹8,000</span></div>
      <div class="wspecs" data-hl=".specs"><b>Specs</b><div>Display 6.4" AMOLED 120 Hz</div><div>Storage <span data-hl>256 GB</span> · 12 GB RAM</div><div>Battery 5,200 mAh · 80 W</div></div>
      <button class="btn filled wbuy" data-hl="buy">Add to cart</button>`,
  },
  signup: {
    url: 'https://shop.example/signup', title: 'Create your account',
    html: `<h2>Create your account</h2>
      <label class="wlab">Full name</label><input class="winp" data-f="fullname" value="" placeholder="Priya Rao">
      <label class="wlab">Email</label><input class="winp" data-f="email" placeholder="you@example.com">
      <label class="wlab">Password</label><input class="winp" data-f="pass" type="password" placeholder="••••••••">
      <label class="wrow"><input type="checkbox" data-f="terms"> I agree to the Terms</label>
      <button class="btn ghost wgoogle">Continue with Google</button>
      <button class="btn filled wcreate" data-hl="create">Create account</button>`,
  },
};

const SCRIPTS = {
  price: [
    { say: 'One search beats five clicks — looking it up first.', tool: 'web_search', detail: 'Search the web for "Aurion X2 256 GB price"', ms: 1300, page: 'search' },
    { say: 'The official store is the first hit.', tool: 'open_url', detail: 'Open aurion.example/x2', ms: 1500, page: 'product' },
    { say: 'The spec block answers it; reading just that section.', tool: 'read_page', detail: 'Read the page (markdown, selector .specs)', ms: 1300, flash: '.wspecs' },
    { say: '', tool: 'finish_task', detail: 'Aurion X2 256 GB: ₹74,900 on aurion.example (exchange offer, was ₹82,900). Free delivery.', ms: 700, done: true, summary: 'Aurion X2 256 GB: ₹74,900 on aurion.example — exchange offer, was ₹82,900.' },
  ],
  signup: [
    { say: 'The sign-up page first.', tool: 'open_url', detail: 'Open shop.example/signup', ms: 1400, page: 'signup' },
    { say: 'Everything but the password is in the task: one call fills the form.', tool: 'fill_form', detail: 'Fill 3 fields: Full name, Email, I agree to the Terms', ms: 1600, fill: { fullname: 'Priya Rao', email: 'priya@example.com', terms: true }, flash: '.wcreate' },
    { say: 'The password never travels through me.', tool: 'ask_user_secret', detail: 'your shop.example password', secret: true },
    { say: 'Secret entered — submitting.', tool: 'click', detail: 'Click "Create account"', approval: 'click “Create account” on shop.example', ms: 1400, page: 'welcome' },
    { say: '', tool: 'finish_task', detail: 'Account created for priya@example.com; the dashboard loaded.', ms: 700, done: true, summary: 'Signed up on shop.example as priya@example.com. The password was typed by you, never by the model.' },
  ],
};

const Starters = [
  ['Find the price of the 256 GB Aurion X2 and any offer on it', 'price'],
  ['Sign me up on shop.example — I will type the password', 'signup'],
];

let runTimer = null;

export default {
  render() {
    const br = state.browserRun = {
      running: false, paused: false, task: '', status: '', actions: 0, steps: [],
      page: 'blank', url: '', title: '', waiting: null, summary: null, script: null, at: 0,
    };
    const el = h(`<div class="bscreen">
      <div class="topbar"><button class="iconbtn" data-back>${ic('arrow_back')}</button><span class="title" data-title>Browser</span>
        <button class="iconbtn" data-transcript style="display:none">${ic('list_alt')}</button>
        <button class="iconbtn" data-more>${ic('more_vert')}</button></div>
      <div class="urlbar">
        <div class="urlchip t-body-s" data-url>No page open</div>
      </div>
      <div class="bprogress" data-prog><i></i></div>
      <div class="pagewrap" data-pagewrap>
        <div class="webpage" data-page></div>
        <div class="hbanner" data-hbanner style="display:none">${ic('gpp_maybe')} Pass the check on the page — Orobi waits, it never solves it</div>
        <div class="welcome" data-welcome>
          <div class="t-headline-s">Orobi's browser</div>
          <p class="t-body-s muted" style="max-width:300px;margin:8px auto 16px">This browser opens when a chat task needs the web — Orobi calls <b>use_browser</b> and works here while you watch or carry on elsewhere. Checks and secrets it cannot handle come to you on this screen.</p>
          <div data-starters style="display:flex;flex-direction:column;gap:8px;width:100%;max-width:340px;margin:0 auto"></div>
        </div>
      </div>
      <div class="bcard" data-card style="display:none"></div>
      <div class="runbar" data-runbar style="display:none">
        <span data-loader>${loader(22)}</span>
        <span class="grow"><span class="t-label-l one" data-rstatus>Working</span><span class="t-body-s muted one" data-rmeta></span></span>
        <button class="iconbtn sm" data-pause>${ic('pause')}</button>
        <button class="iconbtn sm" data-stop>${ic('close')}</button>
      </div>
    </div>`);

    const pageEl = el.querySelector('[data-page]');
    const urlEl = el.querySelector('[data-url]');
    const runbar = el.querySelector('[data-runbar]');
    const card = el.querySelector('[data-card]');
    const starters = el.querySelector('[data-starters]');

    Starters.forEach(([text, key]) => {
      const b = h(`<button class="card tap" style="text-align:left;padding:12px 14px"><span class="t-label-s-e muted">use_browser · demo</span><div class="t-body-s" style="margin-top:4px">${esc(text)}</div></button>`);
      b.onclick = () => start(text, key);
      starters.append(b);
    });

    function showPage(name) {
      const p = PAGES[name] || PAGES.blank;
      br.page = name; br.url = p.url; br.title = p.title;
      if (name === 'welcome') {
        pageEl.innerHTML = `<h2>Welcome, Priya</h2><p class="t-body-s muted">Your account is ready. This is where the dashboard would load.</p>`;
        br.url = 'https://shop.example/welcome'; br.title = 'Welcome, Priya';
      } else pageEl.innerHTML = p.html;
      urlEl.textContent = (br.url || 'No page open').replace(/^https?:\/\//, '');
      el.querySelector('[data-welcome]').style.display = name === 'blank' ? '' : 'none';
      pageEl.style.display = name === 'blank' ? 'none' : '';
    }

    function addStep(kind, tool, title, detail = '') {
      br.steps.push({ kind, tool, title, detail, status: 'running', at: Date.now() });
      drawSteps();
    }
    function endStep(status, detail) {
      const s = br.steps[br.steps.length - 1];
      if (s) { s.status = status; if (detail !== undefined) s.detail = detail; }
      drawSteps();
    }
    function drawSteps() {
      const t = el.querySelector('[data-title]');
      const last = br.steps.filter((s) => s.kind !== 'thought').slice(-1)[0];
      t.textContent = br.running ? (br.status || 'Working') : (br.summary ? 'Done' : 'Browser');
      el.querySelector('[data-rstatus]').textContent = br.status || '';
      el.querySelector('[data-rmeta]').textContent = `${br.actions} action${br.actions === 1 ? '' : 's'} · ${(br.url || '').replace(/^https?:\/\//, '').slice(0, 34)}`;
      el.querySelector('[data-transcript]').style.display = br.steps.length ? '' : 'none';
      if (last && last.status === 'running') br.status = last.detail || last.title;
    }

    function flash(sel) {
      const target = pageEl.querySelector(sel);
      if (!target) return;
      target.classList.add('oid-flash');
      setTimeout(() => target.classList.remove('oid-flash'), 900);
    }

    function ask(kind, data, resume) {
      br.waiting = kind;
      card.style.display = '';
      if (kind === 'secret') {
        card.innerHTML = `<div class="t-label-l">Orobi needs a secret from you</div>
          <div class="t-body-s muted" style="margin:4px 0 10px">${esc(data.purpose)} — it goes straight into the page, never to the model.</div>
          <input type="password" class="winp" data-secret placeholder="Type it, or tap the field in the page">
          <div style="display:flex;gap:8px;margin-top:10px">
            <button class="btn filled sm" data-enter>Enter it</button>
            <button class="btn outlined sm" data-typed>I typed it in the page</button>
            <button class="btn outlined sm" data-no>Cancel</button></div>`;
        card.querySelector('[data-enter]').onclick = () => {
          const v = card.querySelector('[data-secret]').value;
          if (!v) return snackbar('Type the secret first (or tap "I typed it in the page")');
          card.style.display = 'none'; br.waiting = null;
          pageEl.querySelector('[data-f=pass]').value = '••••••••';
          endStep('done', 'You provided it; it was entered.');
          resume('entered');
        };
        card.querySelector('[data-typed]').onclick = () => {
          card.style.display = 'none'; br.waiting = null;
          pageEl.querySelector('[data-f=pass]').value = '••••••••';
          endStep('done', 'You typed it into the page yourself.');
          resume('direct');
        };
        card.querySelector('[data-no]').onclick = () => {
          card.style.display = 'none'; br.waiting = null;
          endStep('blocked', 'Declined.');
          resume('declined');
        };
      } else if (kind === 'approval') {
        card.innerHTML = `<div class="t-label-l">Allow Orobi to ${esc(data.what)}?</div>
          <div class="t-body-s muted" style="margin:4px 0 10px">Sensitive actions always ask. One Allow is one action.</div>
          <div style="display:flex;gap:8px"><button class="btn filled sm" data-yes>Allow</button><button class="btn outlined sm" data-no>Deny</button></div>`;
        card.querySelector('[data-yes]').onclick = () => { card.style.display = 'none'; br.waiting = null; endStep('done', 'You allowed it.'); resume(true); };
        card.querySelector('[data-no]').onclick = () => { card.style.display = 'none'; br.waiting = null; endStep('blocked', 'You denied it.'); resume(false); };
      }
    }

    function play() {
      if (!br.running || br.paused || br.waiting) return;
      const step = br.script[br.at];
      if (!step) return finishRun(null);
      if (step.say) { br.steps.push({ kind: 'thought', title: step.say, status: 'done' }); }
      addStep('action', step.tool, step.toolLabel || step.tool, step.detail);
      br.status = step.detail;
      drawSteps();
      if (step.secret) { br.status = 'Waiting for your secret'; drawSteps(); ask('secret', { purpose: step.detail }, () => { br.at++; play(); }); return; }
      if (step.approval) {
        br.status = 'Waiting for your approval'; drawSteps();
        ask('approval', { what: step.approval }, (ok) => {
          if (!ok) return finishRun('You denied the last step; the run stopped there.');
          applyStep(step); br.at++; play();
        });
        return;
      }
      runTimer = setTimeout(() => {
        applyStep(step);
        endStep('done');
        br.actions += ['read_page', 'web_search', 'finish_task'].includes(step.tool) ? 0 : 1;
        br.at++;
        if (step.done) return finishRun(step.summary);
        play();
      }, step.ms || 1000);
    }

    function applyStep(step) {
      if (step.page) { progress(700); showPage(step.page === 'welcome' ? 'welcome' : step.page); }
      if (step.flash) flash(step.flash);
      if (step.fill) {
        Object.entries(step.fill).forEach(([k, v]) => {
          const f = pageEl.querySelector(`[data-f=${k}]`);
          if (!f) return;
          if (f.type === 'checkbox') f.checked = !!v; else f.value = v;
          f.classList.add('oid-flash'); setTimeout(() => f.classList.remove('oid-flash'), 900);
        });
      }
    }

    function progress(ms) {
      const p = el.querySelector('[data-prog]');
      p.style.display = ''; p.firstElementChild.style.width = '0';
      requestAnimationFrame(() => { p.firstElementChild.style.transition = `width ${ms}ms ease`; p.firstElementChild.style.width = '100%'; });
      setTimeout(() => { p.style.display = 'none'; }, ms + 200);
    }

    function finishRun(summary) {
      br.running = false; clearTimeout(runTimer);
      runbar.style.display = 'none';
      br.summary = summary || br.summary;
      if (br.summary) {
        card.style.display = '';
        card.innerHTML = `<div class="t-label-l">✓ Done</div><div class="t-body-s bcard-scroll" style="margin:6px 0 10px">${esc(br.summary)}</div>
          <div style="display:flex;gap:8px"><button class="btn filled sm" data-new>Close</button></div>`;
        card.querySelector('[data-new]').onclick = () => { card.style.display = 'none'; reset(); };
      }
      drawSteps();
    }

    function reset() {
      br.running = false; br.paused = false; br.steps = []; br.actions = 0; br.summary = null; br.script = null; br.at = 0; br.waiting = null;
      showPage('blank');
      runbar.style.display = 'none'; card.style.display = 'none';
      el.querySelector('[data-title]').textContent = 'Browser';
    }

    function start(task, key) {
      clearTimeout(runTimer);
      Object.assign(br, { running: true, paused: false, task, steps: [], actions: 0, summary: null, at: 0, waiting: null, script: SCRIPTS[key] || SCRIPTS.price });
      state.browser.history = [task, ...(state.browser.history || []).filter((t) => t !== task)].slice(0, 6);
      card.style.display = 'none';
      runbar.style.display = '';
      br.status = 'Starting';
      showPage('blank');
      el.querySelector('[data-welcome]').style.display = 'none';
      drawSteps();
      progress(900);
      setTimeout(play, 500);
    }

    el.querySelector('[data-back]').onclick = () => back();
    el.querySelector('[data-pause]').onclick = (e) => {
      br.paused = !br.paused;
      e.currentTarget.innerHTML = ic(br.paused ? 'play_arrow' : 'pause');
      br.status = br.paused ? 'Paused' : 'Continuing';
      drawSteps();
      if (!br.paused) play();
    };
    el.querySelector('[data-stop]').onclick = () => { clearTimeout(runTimer); finishRun(null); br.summary = null; card.style.display = 'none'; reset(); snackbar('Stopped'); };
    el.querySelector('[data-more]').onclick = () => snackbar('Downloads · Browser settings (approvals, action limit, desktop mode, saved logins)');
    el.querySelector('[data-transcript]').onclick = () => {
      const iconOf = (t) => t === 'finish_task' ? 'check_circle' : t === 'web_search' ? 'search' : t === 'ask_user_secret' ? 'key' : t === 'fill_form' ? 'tune' : t === 'read_page' ? 'menu_book' : t === 'open_url' ? 'open_in_new' : 'language';
      const rows = br.steps.map((s) => s.kind === 'thought'
        ? `<div class="t-body-s" style="margin:8px 0">${esc(s.title)}</div>`
        : `<div class="tool">${ic(iconOf(s.tool))}<span class="grow" style="min-width:0"><span class="verb">${esc(s.title)}</span><div class="subj" style="font-family:var(--font)">${esc(s.detail || '')}</div></span>
           <span class="meta ${s.status === 'blocked' ? 'err' : ''}">${s.status === 'done' ? '✓' : s.status}</span></div>`).join('');
      sheet(h(`<div style="padding:16px 20px 24px"><div class="t-title-m">Transcript</div>
        <div class="t-body-s muted" style="margin:2px 0 8px">${esc(br.task)}</div>${rows || '<div class="t-body-s muted">Nothing yet.</div>'}</div>`));
    };
    showPage('blank');
    return el;
  },
};
