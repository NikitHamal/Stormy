package com.oro.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThreatTraceTest {

    @Test
    fun `extracts real indicators and ignores noise`() {
        val text = """
            Mail from Security <security@bank-alert.co> at 10:42.
            Link: https://paypa1-secure.example-login.xyz/verify?token=abc123
            Origin 185.199.108.153 and private 192.168.1.5 (ignore that one).
            File sha256 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
            CVE-2026-109618 was used. Version 2.4.1 is not an indicator.
        """.trimIndent()
        val found = ThreatTrace.extract(text)
        val values = found.map { it.value }
        assertThat(values).contains("https://paypa1-secure.example-login.xyz/verify?token=abc123")
        assertThat(values).contains("paypa1-secure.example-login.xyz")
        assertThat(values).contains("security@bank-alert.co")
        assertThat(values).contains("185.199.108.153")
        assertThat(values).contains("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        assertThat(values).contains("CVE-2026-109618")
        assertThat(values).doesNotContain("192.168.1.5")
        assertThat(values).doesNotContain("2.4.1")
    }

    @Test
    fun `timeline sorts every date format oldest first`() {
        val text = """
            Oct 9, 2026: the bank was notified.
            2026-10-02 the first login alert arrived.
            On 7 Oct 2026 the attacker changed the recovery address.
            2026-10-05T11:22:00Z a second session appeared.
            No date on this explanatory line.
        """.trimIndent()
        val lines = ThreatTrace.timeline(text).filter { it.epochMillis != null }
        assertThat(lines).hasSize(4)
        assertThat(lines[0].text).contains("2026-10-02")
        assertThat(lines[1].text).contains("2026-10-05T11:22:00Z")
        assertThat(lines[2].text).contains("7 Oct 2026")
        assertThat(lines[3].text).contains("Oct 9, 2026")
        assertThat(ThreatTrace.span(ThreatTrace.timeline(text))).isNotNull()
    }

    @Test
    fun `redaction keeps the key and removes the value`() {
        val raw = """
            Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature
            password=hunter2
            api_key: "sk_live_abcdefghijklmnop"
            session_id: 8f14e45fceea167a5a36dedd4bea2543
            Cookie: session=abc123; theme=dark
        """.trimIndent()
        val clean = ThreatTrace.redact(raw)
        assertThat(clean).contains("Authorization: [redacted]")
        assertThat(clean).contains("password: [redacted]")
        assertThat(clean).contains("api_key: [redacted]")
        assertThat(clean).doesNotContain("hunter2")
        assertThat(clean).doesNotContain("sk_live_abcdefghijklmnop")
        assertThat(clean).doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        assertThat(clean).doesNotContain("session=abc123")
    }

    @Test
    fun `the fingerprint is stable and independent of order`() {
        val a = ThreatTrace.extract("evil.xyz 185.199.108.153 CVE-2026-109618")
        val b = ThreatTrace.extract("CVE-2026-109618 185.199.108.153 evil.xyz")
        assertThat(ThreatTrace.fingerprint(a)).isEqualTo(ThreatTrace.fingerprint(b))
        assertThat(ThreatTrace.fingerprint(a)).hasLength(16)
    }

    @Test
    fun `analysis notes what the artifact cannot prove`() {
        val dossier = ThreatTrace.analyze("password=hunter2\nvisit evil.xyz")
        assertThat(dossier.indicators.map { it.value }).contains("evil.xyz")
        assertThat(dossier.notes.joinToString()).contains("redacted")
        assertThat(dossier.notes.joinToString()).contains("no machine-readable date")
        assertThat(dossier.render()).contains("Indicators")
    }
}
