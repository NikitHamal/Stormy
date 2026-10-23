---
name: auth
description: Sign up, log in, log out and private pages with Oro's built-in accounts API — complete, secure-by-default flows
---
# Accounts in Oro apps

Oro's preview has real accounts at `/api/auth/*`. Users persist in `db.json` (profile fields only);
password hashes and sessions are kept by Oro outside the project, never sent back and never
deployed. To seed demo accounts, give users a plain `"password"` in `db.json`: it works at login,
is hashed then and removed from the file. Use them instead of faking a login with localStorage.

| Call | Body | Returns |
|---|---|---|
| `POST /api/auth/register` | `{email or username, password (≥4), name?, …any profile fields}` | 201 `{token, user}` · 409 taken |
| `POST /api/auth/login` | `{email or username, password}` | `{token, user}` · 401 wrong |
| `GET /api/auth/me` | header `Authorization: Bearer <token>` | `user` · 401 |
| `POST /api/auth/logout` | (same header) | 204 |

With the UI kit (`add_ui_kit`) it is one line each: `await Oro.auth.register({...})`,
`await Oro.auth.login({...})`, `Oro.auth.logout()`, `Oro.auth.user`, and
`const me = await Oro.auth.require('login.html')` at the top of every private page (it sends
signed-out visitors to the login page with `?next=` and back after login). `Oro.api` sends the
token on every request automatically. Without the kit, keep the token in localStorage and send it
as `Authorization: Bearer …`.

## The screens (build all of them, they are one feature)
1. **login.html / signup.html** — `.auth` centred `.auth-card`; app logo/name, one heading, the
   form, a link to the other screen. Fields: `.field` + `.label` + `.input` with `autocomplete`
   (`email`, `current-password` / `new-password`, `name`), `required`, `minlength`, `type=email`.
   Password field in `.password` with a show/hide toggle button (`aria-pressed`, label changes).
2. **Validation** — on submit and on blur: inline `.error` under the field + `aria-invalid=true`,
   never `alert()`. Server errors (401 "Wrong email or password", 409 "already exists") show in an
   `.alert.alert-error` above the button, focused for screen readers (`role=alert`).
3. **Busy state** — `Oro.setBusy(button, true)` while the request runs; inputs disabled; restore on
   error. On success: `Oro.toast('Welcome back, Ana', 'success')` and redirect to `next` or home.
4. **Signed-in chrome** — header shows the user's name/avatar (initials fallback) with a menu:
   Profile, Settings, Log out. Log out → `await Oro.auth.logout()` → login page.
5. **Private pages** — `require()` before rendering private data; show a skeleton meanwhile.
6. **Profile** — edit name/avatar with `PATCH /api/users/:id` (the password stays as it was; send
   `password` only to change it); avatar via `await Oro.api.upload(file)` (up to 8 MB) → store the
   returned `url`.
7. **Per-user data** — save `userId: me.id` on records and list with `?userId=${me.id}`.

## Quality bar
- Every state: idle, typing (validation), submitting, error, success. Empty profile fields have
  sensible fallbacks. Works with keyboard only; focus moves to the first invalid field.
- Copy is specific: "Create your account" / "Log in to Recipes", not "Submit".
- Never put real secrets in frontend code; say in the summary that a production app needs a real
  server (use_skill "backend") with the same request shapes.
