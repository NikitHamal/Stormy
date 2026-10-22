package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class QuickActionsTest {

    @Test
    fun `clock times in the forms people write`() {
        assertThat(QuickActions.parseClock("07:30")).isEqualTo(7 to 30)
        assertThat(QuickActions.parseClock("7:30 pm")).isEqualTo(19 to 30)
        assertThat(QuickActions.parseClock("7pm")).isEqualTo(19 to 0)
        assertThat(QuickActions.parseClock("12 am")).isEqualTo(0 to 0)
        assertThat(QuickActions.parseClock("12pm")).isEqualTo(12 to 0)
        assertThat(QuickActions.parseClock("19.05")).isEqualTo(19 to 5)
        assertThat(QuickActions.parseClock("25:00")).isNull()
        assertThat(QuickActions.parseClock("7")).isNull()
        assertThat(QuickActions.parseClock("soon")).isNull()
    }

    @Test
    fun `dates resolve relative to now`() {
        val now = LocalDateTime.of(2026, 10, 22, 20, 0)
        val utc = ZoneOffset.UTC
        fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).toInstant(utc).toEpochMilli()
        assertThat(QuickActions.parseDateTime("2026-10-23 15:00", now, utc)).isEqualTo(at(2026, 10, 23, 15, 0))
        assertThat(QuickActions.parseDateTime("tomorrow 9am", now, utc)).isEqualTo(at(2026, 10, 23, 9, 0))
        assertThat(QuickActions.parseDateTime("today at 21:30", now, utc)).isEqualTo(at(2026, 10, 22, 21, 30))
        // A bare time already past today means tomorrow.
        assertThat(QuickActions.parseDateTime("08:00", now, utc)).isEqualTo(at(2026, 10, 23, 8, 0))
        assertThat(QuickActions.parseDateTime("someday", now, utc)).isNull()
    }

    @Test
    fun `settings pages by everyday names`() {
        assertThat(QuickActions.settingsAction("Wi-Fi")).isEqualTo("android.settings.WIFI_SETTINGS")
        assertThat(QuickActions.settingsAction("bluetooth settings")).isEqualTo("android.settings.BLUETOOTH_SETTINGS")
        assertThat(QuickActions.settingsAction("do not disturb")).isEqualTo("android.settings.ZEN_MODE_PRIORITY_SETTINGS")
        assertThat(QuickActions.settingsAction(null)).isNull()
    }

    @Test
    fun `step titles read as plain actions`() {
        assertThat(QuickActions.title("alarm", "Gym", "6:45 am", null, null, null)).isEqualTo("Set an alarm for 06:45")
        assertThat(QuickActions.title("timer", null, null, 5.0, null, null)).isEqualTo("Start a timer for 5 min")
        assertThat(QuickActions.title("navigate", "Thamel, Kathmandu", null, null, null, null)).isEqualTo("Directions to Thamel, Kathmandu")
        assertThat(QuickActions.title("settings", null, null, null, "bluetooth", null)).isEqualTo("Open bluetooth settings")
        assertThat(QuickActions.formatSeconds(90)).isEqualTo("1 min 30 s")
        assertThat(QuickActions.formatSeconds(3600)).isEqualTo("1 h")
    }
}
