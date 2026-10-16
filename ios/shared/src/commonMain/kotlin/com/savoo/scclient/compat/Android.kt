package com.savoo.scclient.compat

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.io.encoding.Base64 as KBase64

typealias Volatile = kotlin.concurrent.Volatile

val LocalContext = staticCompositionLocalOf<Context> { AppContext }

object Build {
    object VERSION {
        const val SDK_INT = 36
        const val RELEASE = "iOS"
    }

    object VERSION_CODES {
        const val M = 23
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
        const val BAKLAVA = 36
    }

    const val MANUFACTURER = "Apple"
    const val MODEL = "iPhone"
    const val BRAND = "Apple"
    const val DEVICE = "iPhone"
}

object Math {
    fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun max(a: Long, b: Long) = kotlin.math.max(a, b)
    fun max(a: Float, b: Float) = kotlin.math.max(a, b)
    fun max(a: Double, b: Double) = kotlin.math.max(a, b)
    fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    fun min(a: Long, b: Long) = kotlin.math.min(a, b)
    fun min(a: Float, b: Float) = kotlin.math.min(a, b)
    fun min(a: Double, b: Double) = kotlin.math.min(a, b)
    fun abs(a: Int) = kotlin.math.abs(a)
    fun abs(a: Long) = kotlin.math.abs(a)
    fun abs(a: Float) = kotlin.math.abs(a)
    fun abs(a: Double) = kotlin.math.abs(a)
    fun sqrt(a: Double) = kotlin.math.sqrt(a)
    fun pow(a: Double, b: Double) = kotlin.math.exp(b * kotlin.math.ln(a))
    fun log(a: Double) = kotlin.math.ln(a)
    fun log10(a: Double) = kotlin.math.log10(a)
    fun exp(a: Double) = kotlin.math.exp(a)
    fun sin(a: Double) = kotlin.math.sin(a)
    fun cos(a: Double) = kotlin.math.cos(a)
    fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)
    fun floor(a: Double) = kotlin.math.floor(a)
    fun ceil(a: Double) = kotlin.math.ceil(a)
    fun round(a: Double): Long = kotlin.math.round(a).toLong()
    fun round(a: Float): Int = kotlin.math.round(a).toInt()
    fun floorMod(a: Int, b: Int) = a.mod(b)
    fun floorMod(a: Long, b: Long) = a.mod(b)
    fun toRadians(a: Double) = a * kotlin.math.PI / 180.0
    fun toDegrees(a: Double) = a * 180.0 / kotlin.math.PI
    fun random() = kotlin.random.Random.nextDouble()
    const val PI = kotlin.math.PI
}

class ActivityNotFoundException(message: String? = null) : Exception(message)

class Intent(var action: String? = null, var data: Uri? = null) {
    private val extras = HashMap<String, Any?>()
    var type: String? = null
    var flags: Int = 0
    var `package`: String? = null

    constructor(action: String?) : this(action, null)

    fun putExtra(key: String, value: Any?): Intent = apply { extras[key] = value }
    fun getStringExtra(key: String): String? = extras[key] as? String
    fun setType(type: String?): Intent = apply { this.type = type }
    fun setData(uri: Uri?): Intent = apply { data = uri }
    fun setDataAndType(uri: Uri?, type: String?): Intent = apply { data = uri; this.type = type }
    fun addFlags(flags: Int): Intent = apply { this.flags = this.flags or flags }
    fun setPackage(pkg: String?): Intent = apply { `package` = pkg }
    fun addCategory(category: String): Intent = this
    fun resolveActivity(pm: Any?): Any? = this

    internal fun extra(key: String): Any? = extras[key]

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_CLEAR_TASK = 0x00008000
        const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002

        fun createChooser(target: Intent, title: CharSequence?): Intent = target
    }
}

fun Context.startActivity(intent: Intent) {
    when (intent.action) {
        Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> intent.data?.toString()?.let { Platform.openUrl(it) }
        Intent.ACTION_SEND -> {
            val stream = intent.extra(Intent.EXTRA_STREAM)
            if (stream != null) {
                Platform.shareFile(stream.toString().removePrefix("file://"), intent.type ?: "*/*")
            } else {
                Platform.share(intent.extra(Intent.EXTRA_TEXT)?.toString().orEmpty())
            }
        }
    }
}

class ClipData private constructor(val text: String) {
    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence): ClipData = ClipData(text.toString())
    }
}

object ClipboardManager {
    fun setPrimaryClip(clip: ClipData) = Platform.copyToClipboard(clip.text)
}

const val CLIPBOARD_SERVICE = "clipboard"

fun Context.getSystemService(name: String): Any? = when (name) {
    CLIPBOARD_SERVICE -> ClipboardManager
    else -> null
}

val Context.Companion.CLIPBOARD_SERVICE: String get() = "clipboard"

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    private fun codec(flags: Int) = when {
        flags and URL_SAFE != 0 && flags and NO_PADDING != 0 -> KBase64.UrlSafe.withPadding(KBase64.PaddingOption.ABSENT_OPTIONAL)
        flags and URL_SAFE != 0 -> KBase64.UrlSafe.withPadding(KBase64.PaddingOption.PRESENT_OPTIONAL)
        flags and NO_PADDING != 0 -> KBase64.Default.withPadding(KBase64.PaddingOption.ABSENT_OPTIONAL)
        else -> KBase64.Default.withPadding(KBase64.PaddingOption.PRESENT_OPTIONAL)
    }

    fun encodeToString(input: ByteArray, flags: Int): String = codec(flags).encode(input)
    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).encodeToByteArray()
    fun decode(input: String, flags: Int): ByteArray = codec(flags).decode(input.filterNot { it.isWhitespace() })
    fun decode(input: ByteArray, flags: Int): ByteArray = decode(input.decodeToString(), flags)
}

object GraphicsColor {
    fun colorToHSV(argb: Int, hsv: FloatArray) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta).mod(6f))
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        hsv[0] = if (h < 0f) h + 360f else h
        hsv[1] = if (max == 0f) 0f else delta / max
        hsv[2] = max
    }

    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(0xFF, hsv)

    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val h = (hsv[0].mod(360f)) / 60f
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - kotlin.math.abs(h.mod(2f) - 1))
        val m = v - c
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

object Formatter {
    fun formatShortFileSize(context: Context?, bytes: Long): String = formatFileSize(context, bytes)

    fun formatFileSize(context: Context?, bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        return if (unit == 0) "$bytes B" else "${formatDecimal(value, if (value < 10) 1 else 0)} ${units[unit]}"
    }
}

class Configuration internal constructor() {
    val locales: LocaleList get() = LocaleList
    val locale: Locale get() = Locale.getDefault()
}

object LocaleList {
    operator fun get(index: Int): Locale = Locale.getDefault()
}

val LocalConfiguration = androidx.compose.runtime.compositionLocalOf { Configuration() }

fun Char.titlecase(locale: Locale): String = titlecase()
