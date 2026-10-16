package com.savoo.scclient.player

import kotlinx.coroutines.flow.StateFlow

data class PlaybackStatus(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isReady: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class NowPlayingInfo(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
)

interface RemoteCommandHandler {
    fun onPlay()
    fun onPause()
    fun onNext()
    fun onPrevious()
    fun onSeek(positionMs: Long)
}

interface AudioPlayer {
    val status: StateFlow<PlaybackStatus>
    var onEnded: (() -> Unit)?
    var onError: ((String) -> Unit)?
    var remoteCommands: RemoteCommandHandler?
    var volume: Float
    var rate: Float
    fun load(url: String, info: NowPlayingInfo, playWhenReady: Boolean, startPositionMs: Long)
    fun updateInfo(info: NowPlayingInfo)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
}
