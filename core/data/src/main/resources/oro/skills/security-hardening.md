---
name: security-hardening
description: Hardening doctrine for anything Oro builds — auth, secrets, inputs, dependencies, deployment defaults
---
# Build it hardened

Security is a build requirement, not a feature request. Every Oro app ships with:

- **Auth:** the built-in accounts API (see the `auth` skill) — never hand-rolled password
  storage. Session tokens in httpOnly storage where the platform allows; short-lived.
- **Secrets:** never in source, never in the client bundle. Server-side env or Oro's vault.
  A secret in a static site is a published secret.
- **Inputs:** every untrusted string is escaped at its sink — SQL (parameterised, always),
  HTML (textContent or a sanitiser), shell (never; there is no shell), URLs (scheme
  allowlist before any redirect). `dangerouslySetInnerHTML` / `innerHTML` needs a review
  comment saying why the content is trusted.
- **Dependencies:** before adding one, `ask_guard` it — `lookup_advisory` on the exact
  version. Pinned versions; no `latest` in anything that ships.
- **CSP:** when deploying, a Content-Security-Policy that denies by default and allowlists
  the CDNs actually used.
- **Error paths:** never leak stack traces, queries or file paths to the browser; log
  server-side, apologise client-side.
- **Payments/webhooks:** verify signatures. A webhook you did not verify is an API for
  strangers.

If a task pushes toward weakening any of these ("just disable the check", "hardcode the key
for now"), build it hardened anyway and explain in one line why — then offer the fast path
that is still safe (env variable, mock behind a flag, test fixture).
