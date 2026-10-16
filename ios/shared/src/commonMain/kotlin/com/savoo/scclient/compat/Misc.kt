package com.savoo.scclient.compat

import kotlin.concurrent.atomics.AtomicBoolean as KAtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

private val processStart = TimeSource.Monotonic.markNow()

object SystemClock {
    fun elapsedRealtime(): Long = processStart.elapsedNow().inWholeMilliseconds
    fun uptimeMillis(): Long = elapsedRealtime()
}

object Log {
    fun d(tag: String, msg: String, tr: Throwable? = null): Int = emit("D", tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null): Int = emit("I", tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null): Int = emit("W", tag, msg, tr)
    fun w(tag: String, tr: Throwable?): Int = emit("W", tag, "", tr)
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = emit("E", tag, msg, tr)
    fun v(tag: String, msg: String, tr: Throwable? = null): Int = emit("V", tag, msg, tr)
    fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString().orEmpty()

    private fun emit(level: String, tag: String, msg: String, tr: Throwable?): Int {
        println("$level/$tag: $msg${tr?.let { "\n" + it.stackTraceToString() }.orEmpty()}")
        return 0
    }
}

object Toast {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    fun makeText(context: Context?, text: CharSequence, duration: Int): Shown = Shown(text.toString(), duration)

    class Shown(private val text: String, private val duration: Int) {
        fun show() = Platform.showToast(text, duration == LENGTH_LONG)
    }
}

class Locale private constructor(val language: String, val country: String = "") {
    fun toLanguageTag(): String = if (country.isEmpty()) language else "$language-$country"
    override fun toString(): String = toLanguageTag().replace('-', '_')

    companion object {
        val US = Locale("en", "US")
        val ENGLISH = Locale("en")
        val ROOT = Locale("")
        private var override: Locale? = null

        fun getDefault(): Locale = override ?: forLanguageTag(Platform.languageTag)
        fun setDefault(locale: Locale) {
            override = locale
        }

        fun forLanguageTag(tag: String): Locale {
            val parts = tag.replace('_', '-').split('-')
            return Locale(parts[0].lowercase(), parts.getOrNull(1)?.uppercase().orEmpty())
        }

        operator fun invoke(language: String, country: String = ""): Locale = Locale(language, country)
    }
}

fun String.lowercase(locale: Locale): String = lowercase()
fun String.uppercase(locale: Locale): String = uppercase()

class UUID private constructor(private val value: String) {
    override fun toString(): String = value

    companion object {
        fun randomUUID(): UUID = UUID(Uuid.random().toString())
        fun nameUUIDFromBytes(bytes: ByteArray): UUID = UUID(Uuid.fromByteArray(bytes.copyOf(16)).toString())
    }
}

class ConcurrentHashMap<K, V>(private val map: HashMap<K, V> = HashMap()) : MutableMap<K, V> by map {
    fun remove(key: K, value: V): Boolean = if (map[key] == value) { map.remove(key); true } else false
    fun putIfAbsent(key: K, value: V): V? = map[key] ?: run { map[key] = value; null }
}

@OptIn(ExperimentalAtomicApi::class)
class AtomicBoolean(initial: Boolean = false) {
    private val ref = KAtomicBoolean(initial)
    fun get(): Boolean = ref.load()
    fun set(value: Boolean) = ref.store(value)
    fun getAndSet(value: Boolean): Boolean = ref.exchange(value)
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = ref.compareAndSet(expect, update)
}

expect object GlobalLock {
    fun lock()
    fun unlock()
}

inline fun <T> synchronized(lock: Any, block: () -> T): T {
    GlobalLock.lock()
    try {
        return block()
    } finally {
        GlobalLock.unlock()
    }
}

class Uri(private val raw: String) {
    val scheme: String? get() = raw.substringBefore("://", "").ifEmpty { null }
    val host: String? get() = raw.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#').ifEmpty { null }
    val path: String? get() = raw.substringAfter("://", raw).let { rest ->
        val afterHost = if (raw.contains("://")) rest.substringAfter('/', "") else rest
        "/" + afterHost.substringBefore('?').substringBefore('#')
    }
    val pathSegments: List<String> get() = path.orEmpty().split('/').filter { it.isNotEmpty() }
    val lastPathSegment: String? get() = pathSegments.lastOrNull()
    val query: String? get() = raw.substringAfter('?', "").substringBefore('#').ifEmpty { null }
    val fragment: String? get() = raw.substringAfter('#', "").ifEmpty { null }

    fun getQueryParameter(name: String): String? =
        query?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == name }?.getOrNull(1)?.decodeUrlComponent()

    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    companion object {
        val EMPTY = Uri("")
        fun parse(value: String): Uri = Uri(value)
        fun encode(value: String): String = value.encodeUrlComponent()
    }
}

fun String.encodeUrlComponent(): String = buildString {
    for (b in this@encodeUrlComponent.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch) else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

fun String.decodeUrlComponent(): String {
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c == '%' && i + 2 <= lastIndex -> {
                bytes.add(substring(i + 1, i + 3).toInt(16).toByte())
                i += 3
            }
            c == '+' -> { bytes.add(' '.code.toByte()); i++ }
            else -> { c.toString().encodeToByteArray().forEach { bytes.add(it) }; i++ }
        }
    }
    return bytes.toByteArray().decodeToString()
}

object URLEncoder {
    fun encode(value: String, charset: String = "UTF-8"): String = value.encodeUrlComponent().replace("%20", "+")
}
