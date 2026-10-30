package com.oro.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThreatIntelTest {

    private val rdap = """
        {"objectClassName":"domain","ldhName":"EVIL.EXAMPLE",
         "status":["client transfer prohibited"],
         "events":[{"eventAction":"registration","eventDate":"2026-10-01T00:00:00Z"},
                   {"eventAction":"expiration","eventDate":"2027-10-01T00:00:00Z"}],
         "nameservers":[{"ldhName":"NS1.EVIL.EXAMPLE"}],
         "entities":[
           {"roles":["registrar"],"vcardArray":["vcard",[["fn",{},"text","NameSilo, LLC"]]]},
           {"roles":["abuse"],"vcardArray":["vcard",[["email",{},"text","abuse@namesilo.com"]]]}
         ]}
    """.trimIndent()

    @Test
    fun `rdap domain gives registrar dates status and abuse contact`() {
        val report = ThreatIntel.parseRdapDomain(rdap, "evil.example")!!
        assertThat(report.registrar).isEqualTo("NameSilo, LLC")
        assertThat(report.abuseEmail).isEqualTo("abuse@namesilo.com")
        assertThat(report.created).isEqualTo("2026-10-01T00:00:00Z")
        assertThat(report.expires).isEqualTo("2027-10-01T00:00:00Z")
        assertThat(report.statuses).contains("client transfer prohibited")
        assertThat(report.nameservers).contains("ns1.evil.example")
    }

    @Test
    fun `doh answers become values`() {
        val json = """{"Status":0,"Answer":[{"name":"evil.example","type":1,"data":"185.199.108.153"}]}"""
        assertThat(ThreatIntel.parseDns(json, "A")).containsExactly("185.199.108.153")
    }

    @Test
    fun `certificate transparency rows dedupe wildcards and keep coverage`() {
        val json = """
            [{"name_value":"*.evil.example\nevil.example","not_before":"2026-01-01T00:00:00","not_after":"2027-01-01T00:00:00"},
             {"name_value":"mail.evil.example","not_before":"2026-02-01T00:00:00","not_after":"2026-12-01T00:00:00"}]
        """.trimIndent()
        val summary = ThreatIntel.parseCrtSh(json, "evil.example")
        assertThat(summary.names).containsExactly("evil.example", "mail.evil.example")
        assertThat(summary.certCount).isEqualTo(2)
        assertThat(summary.first).isEqualTo("2026-01-01T00:00:00")
        assertThat(summary.last).isEqualTo("2027-01-01T00:00:00")
    }

    @Test
    fun `urlscan search counts malicious verdicts`() {
        val json = """
            {"results":[
              {"task":{"url":"https://evil.example/login","time":"2026-10-20T10:00:00Z"},
               "page":{"url":"https://evil.example/login"},
               "verdicts":{"overall":{"malicious":true,"score":80}}},
              {"task":{"url":"https://evil.example/","time":"2026-10-21T10:00:00Z"},
               "verdicts":{"overall":{"malicious":false,"score":0}}}
            ]}
        """.trimIndent()
        val scan = ThreatIntel.parseUrlscan(json)
        assertThat(scan.scans).isEqualTo(2)
        assertThat(scan.malicious).isEqualTo(1)
        assertThat(scan.lastScan).isEqualTo("2026-10-21T10:00:00Z")
        assertThat(scan.samples).contains("https://evil.example/login")
    }

    @Test
    fun `manifests declare exact and ranged dependencies`() {
        val files = listOf(
            "app/package.json" to """{"dependencies":{"lodash":"4.17.20","express":"^4.18.2","local":"file:../x"},
                "devDependencies":{"vite":"5.0.0"}}""",
            "api/requirements.txt" to "requests==2.31.0\nflask>=3.0\n# comment",
            "service/go.mod" to "module x\nrequire github.com/gin-gonic/gin v1.9.1",
        )
        val deps = ThreatIntel.declaredDependencies(files)
        assertThat(deps).contains(Triple("npm", "lodash", "4.17.20"))
        assertThat(deps).contains(Triple("npm", "express", null))
        assertThat(deps).contains(Triple("npm", "vite", "5.0.0"))
        assertThat(deps).contains(Triple("PyPI", "requests", "2.31.0"))
        assertThat(deps).contains(Triple("PyPI", "flask", null))
        assertThat(deps).contains(Triple("Go", "github.com/gin-gonic/gin", "v1.9.1"))
        assertThat(deps.map { it.second }).doesNotContain("local")
    }

    @Test
    fun `an abuse report redacts evidence and names the requested action`() {
        val indicators = ThreatTrace.extract("https://evil.example/login 185.199.108.153")
        val channels = ThreatIntel.abuseChannels(
            ThreatIntel.DomainReport("evil.example", abuseEmail = "abuse@registrar.example"),
            ThreatIntel.IpReport("185.199.108.153", abuseEmail = "abuse@host.example"),
        )
        val report = ThreatIntel.draftAbuseReport(
            kind = "phishing",
            userNote = "The login form steals a bank password.",
            evidence = "Host: evil.example\npassword=hunter2",
            indicators = indicators,
            channels = channels,
        )
        assertThat(report).contains("Requested action")
        assertThat(report).contains("https://evil.example/login")
        assertThat(report).doesNotContain("hunter2")
        assertThat(report).contains("abuse@registrar.example")
        assertThat(report).contains("Google Safe Browsing")
    }

    @Test
    fun `web posture render lists findings with the severity contract`() {
        val posture = ThreatIntel.WebPosture(
            url = "https://example.org",
            finalUrl = "https://example.org",
            status = 200,
            headers = emptyMap(),
            findings = listOf(ThreatIntel.PostureFinding("HIGH", "No DMARC record", "Publish _dmarc")),
            spf = false,
            dmarc = false,
            dnssec = false,
        )
        assertThat(posture.render()).contains("[HIGH] No DMARC record -> Publish _dmarc")
        assertThat(posture.render()).contains("SPF MISSING")
    }
}
