package com.savoo.scclient.ui.navigation

import com.savoo.scclient.data.remote.SoundCloudImportRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.PlayerController
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object DeepLinkEntryPoint : KoinComponent {
    fun soundCloudImportRepository(): SoundCloudImportRepository = get()
    fun trackRepository(): TrackRepository = get()
    fun playerController(): PlayerController = get()
}
