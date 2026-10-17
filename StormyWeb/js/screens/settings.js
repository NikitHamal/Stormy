// Settings and its screens: providers, provider editor, MCP, voice, skills, on-device models, AI models.
import { h, ic, esc, segmented, bindSegmented, sw, bindSwitches, field, snackbar, formSheet, actionSheet } from '../ui/kit.js';
import { state } from '../data.js';
import { navigate, back, setTheme } from '../app.js';

const frame = (title, inner, { fab } = {}) => {
  const el = h(`<div><div class="topbar"><button class="iconbtn" data-back>${ic('arrow_back')}</button><span class="title t-title-l-e">${esc(title)}</span></div>
    <div class="scroll" style="padding-bottom:96px">${inner}</div>${fab ? `<button class="fab" data-fab>${ic(fab[0])}<span>${esc(fab[1])}</span></button>` : ''}</div>`);
  el.querySelector('[data-back]').onclick = back;
  return el;
};
const nav = (icon, title, sub, to) => `<div class="row" data-to="${to}">${ic(icon, 'l')}<span class="grow"><div class="t-body-l-e">${esc(title)}</div><div class="t-body-s muted one">${esc(sub)}</div></span>${ic('chevron_right', 'muted')}</div>`;
const section = (t, body) => `<div class="section-label t-label-s-e muted">${esc(t)}</div><div class="card" style="margin:0 16px">${body}</div>`;
const bindNav = (el) => el.querySelectorAll('[data-to]').forEach((r) => (r.onclick = () => navigate(r.dataset.to)));

export const settingsScreen = { render() {
  const el = frame('Settings', `
    ${section('Appearance', `<div class="row"><span class="grow">${segmented('Theme', ['System', 'Light', 'Dark'], state.theme === 'dark' ? 'Dark' : 'Light', 'theme')}</span></div>
      <div class="row">${ic('palette', 'l')}<span class="grow"><div class="t-body-l-e">Dynamic colour</div><div class="t-body-s muted">Use your wallpaper colours</div></span>${sw(false, 'dyn')}</div>`)}
    ${section('AI', nav('auto_awesome', 'AI models', 'Turn models on or off, set the default', 'aiModels') + nav('key', 'AI providers', '14 providers · 4 ready', 'providers') +
      nav('hub', 'MCP servers', 'GitHub, Supabase, docs and more for Orobi', 'mcp') + nav('mic', 'Voice dictation', 'System or Whistle, on-device', 'voice') +
      nav('auto_awesome', 'Skills', 'Playbooks Orobi loads when a task matches', 'skills'))}
    ${section('Performance', `<div class="row"><span class="grow">${segmented('Lite mode', ['Auto', 'On', 'Off'], 'Auto', 'lite')}<div class="t-body-s muted" style="margin-top:8px">Auto turns it on for phones with 3 GB of RAM or less: no blur, calmer motion.</div></span></div>`)}
    ${section('Editor', `<div class="row">${ic('format_list_numbered', 'l')}<span class="grow t-body-l-e">Line numbers</span>${sw(true, 'ln')}</div><div class="row">${ic('wrap_text', 'l')}<span class="grow t-body-l-e">Word wrap</span>${sw(true, 'ww')}</div>`)}
    ${section('Git', nav('commit', 'Git settings', 'Identity, credentials & preferences', 'settings'))}
    ${section('About', `<div class="row">${ic('info', 'l')}<span class="grow"><div class="t-body-l-e">Oro 2.0.0</div><div class="t-body-s muted">Build for the web, from a sentence.</div></span></div>`)}`);
  bindNav(el); bindSwitches(el);
  bindSegmented(el, 'theme', (v) => setTheme(v === 'Light' ? 'light' : 'dark'));
  bindSegmented(el, 'lite');
  return el;
} };

export const providersScreen = { render() {
  const row = (name, status, ready, i, to) => `<div class="card tap row" style="margin-bottom:8px" data-ed="${esc(to)}"><span class="avatar ${ready ? 'ready' : ''} shape-${i % 6}">${esc(name[0])}</span>
    <span class="grow"><div class="t-title-s one">${esc(name)}</div><div class="t-label-m ${ready ? 'primary' : 'muted'}">${esc(status)}</div></span>${ic('chevron_right', 'muted')}</div>`;
  const el = frame('AI providers', `<p class="t-body-s muted" style="padding:8px 20px 12px">Add as many keys as you like to any provider: when one hits a rate limit or runs out of credits, Oro moves to the next without interrupting your run. Keys stay on this device.</p>
    <div class="section-label t-label-s-e muted">Your providers</div><div style="padding:0 16px">${state.providers.yours.map((p, i) => row(p.name, p.status, true, i + 3, p.name)).join('')}</div>
    <div class="section-label t-label-s-e muted">Built in</div><div style="padding:0 16px">${state.providers.builtIn.slice().sort((a, b) => b[2] - a[2]).map(([n, s, r], i) => row(n, s, r, i, n)).join('')}</div>`,
  { fab: ['add', 'Add provider'] });
  el.querySelectorAll('[data-ed]').forEach((r) => (r.onclick = () => navigate('providerEditor', { name: r.dataset.ed })));
  el.querySelector('[data-fab]').onclick = () => navigate('providerEditor', { isNew: true });
  return el;
} };

export const providerEditorScreen = { render(p = {}) {
  const isNew = !!p.isNew;
  const el = frame(isNew ? 'Add provider' : p.name || 'DeepSeek', `<div style="padding:8px 16px">
    ${isNew ? '<p class="t-body-s muted" style="margin-bottom:16px">For any server that speaks the OpenAI or Anthropic API. DeepSeek, Kimi, xAI and the other well-known vendors are already in your provider list.</p>' : ''}
    ${segmented('API protocol', ['OpenAI', 'Anthropic'], 'OpenAI', 'proto')}<div style="height:16px"></div>
    ${field('Name', isNew ? '' : p.name || 'DeepSeek')}${field('Base URL', isNew ? '' : 'https://api.deepseek.com/v1', { placeholder: 'https://api.example.com/v1', support: 'Calls <base>/chat/completions and <base>/models', id: 'url' })}
    ${isNew ? field('API key (optional)', '', { type: 'password', support: 'Add more keys after saving; Oro fails over between them.' }) : ''}
    ${field('Extra headers (optional)', '', { area: true, placeholder: 'Header-Name: value' })}${field('Max output tokens (optional)', '')}
    <div class="row" style="padding:8px 0"><span class="grow"><div class="t-body-l">Native tool calling</div><div class="t-body-s muted">Off for chat-only models: Orobi then uses text tool calls</div></span>${sw(true, 'tools')}</div>
    ${isNew ? '' : `<div style="display:flex;align-items:center;margin-top:16px"><span class="t-label-s-e muted" style="flex:1">API keys</span><button class="btn text">Get a key ${ic('open_in_new', 's')}</button></div>
      ${[['sk-or-…a1B2', 'Active'], ['sk-or-…9fQe', 'Cooling down until 14:32']].map(([k, st], i) => `<div class="card row" style="margin-bottom:8px;background:var(--sc-high);min-height:0;padding:8px 12px" data-key="${i}">${ic('key', 's ' + (i ? 'err' : 'muted'))}<span class="grow"><div class="t-body-m mono">${k}</div><div class="t-label-s ${i ? 'err' : 'muted'}">${st}</div></span>${ic('more_vert', 'muted')}</div>`).join('')}
      <div style="display:flex;gap:8px"><div class="field" style="flex:1"><input type="password" placeholder="Paste an API key"></div><button class="btn filled" style="height:56px">Add</button></div>
      <div style="height:16px"></div>${segmented('When several keys work', ['Failover', 'Round robin'], 'Failover', 'rot')}
      <div style="display:flex;align-items:center;margin-top:16px"><span class="t-label-s-e muted" style="flex:1">Models</span><span class="t-label-s muted">1 of 3 on</span><button class="iconbtn dim">${ic('refresh')}</button><button class="iconbtn dim">${ic('add')}</button></div>
      ${[['deepseek-chat', true], ['deepseek-reasoner', false], ['deepseek-coder', false]].map(([m, on]) => `<div class="row" style="padding:6px 0;min-height:0"><span class="grow"><div class="t-body-m">${m}</div><div class="t-label-s muted">${m} · 64K ctx</div></span>${sw(on, m)}</div>`).join('')}`}
  </div><div style="padding:16px"><button class="btn filled block">${isNew ? 'Add provider' : 'Save changes'}</button></div>`);
  bindSegmented(el, 'proto', (v) => {
    const support = el.querySelector('[data-f=url]')?.parentElement.querySelector('.support');
    if (support) support.textContent = v === 'Anthropic' ? 'Calls <base>/messages and <base>/models' : 'Calls <base>/chat/completions and <base>/models';
  });
  bindSegmented(el, 'rot'); bindSwitches(el);
  el.querySelectorAll('[data-key]').forEach((k) => (k.onclick = () => actionSheet({ title: 'sk-or-…a1B2', subtitle: 'Active', actions: [{ label: 'Test key', icon: 'verified', onClick: () => snackbar('Key works · 3 models available') }, { label: 'Move down', icon: 'keyboard_arrow_down' }, { label: 'Turn off', icon: 'power_settings_new' }, { label: 'Delete key', icon: 'delete', destructive: true }] })));
  return el;
} };

export const mcpScreen = { render() {
  const servers = [['Context7', 'https://mcp.context7.com/mcp', false], ['GitHub', 'https://api.githubcopilot.com/mcp/', true]];
  const el = frame('MCP servers', `<p class="t-body-s muted" style="padding:8px 20px 12px">Connect remote MCP servers and Orobi can use their tools: GitHub issues, your Supabase database, library docs and more. Tokens stay on this device.</p>
    <div style="padding:0 16px">${servers.map(([n, u, signed]) => `<div class="card row" style="margin-bottom:8px">${ic('hub', 'primary')}<span class="grow"><div class="t-title-s">${n}</div><div class="t-body-s muted one">${u}</div></span>
      <button class="iconbtn ${signed ? '' : 'dim'}" data-signin>${ic('login', signed ? 'primary' : '')}</button><button class="iconbtn dim" data-test="${n}">${ic('verified')}</button>${sw(true, n)}</div>`).join('')}</div>`, { fab: ['add', 'Add server'] });
  el.querySelectorAll('[data-test]').forEach((b) => (b.onclick = () => snackbar(b.dataset.test + ': connected · 12 tools')));
  el.querySelectorAll('[data-signin]').forEach((b) => (b.onclick = () => snackbar('Opening the browser to sign in…')));
  el.querySelector('[data-fab]').onclick = () => formSheet({ title: 'Add MCP server', icon: 'hub', confirm: 'Add and test',
    body: `<div class="chips" style="margin-bottom:12px">${['Context7', 'DeepWiki', 'GitHub', 'Supabase', 'Figma', 'Custom'].map((c, i) => `<span class="chip ${i ? '' : 'on'}">${c}</span>`).join('')}</div>${field('Name', 'Context7')}${field('Server URL', 'https://mcp.context7.com/mcp')}`,
    onConfirm: () => snackbar('Context7: connected · 2 tools') });
  bindSwitches(el);
  return el;
} };

export const voiceScreen = { render() {
  const el = frame('Voice dictation', `<div style="padding:8px 16px;display:flex;flex-direction:column;gap:16px">
    <p class="t-body-s muted">Tap the mic in the prompt bar and speak. Both engines can run with no internet.</p>
    ${segmented('Engine', ['System', 'Whistle'], state.dictation, 'eng')}
    <div class="card" style="padding:16px"><div class="t-title-s">System</div><p class="t-body-s muted" style="margin-top:6px">Android's own recogniser, free with no limits. It prefers Google's offline model; if it asks for a network, download your language in Android Settings.</p></div>
    <div class="card" style="padding:16px"><div class="t-title-s">Whistle (on-device, 16.9 MB)</div><p class="t-body-s muted" style="margin:6px 0 12px">Cactus Compute's speech model, running entirely on this phone: private, offline, seven languages.</p><button class="btn filled" data-dl>Download Whistle</button><div class="bar" style="margin-top:12px;display:none" data-bar><i style="width:0"></i></div></div></div>`);
  bindSegmented(el, 'eng', (v) => (state.dictation = v));
  el.querySelector('[data-dl]').onclick = (e) => {
    const bar = el.querySelector('[data-bar]'), fill = bar.firstElementChild; bar.style.display = 'block'; e.target.style.display = 'none';
    let p = 0; const t = setInterval(() => { p += 9; fill.style.width = Math.min(100, p) + '%'; if (p >= 100) { clearInterval(t); bar.outerHTML = '<div class="primary t-label-l">Installed</div>'; } }, 120);
  };
  return el;
} };

export const skillsScreen = { render() {
  const skills = [['dark-mode', 'Add a dark theme with a toggle that follows the system and remembers the choice', 'Built in'], ['seo-basics', 'Make a site findable: title, description, social cards, headings, sitemap', 'Built in'],
    ['accessibility-pass', 'Fix accessibility: labels, contrast, focus, keyboard, landmarks', 'Built in'], ['make-responsive', 'Make every page work from 320px phones to wide desktops', 'Built in'], ['brand-voice', 'Write copy in the Bean There voice: warm, short sentences', 'Mine']];
  const el = frame('Skills', `<p class="t-body-s muted" style="padding:8px 20px 12px">Orobi sees each skill's name and description and loads the full playbook when a task matches.</p>
    <div style="padding:0 16px">${skills.map(([n, d, src]) => `<div class="card row" style="margin-bottom:8px">${ic('auto_awesome', 'primary')}<span class="grow"><div class="t-title-s">${n}</div><div class="t-body-s muted">${d}</div><div class="t-label-s" style="color:var(--outline)">${src}</div></span><button class="iconbtn dim">${ic('share')}</button></div>`).join('')}</div>`, { fab: ['add', 'New skill'] });
  el.querySelector('[data-fab]').onclick = () => formSheet({ title: 'New skill', icon: 'auto_awesome', body: field('Name', '') + field('When to use it', '') + field('Steps', '', { area: true }) });
  return el;
} };

export const aiModelsScreen = { render() {
  const el = frame('AI models', `<div class="chips" style="padding:0 16px 8px">${['All', 'K2 Horizon', 'Kilo Code', 'OVHcloud', 'Groq', 'DeepSeek'].map((c, i) => `<span class="chip ${i ? '' : 'on'}">${c}</span>`).join('')}</div>
    ${state.models.map(([g, list]) => `<div class="section-label t-label-s-e muted">${g}</div><div style="padding:0 16px">${list.map(([n, c]) => `<div class="row" style="padding:8px 4px"><span class="grow"><div class="t-body-l-e one">${n}</div><div class="t-body-s muted one">${c}</div></span>${sw(true, n)}</div>`).join('')}</div>`).join('')}`);
  bindSwitches(el);
  return el;
} };
