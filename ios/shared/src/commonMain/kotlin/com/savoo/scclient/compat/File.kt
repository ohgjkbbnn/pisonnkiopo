package com.savoo.scclient.compat

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class File(path: String) {
    constructor(parent: File, child: String) : this("${parent.path.trimEnd('/')}/$child")
    constructor(parent: String, child: String) : this("${parent.trimEnd('/')}/$child")

    val path: String = path
    private val p get() = Path(path)

    val absolutePath: String get() = path
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val parent: String? get() = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let { File(it) }

    fun exists(): Boolean = SystemFileSystem.exists(p)
    fun isFile(): Boolean = SystemFileSystem.metadataOrNull(p)?.isRegularFile == true
    fun isDirectory(): Boolean = SystemFileSystem.metadataOrNull(p)?.isDirectory == true
    val isFile: Boolean get() = isFile()
    val isDirectory: Boolean get() = isDirectory()
    fun length(): Long = SystemFileSystem.metadataOrNull(p)?.size ?: 0L

    fun mkdirs(): Boolean {
        if (exists()) return false
        SystemFileSystem.createDirectories(p)
        return true
    }

    fun delete(): Boolean = runCatching { SystemFileSystem.delete(p, mustExist = false); true }.getOrDefault(false)

    fun deleteRecursively(): Boolean {
        if (isDirectory()) listFiles()?.forEach { it.deleteRecursively() }
        return delete()
    }

    fun renameTo(dest: File): Boolean = runCatching { SystemFileSystem.atomicMove(p, Path(dest.path)); true }.getOrDefault(false)

    fun listFiles(): Array<File>? =
        if (!isDirectory()) null else SystemFileSystem.list(p).map { File(it.toString()) }.toTypedArray()

    fun listFiles(filter: (File) -> Boolean): Array<File>? = listFiles()?.filter(filter)?.toTypedArray()

    fun readBytes(): ByteArray = SystemFileSystem.source(p).buffered().use { it.readByteArray() }
    fun readText(): String = SystemFileSystem.source(p).buffered().use { it.readString() }

    fun writeBytes(bytes: ByteArray) {
        parentFile?.mkdirs()
        SystemFileSystem.sink(p).buffered().use { it.write(bytes) }
    }

    fun writeText(text: String) {
        parentFile?.mkdirs()
        SystemFileSystem.sink(p).buffered().use { it.writeString(text) }
    }

    fun appendBytes(bytes: ByteArray) {
        SystemFileSystem.sink(p, append = true).buffered().use { it.write(bytes) }
    }

    fun resolve(child: String): File = File(this, child)

    fun walkBottomUp(): Sequence<File> = sequence {
        listFiles()?.forEach { yieldAll(it.walkBottomUp()) }
        yield(this@File)
    }

    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()

    companion object {
        fun createTempFile(prefix: String, suffix: String?, directory: File?): File {
            val dir = directory ?: AppContext.cacheDir
            dir.mkdirs()
            return File(dir, "$prefix${Random.nextLong().toULong()}${suffix ?: ".tmp"}").also { it.writeBytes(ByteArray(0)) }
        }
    }
}


expect fun fileLastModified(path: String): Long

fun File.lastModified(): Long = fileLastModified(path)

class SocketTimeoutException(message: String? = null) : IOException(message)
class UnknownHostException(message: String? = null) : IOException(message)
