# The Shield — Oro's personal cyber-defence agent

*Branch `black-october`, October 2026. The owner's brief: after a month of
breach after breach, every individual needs their own always-ready defensive
agent. The Shield is that agent. Its doctrine is audit, detect, verify,
recover — and **counter lawfully**: preserve evidence, trace public records,
block and report the infrastructure through the parties that control it, and
harden what is the user's. It never attacks, probes, scans or accesses a
system the user does not own, and it says so plainly.*

## The conversation, not a wizard

The shield is a chat. A message starts or continues a run; the guard's prose
streams as replies; every tool call becomes an inline card that opens to its
evidence; a question is a tap-to-answer card in the transcript. Quick orders
above the composer (Incident, Trace, Link, Phone, Leaks, Harden, News) fill the
draft rather than send it. The council and the findings board are sheets built
on the design tokens. Follow-ups keep the transcript and the last twelve
turns as model context; "New chat" clears it, findings stay.

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
| `trace_incident` | Turns a mail, headers, a breach notice or a chat log into indicators and a dated timeline, then passively enriches the top domains/IPs/URLs. Always the first step in an incident; the record names its own gaps. |
| `enrich_indicator` | Passive dossier: RDAP registrar/abuse and registration age, DNS records and DNSSEC, certificate-transparency subdomains, urlscan verdicts, IP owner/netblock. Public records only — never contacts the host. |
| `audit_web_posture` | On a site the user **owns**: HSTS, CSP/frame-ancestors, cookie flags, CORS, version banners, HTTP→HTTPS, SPF/DMARC/DNSSEC, TLS. Findings name the exact header or record to change. |
| `audit_dependencies` | Reads package.json / requirements.txt / go.mod and asks OSV about the declared versions; a failed lookup is reported as unverified, not clean. |
| `draft_abuse_report` | The lawful counter: a ready-to-send report with indicators, a UTC timeline, the requested suspension, and the registrar/host/Safe Browsing/Microsoft/APWG/IC3/CISA channels. Evidence is redacted first. |
| `read_file`, `list_files`, `write_file`, `edit_file` | Project and private `workspace/` only. Reads mask credential-shaped values in secret files; an ambiguous edit fails rather than guesses. This is how the guard patches a hardening fix or writes a report the user asked for. |
| `run_script` | A short defensive script in a disposable, account-less sandbox: Judge0, then Wandbox, or the user's own runner (self-hosted Piston, a free VM, a Cloudflare tunnel) configured in the shield settings. No network; never secrets in code or stdin. |
| `delegate_investigation` | Parallel read-only investigator subagents on different seated models, for a large artifact or an independent read. The parent merges them and keeps the disagreements. |
| `add_finding` | Puts one actionable finding on the persisted board, with the worst honest severity. |
| `convene_council` | The multi-model deliberation above. |
| `use_browser` | The browser agent walks the user's **own** account-security pages with them (sessions, 2FA, recovery). Captchas hand off to the user; sensitive actions ask first. |
| `ask_user`, `take_note`, `finish_task` | The house contract: decisions that are the user's, memory across turns, and a verdict-shaped ending (verdict first, actions in order, unproven last). |

## Counter-intelligence, not retaliation

"Counter the attacker" has exactly one lawful shape here, and the guard is
built around it:

1. **Preserve** the artifact before touching anything — original headers, UTC
   timestamps, transaction ids. The record redacts credential-shaped values.
2. **Trace** what public records support: registration age and abuse contact
   (RDAP), infrastructure and netblock (RDAP/ipinfo), subdomains (certificate
   transparency), existing scan verdicts (urlscan), mail authentication
   (SPF/DMARC). Attribution names infrastructure, never a guessed human.
3. **Block** locally: the URL guard, the vault rotation order, project
   hardening patches, 2FA and session revocation.
4. **Report** through the parties that control the infrastructure: registrar,
   hosting provider, Google Safe Browsing, Microsoft, APWG, IC3, CISA. A
   report that asks to suspend a confirmed phishing URL is the actual takedown
   request.
5. **Never retaliate.** No scan, exploit, login attempt, flood, doxxing or
   contact with the attacker or their host. That is both the ethics line and
   the practical one: retaliation destroys evidence and creates liability.


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

- **Shield screen** (home top bar, security icon): a chat with Orobi's guard.
  Welcome starters (incident first, in error colours), streamed replies, inline
  tool cards, tap-to-answer question cards, quick orders, a findings badge in
  the top bar, the council sheet (seats, roles, guard model, daily pass) and
  the execution sheet (public sandboxes or the user's own runner).
- **Incident mode** is a standing order, not a wizard: calm, contain (what to
  stop touching NOW), preserve, trace with tools, council on the evidence, then
  guided recovery — email first, then banking, then sessions, then 2FA, then
  notification duties.

## Safety rules (carried, non-negotiable)

1. Defensive and lawful only. Counter means trace, block, report and harden;
   no exploitation, credential testing against services, scanning, probing or
   retaliation against any host the user does not own. Requests for those get a
   plain refusal and the defensive alternative.
2. Plaintext passwords never travel through chat or model context: vault or
   user-typed fields only; every quoted secret is masked (shape and length).
3. Captchas and human checks are never solved — handed to the user, auto-resume.
4. Sensitive actions (pay, send, post, delete) ask first.
5. Confirmed vs suspected is spoken out loud; an unreachable oracle returns
   null, never "safe".
6. A third-party sandbox is never trusted with a secret; the own-runner option
   exists precisely so code can stay off third parties.

## Tests

JVM, offline: council deliberation/merge/absence semantics
(`SecurityCouncilTest`), URL heuristics incl. homoglyphs and typo-squats
(`UrlGuardTest`), HIBP k-anonymity parsing + published SHA-1 vector
(`PwnedTest`), OSV/KEV parsing and diffing (`AdvisoriesTest`), secret shapes,
placeholders and masking (`SecretScanTest`), indicator extraction, timeline
ordering and evidence redaction (`ThreatTraceTest`), RDAP/DNS/crt.sh/urlscan
parsing, manifest parsing and abuse-report redaction (`ThreatIntelTest`),
sandbox request/response contracts (`SandboxTest`), the Gemini web chunk parser
(`GeminiWebParserTest`), the guard's tool contract on a scripted `Guard`
(`GuardRunTest` — including that a scanned secret never reaches the output),
and the posture rules (`PhoneAuditReportTest`).

