// Editor (EditorScreen.kt + ChatTab.kt + sheets). Chat tab simulates an Orobi run.
import { h, ic, esc, sheet, actionSheet, formSheet, snackbar, segmented, bindSegmented, sw, bindSwitches, field } from '../ui/kit.js';
import { orobi, loader } from '../ui/orobi.js';
import { state } from '../data.js';
import { navigate, back } from '../app.js';

const STARTERS = ['Build a landing page for a coffee shop', 'Add a responsive navbar with a mobile menu', 'Review this project and suggest improvements', 'Audit my site and fix what it finds', 'Check my site on phone, tablet and desktop'];
const ICONS = { Read: ['description', '#6E8CF7'], Edit: ['edit', '#F0965A'], Wrote: ['edit_note', '#F0965A'], Searched: ['search', '#B285F2'], 'Looked at the preview': ['bug_report', '#4FC1B6'], Audited: ['bug_report', '#4FC1B6'] };

const toolRow = (verb, subj, meta) => {
  const [icon, color] = ICONS[verb] || ['build', 'var(--primary)'];
  return `<div class="tool"><span class="ic" style="color:${color}">${icon}</span><span class="verb">${esc(verb)}</span><span class="subj">${esc(subj)}</span><span class="meta">${meta}</span></div>`;
};

export default {
  render(params = {}) {
    const project = state.projects.find((p) => p.id === params.id) || state.projects[1];
    let tab = params.tab || 'chat';
    const el = h(`<div>
      <div class="topbar"><button class="iconbtn" data-back>${ic('arrow_back')}</button><span class="title t-title-l">${esc(project.name)}</span>
        <button class="iconbtn dim" data-files>${ic('menu')}</button><button class="iconbtn dim">${ic('image')}</button><button class="iconbtn dim">${ic('commit')}</button>
        <button class="iconbtn dim" data-preview>${ic('play_arrow')}</button><button class="iconbtn dim" data-deploy>${ic('cloud_upload')}</button><button class="iconbtn dim" data-set>${ic('settings')}</button></div>
      <div class="tabs" data-tabs></div><div data-body style="flex:1;display:flex;flex-direction:column;min-height:0"></div></div>`);
    el.querySelector('[data-back]').onclick = back;
    el.querySelector('[data-set]').onclick = () => navigate('settings');
    el.querySelector('[data-preview]').onclick = () => navigate('preview');
    el.querySelector('[data-deploy]').onclick = () => deploySheet(project);

    const drawTabs = () => {
      const t = el.querySelector('[data-tabs]');
      const onLight = state.theme === 'dark' ? tab === 'chat' : tab !== 'chat';
      t.innerHTML = `<button class="seg-tab l ${tab === 'chat' ? 'on' : ''}" data-t="chat">${orobi(22, onLight ? { skin: 'var(--ink)', eye: 'var(--peach)' } : { skin: 'var(--peach)', eye: 'var(--ink)' })}Chat</button>
        <button class="seg-tab r ${tab === 'chat' ? 'on' : ''}" data-sessions>${ic('keyboard_arrow_down')}</button>
        <button class="seg-tab solo ${tab === 'code' ? 'on' : ''}" data-t="code">${ic('code')}Editor</button>`;
      t.querySelectorAll('[data-t]').forEach((b) => (b.onclick = () => { tab = b.dataset.t; drawTabs(); drawBody(); }));
      t.querySelector('[data-sessions]').onclick = () => { if (tab !== 'chat') { tab = 'chat'; drawTabs(); drawBody(); } sessionsSheet(); };
    };
    const drawBody = () => {
      const body = el.querySelector('[data-body]');
      body.innerHTML = '';
      body.append(tab === 'chat' ? chatTab() : codeTab());
    };
    drawTabs(); drawBody();
    return el;
  },
};

function chatTab() {
  const el = h(`<div style="flex:1;display:flex;flex-direction:column;min-height:0"><div class="chat" data-chat></div><div data-dock></div>
    <div class="composer"><div class="t-label-m muted" data-partial style="display:none;padding:0 8px 6px"></div><textarea rows="1" placeholder="Ask anything, or describe what to build" data-input></textarea>
      <div class="controls"><button class="iconbtn dim">${ic('add')}</button><button class="iconbtn dim" data-tune>${ic('tune')}</button>
        <button class="model-chip" data-model>${ic('auto_awesome', 's')}<span style="overflow:hidden;text-overflow:ellipsis">${esc(state.model.name)}</span>${ic('expand_more', 's')}</button>
        <button class="round" data-ctx style="width:32px;height:32px"><svg width="20" height="20" viewBox="0 0 36 36"><circle cx="18" cy="18" r="15" fill="none" stroke="var(--outline-variant)" stroke-width="4"/><circle cx="18" cy="18" r="15" fill="none" stroke="var(--primary)" stroke-width="4" stroke-dasharray="${0.34 * 94} 94" transform="rotate(-90 18 18)"/></svg></button>
        <button class="round" data-mic>${ic('mic')}</button><button class="round send" data-send disabled>${ic('arrow_upward')}</button></div></div></div>`);
  const chat = el.querySelector('[data-chat]'), input = el.querySelector('[data-input]'), send = el.querySelector('[data-send]');
  const scroll = () => (chat.scrollTop = chat.scrollHeight);
  const drawWelcome = () => {
    chat.innerHTML = `<div class="welcome">${orobi(150)}<div class="t-headline-s" style="margin-top:16px">Hi, I'm Orobi</div>
      <div class="t-body-m muted">Tell me what to build or change. I work right in your project files.</div>
      <div style="height:20px"></div>${STARTERS.map((s) => `<button class="starter" data-s>${ic('north_east', 's muted')}${esc(s)}</button>`).join('')}</div>`;
    chat.querySelectorAll('[data-s]').forEach((b) => (b.onclick = () => { input.value = b.textContent.replace('north_east', '').trim(); input.oninput(); input.focus(); }));
  };
  input.oninput = () => { send.disabled = !input.value.trim(); input.style.height = 'auto'; input.style.height = Math.min(60, input.scrollHeight) + 'px'; };
  send.onclick = () => run(input.value.trim());
  el.querySelector('[data-tune]').onclick = chatSettingsSheet;
  el.querySelector('[data-model]').onclick = modelSheet;
  el.querySelector('[data-ctx]').onclick = contextSheet;
  // Dictation: browser speech recognition when available, a simulation otherwise.
  const mic = el.querySelector('[data-mic]'), partial = el.querySelector('[data-partial]');
  mic.onclick = () => {
    if (mic.classList.toggle('rec')) {
      mic.innerHTML = ic('stop'); partial.style.display = 'block'; partial.textContent = 'Listening…';
      setTimeout(() => { partial.textContent = 'add a dark mode toggle'; }, 900);
      setTimeout(() => { if (!mic.classList.contains('rec')) return; mic.click(); }, 2200);
    } else {
      mic.innerHTML = ic('mic'); partial.style.display = 'none';
      input.value = (input.value ? input.value + ' ' : '') + 'Add a dark mode toggle'; input.oninput();
    }
  };

  function dock(items) {
    const d = el.querySelector('[data-dock]');
    if (!items) { d.innerHTML = ''; return; }
    const done = items.filter((x) => x[1]).length;
    d.innerHTML = `<div class="plan"><div class="plan-head">${ic('checklist', 's')}<span class="t-label-l-e" style="flex:1">Plan</span><span class="t-label-m" style="padding:2px 10px;border-radius:999px;background:var(--secondary-container)">${done}/${items.length}</span><button class="iconbtn dim" data-pt style="width:28px;height:28px">${ic('expand_more', 's')}</button></div>
      <div data-pl style="padding-bottom:8px">${items.map(([t, ok]) => `<div class="plan-item ${ok ? 'done' : ''}">${ic(ok ? 'check_circle' : 'radio_button_unchecked', 's')}${esc(t)}</div>`).join('')}</div></div>`;
    d.querySelector('[data-pt]').onclick = () => { const pl = d.querySelector('[data-pl]'); pl.style.display = pl.style.display === 'none' ? '' : 'none'; };
  }

  async function run(text) {
    if (!text) return;
    if (chat.querySelector('.welcome')) chat.innerHTML = '';
    input.value = ''; input.oninput();
    chat.insertAdjacentHTML('beforeend', `<div class="ububble">${esc(text)}</div>`);
    const ai = h(`<div class="ai"></div>`); chat.append(ai);
    const status = h(`<div class="t-label-l muted" style="display:flex;gap:8px;align-items:center">${loader()} Thinking</div>`); chat.append(status); scroll();
    const wait = (ms) => new Promise((r) => setTimeout(r, ms));
    await wait(700);
    ai.insertAdjacentHTML('beforeend', `<div class="think">The user wants a gallery. Read the page and stylesheet first, then add a grid.</div>`);
    dock(state.plan.items.map(([t]) => [t, false]));
    const steps = [['Read', 'index.html', '124 lines'], ['Read', 'style.css', '58 lines'], ['Edit', 'index.html', '<span class="add">+38</span> <span class="del">−2</span>'], ['Wrote', 'gallery.css', '<span class="add">+64</span>'], ['Looked at the preview', 'index.html', 'phone · tablet · desktop']];
    for (let i = 0; i < steps.length; i++) {
      await wait(500);
      ai.insertAdjacentHTML('beforeend', toolRow(...steps[i]));
      dock(state.plan.items.map(([t], k) => [t, k <= Math.min(3, Math.floor(i * 0.8))]));
      scroll();
    }
    status.remove();
    const answers = await askUser([
      { header: 'Captions', q: 'Should the photos have captions?', options: [['Yes, place and year', 'Short caption under each photo'], ['No captions', 'Just the photos']] },
      { header: 'Layout', q: 'Which grid on phones?', options: [['Two columns', ''], ['One column', 'Bigger photos']], multi: false },
    ]);
    ai.insertAdjacentHTML('beforeend', `<div class="quote"><b>You answered 2 questions</b><br>1. Should the photos have captions? — <b>${esc(answers[0] || 'skipped')}</b><br>2. Which grid on phones? — <b>${esc(answers[1] || 'skipped')}</b></div>`);
    await wait(600);
    ai.insertAdjacentHTML('beforeend', toolRow('Edit', 'gallery.css', '<span class="add">+6</span> <span class="del">−1</span>'));
    dock(state.plan.items.map(([t]) => [t, true]));
    await wait(500);
    ai.insertAdjacentHTML('beforeend', `<p>Done. I added a <code>gallery</code> section to <code>index.html</code> with ${answers[1] === 'One column' ? 'one column' : 'two columns'} on phones and three on desktop, and put the styles in <code>gallery.css</code>. It has no horizontal overflow at any width.</p>`);
    chat.insertAdjacentHTML('beforeend', `<button class="restore">${ic('history', 's')} Restore files</button>`);
    chat.lastElementChild.onclick = () => formSheet({ title: 'Restore files?', icon: 'history', confirm: 'Restore', destructive: true, body: `<p class="t-body-m">Every file Orobi created, changed or deleted from this message on goes back to how it was before it. The conversation itself is kept.</p>`, onConfirm: () => snackbar('Restored 2 files') });
    scroll();
    setTimeout(() => dock(null), 2500);
  }

  /** AskUserCard stepper; resolves with one answer per question (null = skipped). */
  function askUser(questions) {
    return new Promise((resolve) => {
      const answers = questions.map(() => null);
      let step = 0;
      const card = h(`<div class="ask"></div>`); chat.append(card);
      const draw = () => {
        const q = questions[step];
        card.innerHTML = `<div style="display:flex;align-items:center;gap:8px">${loader()}<span class="t-label-l" style="flex:1">A few quick questions</span><span class="t-label-m muted">${step + 1} of ${questions.length}</span></div>
          <div class="steps">${questions.map((_, i) => `<i class="${i === step ? 'cur' : answers[i] ? 'done' : ''}"></i>`).join('')}</div>
          <div class="t-label-s-e primary">${esc(q.header)}</div><div class="t-title-m">${esc(q.q)}</div>
          ${q.options.map(([l, d], i) => `<button class="opt ${answers[step] === l ? 'on' : ''}" data-o="${i}"><span class="box">${answers[step] === l ? ic('check', 's') : ''}</span><span><div class="t-body-l">${esc(l)}</div>${d ? `<div class="t-body-s muted">${esc(d)}</div>` : ''}</span></button>`).join('')}
          <div class="field"><input placeholder="Or type your own answer" data-own></div>
          <div style="display:flex;gap:4px">${step ? `<button class="btn text" data-b>${ic('arrow_back', 's')} Back</button>` : ''}<button class="btn text" data-skip>Skip question</button></div>`;
        const next = (a) => { answers[step] = a; if (step === questions.length - 1) { card.remove(); resolve(answers); } else { step++; draw(); } scroll(); };
        card.querySelectorAll('[data-o]').forEach((b) => (b.onclick = () => next(q.options[+b.dataset.o][0])));
        card.querySelector('[data-own]').onkeydown = (e) => { if (e.key === 'Enter' && e.target.value.trim()) next(e.target.value.trim()); };
        card.querySelector('[data-skip]').onclick = () => next(null);
        card.querySelector('[data-b]')?.addEventListener('click', () => { step--; draw(); });
      };
      draw(); scroll();
    });
  }

  drawWelcome();
  return el;
}

function codeTab() {
  const name = 'index.html';
  const hl = (line) => esc(line).replace(/(&lt;\/?)([a-z0-9]+)/g, '$1<span class="t">$2</span>').replace(/([a-z-]+)=(&quot;[^&]*&quot;)/g, '<span class="a">$1</span>=<span class="s">$2</span>');
  return h(`<div style="flex:1;display:flex;flex-direction:column;min-height:0"><div class="filetabs">${Object.keys(state.files).map((f) => `<span class="filetab ${f === name ? 'on' : ''}">${ic('description', 's')}${f}</span>`).join('')}</div>
    <div class="code">${state.files[name].map((l, i) => `<div><span class="ln">${i + 1}</span>${hl(l)}</div>`).join('')}</div></div>`);
}

function sessionsSheet() {
  const groups = ['Pinned', 'Today', 'Yesterday', 'Previous 7 days', 'Older'];
  const rows = (g) => state.sessions.filter((s) => (g === 'Pinned' ? s.pinned : !s.pinned && s.group === g));
  const s = sheet(`<div style="display:flex;align-items:center;gap:12px;margin-bottom:12px"><div style="flex:1"><div class="t-title-l-e">Chats</div><div class="t-body-s muted">${state.sessions.length} chats in this project</div></div>
      <button class="btn tonal" data-new>${ic('add_comment')} New chat</button></div>
    <div class="search" style="margin:0 0 8px">${ic('search')}<span class="t-body-m">Search chats</span></div>
    ${groups.map((g) => rows(g).length ? `<div class="t-label-m primary" style="padding:16px 12px 4px">${g}</div>` + rows(g).map((x) => `
      <div class="row" style="padding:10px 12px;border-radius:16px;${x.id === state.currentSession ? 'background:var(--secondary-container)' : ''}" data-s="${x.id}">
        <span class="avatar" style="width:40px;height:40px;border-radius:12px;${x.id === state.currentSession ? 'background:var(--primary);color:var(--on-primary)' : ''}">${ic(x.pinned ? 'push_pin' : 'chat_bubble', 's ' + (x.pinned ? 'fill' : ''))}</span>
        <span class="grow"><div style="display:flex;gap:8px"><span class="t-body-l-e one" style="flex:1">${esc(x.title)}</span><span class="t-label-s muted">${x.when}</span></div><div class="t-body-s muted one">${esc(x.preview)}</div></span>
        <button class="iconbtn dim" data-m="${x.id}">${ic('more_vert')}</button></div>`).join('') : '').join('')}`);
  s.el.querySelector('[data-new]').onclick = () => { s.close(); snackbar('New chat'); };
  s.el.querySelectorAll('[data-s]').forEach((r) => (r.onclick = (e) => { if (e.target.closest('[data-m]')) return; state.currentSession = r.dataset.s; s.close(); }));
  s.el.querySelectorAll('[data-m]').forEach((b) => (b.onclick = () => actionSheet({ title: state.sessions.find((x) => x.id === b.dataset.m).title, subtitle: 'Long-press a chat for the same actions', actions: [{ label: 'Pin to top', icon: 'push_pin' }, { label: 'Rename', icon: 'edit' }, { label: 'Delete chat', icon: 'delete', destructive: true, description: 'You can undo this for a few seconds' }] })));
}

function modelSheet() {
  const s = sheet(`<div class="t-title-l-e" style="margin-bottom:8px">Models</div>${state.models.map(([g, list]) => `<div class="t-label-m ${g === 'On this phone' ? '' : 'primary'}" style="padding:16px 4px 4px">${g}</div>` +
    list.map(([n, c]) => `<div class="row" style="padding:10px 4px" data-m="${esc(n)}"><span class="avatar shape-${n.length % 6}">${esc(n[0])}</span><span class="grow"><div class="t-body-l-e one">${esc(n)}</div><div class="t-body-s muted one">${esc(c)}</div></span>${n === state.model.name ? ic('check', 'primary') : ''}</div>`).join('')).join('')}`);
  s.el.querySelectorAll('[data-m]').forEach((r) => (r.onclick = () => { state.model.name = r.dataset.m; s.close(); snackbar('Model: ' + r.dataset.m); }));
}

function chatSettingsSheet() {
  const s = sheet(`<div class="t-title-m-e" style="margin-bottom:12px">Chat settings</div>
    <div class="row" style="padding:8px 0">${ic('handyman', 'l')}<span class="grow"><div class="t-body-l-e">Agent mode</div><div class="t-body-s muted">Let the assistant read and edit files on its own</div></span>${sw(state.agentMode, 'agent')}</div>
    <div class="card" style="padding:12px;margin-top:12px">${segmented('Autonomy', ['Leashed', 'Unleashed'], state.autonomy, 'auto')}<div class="t-body-s muted" data-ad style="margin-top:8px"></div></div>
    <div style="margin-top:16px">${segmented('Reasoning effort', ['Low', 'Balanced', 'Deep'], 'Balanced', 'eff')}</div>
    <div class="card" style="padding:12px;margin-top:16px;display:flex;gap:8px;background:var(--sc-high)">${ic('info', 's muted')}<span class="t-label-s muted">Which options appear depends on the model you have selected.</span></div>`);
  const desc = () => (s.el.querySelector('[data-ad]').textContent = state.autonomy === 'Leashed'
    ? 'Orobi shows each change (write, edit, delete, move, MCP action) and waits for your OK.'
    : 'Orobi works on its own. It still asks before deleting folders, touching secrets or running risky MCP actions. Checkpoints let you undo any run.');
  desc();
  bindSegmented(s.el, 'auto', (v) => { state.autonomy = v; desc(); });
  bindSegmented(s.el, 'eff');
  bindSwitches(s.el, (id, on) => { if (id === 'agent') state.agentMode = on; });
}

function contextSheet() {
  sheet(`<div style="display:flex"><span class="t-title-m-e" style="flex:1">Context window</span><span class="t-title-m-e">34%</span></div>
    <p class="t-body-s muted" style="margin:4px 0 16px">How much of this conversation the model can still see. Compacting summarises the earlier messages and keeps the recent ones.</p>
    <div style="display:flex;height:10px;border-radius:5px;overflow:hidden;gap:2px"><i style="flex:8;background:var(--primary)"></i><i style="flex:12;background:var(--secondary)"></i><i style="flex:14;background:var(--outline)"></i><i style="flex:66;background:var(--sc-highest)"></i></div>
    ${[['System & tools', '8.1K'], ['Project context', '12.4K'], ['Conversation', '14.0K'], ['Free', '84.5K']].map(([a, b]) => `<div class="row" style="padding:6px 0;min-height:0"><span class="grow t-body-m">${a}</span><span class="t-label-m muted">${b}</span></div>`).join('')}
    <div class="card" style="padding:12px;margin-top:12px;background:var(--sc)"><div style="display:flex"><span class="t-label-l-e" style="flex:1">This chat</span><span class="t-title-s primary">Free</span></div><div class="t-body-s muted">48.2K in (64% cached) · 3.1K out · 9 requests</div></div>
    <button class="btn tonal block" style="margin-top:16px">${ic('compress')} Compact 6 earlier messages</button>`);
}

function deploySheet(project) {
  const s = sheet(`<div class="t-title-l-e">Publish</div><p class="t-body-s muted" style="margin:4px 0 16px">Put this project on the web. Deploying again updates the same site.</p>
    ${segmented('Where', ['Netlify', 'GitHub Pages'], 'Netlify', 'where')}<div style="height:16px"></div>
    ${field('Site name', project.name.toLowerCase().replace(/[^a-z0-9]+/g, '-'), { support: 'mara-ilves.netlify.app if it is free' })}
    ${field('Netlify personal access token', '', { type: 'password', support: 'app.netlify.com → User settings → Applications' })}
    <button class="btn filled block" data-go>Publish</button>`);
  bindSegmented(s.el, 'where');
  s.el.querySelector('[data-go]').onclick = (e) => { e.target.textContent = 'Uploading 12 files…'; setTimeout(() => { s.close(); snackbar('Live at https://mara-ilves.netlify.app'); }, 1200); };
}
