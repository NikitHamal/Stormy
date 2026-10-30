package com.oro.app.guard

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.oro.app.data.ai.council.Severity as SeverityRank

/**
 * What the phone says about its own posture, read with nothing more exotic
 * than the settings and package APIs any app may call: no accessibility
 * service, no device-admin, no all-packages query (Play-safe by
 * construction -- [Context.getSystemService] and
 * [PackageManager.getPackagesHoldingPermissions] are the sharpest tools
 * here, and both answer only about the device, never about other apps'
 * data).
 *
 * The collector fills [PhoneFacts]; the report is a pure function of the
 * facts, so every rule ("no screen lock", "ADB on", "an accessibility
 * service from outside the Play install") is unit-tested without a device.
 */
data class PhoneFacts(
    val sdkInt: Int,
    val securityPatch: String,
    val isDeviceSecure: Boolean,
    val isEncrypted: Boolean,
    val adbEnabled: Boolean,
    val developerMode: Boolean,
    val installFromUnknownSources: Boolean,
    val accessibilityServices: List<String>,
    val deviceAdmins: List<String>,
    /** Packages holding each dangerous permission we ask about. */
    val permissionHolders: Map<String, List<String>>,
    val playProtectEnabled: Boolean?,
    val ownPermissions: List<String>,
)

object PhoneAudit {

    /** The permissions worth knowing about, mapped to why a person cares. */
    val WATCHED_PERMISSIONS = listOf(
        android.Manifest.permission.READ_SMS to "read your SMS",
        android.Manifest.permission.RECEIVE_SMS to "receive your SMS",
        android.Manifest.permission.READ_CONTACTS to "read your contacts",
        android.Manifest.permission.ACCESS_FINE_LOCATION to "know your precise location",
        android.Manifest.permission.RECORD_AUDIO to "record the microphone",
        android.Manifest.permission.CAMERA to "use the camera",
        android.Manifest.permission.READ_CALL_LOG to "read your call log",
    )

    @SuppressLint("NewApi")
    fun collect(context: Context): PhoneFacts {
        val pm = context.packageManager
        val cr = context.contentResolver
        val secure = runCatching {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true
        }.getOrDefault(false)
        val encrypted = runCatching {
            android.os.storage.StorageManager.isEncrypted() || Build.VERSION.SDK_INT >= 24
        }.getOrDefault(true)
        val adb = runCatching { Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
        val devMode = runCatching { Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)
        val unknownSources = runCatching { Settings.Secure.getInt(cr, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1 }.getOrDefault(false)
        val a11y = runCatching {
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        }.getOrDefault(emptyList())
        val admins = runCatching {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager)
                ?.activeAdmins?.map { it.packageName ?: "?" }.orEmpty()
        }.getOrDefault(emptyList())
        val holders = HashMap<String, List<String>>()
        for ((perm, _) in WATCHED_PERMISSIONS) {
            holders[perm] = runCatching {
                pm.getPackagesHoldingPermissions(arrayOf(perm))
                    .map { it.packageName }
                    .filter { it != context.packageName }
            }.getOrDefault(emptyList<String>())
        }
        val own: List<String> = runCatching {
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return PhoneFacts(
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "unknown",
            isDeviceSecure = secure,
            isEncrypted = encrypted,
            adbEnabled = adb,
            developerMode = devMode,
            installFromUnknownSources = unknownSources,
            accessibilityServices = a11y,
            deviceAdmins = admins,
            permissionHolders = holders.toMap(),
            playProtectEnabled = null, // only the Play app knows; the report links to it
            ownPermissions = own,
        )
    }

    /** Months between the device's patch and [nowYear]-[nowMonth]; negative means future-dated (emulators). */
    fun patchAgeMonths(securityPatch: String, nowYear: Int, nowMonth: Int): Int {
        val parts = securityPatch.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
        return (nowYear - y) * 12 + (nowMonth - m)
    }

    /**
     * The posture report: findings worst-first, each with the setting that
     * changes it. Pure over [PhoneFacts] so the rules are tested, not hoped.
     */
    fun report(f: PhoneFacts, nowYear: Int, nowMonth: Int): List<Triple<SeverityRank, String, String>> {
        val out = ArrayList<Triple<SeverityRank, String, String>>()
        if (!f.isDeviceSecure) {
            out += Triple(SeverityRank.CRITICAL, "No screen lock on this phone",
                "Settings > Security > Screen lock. Everything else on the device depends on this one.")
        }
        val age = patchAgeMonths(f.securityPatch, nowYear, nowMonth)
        if (age >= 6) {
            out += Triple(SeverityRank.HIGH, "Security patch is $age months old (${f.securityPatch})",
                "Settings > System > System update. Exploited-after-patch attacks live exactly here.")
        } else if (age >= 3) {
            out += Triple(SeverityRank.MEDIUM, "Security patch is $age months old (${f.securityPatch})",
                "Check for a system update when convenient.")
        }
        if (f.adbEnabled) {
            out += Triple(SeverityRank.HIGH, "USB debugging (ADB) is on",
                "Developer options > USB debugging. With it on, a cable (or a bad charger) is a root shell waiting to happen.")
        }
        if (f.installFromUnknownSources) {
            out += Triple(SeverityRank.HIGH, "Install from unknown sources is allowed",
                "Settings > Apps > Special access > Install unknown apps. This is how loader APKs arrive.")
        }
        for (svc in f.accessibilityServices) {
            out += Triple(SeverityRank.HIGH, "Accessibility service enabled: $svc",
                "Accessibility can read and drive every screen. Keep only services you installed on purpose and still use; Settings > Accessibility to review.")
        }
        for (admin in f.deviceAdmins) {
            out += Triple(SeverityRank.MEDIUM, "Device-admin app: $admin",
                "Device admins can enforce policy and block uninstall; review whether it still earns the role.")
        }
        for ((perm, why) in WATCHED_PERMISSIONS) {
            val holders = f.permissionHolders[perm].orEmpty()
            if (holders.size >= 3) {
                out += Triple(SeverityRank.MEDIUM, "${holders.size} apps can $why",
                    holders.joinToString(", ") + " — prune to the ones you would defend in court.")
            } else if (holders.isNotEmpty() && (perm.endsWith("READ_SMS") || perm.endsWith("RECEIVE_SMS"))) {
                out += Triple(SeverityRank.MEDIUM, "${holders.joinToString()} can $why",
                    "SMS is your second-factor channel; every reader of it is a takeover path.")
            }
        }
        if (out.isEmpty()) {
            out += Triple(SeverityRank.INFO, "No structural posture problems found",
                "Lock screen, patch level and install sources all look sane today. Re-audit after any new app or odd behaviour.")
        }
        return out.sortedBy { it.first.rank }
    }
}
