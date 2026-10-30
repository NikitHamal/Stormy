package com.oro.app.guard

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool

/**
 * The guard agent's tool set: defensive only. Every tool audits, detects,
 * verifies or recovers; none of them reaches out and touches a system the
 * user does not own. The council tool convenes the models; the rest feed it
 * evidence.
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

    val CONVENE_COUNCIL = tool(
        "convene_council",
        "Convene the security council: every configured model (Mercury 3, K2 Horizon, Hy3 by default, plus any the user seated) deliberates the brief in parallel at maximum reasoning -- first responder, adversary's advocate, verifier -- then a chair merges their findings and keeps the disagreements on the record. Use for anything worth a second and third opinion: a breach notice, a suspicious mail, an incident decision.",
        """{"type":"object","properties":{
          "topic":{"type":"string","description":"The question the council must answer."},
          "artifact":{"type":"string","description":"The evidence: headers, notices, URLs, logs, configs. Quoted verbatim."}
        },"required":["topic"]}"""
    )

    val ASK_USER = tool(
        "ask_user",
        "Ask the user a question and wait (shown on the shield screen). Use when a decision is theirs: which account, whether to rotate now, what the mail actually was.",
        """{"type":"object","properties":{
          "question":{"type":"string","description":"The question, short."},
          "options":{"type":"array","items":{"type":"string"},"description":"Up to 4 tap-to-answer options."}
        },"required":["question"]}"""
    )

    val TAKE_NOTE = tool("take_note", "Remember a fact for this run (an indicator, a date, a hash).",
        """{"type":"object","properties":{"note":{"type":"string","description":"One line."}},"required":["note"]}""")

    val FINISH_TASK = tool(
        "finish_task",
        "End the guard task with its outcome. The summary is what the user reads in a fright: put the verdict first, the actions in order, and say plainly what is confirmed versus suspected.",
        """{"type":"object","properties":{
          "summary":{"type":"string","description":"Verdict, then actions in order, then what remains unproven."},
          "success":{"type":"boolean","description":"True when the task's question is answered."}
        },"required":["summary","success"]}"""
    )

    val all: List<Tool> = listOf(
        AUDIT_PHONE, CHECK_URL, LOOKUP_ADVISORY, KEV_RECENT, SCAN_PROJECT_SECRETS,
        CHECK_VAULT, CONVENE_COUNCIL, ASK_USER, TAKE_NOTE, FINISH_TASK,
    )

    val names: Set<String> = all.map { it.function.name }.toSet()

    val passive: Set<String> = setOf("ask_user", "take_note", "finish_task")
}
