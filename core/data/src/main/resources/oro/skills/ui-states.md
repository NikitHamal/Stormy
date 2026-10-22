---
name: ui-states
description: Loading skeletons, empty, error and success states, interaction states, toasts
---
# UI states

Amateur UIs show only the happy path. Every view that depends on data has FIVE states — design all:
**idle → loading → success (with data) | empty | error**, plus disabled/pending for actions.

## Pattern: render from state
```js
const state = { status: 'idle', items: [], error: null };
function setState(patch){ Object.assign(state, patch); render(); }
async function load(){
  setState({ status: 'loading', error: null });
  try {
    const res = await fetch('/api/items');
    if(!res.ok) throw new Error(`Request failed (${res.status})`);
    const items = await res.json();
    setState({ status: items.length ? 'success' : 'empty', items });
  } catch (e) { setState({ status: 'error', error: e.message }); }
}
function render(){
  const root = document.querySelector('#list');
  root.setAttribute('aria-busy', state.status === 'loading');
  if(state.status === 'loading') root.innerHTML = skeleton(6);
  else if(state.status === 'empty') root.innerHTML = emptyView();
  else if(state.status === 'error') root.innerHTML = errorView(state.error);
  else root.innerHTML = state.items.map(itemView).join('');
}
```
Always escape user/data text before putting it in innerHTML (`textContent`, or an `esc()` helper).

## Loading
- Skeletons that match the final layout (same heights), shimmer:
  `background:linear-gradient(90deg,var(--surface-2) 25%,var(--surface) 37%,var(--surface-2) 63%);background-size:400% 100%;animation:shimmer 1.4s ease infinite`
- Show loaders only after ~150ms to avoid flashes. Buttons: spinner inside + `disabled` + keep width.
- Long tasks: progress bar with real percentage, or steps.

## Empty
Illustration/icon + one-line explanation + primary action ("No projects yet — Create your first").
Distinguish "nothing yet" from "no results for 'abc'" (offer clear filters).

## Error
Human message + what to do + Retry button. Inline field errors under the field (`aria-describedby`),
never only colour. Keep user input after an error. Network offline: `navigator.onLine` banner.

## Success & feedback
- Toasts for background results (bottom-centre on mobile, auto-hide 4s, pause on hover, `role="status"`).
- Optimistic updates for likes/toggles, roll back on failure with a toast.
- Destructive actions: confirm dialog OR undo toast (better).
- Disabled ≠ hidden: explain why disabled (tooltip / helper text).

## Interaction states for every control
default · hover · focus-visible · active/pressed · disabled · loading · selected/checked · invalid.
If you styled a button, you must have styled all of these.
