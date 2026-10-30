---
name: secret-hygiene
description: Handling credentials in Oro — vault-first, k-anonymity breach checks, masked-only quoting, and rotation doctrine
---
# Secret hygiene

Rules for any task where a credential appears:

- **The vault is the only home.** Logins the agent needs (the browser agent filling a form)
  come from Oro's vault or from fields the user types directly — never from chat text. Refuse
  plaintext passwords pasted into the task and say why once, then offer the vault.
- **Quoting:** masked forms only — first characters plus length ("sk_… 40 chars"). A secret
  in a transcript is a secret in every log, backup and model context downstream.
- **Breach checks:** HaveIBeenPwned by k-anonymity — five hex characters of the SHA-1 leave
  the device, never the password. "Not seen" means "not in HIBP's corpus", not "safe";
  uniqueness and 2FA are the safety.
- **Found in a repo?** It is leaked until rotated — commits, CI logs and forks included.
  Rotate at the provider first, then remove it from the file, then load from env/vault, then
  (only then) consider history rewriting, and say plainly that rewriting does not un-leak.
- **Rotation order** after any compromise: email, banking/payments, dev platforms (they sign
  releases), socials. Then sessions, then 2FA everywhere, then unique passwords for what
  remains.
- **Reuse is the multiplier.** One breached site with a reused password is every site with
  that password. The vault audit exists to find exactly this.

Defensive only: never test a found credential against a service, never enumerate accounts,
never "check" someone else's secrets.
