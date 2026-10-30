package com.oro.app.guard

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The guard agent's tool set: defensive only. Every tool audits, detects,
 * verifies or recovers; none of them reaches out and touches a system the
 * user does not own. "Counter" here means lawful counter-intelligence --
 * preserve evidence, identify the infrastructure from public records, block
 * it, report it, and harden -- never retaliation.
 */
object GuardTools {

    private fun tool(name: String, description: String, schema: String) = Tool(
        function = FunctionDefinition(name, description.trimIndent(), AiHttp.json.parseToJsonElement(schema.trimIndent()))
    )

    val AUDIT_PHONE = tool(
        "audit_phone",
        "Audit this phone's security posture: screen lock, patch age, USB debugging, unknown-source installs, accessibility services, device admins, and which apps hold SMS/contacts/location/mic/camera. Returns findings worst-first with the setting that fixes each. Read-only introspection of the device itself.",
        """{"type":"object","properties":{}}"""
    )

    val CHECK_URL = tool(
        "check_url",
        "Judge a link before anyone clicks it: structural phishing tells (brand-on-wrong-domain, typo-squats, homoglyphs, userinfo tricks, raw IPs, shorteners), plus -- with deep=true -- the TLS certificate the host actually presents (expiry, name match, issuer). Returns a score, reasons and a risk band. Never opens the page.",
        """{"type":"object","properties":{
          "url":{"type":"string","description":"The http(s) URL to judge."},
          "deep":{"type":"boolean","description":"Also probe the TLS certificate. Default true."}
        },"required":["url"]}"""
    )

    val LOOKUP_ADVISORY = tool(
        "lookup_advisory",
        "Ask OSV (the open advisory database, no key) whether a package version has known vulnerabilities: ids, summaries, severities, references. Use before adding a dependency, or when a breach notice names a library.",
        """{"type":"object","properties":{
          "package":{"type":"string","description":"Package name, e.g. lodash, log4j-core, requests."},
          "ecosystem":{"type":"string","description":"npm, PyPI, Maven, Go, crates.io, NuGet, Packagist, RubyGems."},
          "version":{"type":"string","description":"The version in use. Omit to list advisories for all versions."}
        },"required":["package","ecosystem"]}"""
    )

    val KEV_RECENT = tool(
        "kev_recent",
        "CISA's Known-Exploited-Vulnerabilities catalog: what is actually being exploited in the wild right now (not merely published). Returns entries added in the last N days, ransomware-linked first. The difference between a CVE and tonight's news.",
        """{"type":"object","properties":{
          "days":{"type":"integer","description":"Window in days. Default 14, max 90."}
        }}"""
    )

    val SCAN_PROJECT_SECRETS = tool(
        "scan_project_secrets",
        "Scan the open project's files for escaped credentials: cloud keys, tokens, private keys, connection strings, assigned secrets. Findings are masked (shape and length only) -- the values never enter this conversation. High-precision patterns; examples and placeholders do not fire.",
        """{"type":"object","properties":{
          "path":{"type":"string","description":"Folder to scan. Default the whole project."}
        }}"""
    )

    val CHECK_VAULT = tool(
        "check_vault",
        "Audit the logins saved in Oro's vault: passwords seen in known breaches (checked by k-anonymity -- only a hash prefix leaves the device), passwords reused across sites, and entries whose domain no longer matches. Passwords are never shown or quoted.",
        """{"type":"object","properties":{}}"""
    )

    val TRACE_INCIDENT = tool(
        "trace_incident",
        "Turn an artifact -- mail headers, a breach notice, a chat log, a ransom note, a bank statement description -- into an evidence record: indicators (URLs, domains, IPs, emails, hashes, CVEs, wallets), a timeline sorted by date, and passive enrichment from public records (RDAP, DNS, certificate transparency, urlscan). Never contacts the attacker or their infrastructure. Use this first in every incident; then convene the council on the record.",
        """{"type":"object","properties":{
          "evidence":{"type":"string","description":"The artifact verbatim. Secrets may stay: the tool redacts credential-shaped values before writing the record."},
          "subjects":{"type":"array","items":{"type":"string"},"description":"What the user says is affected, e.g. bank account, Instagram, this phone."},
          "enrich":{"type":"boolean","description":"Enrich the top domains/IPs/URLs passively. Default true."}
        },"required":["evidence"]}"""
    )

    val ENRICH_INDICATOR = tool(
        "enrich_indicator",
        "Passive dossier on one indicator: domain (RDAP registrar/abuse, DNS, certificate-transparency subdomains, urlscan verdicts), IP (owner, netblock, abuse contact), URL (structural verdict + its domain), email (its domain + what to check next). Public records only; never scans the host.",
        """{"type":"object","properties":{
          "indicator":{"type":"string","description":"A domain, URL, IP, email, hash or wallet address."},
          "kind":{"type":"string","description":"Optional: domain, url, ip, email, hash, wallet. Guessed when omitted."}
        },"required":["indicator"]}"""
    )

    val AUDIT_WEB_POSTURE = tool(
        "audit_web_posture",
        "Review a site the user owns: HSTS, CSP and frame-ancestors, cookie flags, CORS, version banners, HTTP-to-HTTPS, SPF/DMARC/DNSSEC, plus the TLS chain. Returns findings with the exact header or DNS record to change. Only run this on the user's own domain; if ownership is unclear, ask first.",
        """{"type":"object","properties":{
          "url":{"type":"string","description":"The site's URL."}
        },"required":["url"]}"""
    )

    val AUDIT_DEPENDENCIES = tool(
        "audit_dependencies",
        "Read the open project's package.json, requirements.txt and go.mod and ask OSV which declared versions have known advisories. Reports only vulnerable packages, and says which lookups failed rather than calling them clean.",
        """{"type":"object","properties":{}}"""
    )

    val DRAFT_ABUSE_REPORT = tool(
        "draft_abuse_report",
        "Draft a ready-to-send abuse report for the registrar, hosting provider, Google Safe Browsing, Microsoft, APWG, IC3 or CISA -- with indicators, a UTC timeline and the exact remediation requested. Evidence is redacted. This is the lawful way to 'counter' an attacker: take their infrastructure down through the parties that control it, never by touching it yourself.",
        """{"type":"object","properties":{
          "kind":{"type":"string","description":"phishing, malware, fraud, ransomware, account takeover, doxxing..."},
          "note":{"type":"string","description":"One or two sentences tying the evidence to the harm."},
          "evidence":{"type":"string","description":"The original artifact or a summary. Redacted before it is quoted."}
        },"required":["kind","evidence"]}"""
    )

    val READ_FILE = tool(
        "read_file",
        "Read a text file from the open project or the guard workspace (workspace/...). Use for code, logs, configs and evidence the user saved. The result is capped; read a file in parts if it is long.",
        """{"type":"object","properties":{
          "path":{"type":"string","description":"Project-relative path, or workspace/... for the guard's scratch space."}
        },"required":["path"]}"""
    )

    val LIST_FILES = tool(
        "list_files",
        "List files in the open project or workspace, optionally under a folder. Skips build output, dependency trees and .git.",
        """{"type":"object","properties":{
          "path":{"type":"string","description":"Folder or file to list. Default the project root."}
        }}"""
    )

    val GREP = tool(
        "grep",
        "Search the project or workspace with a regular expression; returns path:line: match, capped at 200. Credential-shaped values in a matching line are masked. Use it to find the exact place to patch before edit_file.",
        """{"type":"object","properties":{
          "pattern":{"type":"string","description":"Kotlin/Java regular expression."},
          "path":{"type":"string","description":"File or folder to search. Default the whole project."}
        },"required":["pattern"]}"""
    )

    val WRITE_FILE = tool(
        "write_file",
        "Create or overwrite a text file in the open project or the guard workspace. Use for hardening patches, a response script, an evidence note, or a report the user asked for. Never write credentials into a project file; workspace/ is the scratch space.",
        """{"type":"object","properties":{
          "path":{"type":"string","description":"Project-relative path or workspace/..."},
          "content":{"type":"string","description":"The full file content."},
          "append":{"type":"boolean","description":"Append instead of overwrite."}
        },"required":["path","content"]}"""
    )

    val EDIT_FILE = tool(
        "edit_file",
        "Replace one exact, unique string in a project file. Fails on ambiguity or a missing match -- read the file first. Use for a minimal hardening patch (a header, a validation check, a pinned version).",
        """{"type":"object","properties":{
          "path":{"type":"string","description":"Project-relative path."},
          "old":{"type":"string","description":"The exact text to replace; must occur exactly once."},
          "new":{"type":"string","description":"The replacement text."}
        },"required":["path","old","new"]}"""
    )

    val RUN_SCRIPT = tool(
        "run_script",
        "Run a short defensive script in a disposable, account-less sandbox (Judge0, then Wandbox, or the user's own runner if configured): parse a log, decode an artifact, hash a file, test a regex, compute a timeline. Never put passwords, keys or personal data in the code or stdin -- the sandbox is a third party unless the user configured their own runner. No network access from the sandbox. Output is capped.",
        """{"type":"object","properties":{
          "language":{"type":"string","description":"python, javascript, typescript, bash, ruby, php, go, rust, java, c or cpp."},
          "code":{"type":"string","description":"The script."},
          "stdin":{"type":"string","description":"Optional input; never secrets."}
        },"required":["language","code"]}"""
    )

    val DELEGATE_INVESTIGATION = tool(
        "delegate_investigation",
        "Spawn focused, read-only investigator subagents in parallel, each on a different seated council model. Use for a large artifact, for independent reads of the same evidence, or to split 'what happened' from 'what do we do'. They return compact reports; you merge them and keep the disagreements.",
        """{"type":"object","properties":{
          "task":{"type":"string","description":"What the investigators must determine, with the evidence or where to find it."},
          "focus":{"type":"string","description":"Optional single focus: evidence, attribution, countermeasures, exposure. Omit for two complementary foci."}
        },"required":["task"]}"""
    )

    val ADD_FINDING = tool(
        "add_finding",
        "Record one finding on the shield's board, with the worst honest severity and one concrete action. Use it for anything the user must not lose when the chat scrolls -- not for observations.",
        """{"type":"object","properties":{
          "severity":{"type":"string","description":"critical, high, medium, low or info."},
          "title":{"type":"string","description":"What is wrong, one line."},
          "action":{"type":"string","description":"What to do about it, one line."}
        },"required":["severity","title","action"]}"""
    )

    val CONVENE_COUNCIL = tool(
        "convene_council",
        "Convene the security council: every configured model (Mercury 3, K2 Horizon, Hy3 by default, plus any the user seated) deliberates the brief in parallel at maximum reasoning -- first responder, adversary's advocate, verifier -- then a chair merges their findings and keeps the disagreements on the record. Use for anything worth a second and third opinion: a breach notice, a suspicious mail, an incident decision, an attribution claim.",
        """{"type":"object","properties":{
          "topic":{"type":"string","description":"The question the council must answer."},
          "artifact":{"type":"string","description":"The evidence: headers, notices, URLs, logs, configs. Quoted verbatim."}
        },"required":["topic"]}"""
    )

    val ASK_USER = tool(
        "ask_user",
        "Ask the user a question and wait (shown in the chat). Use when a decision is theirs: which account, whether to rotate now, what the mail actually was.",
        """{"type":"object","properties":{
          "question":{"type":"string","description":"The question, short."},
          "options":{"type":"array","items":{"type":"string"},"description":"Up to 4 tap-to-answer options."}
        },"required":["question"]}"""
    )

    val TAKE_NOTE = tool("take_note", "Remember a fact for this run (an indicator, a date, a hash).",
        """{"type":"object","properties":{"note":{"type":"string","description":"One line."}},"required":["note"]}""")

    val FINISH_TASK = tool(
        "finish_task",
        "End the guard task with its outcome. The summary is what the user reads first: put the verdict first, the actions in order, and say plainly what is confirmed versus suspected.",
        """{"type":"object","properties":{
          "summary":{"type":"string","description":"Verdict, then actions in order, then what remains unproven."},
          "success":{"type":"boolean","description":"True when the task's question is answered."}
        },"required":["summary","success"]}"""
    )

    val all: List<Tool> = listOf(
        AUDIT_PHONE, CHECK_URL, LOOKUP_ADVISORY, KEV_RECENT, SCAN_PROJECT_SECRETS,
        CHECK_VAULT, TRACE_INCIDENT, ENRICH_INDICATOR, AUDIT_WEB_POSTURE, AUDIT_DEPENDENCIES,
        DRAFT_ABUSE_REPORT, READ_FILE, LIST_FILES, GREP, WRITE_FILE, EDIT_FILE, RUN_SCRIPT,
        DELEGATE_INVESTIGATION, ADD_FINDING, CONVENE_COUNCIL, ASK_USER, TAKE_NOTE, FINISH_TASK,
    )

    val names: Set<String> = all.map { it.function.name }.toSet()

    val passive: Set<String> = setOf("ask_user", "take_note", "finish_task", "add_finding")

    /** The read-only subset an investigator subagent may use. */
    val investigatorNames: Set<String> = setOf(
        "check_url", "lookup_advisory", "kev_recent", "scan_project_secrets",
        "trace_incident", "enrich_indicator", "audit_web_posture", "audit_dependencies",
        "read_file", "list_files", "grep", "run_script",
    )

    fun forNames(names: Set<String>): List<Tool> = all.filter { it.function.name in names }

    fun humanize(name: String): String = when (name) {
        "audit_phone" -> "Auditing this phone's posture"
        "check_url" -> "Judging a link"
        "lookup_advisory" -> "Checking advisories"
        "kev_recent" -> "Reading the exploitation news"
        "scan_project_secrets" -> "Scanning the project for escaped secrets"
        "check_vault" -> "Auditing saved logins"
        "trace_incident" -> "Building the evidence record"
        "enrich_indicator" -> "Tracing the infrastructure"
        "audit_web_posture" -> "Auditing your site's posture"
        "audit_dependencies" -> "Auditing declared dependencies"
        "draft_abuse_report" -> "Drafting the abuse report"
        "read_file" -> "Reading a file"
        "list_files" -> "Listing files"
        "grep" -> "Searching the project"
        "write_file" -> "Writing a file"
        "edit_file" -> "Applying a hardening patch"
        "run_script" -> "Running a script in the sandbox"
        "delegate_investigation" -> "Delegating to investigators"
        "add_finding" -> "Recording a finding"
        "convene_council" -> "Convening the council"
        "use_browser" -> "Working in the browser"
        "ask_user" -> "Asking you"
        "take_note" -> "Taking a note"
        "finish_task" -> "Wrapping up"
        else -> name
    }
}
