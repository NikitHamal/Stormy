package com.oro.app.device

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * device_status: the facts a task often hinges on and the screen rarely
 * shows -- the time and date (for "remind me tomorrow"), battery, whether the
 * phone is online and how -- read from the system in one call.
 */
object DeviceInfo {

    fun status(context: Context): String = buildString {
        val now = ZonedDateTime.now()
        append("Time: ").append(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH)))
            .append(" (").append(now.zone.id).append(")\n")

        // BatteryManager properties rather than the sticky broadcast: no
        // receiver registration (and none of its export-flag rules) needed.
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level in 0..100) append("Battery: ").append(level).append('%').append(if (bm?.isCharging == true) ", charging" else "").append('\n')

        append("Network: ").append(network(context)).append('\n')

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            val mode = when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "ring"
            }
            val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            append("Sound: ").append(mode).append(", media volume ").append(vol * 100 / max).append("%\n")
        }
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        if (airplane) append("Airplane mode: on\n")
        append("Android ").append(Build.VERSION.RELEASE).append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" · language ").append(Locale.getDefault().toLanguageTag())
    }

    private fun network(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "connected"
        }
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (internet) "$kind, online" else "$kind, no internet"
    }
}
