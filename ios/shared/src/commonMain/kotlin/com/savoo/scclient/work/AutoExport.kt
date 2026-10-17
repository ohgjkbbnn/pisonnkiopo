package com.savoo.scclient.work

import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.Date
import com.savoo.scclient.compat.File
import com.savoo.scclient.compat.Locale
import com.savoo.scclient.compat.Platform
import com.savoo.scclient.compat.SimpleDateFormat
import com.savoo.scclient.compat.Uri
import com.savoo.scclient.compat.currentTimeMillis
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.repository.EmptyFavoritesBackupException
import com.savoo.scclient.data.repository.FavoritesExporter
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

expect object BackgroundExport {
    fun schedule(intervalHours: Long)
    fun cancel()
}

class AutoExportManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val exporter: FavoritesExporter,
    private val favoritesDao: FavoritesDao,
) {
    private val mutex = Mutex()

    val backupsDir: File get() = File(Platform.documentsDirPath, BACKUPS_DIR).apply { mkdirs() }

    suspend fun syncSchedule() {
        val settings = settingsRepository.settings.first()
        if (settings.autoExportEnabled) {
            BackgroundExport.schedule(settings.autoExportInterval.hours)
        } else {
            BackgroundExport.cancel()
        }
    }

    suspend fun catchUpIfDue() {
        val settings = settingsRepository.settings.first()
        if (!settings.autoExportEnabled) return
        val dueAt = settings.autoExportLastRunAt + settings.autoExportInterval.hours * HOUR_MS
        if (currentTimeMillis() < dueAt) return
        exportNow()
            .onSuccess { DebugLog.log(TAG, "catch-up export OK") }
            .onFailure { DebugLog.log(TAG, "catch-up export failed: $it") }
    }

    suspend fun exportNow(): Result<Unit> = mutex.withLock {
        val dir = backupsDir
        val existing = backups(dir)
        if (existing.isNotEmpty() && favoritesDao.trackCount() == 0) {
            return@withLock Result.failure(EmptyFavoritesBackupException())
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val file = File(dir, "$PREFIX$stamp.json")
        exporter.exportToFile(Uri.parse("file://" + file.path))
            .onSuccess {
                settingsRepository.setAutoExportLastRunAt(currentTimeMillis())
                backups(dir).drop(KEEP_BACKUPS).forEach { it.delete() }
                syncSchedule()
            }
            .onFailure { file.delete() }
    }

    private fun backups(dir: File): List<File> =
        dir.listFiles { it.name.startsWith(PREFIX) && it.name.endsWith(".json") }
            .orEmpty()
            .sortedByDescending { it.name }

    companion object {
        const val BACKUPS_DIR = "Backups"
        private const val TAG = "AutoExport"
        private const val PREFIX = "resona_favorites_"
        private const val KEEP_BACKUPS = 10
        private const val HOUR_MS = 60L * 60L * 1000L
    }
}
