package nl.spoonpaco.ninegrid.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Hoe datums en tijden op het scherm staan. Alleen weergave: opslaan gebeurt altijd als 2026-11-05 en millis. */
data class DateStyle(
    /** system, 24 of 12. */
    val time: String = "system",
    /** system, dmy (05-11-2026), mdy (11/05/2026) of ymd (2026-11-05). */
    val date: String = "system",
    /** system, mon of sun. */
    val weekStart: String = "system",
    val weekday: Boolean = false,
)

object Dates {
    /** Staat de telefoon zelf op 24 uur? Wordt bij het openen van de app gezet (Android-instelling). */
    @Volatile var system24: Boolean = true

    private val zone get() = ZoneId.systemDefault()

    private fun datePattern(s: DateStyle): DateTimeFormatter {
        val base = when (s.date) {
            "dmy" -> "dd-MM-yyyy"
            "mdy" -> "MM/dd/yyyy"
            "ymd" -> "yyyy-MM-dd"
            else -> null
        }
        val f = base?.let { DateTimeFormatter.ofPattern(it, Locale.getDefault()) } ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        return f
    }

    private fun use24(s: DateStyle) = when (s.time) { "24" -> true; "12" -> false; else -> system24 }

    private fun timePattern(s: DateStyle): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (use24(s)) "HH:mm" else "h:mm a", Locale.getDefault())

    fun date(d: LocalDate, s: DateStyle): String {
        val core = datePattern(s).format(d)
        return if (s.weekday) DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(d) + " " + core else core
    }

    fun date(ms: Long, s: DateStyle): String = date(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), s)

    fun time(ms: Long, s: DateStyle): String = timePattern(s).format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone))

    fun dateTime(ms: Long, s: DateStyle): String = date(ms, s) + " " + time(ms, s)

    /** Eerste dag van de week volgens de instelling (voor Winner of the Week). */
    fun weekStart(s: DateStyle): DayOfWeek = when (s.weekStart) {
        "mon" -> DayOfWeek.MONDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
    }
}
