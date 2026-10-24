---
name: web-app
description: Architecture for vanilla SPAs: store, router, app shell, lists, persistence, charts
---
# Building web apps

## Architecture (vanilla, no build step)
```
index.html        app shell (header/sidebar/main), loads css + js/app.js as type="module"
css/styles.css    tokens + components
js/app.js         bootstraps: router + store + views
js/store.js       state + subscribe + persistence
js/api.js         data access (fetch to /api/... , see backend skill)
js/views/*.js     one module per screen, each exports render(root, state)
js/ui.js          toast, modal, confirm, formatters, esc()
```
Use ES modules (`<script type="module" src="js/app.js">`). Oro's preview serves over HTTP, so
imports and fetch work.

## Store
```js
// store.js
const listeners = new Set();
let state = JSON.parse(localStorage.getItem('app:state') || 'null') ?? { items: [], filter: 'all' };
export const getState = () => state;
export function setState(patch){ state = { ...state, ...patch };
  localStorage.setItem('app:state', JSON.stringify(state)); listeners.forEach(l => l(state)); }
export const subscribe = (fn) => (listeners.add(fn), () => listeners.delete(fn));
```
## Router (hash based, works in preview)
```js
const routes = { '': home, 'settings': settings, 'item': itemDetail };
function route(){ const [path, id] = location.hash.slice(2).split('/');
  (routes[path] ?? notFound)(document.querySelector('#view'), id); }
addEventListener('hashchange', route); route();
```
## App UX essentials
- App shell: sidebar on desktop, bottom tab bar on mobile (≤ 5 items, icon + label, active state).
- Every list: search, sort/filter, empty state, loading skeleton, item actions.
- Forms in modals/sheets on mobile; keyboard shortcuts on desktop (`/` focuses search, `Esc` closes).
- Persist user data (localStorage or the /api backend). Reload must not lose work.
- Dates/numbers via `Intl.DateTimeFormat` / `Intl.NumberFormat`. Relative times ("2h ago").
- Charts: Chart.js CDN (`https://cdn.jsdelivr.net/npm/chart.js`), themed with your tokens,
  `maintainAspectRatio:false` inside a sized container.
- Dark mode toggle stored in localStorage, applied before paint (inline script in `<head>`).
- Event delegation for lists (`root.addEventListener('click', e => e.target.closest('[data-action]'))`).
- No `alert()`/`prompt()` — use your modal/toast components.
- Accessibility: buttons are `<button>`, dialogs use `<dialog>` with focus return, live regions for toasts.

Frameworks: if the project already uses React/Vue/Svelte, follow it. React without a build:
```html
<script type="importmap">{"imports":{"react":"https://esm.sh/react@19","react-dom/client":"https://esm.sh/react-dom@19/client","htm":"https://esm.sh/htm@3"}}</script>
```
then `import htm from 'htm'; const html = htm.bind(React.createElement);`.
