// StormyWeb shell: device stage, theme, a navigation stack with Compose-like transitions, screen registry.
import { setHost, $ } from './ui/kit.js';
import { state } from './data.js';
import onboarding from './screens/onboarding.js';
import home from './screens/home.js';
import editor from './screens/editor.js';
import * as settings from './screens/settings.js';
import preview from './screens/preview.js';
import browser from './screens/browser.js';

export const SCREENS = {
  onboarding, home, editor, preview, browser,
  settings: settings.settingsScreen, providers: settings.providersScreen, providerEditor: settings.providerEditorScreen,
  mcp: settings.mcpScreen, voice: settings.voiceScreen, skills: settings.skillsScreen,
  aiModels: settings.aiModelsScreen,
};

const appEl = $('#app');
const display = $('#display');
setHost(display);
const stack = [];

export function navigate(name, params = {}, { replace = false } = {}) {
  const mod = SCREENS[name];
  const el = mod.render(params);
  el.classList.add('screen', 'enter');
  const prev = appEl.lastElementChild;
  if (replace && prev) { stack.pop(); }
  appEl.append(el);
  requestAnimationFrame(() => {
    el.classList.remove('enter');
    if (prev) { prev.classList.add('leave'); setTimeout(() => prev.remove(), 350); }
  });
  stack.push({ name, params });
  $('#jump').value = name;
}

export function back() {
  if (stack.length < 2) return;
  stack.pop();
  const { name, params } = stack.pop();
  navigate(name, params);
}

export function setTheme(t) {
  state.theme = t;
  display.dataset.theme = t;
  $('#theme').textContent = t === 'dark' ? 'Dark' : 'Light';
}

// Toolbar
const jump = $('#jump');
Object.keys(SCREENS).forEach((k) => jump.append(new Option(k, k)));
jump.onchange = () => navigate(jump.value);
$('#theme').onclick = () => setTheme(state.theme === 'dark' ? 'light' : 'dark');
$('#back').onclick = back;
function fit() {
  const d = $('#device'), box = d.parentElement.getBoundingClientRect();
  d.style.transform = `scale(${Math.min(1, (box.height - 16) / 892, (box.width - 16) / 412)})`;
}
$('#fit').onclick = fit;
addEventListener('resize', fit);
fit();
setTheme('dark');
navigate(location.hash.slice(1) in SCREENS ? location.hash.slice(1) : 'onboarding');
window.oro = { navigate, back, setTheme, state };
