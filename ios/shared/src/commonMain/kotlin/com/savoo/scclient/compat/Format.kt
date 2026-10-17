package com.savoo.scclient.compat

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

private val FORMAT_SPEC = Regex("""%(?:(\d+)\$)?([-0+ ,]*)(\d+)?(?:\.(\d+))?([sdfxXc%])""")

fun String.formatArgs(vararg args: Any?): String {
    var next = 0
    return FORMAT_SPEC.replace(this) { m ->
        val conv = m.groupValues[5]
        if (conv == "%") return@replace "%"
        val index = m.groupValues[1].toIntOrNull()?.minus(1) ?: next++
        val arg = args.getOrNull(index)
        val flags = m.groupValues[2]
        val width = m.groupValues[3].toIntOrNull() ?: 0
        val precision = m.groupValues[4].toIntOrNull()
        val body = when (conv) {
            "d" -> {
                val n = (arg as? Number)?.toLong() ?: arg.toString().toLongOrNull() ?: 0L
                if (',' in flags) groupThousands(n) else n.toString()
            }
            "f" -> formatDecimal((arg as? Number)?.toDouble() ?: 0.0, precision ?: 6)
            "x" -> ((arg as? Number)?.toLong() ?: 0L).toString(16)
            "X" -> ((arg as? Number)?.toLong() ?: 0L).toString(16).uppercase()
            "c" -> arg.toString()
            else -> arg.toString()
        }
        val signed = if ('+' in flags && (conv == "d" || conv == "f") && !body.startsWith("-")) "+$body" else body
        val padChar = if ('0' in flags && conv != "s") '0' else ' '
        when {
            signed.length >= width -> signed
            '-' in flags -> signed.padEnd(width)
            padChar == '0' && (signed.startsWith("-") || signed.startsWith("+")) -> signed.take(1) + signed.drop(1).padStart(width - 1, '0')
            else -> signed.padStart(width, padChar)
        }
    }
}

fun String.format(vararg args: Any?): String = formatArgs(*args)

fun String.format(locale: Locale?, vararg args: Any?): String = formatArgs(*args)

fun String.Companion.format(format: String, vararg args: Any?): String = format.formatArgs(*args)

fun String.Companion.format(locale: Locale?, format: String, vararg args: Any?): String = format.formatArgs(*args)

fun formatDecimal(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (decimals <= 0) return value.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val scaled = (abs(value) * factor).roundToLong()
    val intPart = scaled / factor.toLong()
    val frac = (scaled % factor.toLong()).toString().padStart(decimals, '0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return "$sign$intPart.$frac"
}

private fun groupThousands(n: Long): String {
    val s = abs(n).toString()
    val grouped = s.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}
