package com.savoo.scclient.compat

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant as KInstant
import kotlinx.datetime.LocalDate as KLocalDate

expect object DateFormatting {
    fun pattern(epochMs: Long, pattern: String, localeTag: String, timeZoneId: String): String
    fun localized(epochMs: Long, dateStyle: Int, timeStyle: Int, localeTag: String, timeZoneId: String): String
    fun integer(value: Long, localeTag: String): String
    fun sleep(ms: Long)
    fun nanoTime(): Long
}

annotation class Synchronized

object System {
    fun currentTimeMillis(): Long = com.savoo.scclient.compat.currentTimeMillis()
    fun nanoTime(): Long = DateFormatting.nanoTime()
}

object Thread {
    fun sleep(ms: Long) = DateFormatting.sleep(ms)
}

class ZoneId private constructor(internal val tz: TimeZone) {
    val id: String get() = tz.id

    companion object {
        fun systemDefault(): ZoneId = ZoneId(TimeZone.currentSystemDefault())
        fun of(id: String): ZoneId = ZoneId(TimeZone.of(id))
    }
}

class Instant private constructor(internal val value: KInstant) : Comparable<Instant> {
    fun toEpochMilli(): Long = value.toEpochMilliseconds()
    val epochSecond: Long get() = value.epochSeconds
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(value, zone)
    fun plusMillis(ms: Long) = Instant(KInstant.fromEpochMilliseconds(toEpochMilli() + ms))
    fun minusMillis(ms: Long) = plusMillis(-ms)
    fun isBefore(other: Instant) = value < other.value
    fun isAfter(other: Instant) = value > other.value
    override fun compareTo(other: Instant): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    override fun equals(other: Any?) = other is Instant && other.value == value
    override fun hashCode() = value.hashCode()

    companion object {
        fun now(): Instant = Instant(Clock.System.now())
        fun ofEpochMilli(ms: Long): Instant = Instant(KInstant.fromEpochMilliseconds(ms))
        fun ofEpochSecond(s: Long): Instant = Instant(KInstant.fromEpochSeconds(s))
        fun parse(text: CharSequence): Instant = Instant(KInstant.parse(text.toString()))
    }
}

class ZonedDateTime internal constructor(private val instant: KInstant, val zone: ZoneId) {
    private val local: LocalDateTime by lazy { instant.toLocalDateTime(zone.tz) }
    val year: Int get() = local.year
    val monthValue: Int get() = local.month.number
    val dayOfMonth: Int get() = local.day
    val dayOfYear: Int get() = local.dayOfYear
    val hour: Int get() = local.hour
    val minute: Int get() = local.minute
    val dayOfWeek: DayOfWeek get() = DayOfWeek.of(local.dayOfWeek.isoDayNumber)
    fun toLocalDate(): LocalDate = LocalDate(local.date)
    fun toInstant(): Instant = Instant.ofEpochMilli(instant.toEpochMilliseconds())
    fun toEpochSecond(): Long = instant.epochSeconds
    internal val epochMs: Long get() = instant.toEpochMilliseconds()
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    val value: Int get() = ordinal + 1

    companion object {
        fun of(n: Int): DayOfWeek = entries[(n - 1).mod(7)]
    }
}

enum class Month {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    val value: Int get() = ordinal + 1

    fun getDisplayName(style: TextStyle, locale: Locale): String {
        val ms = KLocalDate(2024, value, 15).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val pattern = when (style) {
            TextStyle.FULL, TextStyle.FULL_STANDALONE -> "LLLL"
            TextStyle.SHORT, TextStyle.SHORT_STANDALONE -> "LLL"
            TextStyle.NARROW, TextStyle.NARROW_STANDALONE -> "LLLLL"
        }
        return DateFormatting.pattern(ms, pattern, locale.toLanguageTag(), "UTC")
    }

    companion object {
        fun of(n: Int): Month = entries[n - 1]
    }
}

enum class TextStyle { FULL, FULL_STANDALONE, SHORT, SHORT_STANDALONE, NARROW, NARROW_STANDALONE }

class LocalDate internal constructor(internal val value: KLocalDate) : Comparable<LocalDate> {
    val year: Int get() = value.year
    val monthValue: Int get() = value.month.number
    val month: Month get() = Month.of(monthValue)
    val dayOfMonth: Int get() = value.day
    val dayOfYear: Int get() = value.dayOfYear
    val dayOfWeek: DayOfWeek get() = DayOfWeek.of(value.dayOfWeek.isoDayNumber)

    fun plusDays(n: Long) = LocalDate(value.plus(n, DateTimeUnit.DAY))
    fun minusDays(n: Long) = LocalDate(value.minus(n, DateTimeUnit.DAY))
    fun plusMonths(n: Long) = LocalDate(value.plus(n, DateTimeUnit.MONTH))
    fun minusMonths(n: Long) = LocalDate(value.minus(n, DateTimeUnit.MONTH))
    fun plusYears(n: Long) = LocalDate(value.plus(n, DateTimeUnit.YEAR))
    fun minusYears(n: Long) = LocalDate(value.minus(n, DateTimeUnit.YEAR))
    fun plusDays(n: Int) = plusDays(n.toLong())
    fun minusDays(n: Int) = minusDays(n.toLong())
    fun withDayOfMonth(day: Int) = LocalDate(KLocalDate(year, monthValue, day))
    fun withDayOfYear(day: Int) = LocalDate(KLocalDate(year, 1, 1).plus(DatePeriod(days = day - 1)))
    fun toEpochDay(): Long = value.toEpochDays()
    fun lengthOfMonth(): Int = KLocalDate(year, monthValue, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    fun isBefore(other: LocalDate) = value < other.value
    fun isAfter(other: LocalDate) = value > other.value
    fun isEqual(other: LocalDate) = value == other.value
    fun atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime(value.atStartOfDayIn(zone.tz), zone)
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)
    override fun compareTo(other: LocalDate): Int = value.compareTo(other.value)
    override fun equals(other: Any?) = other is LocalDate && other.value == value
    override fun hashCode() = value.hashCode()
    override fun toString(): String = value.toString()

    internal val epochMsUtcNoon: Long
        get() = LocalDateTime(year, monthValue, dayOfMonth, 12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    companion object {
        fun now(): LocalDate = LocalDate(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
        fun now(zone: ZoneId): LocalDate = LocalDate(Clock.System.now().toLocalDateTime(zone.tz).date)
        fun of(year: Int, month: Int, day: Int) = LocalDate(KLocalDate(year, month, day))
        fun ofEpochDay(day: Long) = LocalDate(KLocalDate.fromEpochDays(day))
        fun parse(text: CharSequence) = LocalDate(KLocalDate.parse(text.toString()))
        fun ofYearDay(year: Int, dayOfYear: Int) = LocalDate(KLocalDate(year, 1, 1).plus(DatePeriod(days = dayOfYear - 1)))
    }
}

fun ZonedDateTime.format(formatter: DateTimeFormatter): String = formatter.formatMs(epochMs)

enum class FormatStyle(internal val code: Int) { FULL(4), LONG(3), MEDIUM(2), SHORT(1) }

class DateTimeFormatter private constructor(
    private val pattern: String?,
    private val dateStyle: Int,
    private val timeStyle: Int,
    private val locale: Locale?,
) {
    fun format(date: LocalDate): String = formatMs(date.epochMsUtcNoon)
    fun format(dt: ZonedDateTime): String = formatMs(dt.epochMs)

    internal fun formatMs(ms: Long): String {
        val tag = (locale ?: Locale.getDefault()).toLanguageTag()
        val tz = TimeZone.currentSystemDefault().id
        return if (pattern != null) DateFormatting.pattern(ms, pattern, tag, tz)
        else DateFormatting.localized(ms, dateStyle, timeStyle, tag, tz)
    }

    fun withLocale(locale: Locale) = DateTimeFormatter(pattern, dateStyle, timeStyle, locale)

    companion object {
        fun ofPattern(pattern: String) = DateTimeFormatter(pattern, 0, 0, null)
        fun ofPattern(pattern: String, locale: Locale) = DateTimeFormatter(pattern, 0, 0, locale)
        fun ofLocalizedDate(style: FormatStyle) = DateTimeFormatter(null, style.code, 0, null)
        fun ofLocalizedDateTime(style: FormatStyle) = DateTimeFormatter(null, style.code, style.code, null)
        fun ofLocalizedDateTime(date: FormatStyle, time: FormatStyle) = DateTimeFormatter(null, date.code, time.code, null)
        val ISO_LOCAL_DATE = DateTimeFormatter("yyyy-MM-dd", 0, 0, Locale.US)
    }
}

class Date(val time: Long = currentTimeMillis()) {
    override fun toString(): String = Instant.ofEpochMilli(time).toString()
}

class SimpleDateFormat(private val pattern: String, private val locale: Locale = Locale.getDefault()) {
    fun format(date: Date): String =
        DateFormatting.pattern(date.time, pattern, locale.toLanguageTag(), TimeZone.currentSystemDefault().id)
    fun format(ms: Long): String = format(Date(ms))
}

object DateFormat {
    const val FULL = 4
    const val LONG = 3
    const val MEDIUM = 2
    const val SHORT = 1

    class Instance internal constructor(private val dateStyle: Int, private val timeStyle: Int) {
        fun format(date: Date): String = DateFormatting.localized(
            date.time, dateStyle, timeStyle, Locale.getDefault().toLanguageTag(), TimeZone.currentSystemDefault().id,
        )
        fun format(ms: Long): String = format(Date(ms))
    }

    fun getDateTimeInstance(dateStyle: Int, timeStyle: Int) = Instance(dateStyle, timeStyle)
    fun getDateInstance(dateStyle: Int) = Instance(dateStyle, 0)
    fun getTimeInstance(timeStyle: Int) = Instance(0, timeStyle)
}

class Calendar private constructor(var timeInMillis: Long) {
    private val zone get() = TimeZone.currentSystemDefault()

    fun get(field: Int): Int {
        val dt = KInstant.fromEpochMilliseconds(timeInMillis).toLocalDateTime(zone)
        return when (field) {
            YEAR -> dt.year
            MONTH -> dt.month.number - 1
            DAY_OF_MONTH -> dt.day
            DAY_OF_YEAR -> dt.dayOfYear
            DAY_OF_WEEK -> dt.dayOfWeek.isoDayNumber % 7 + 1
            HOUR_OF_DAY -> dt.hour
            MINUTE -> dt.minute
            SECOND -> dt.second
            MILLISECOND -> dt.nanosecond / 1_000_000
            else -> 0
        }
    }

    fun set(field: Int, value: Int) {
        val dt = KInstant.fromEpochMilliseconds(timeInMillis).toLocalDateTime(zone)
        val updated = when (field) {
            YEAR -> LocalDateTime(value, dt.month.number, dt.day, dt.hour, dt.minute, dt.second, dt.nanosecond)
            MONTH -> LocalDateTime(dt.year, value + 1, dt.day, dt.hour, dt.minute, dt.second, dt.nanosecond)
            DAY_OF_MONTH -> LocalDateTime(dt.year, dt.month.number, value, dt.hour, dt.minute, dt.second, dt.nanosecond)
            HOUR_OF_DAY -> LocalDateTime(dt.year, dt.month.number, dt.day, value, dt.minute, dt.second, dt.nanosecond)
            MINUTE -> LocalDateTime(dt.year, dt.month.number, dt.day, dt.hour, value, dt.second, dt.nanosecond)
            SECOND -> LocalDateTime(dt.year, dt.month.number, dt.day, dt.hour, dt.minute, value, dt.nanosecond)
            MILLISECOND -> LocalDateTime(dt.year, dt.month.number, dt.day, dt.hour, dt.minute, dt.second, value * 1_000_000)
            else -> dt
        }
        timeInMillis = updated.toInstant(zone).toEpochMilliseconds()
    }

    fun add(field: Int, amount: Int) {
        val instant = KInstant.fromEpochMilliseconds(timeInMillis)
        val unit = when (field) {
            YEAR -> DateTimeUnit.YEAR
            MONTH -> DateTimeUnit.MONTH
            DAY_OF_MONTH, DAY_OF_YEAR, DAY_OF_WEEK -> DateTimeUnit.DAY
            HOUR_OF_DAY -> DateTimeUnit.HOUR
            MINUTE -> DateTimeUnit.MINUTE
            SECOND -> DateTimeUnit.SECOND
            else -> DateTimeUnit.MILLISECOND
        }
        timeInMillis = instant.plus(amount, unit, zone).toEpochMilliseconds()
    }

    val time: Date get() = Date(timeInMillis)

    fun clone(): Calendar = Calendar(timeInMillis)

    companion object {
        const val YEAR = 1
        const val MONTH = 2
        const val DAY_OF_MONTH = 5
        const val DATE = 5
        const val DAY_OF_YEAR = 6
        const val DAY_OF_WEEK = 7
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14

        fun getInstance(): Calendar = Calendar(currentTimeMillis())
    }
}

class NumberFormat private constructor(private val locale: Locale) {
    fun format(value: Long): String = DateFormatting.integer(value, locale.toLanguageTag())
    fun format(value: Int): String = format(value.toLong())

    companion object {
        fun getIntegerInstance(locale: Locale = Locale.getDefault()) = NumberFormat(locale)
        fun getInstance(locale: Locale = Locale.getDefault()) = NumberFormat(locale)
        fun getNumberInstance(locale: Locale = Locale.getDefault()) = NumberFormat(locale)
    }
}

class LocalTime private constructor(val hour: Int, val minute: Int, val second: Int) {
    companion object {
        fun now(): LocalTime {
            val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            return LocalTime(dt.hour, dt.minute, dt.second)
        }
        fun of(hour: Int, minute: Int, second: Int = 0) = LocalTime(hour, minute, second)
    }
}

class OffsetDateTime private constructor(private val instant: KInstant) {
    fun toLocalDate(): LocalDate = LocalDate(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)
    fun toInstant(): Instant = Instant.ofEpochMilli(instant.toEpochMilliseconds())

    companion object {
        fun parse(text: CharSequence): OffsetDateTime = OffsetDateTime(KInstant.parse(text.toString()))
    }
}

enum class TimeUnit(private val ms: Long) {
    MILLISECONDS(1), SECONDS(1000), MINUTES(60_000), HOURS(3_600_000), DAYS(86_400_000);

    fun toMillis(d: Long): Long = d * ms
    fun toSeconds(d: Long): Long = d * ms / 1000
    fun toMinutes(d: Long): Long = d * ms / 60_000
    fun toHours(d: Long): Long = d * ms / 3_600_000
    fun toDays(d: Long): Long = d * ms / 86_400_000
}
