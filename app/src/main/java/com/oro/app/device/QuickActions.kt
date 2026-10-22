package com.oro.app.device

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
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

    val ACTIONS = listOf("alarm", "timer", "search", "navigate", "settings", "dial", "sms", "email", "share", "event", "music")

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
            else -> "Quick action"
        }
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
