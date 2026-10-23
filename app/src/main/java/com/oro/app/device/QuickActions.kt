package com.oro.app.device

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Common tasks done in one step through the intents apps already publish,
 * instead of tapping through their UI: an alarm is one intent, not eight
 * taps in a Clock app whose layout differs on every phone.
 *
 * Nothing here sends, pays or calls on its own: dial opens the dialer with
 * the number filled in, sms/email open a filled-in composer, share opens the
 * chooser. The final "Send" is a normal tap, which [SensitiveGuard] gates.
 */
object QuickActions {

    val ACTIONS = listOf(
        "alarm", "timer", "search", "navigate", "maps", "settings", "panel", "app_info", "dial", "sms", "whatsapp", "email",
        "share", "event", "music", "store", "flashlight", "volume",
    )

    /** Settings pages by the names people use, mapped to their intent actions. */
    private val SETTINGS = mapOf(
        "wifi" to "android.settings.WIFI_SETTINGS",
        "wi-fi" to "android.settings.WIFI_SETTINGS",
        "internet" to "android.settings.WIFI_SETTINGS",
        "bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
        "display" to "android.settings.DISPLAY_SETTINGS",
        "brightness" to "android.settings.DISPLAY_SETTINGS",
        "sound" to "android.settings.SOUND_SETTINGS",
        "volume" to "android.settings.SOUND_SETTINGS",
        "battery" to "android.intent.action.POWER_USAGE_SUMMARY",
        "apps" to "android.settings.APPLICATION_SETTINGS",
        "location" to "android.settings.LOCATION_SOURCE_SETTINGS",
        "gps" to "android.settings.LOCATION_SOURCE_SETTINGS",
        "accessibility" to "android.settings.ACCESSIBILITY_SETTINGS",
        "notifications" to "android.settings.NOTIFICATION_SETTINGS",
        "airplane" to "android.settings.AIRPLANE_MODE_SETTINGS",
        "data" to "android.settings.DATA_ROAMING_SETTINGS",
        "mobile data" to "android.settings.DATA_ROAMING_SETTINGS",
        "nfc" to "android.settings.NFC_SETTINGS",
        "date" to "android.settings.DATE_SETTINGS",
        "time" to "android.settings.DATE_SETTINGS",
        "language" to "android.settings.LOCALE_SETTINGS",
        "security" to "android.settings.SECURITY_SETTINGS",
        "privacy" to "android.settings.PRIVACY_SETTINGS",
        "storage" to "android.settings.INTERNAL_STORAGE_SETTINGS",
        "developer" to "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        "about" to "android.settings.DEVICE_INFO_SETTINGS",
        "hotspot" to "android.settings.WIRELESS_SETTINGS",
        "network" to "android.settings.WIRELESS_SETTINGS",
        "vpn" to "android.settings.VPN_SETTINGS",
        "keyboard" to "android.settings.INPUT_METHOD_SETTINGS",
        "dnd" to "android.settings.ZEN_MODE_PRIORITY_SETTINGS",
        "do not disturb" to "android.settings.ZEN_MODE_PRIORITY_SETTINGS",
        "settings" to "android.settings.SETTINGS",
    )

    val settingsPages: Set<String> get() = SETTINGS.keys

    /** Intent action for a settings page name, or null. Pure. */
    fun settingsAction(page: String?): String? {
        val p = page?.trim()?.lowercase()?.removeSuffix(" settings")?.trim() ?: return null
        return SETTINGS[p] ?: SETTINGS.entries.firstOrNull { p.contains(it.key) }?.value
    }

    /** "7:30", "07:30", "7.30", "7:30 pm", "7pm", "19:05" → (hour, minute). Pure. */
    fun parseClock(text: String?): Pair<Int, Int>? {
        val t = text?.trim()?.lowercase()?.replace(" ", "") ?: return null
        val m = Regex("^(\\d{1,2})(?:[:.h](\\d{2}))?(am|pm|a\\.m\\.|p\\.m\\.)?$").find(t) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val ampm = m.groupValues[3]
        if (ampm.isEmpty() && m.groupValues[2].isEmpty()) return null // a bare number is not a time
        if (ampm.startsWith("p") && hour in 1..11) hour += 12
        if (ampm.startsWith("a") && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    /**
     * "2026-10-23 15:00", "2026-10-23T15:00", "tomorrow 9am", "today 18:30",
     * or a bare time (today, or tomorrow if already past) → epoch millis. Pure
     * apart from [now].
     */
    fun parseDateTime(text: String?, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = text?.trim()?.lowercase() ?: return null
        val iso = Regex("^(\\d{4}-\\d{2}-\\d{2})[ t](.+)$").find(t)
        val (date, timePart) = when {
            iso != null -> (runCatching { LocalDate.parse(iso.groupValues[1]) }.getOrNull() ?: return null) to iso.groupValues[2]
            t.startsWith("tomorrow") -> now.toLocalDate().plusDays(1) to t.removePrefix("tomorrow").removePrefix(" at").trim()
            t.startsWith("today") -> now.toLocalDate() to t.removePrefix("today").removePrefix(" at").trim()
            else -> null to t
        }
        val (h, m) = parseClock(timePart) ?: return null
        val day = date ?: now.toLocalDate().let { today ->
            if (LocalTime.of(h, m).isAfter(now.toLocalTime())) today else today.plusDays(1)
        }
        return day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * A WhatsApp chat link: wa.me with the number in international digits and
     * the message filled in, or wa.me/?text= to pick the contact in WhatsApp.
     * Null when there is neither a usable number nor text. Pure.
     */
    fun whatsappLink(to: String?, text: String?): String? {
        val digits = to.orEmpty().filter { it.isDigit() }
        val msg = text?.trim().orEmpty()
        if (digits.isNotEmpty() && digits.length < 7) return null
        if (digits.isEmpty() && msg.isEmpty()) return null
        val q = if (msg.isNotEmpty()) "?text=" + encode(msg) else ""
        return "https://wa.me/" + digits + q
    }

    /** Play Store: the app's page for a package id, else a search. Pure. */
    fun storeLink(query: String?): String? {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return null
        return if (PACKAGE_ID.matches(q)) "market://details?id=$q" else "market://search?q=" + encode(q) + "&c=apps"
    }

    /** The web page for a market:// link, for phones without the Play Store app. Pure. */
    fun storeWebLink(market: String): String =
        if (market.startsWith("market://details")) market.replace("market://details", "https://play.google.com/store/apps/details")
        else market.replace("market://search", "https://play.google.com/store/search")

    /** "on", "off", "toggle" (or nothing) -> true, false, null (= flip). Pure. */
    fun parseSwitch(text: String?): Boolean? = when (text?.trim()?.lowercase()) {
        "on", "true", "yes", "enable", "enabled", "start" -> true
        "off", "false", "no", "disable", "disabled", "stop" -> false
        else -> null
    }

    /** What a volume request means: up, down, mute, unmute, max, or a percentage. Pure. */
    sealed interface Volume {
        data object Up : Volume
        data object Down : Volume
        data object Mute : Volume
        data object Unmute : Volume
        data class Percent(val value: Int) : Volume
    }

    fun parseVolume(text: String?): Volume? {
        val t = text?.trim()?.lowercase()?.removeSuffix("%")?.trim() ?: return null
        return when (t) {
            "up", "louder", "raise", "increase", "+" -> Volume.Up
            "down", "quieter", "lower", "decrease", "-" -> Volume.Down
            "mute", "silent", "off", "0" -> Volume.Mute
            "unmute", "on" -> Volume.Unmute
            "max", "maximum", "full" -> Volume.Percent(100)
            else -> t.toDoubleOrNull()?.let { v ->
                // "0.4" means 40 %; "40" means 40 %.
                val pct = if (v in 0.0..1.0 && t.contains('.')) v * 100 else v
                Volume.Percent(pct.roundToInt().coerceIn(0, 100))
            }
        }
    }

    /** Settings panels that float over the current app (Android 10+): internet, wifi, nfc, volume. Pure. */
    fun panelAction(page: String?): String? = when (page?.trim()?.lowercase()?.removeSuffix(" panel")) {
        "internet", "data", "mobile data", "network", "connectivity" -> "android.settings.panel.action.INTERNET_CONNECTIVITY"
        "wifi", "wi-fi" -> "android.settings.panel.action.WIFI"
        "nfc" -> "android.settings.panel.action.NFC"
        "volume", "sound" -> "android.settings.panel.action.VOLUME"
        else -> null
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private val PACKAGE_ID = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    data class Args(
        val action: String,
        val text: String? = null,
        val to: String? = null,
        val time: String? = null,
        val minutes: Double? = null,
        val page: String? = null,
        val subject: String? = null,
    )

    /** Runs [a] and says what happened, in a sentence the model can act on. */
    fun run(context: Context, a: Args): String {
        val action = a.action.trim().lowercase()
        val text = a.text?.trim().orEmpty()
        return when (action) {
            "alarm" -> {
                val (h, m) = parseClock(a.time) ?: return "alarm needs time like \"07:30\" or \"7:30 pm\"."
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, h)
                    .putExtra(AlarmClock.EXTRA_MINUTES, m)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (text.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, text)
                start(context, intent, "Asked the Clock app to set an alarm for %02d:%02d%s.".format(h, m, if (text.isNotEmpty()) " ($text)" else ""))
            }
            "timer" -> {
                val seconds = ((a.minutes ?: return "timer needs minutes, e.g. 5 or 0.5.") * 60).toInt()
                if (seconds <= 0) return "timer needs a positive number of minutes."
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (text.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, text)
                start(context, intent, "Started a timer for ${formatSeconds(seconds)}.")
            }
            "search" -> {
                if (text.isEmpty()) return "search needs text (the query)."
                val web = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
                if (tryStart(context, web)) "Searched the web for \"$text\"."
                else start(context, view("https://www.google.com/search?q=" + Uri.encode(text)), "Opened a web search for \"$text\".")
            }
            "navigate" -> {
                if (text.isEmpty()) return "navigate needs text (the destination)."
                val nav = view("google.navigation:q=" + Uri.encode(text))
                if (tryStart(context, nav)) "Started navigation to \"$text\"."
                else start(context, view("geo:0,0?q=" + Uri.encode(text)), "Opened the map at \"$text\".")
            }
            "settings" -> {
                val target = settingsAction(a.page ?: text) ?: "android.settings.SETTINGS"
                start(context, Intent(target), "Opened ${a.page ?: text.ifEmpty { "Settings" }} settings.")
            }
            "dial" -> {
                val number = a.to?.trim().orEmpty().ifEmpty { return "dial needs to (the phone number)." }
                start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))),
                    "Opened the dialer with $number. It does not call until the call button is tapped.")
            }
            "sms" -> {
                val uri = Uri.parse("smsto:" + Uri.encode(a.to?.trim().orEmpty()))
                val intent = Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", text)
                start(context, intent, "Opened a message${a.to?.let { " to $it" } ?: ""} with the text filled in. It is not sent until Send is tapped.")
            }
            "email" -> {
                val to = a.to?.trim().orEmpty()
                val query = buildList {
                    a.subject?.takeIf { it.isNotBlank() }?.let { add("subject=" + Uri.encode(it)) }
                    if (text.isNotEmpty()) add("body=" + Uri.encode(text))
                }.joinToString("&")
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to) + if (query.isNotEmpty()) "?$query" else ""))
                start(context, intent, "Opened an email draft${if (to.isNotEmpty()) " to $to" else ""}. It is not sent until Send is tapped.")
            }
            "share" -> {
                if (text.isEmpty()) return "share needs text."
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                start(context, Intent.createChooser(send, null), "Opened the share sheet. Pick the app on screen.")
            }
            "event" -> {
                if (text.isEmpty()) return "event needs text (the title)."
                val begin = parseDateTime(a.time) ?: return "event needs time like \"2026-10-23 15:00\" or \"tomorrow 9am\"."
                val end = begin + ((a.minutes ?: 60.0) * 60_000).toLong()
                val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, text)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                start(context, intent, "Opened a new calendar event \"$text\". Tap Save on screen to keep it.")
            }
            "maps" -> {
                if (text.isEmpty()) return "maps needs text (the place or what to find)."
                start(context, view("geo:0,0?q=" + Uri.encode(text)), "Opened the map searching \"$text\".")
            }
            "whatsapp" -> {
                val link = whatsappLink(a.to, text) ?: return "whatsapp needs to (phone number with country code) and/or text."
                start(context, view(link), "Opened WhatsApp" + (a.to?.let { " for $it" } ?: "") +
                    (if (text.isNotEmpty()) " with the message filled in" else "") + ". It is not sent until Send is tapped.")
            }
            "store" -> {
                val link = storeLink(text.ifEmpty { a.to }) ?: return "store needs text (app name or package id)."
                if (tryStart(context, view(link))) "Opened the Play Store for \"${text.ifEmpty { a.to }}\"."
                else start(context, view(storeWebLink(link)), "Opened the Play Store page in the browser.")
            }
            "panel" -> {
                val panel = panelAction(a.page ?: text)
                if (panel != null && Build.VERSION.SDK_INT >= 29 && tryStart(context, Intent(panel))) {
                    "Opened the ${(a.page ?: text).trim()} panel over the current app. Toggle what the task needs, then press back."
                } else {
                    val target = settingsAction(a.page ?: text) ?: "android.settings.SETTINGS"
                    start(context, Intent(target), "Opened ${(a.page ?: text).ifEmpty { "phone" }} settings.")
                }
            }
            "app_info" -> {
                if (text.isEmpty()) return "app_info needs text (the app name)."
                val app = AppCatalog.resolve(context, text) ?: return "No installed app matches \"$text\"."
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                start(context, intent, "Opened ${app.label}'s app settings (notifications, permissions, storage, force stop).")
            }
            "flashlight" -> flashlight(context, parseSwitch(text.ifEmpty { a.page }))
            "volume" -> {
                val v = parseVolume(text.ifEmpty { a.page }) ?: return "volume needs text: up, down, mute, unmute, max or a percentage like 40."
                volume(context, v)
            }
            "music" -> {
                if (text.isEmpty()) return "music needs text (what to play)."
                val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(SearchManager.QUERY, text)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                if (tryStart(context, intent)) "Asked the music app to play \"$text\"."
                else start(context, view("https://music.youtube.com/search?q=" + Uri.encode(text)), "Opened a music search for \"$text\".")
            }
            else -> "Unknown action \"${a.action}\". Use one of: ${ACTIONS.joinToString()}."
        }
    }

    /** Step title for the live timeline, e.g. "Set an alarm for 07:30". Pure. */
    fun title(action: String?, text: String?, time: String?, minutes: Double?, page: String?, to: String?): String {
        val t = text?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
        return when (action?.trim()?.lowercase()) {
            "alarm" -> "Set an alarm" + (parseClock(time)?.let { (h, m) -> " for %02d:%02d".format(h, m) } ?: "")
            "timer" -> "Start a timer" + (minutes?.takeIf { it > 0 }?.let { " for " + formatSeconds((it * 60).toInt()) } ?: "")
            "search" -> if (t != null) "Search “$t”" else "Search the web"
            "navigate" -> if (t != null) "Directions to $t" else "Open directions"
            "settings" -> "Open ${(page ?: t ?: "phone").trim()} settings"
            "dial" -> "Open the dialer" + (to?.let { " for $it" } ?: "")
            "sms" -> "Write a text" + (to?.let { " to $it" } ?: "")
            "email" -> "Draft an email" + (to?.let { " to $it" } ?: "")
            "share" -> "Share"
            "event" -> if (t != null) "Add “$t” to the calendar" else "Add a calendar event"
            "music" -> if (t != null) "Play “$t”" else "Play music"
            "maps" -> if (t != null) "Find “$t” on the map" else "Open the map"
            "whatsapp" -> "Open WhatsApp" + (to?.let { " for $it" } ?: "")
            "store" -> if (t != null) "Open “$t” in the Play Store" else "Open the Play Store"
            "panel" -> "Open the ${(page ?: t ?: "settings").trim()} panel"
            "app_info" -> if (t != null) "Open $t's app settings" else "Open app settings"
            "flashlight" -> when (parseSwitch(t ?: page)) { true -> "Turn the flashlight on"; false -> "Turn the flashlight off"; null -> "Toggle the flashlight" }
            "volume" -> "Set the volume" + (t?.let { " ($it)" } ?: "")
            else -> "Quick action"
        }
    }

    /** Torch state per camera, kept current by a callback registered on first use. */
    private val torch = ConcurrentHashMap<String, Boolean>()
    @Volatile private var torchWatched = false

    /** No permission needed: setTorchMode works for any app (Android 6+). */
    private fun flashlight(context: Context, want: Boolean?): String {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return "This phone has no camera service."
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return "This phone has no flashlight."
        if (!torchWatched) {
            torchWatched = true
            runCatching {
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torch[cameraId] = enabled }
                }, Handler(Looper.getMainLooper()))
            }
        }
        val on = want ?: !(torch[id] ?: false)
        return runCatching {
            cm.setTorchMode(id, on)
            torch[id] = on
            if (on) "Turned the flashlight on." else "Turned the flashlight off."
        }.getOrElse { "The flashlight could not be switched (${it.message ?: "camera busy"}). Use quick settings instead." }
    }

    /** Media volume (what videos and music use); no permission needed. */
    private fun volume(context: Context, v: Volume): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "No audio service."
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        runCatching {
            when (v) {
                Volume.Up -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                Volume.Down -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                Volume.Mute -> am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                Volume.Unmute -> am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                is Volume.Percent -> am.setStreamVolume(stream, (v.value * max / 100.0).roundToInt(), AudioManager.FLAG_SHOW_UI)
            }
        }.onFailure { return "The volume could not be changed (${it.message ?: "not allowed"})." }
        val now = (am.getStreamVolume(stream) * 100.0 / max).roundToInt()
        return if (v == Volume.Mute) "Muted media volume." else "Media volume is now $now%."
    }

    fun formatSeconds(seconds: Int): String = when {
        seconds % 3600 == 0 -> "${seconds / 3600} h"
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        seconds % 60 == 0 -> "${seconds / 60} min"
        seconds > 60 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "$seconds s"
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun start(context: Context, intent: Intent, success: String): String =
        if (tryStart(context, intent)) success else "No app on this phone handles that. Do it through the app's screens instead."
}
