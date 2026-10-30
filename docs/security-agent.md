# The Shield — Oro's personal cyber-defence agent

*Branch `black-october`, October 2026. The owner's brief: after a month of
breach after breach, every individual needs their own always-ready defensive
agent. The Shield is that agent. Its whole doctrine is defensive: audit,
detect, verify, recover. It never attacks, never probes systems the user does
not own, and refuses to.*

## Why multi-model

One model reviewing a breach notice is one opinion wearing a costume. The
**council** runs three seats in parallel over the same brief — a **first
responder** (what is happening, what to do in the next hour), an **adversary's
advocate** (what the attacker would exploit, and the strongest contrary
reading), and a **verifier** (what the artifact actually supports: confirmed /
suspected / false alarm) — each at its model's **deepest reasoning level**
(`ReasoningControls.maxLevel`), none seeing the others. A chair then merges:
findings deduplicated at the worst severity claimed, disagreements kept on the
record, absent seats counted as absent (a provider outage is information, not
silence).

Default seats are the owner's three that answer: **Mercury 3**, **K2 Horizon
375B**, **Hy3** — any further model (keyed, custom, community) can be seated
in the council sheet. Three is a council; one is an opinion.

## What the guard can do (tools)

| Tool | Doctrine |
|---|---|
| `audit_phone` | Posture: screen lock, patch age, ADB, unknown sources, **accessibility services** (the loader-malware vector), device admins, which apps hold SMS/contacts/location/mic/camera. Read-only introspection — no accessibility service, no device admin, no all-packages query: Play-safe by construction. |
| `check_url` | Structural phishing tells offline (brand-on-wrong-domain, typo-squats ≤2 edits, homoglyphs/punycode, `user@` tricks, raw IPs, shorteners, abuse TLDs) + TLS certificate probe (expiry, name match, issuer). Never opens the page. |
| `lookup_advisory` | OSV, keyless: is this dependency actually vulnerable. |
| `kev_recent` | CISA KEV, keyless: what is **exploited in the wild**, ransomware-linked first; a diff store means a pass reports only new news. Published ≠ exploited. |
| `scan_project_secrets` | High-precision credential shapes (AWS/GitHub/Stripe/Slack/JWT/conn-strings/private keys/assigned secrets), placeholder-aware, and **masked-only output**: a found secret never enters a transcript or model context. |
| `check_vault` | Saved logins vs HIBP **by k-anonymity** (five hex chars of a SHA-1 leave the device, never the password) + reuse detection + rotation order. |
| `convene_council` | The multi-model deliberation above. |
| `use_browser` | The browser agent walks the user's **own** account-security pages with them (sessions, 2FA, recovery). Captchas hand off to the user; sensitive actions ask first. |
| `ask_user`, `take_note`, `finish_task` | The house contract: decisions that are the user's, memory across turns, and a verdict-shaped ending (verdict first, actions in order, unproven last). |

## How it works together with the coding agents

- **`ask_guard`** (GuardDelegationTools) is always on the coding agent's menu:
  a security question mid-build ("is this dependency safe?", "the user says
  they were breached") goes to the specialist instead of being improvised in
  chat. Leashed mode asks before consulting, like `use_browser`.
- **Four craft skills** carry the doctrine to every agent that never meets the
  guard: `security-incident` (containment and rotation order),
  `security-hardening` (build it hardened: vault-first secrets, parameterised
  queries, pinned deps, CSP, verified webhooks), `phishing-triage` (headers
  over body, verify out-of-band, clicked = incident), `secret-hygiene`
  (masked-only quoting, rotate-before-clean, reuse is the multiplier).
- The guard itself can call `use_browser`; follow-ups queue into a running
  guard the same way they queue into the browser and subagents.

## Surfaces

- **Shield screen** (home top bar, security icon): action cards (Incident mode
  first, in error colours — "something is wrong, start here"), the live
  transcript with question cards while a run is on, council seat chips, the
  findings board (persisted, worst-first), and the council settings sheet
  (seat any model, daily-pass toggle).
- **Incident mode** is a standing order, not a wizard: calm, contain (what to
  stop touching NOW), verify with tools, council on the evidence, then guided
  recovery — email first, then banking, then sessions, then 2FA, then
  notification duties.

## Safety rules (carried, non-negotiable)

1. Defensive only. No offensive capability exists in this codebase: no
   exploitation, no credential testing against services, no mass scanning, no
   probing hosts the user does not own. Requests for those get a plain
   refusal and the defensive alternative.
2. Plaintext passwords never travel through chat or model context: vault or
   user-typed fields only; every quoted secret is masked (shape and length).
3. Captchas and human checks are never solved — handed to the user, auto-resume.
4. Sensitive actions (pay, send, post, delete) ask first.
5. Confirmed vs suspected is spoken out loud; an unreachable oracle returns
   null, never "safe".

## Tests

JVM, offline: council deliberation/merge/absence semantics
(`SecurityCouncilTest`), URL heuristics incl. homoglyphs and typo-squats
(`UrlGuardTest`), HIBP k-anonymity parsing + published SHA-1 vector
(`PwnedTest`), OSV/KEV parsing and diffing (`AdvisoriesTest`), secret shapes,
placeholders and masking (`SecretScanTest`), the guard's tool contract on a
scripted `Guard` (`GuardRunTest` — including that a scanned secret never
reaches the output), and the posture rules (`PhoneAuditReportTest`).
