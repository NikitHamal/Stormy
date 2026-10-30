---
name: security-incident
description: Incident response doctrine for the coding agent — what to do when a user reports a breach, a leaked secret, or a hijacked account mid-task
---
# Incident response, for coding agents

When a user says "I think I've been hacked", or you find a live credential in the repo, or a
breach notice names this project's stack: **stop building and defend.** The Shield (ask_guard)
is the specialist; consult it and follow its verdict. Until it answers:

1. **Contain before cure.** A leaked secret is compromised the moment it was committed —
   git history included; deleting the file fixes nothing. Rotate first, then clean.
2. **Order matters.** Email account first (it resets everything else), then banking/payments,
   then socials and dev platforms (npm, PyPI, cloud, GitHub). Then kill active sessions
   everywhere, then enable 2FA — app-based or hardware key, not SMS where avoidable.
3. **Confirmed vs suspected, out loud.** Label every claim. Say what evidence would prove or
   disprove it, and tell the user to preserve it (screenshots with timestamps, original .eml,
   do not uninstall the suspicious app yet).
4. **In-repo secrets:** rotate the credential at the provider, remove it from the file, load
   from an environment variable or Oro's vault, and if it ever reached a public remote treat
   the rotation as overdue, not optional. Never echo the secret's value in chat or commits.
5. **Notify.** If other people's data was exposed, tell the user their disclosure duties
   (users, employer, provider) in plain words — a delayed disclosure is its own incident.

Never: solve a captcha, bypass a login, "test" a credential against a service, or probe a host
the user does not own. Defensive only.
