package com.savoo.scclient.compat

import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

open class InputStream(private val data: ByteArray) : AutoCloseable {
    private var pos = 0

    open fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1

    open fun read(buffer: ByteArray, off: Int = 0, len: Int = buffer.size): Int {
        if (pos >= data.size) return -1
        val n = minOf(len, data.size - pos)
        data.copyInto(buffer, off, pos, pos + n)
        pos += n
        return n
    }

    fun readBytes(): ByteArray = data.copyOfRange(pos, data.size).also { pos = data.size }

    fun available(): Int = data.size - pos

    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long {
        val rest = readBytes()
        out.write(rest)
        return rest.size.toLong()
    }

    fun bufferedReader(): Reader = Reader(readBytes().decodeToString())

    override fun close() = Unit
}

class Reader(private val text: String) : AutoCloseable {
    fun readText(): String = text
    fun readLines(): List<String> = text.lines()
    override fun close() = Unit
}

class ByteArrayInputStream(data: ByteArray) : InputStream(data)

open class OutputStream internal constructor(private val sink: Sink?) : AutoCloseable {
    open fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
    open fun write(bytes: ByteArray, off: Int, len: Int) {
        sink?.write(bytes, off, off + len)
    }
    open fun write(b: Int) = write(byteArrayOf(b.toByte()))
    open fun flush() {
        sink?.flush()
    }
    override fun close() {
        sink?.close()
    }
}

class ByteArrayOutputStream : OutputStream(null) {
    private val buffer = ArrayList<Byte>()
    override fun write(bytes: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) buffer.add(bytes[i])
    }
    fun toByteArray(): ByteArray = buffer.toByteArray()
    fun size(): Int = buffer.size
    override fun toString(): String = toByteArray().decodeToString()
}

class FileOutputStream(file: File, append: Boolean = false) :
    OutputStream(SystemFileSystem.sink(Path(file.path), append).buffered()) {
    constructor(path: String) : this(File(path))
}

class FileInputStream(file: File) : InputStream(file.readBytes())

fun File.outputStream(): OutputStream = FileOutputStream(this)
fun File.inputStream(): InputStream = FileInputStream(this)
fun File.bufferedReader(): Reader = Reader(readText())

fun File.copyTo(target: File, overwrite: Boolean = false): File {
    if (target.exists() && !overwrite) throw IOException("exists")
    target.writeBytes(readBytes())
    return target
}

fun ByteArray.inputStream(): InputStream = ByteArrayInputStream(this)

fun readAll(path: String): ByteArray = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
