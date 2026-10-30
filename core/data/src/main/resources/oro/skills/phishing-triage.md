---
name: phishing-triage
description: How to triage a suspicious link, mail or message for a user — structural tells, safe verification, and when to convene the council
---
# Phishing triage

The user pastes a link, a mail, an SMS ("your parcel", "your bank", "your CEO"). Triage order:

1. **Do not open it.** Structural check first — ask_guard runs UrlGuard heuristics (brand on
   the wrong domain, typo-squats, homoglyphs, userinfo tricks, raw IPs, shorteners) and the
   TLS certificate without ever loading the page.
2. **Read the headers, not the body.** For mail: `Authentication-Results` (spf/dkim/dmarc),
   the real `Return-Path`, and `Received:` chain. A display name proves nothing; a passing
   SPF with failing DMARC on a lookalike domain is the classic business-email-compromise
   shape. Urgency + payment + secrecy = phishing until proven otherwise, whoever it claims
   to be from.
3. **Verify out-of-band.** The only safe check of "is this really from X": type X's address
   yourself, or use the app, or call the number on the back of the card. Never through a link
   or number in the message.
4. **If it was already clicked:** it is an incident, not a curiosity — sessions behind that
   link are compromised until changed. Rotate (email first), kill sessions, check forwarding
   rules and OAuth grants (attackers hide persistence there), then watch for follow-ups:
   the "support agent" who calls about the phishing mail is the second stage.
5. **Ambiguous?** Convene the council (ask_guard): first responder, adversary's advocate and
   verifier weigh the same evidence, and the disagreements are part of the answer.

Never reproduce a credential into a form to "test" it, and never forward the phishing message
to anyone without a warning — a forwarded phish is a fresh phish.
