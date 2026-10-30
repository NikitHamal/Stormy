package com.oro.app.guard

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.council.Severity
import org.junit.Test

/**
 * The posture rules, pure over [PhoneFacts]: every finding a phone audit can
 * raise is decided here, without a device.
 */
class PhoneAuditReportTest {

    private val clean = PhoneFacts(
        sdkInt = 36,
        securityPatch = "2026-10-01",
        isDeviceSecure = true,
        isEncrypted = true,
        adbEnabled = false,
        developerMode = false,
        installFromUnknownSources = false,
        accessibilityServices = emptyList(),
        deviceAdmins = emptyList(),
        permissionHolders = emptyMap(),
        playProtectEnabled = null,
        ownPermissions = emptyList(),
    )

    @Test
    fun `a clean phone gets one calm line`() {
        val report = PhoneAudit.report(clean, 2026, 10)
        assertThat(report).hasSize(1)
        assertThat(report[0].first).isEqualTo(Severity.INFO)
        assertThat(report[0].second).contains("No structural posture problems")
    }

    @Test
    fun `no screen lock outranks everything`() {
        val report = PhoneAudit.report(clean.copy(isDeviceSecure = false, adbEnabled = true), 2026, 10)
        assertThat(report.first().first).isEqualTo(Severity.CRITICAL)
        assertThat(report.first().second).contains("No screen lock")
    }

    @Test
    fun `patch age is computed in months and graded`() {
        assertThat(PhoneAudit.patchAgeMonths("2026-10-01", 2026, 10)).isEqualTo(0)
        assertThat(PhoneAudit.patchAgeMonths("2025-01-05", 2026, 10)).isEqualTo(21)
        val stale = PhoneAudit.report(clean.copy(securityPatch = "2025-01-05"), 2026, 10)
        assertThat(stale.any { it.first == Severity.HIGH && it.second.contains("21 months") }).isTrue()
        val mid = PhoneAudit.report(clean.copy(securityPatch = "2026-06-01"), 2026, 10)
        assertThat(mid.any { it.first == Severity.MEDIUM && it.second.contains("4 months") }).isTrue()
    }

    @Test
    fun `adb, unknown sources and accessibility services each raise a finding`() {
        val report = PhoneAudit.report(
            clean.copy(
                adbEnabled = true,
                installFromUnknownSources = true,
                accessibilityServices = listOf("com.spy.loader/.SpyService"),
            ),
            2026, 10,
        )
        val titles = report.map { it.second }
        assertThat(titles.any { it.contains("ADB") }).isTrue()
        assertThat(titles.any { it.contains("unknown sources") }).isTrue()
        assertThat(titles.any { it.contains("Accessibility service") && it.contains("com.spy.loader") }).isTrue()
        // Worst first.
        assertThat(report.map { it.first.rank }).isInOrder()
    }

    @Test
    fun `sms readers are called out individually, camera hordes by count`() {
        val report = PhoneAudit.report(
            clean.copy(
                permissionHolders = mapOf(
                    android.Manifest.permission.READ_SMS to listOf("com.carrier.app"),
                    android.Manifest.permission.CAMERA to listOf("a", "b", "c"),
                )
            ),
            2026, 10,
        )
        val titles = report.map { it.second }
        assertThat(titles.any { it.contains("com.carrier.app") && it.contains("SMS") }).isTrue()
        assertThat(titles.any { it.contains("3 apps") && it.contains("camera") }).isTrue()
    }

    @Test
    fun `a future-dated patch (emulator) is never stale`() {
        assertThat(PhoneAudit.patchAgeMonths("2027-01-01", 2026, 10)).isLessThan(0)
        assertThat(PhoneAudit.report(clean.copy(securityPatch = "2027-01-01"), 2026, 10)).hasSize(1)
    }
}
