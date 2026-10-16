package com.savoo.scclient.work

import com.savoo.scclient.compat.Context
import com.savoo.scclient.data.repository.FavoritesExporter
import com.savoo.scclient.data.repository.SettingsRepository

class AutoExportManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val exporter: FavoritesExporter,
) {
    suspend fun syncSchedule() = Unit
    suspend fun catchUpIfDue() = Unit
    suspend fun exportNow(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Auto export is not available on iOS"))
}
