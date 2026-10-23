---
name: forms
description: Accessible forms: input types, inline validation, busy and success states, multi-step
---
# Forms that feel effortless
- One column, labels above inputs, logical grouping with `<fieldset><legend>`.
- Correct `type`, `inputmode`, `autocomplete` (`email`, `name`, `tel`, `street-address`, `cc-number`,
  `new-password`, `one-time-code`). Mobile keyboards depend on it.
- Validate on blur, re-validate on input once a field was invalid; validate all on submit and focus
  the first invalid field. Use the Constraint Validation API (`required`, `pattern`, `minlength`) +
  custom messages. Error text under the field, red border + icon, `aria-invalid="true"`.
- Password: show/hide toggle, strength meter, rules listed and ticked live.
- Submit button: disabled while submitting with spinner; prevent double submit; success state
  (check animation) then next step. Keep data on error.
- Multi-step: progress indicator, back/next, save between steps, summary before submit.
- Floating labels or clear placeholders (never placeholder-only labels).
- Large touch targets, 16px+ font in inputs (prevents iOS zoom).
```js
form.addEventListener('submit', async (e) => {
  e.preventDefault();
  if(!form.checkValidity()){ form.querySelector(':invalid')?.focus(); showErrors(form); return; }
  setBusy(true);
  try { await api.create('messages', Object.fromEntries(new FormData(form))); showSuccess(); form.reset(); }
  catch (err) { toast(err.message, 'error'); } finally { setBusy(false); }
});
```
