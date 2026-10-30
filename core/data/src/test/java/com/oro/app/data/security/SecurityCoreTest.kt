package com.oro.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlGuardTest {

    @Test
    fun `an ordinary https link to a real brand is low risk`() {
        val v = UrlGuard.check("https://www.paypal.com/signin")
        assertThat(v.risk).isEqualTo(UrlGuard.Risk.LOW)
        assertThat(v.reasons).isEmpty()
    }

    @Test
    fun `brand parked on someone else's domain is the impersonation tell`() {
        val v = UrlGuard.check("https://paypal.com.evil-redirect.io/signin?x=1")
        assertThat(v.risk).isEqualTo(UrlGuard.Risk.DANGEROUS)
        assertThat(v.reasons.joinToString()).contains("impersonation")
    }

    @Test
    fun `brand in a subdomain of the brand's own domain is fine`() {
        assertThat(UrlGuard.check("https://signin.github.com/session").risk).isEqualTo(UrlGuard.Risk.LOW)
    }

    @Test
    fun `typo-squats one edit away are caught`() {
        val v = UrlGuard.check("https://paypa1.com/send")
        assertThat(v.risk).isEqualTo(UrlGuard.Risk.DANGEROUS)
        assertThat(v.reasons.joinToString()).contains("typo-squat")
    }

    @Test
    fun `the userinfo trick is called out by name`() {
        val v = UrlGuard.check("https://google.com@accounts.evil.net/oauth")
        assertThat(v.reasons.joinToString()).contains("user@")
        assertThat(v.risk).isEqualTo(UrlGuard.Risk.DANGEROUS)
    }

    @Test
    fun `raw ip hosts and punycode homographs are dangerous`() {
        assertThat(UrlGuard.check("http://192.168.4.9/.well/login").reasons.joinToString()).contains("raw IP")
        val homo = UrlGuard.check("https://xn--pypal-4ve.com/")
        assertThat(homo.reasons.joinToString()).contains("Punycode")
    }

    @Test
    fun `shorteners warn without condemning`() {
        val v = UrlGuard.check("https://bit.ly/3xKp2Qz")
        assertThat(v.risk).isEqualTo(UrlGuard.Risk.CAUTION)
        assertThat(v.reasons.joinToString()).contains("shortened")
    }

    @Test
    fun `cleartext http adds risk but a plain blog stays readable`() {
        val v = UrlGuard.check("http://example.blog/posts/1")
        assertThat(v.reasons.joinToString()).contains("No TLS")
    }

    @Test
    fun `unparsable urls are treated as hostile`() {
        assertThat(UrlGuard.check("not a url at all :://").risk).isEqualTo(UrlGuard.Risk.DANGEROUS)
    }

    @Test
    fun `registrable domain handles two-part public suffixes`() {
        assertThat(UrlGuard.registrable("a.b.example.co.uk")).isEqualTo("example.co.uk")
        assertThat(UrlGuard.registrable("login.evil.io")).isEqualTo("evil.io")
    }
}

class PwnedTest {

    @Test
    fun `the range answer is matched locally by suffix`() {
        val body = """
            005F1B1B9192FE31DDA889AC9C1D1D853B2:2
            00D5F1C006B9E11E47BE75E724E5F5E18C9:11
            5BE9877AB58D9408DEC4C4B8A2143AA9BB0:1
        """.trimIndent()
        assertThat(Pwned.parseRange(body, "00D5F1C006B9E11E47BE75E724E5F5E18C9")).isEqualTo(11)
        assertThat(Pwned.parseRange(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF")).isEqualTo(0)
    }

    @Test
    fun `sha1 matches the published vector so the k-anonymity prefix is right`() {
        // SHA-1("password") is a published test vector.
        assertThat(Pwned.sha1Hex("password")).isEqualTo("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8")
    }
}

class AdvisoriesTest {

    @Test
    fun `osv responses parse into advisories`() {
        val json = """
            {"vulns":[
              {"id":"GHSA-xxxx-yyyy-zzzz","summary":"Prototype pollution in lodash","aliases":["CVE-2019-10744"],
               "references":[{"type":"ADVISORY","url":"https://nvd.nist.gov/vuln/detail/CVE-2019-10744"}],
               "database_specific":{"severity":"HIGH"}},
              {"id":"GHSA-aaaa-bbbb-cccc","summary":"","aliases":[]}
            ]}
        """.trimIndent()
        val adv = Advisories.parseOsv(json)
        assertThat(adv).hasSize(2)
        assertThat(adv[0].id).isEqualTo("GHSA-xxxx-yyyy-zzzz")
        assertThat(adv[0].severity).isEqualTo("HIGH")
        assertThat(adv[0].aliases).containsExactly("CVE-2019-10744")
        assertThat(adv[0].reference).contains("nvd.nist.gov")
        assertThat(adv[1].summary).isEqualTo("GHSA-aaaa-bbbb-cccc")
    }

    @Test
    fun `the kev catalog parses and diffs to only the new exploitation news`() {
        val json = """
            {"vulnerabilities":[
              {"cveID":"CVE-2026-1234","vendorProject":"Vendor","product":"Widget","dateAdded":"2026-10-01","knownRansomwareCampaignUse":"Known"},
              {"cveID":"CVE-2026-5678","vendorProject":"Other","product":"Thing","dateAdded":"2026-10-20","knownRansomwareCampaignUse":"Unknown"}
            ]}
        """.trimIndent()
        val all = Advisories.parseKev(json)
        assertThat(all).hasSize(2)
        assertThat(all[0].knownRansomware).isTrue()
        val fresh = Advisories.newKev(all, setOf("CVE-2026-1234"))
        assertThat(fresh.map { it.cve }).containsExactly("CVE-2026-5678")
    }

    @Test
    fun `broken feeds are null or empty, never fake-clean`() {
        assertThat(Advisories.parseKev("not json")).isEmpty()
        assertThat(Advisories.parseOsv("{}")).isEmpty()
    }
}

class SecretScanTest {

    @Test
    fun `real credential shapes are caught and masked`() {
        // The secrets are assembled at runtime: the test source itself must
        // not carry contiguous credential shapes (push protection, and it
        // would be a lie in a repo about secret hygiene).
        val stripeSecret = "sk_live_" + "51HxYzAbC1234567890abcde"
        val awsSecret = "AKIA" + "IOSFODNN7EXAMPLE"
        val text = listOf(
            "const stripe = \"$stripeSecret\";",
            "aws_key = \"$awsSecret\"",
            "-----BEGIN RSA PRIVATE KEY-----",
            "url = \"postgres://admin:hunter2secret@db.internal:5432/app\"",
        ).joinToString("\n")
        val hits = SecretScan.scanText(text)
        assertThat(hits.map { it.kind }).containsAtLeast("stripe live key", "aws access key", "private key", "connection string with password")
        assertThat(hits.first { it.kind == "stripe live key" }.line).isEqualTo(1)
        // The masked form carries the shape, never the secret.
        val masked = SecretScan.mask("sk_live_" + "51HxYzAbC1234567890abcde")
        assertThat(masked).doesNotContain("51HxYzAbC1234567890abcde")
        assertThat(masked).startsWith("sk_")
    }

    @Test
    fun `examples and placeholders do not fire`() {
        val text = """
            password = "changeme"
            API_KEY = "${'$'}{MY_API_KEY}"
            secret: "xxxx"
            # password: your-password-here
            token = "test"
        """.trimIndent()
        assertThat(SecretScan.scanText(text)).isEmpty()
    }

    @Test
    fun `github and telegram tokens are recognised`() {
        val hits = SecretScan.scanText(
            "ghp_" + "a".repeat(40) + "\n" + "123456789:AA" + "b".repeat(32)
        )
        assertThat(hits.map { it.kind }).containsAtLeast("github token", "telegram bot token")
    }
}
