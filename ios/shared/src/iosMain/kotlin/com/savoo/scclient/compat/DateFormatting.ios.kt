package com.savoo.scclient.compat

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName
import platform.Foundation.localTimeZone

actual object DateFormatting {
    actual fun pattern(epochMs: Long, pattern: String, localeTag: String, timeZoneId: String): String {
        val formatter = NSDateFormatter()
        formatter.locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))
        formatter.timeZone = NSTimeZone.timeZoneWithName(timeZoneId) ?: NSTimeZone.localTimeZone
        formatter.dateFormat = pattern
        return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
    }

    actual fun localized(epochMs: Long, dateStyle: Int, timeStyle: Int, localeTag: String, timeZoneId: String): String {
        val formatter = NSDateFormatter()
        formatter.locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))
        formatter.timeZone = NSTimeZone.timeZoneWithName(timeZoneId) ?: NSTimeZone.localTimeZone
        formatter.dateStyle = dateStyle.toULong()
        formatter.timeStyle = timeStyle.toULong()
        return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
    }

    actual fun integer(value: Long, localeTag: String): String {
        val formatter = NSNumberFormatter()
        formatter.locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))
        formatter.numberStyle = NSNumberFormatterDecimalStyle
        formatter.maximumFractionDigits = 0u
        return formatter.stringFromNumber(NSNumber(longLong = value)) ?: value.toString()
    }

    actual fun sleep(ms: Long) {
        NSThread.sleepForTimeInterval(ms / 1000.0)
    }

    actual fun nanoTime(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000_000_000).toLong()
}
