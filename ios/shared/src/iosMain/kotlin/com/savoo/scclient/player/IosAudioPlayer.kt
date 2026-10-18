package com.savoo.scclient.player

import kotlinx.cinterop.CValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVAudioTimePitchAlgorithmVarispeed
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.asset
import platform.AVFoundation.audioMix
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadTracksWithMediaType
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setDefaultRate
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.timeControlStatus
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.roundToLong

class IosAudioPlayer(private val effects: SoundEffects) : AudioPlayer {

    private val player = AVPlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _status = MutableStateFlow(PlaybackStatus())
    override val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    override var onEnded: (() -> Unit)? = null
    override var onError: ((String) -> Unit)? = null
    override var remoteCommands: RemoteCommandHandler? = null

    override var volume: Float = 1f
        set(value) {
            field = value
            player.setVolume(value)
        }

    override var rate: Float = 1f
        set(value) {
            field = value
            player.setDefaultRate(value)
            if (wantsPlay) player.setRate(value)
            updateNowPlaying()
        }

    override var pitch: Float = 1f
        set(value) {
            field = value
            player.currentItem?.audioTimePitchAlgorithm = pitchAlgorithm()
        }

    private var wantsPlay = false
    private var info: NowPlayingInfo? = null
    private var artwork: MPMediaItemArtwork? = null
    private var artworkUrl: String? = null
    private var itemObservers: List<NSObjectProtocol> = emptyList()
    private var failureReported = false

    init {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, null)
            setActive(true, null)
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { note ->
            val userInfo = note?.userInfo ?: return@addObserverForName
            val type = (userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeBegan) {
                remoteCommands?.onPause()
            } else {
                val options = (userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
                if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) {
                    AVAudioSession.sharedInstance().setActive(true, null)
                    remoteCommands?.onPlay()
                }
            }
        }
        registerRemoteCommands()
        scope.launch {
            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun load(url: String, info: NowPlayingInfo, playWhenReady: Boolean, startPositionMs: Long) {
        val nsUrl = (if (url.startsWith("/")) NSURL.fileURLWithPath(url) else NSURL.URLWithString(url)) ?: run {
            onError?.invoke("bad url")
            return
        }
        removeItemObservers()
        failureReported = false
        val item = AVPlayerItem(uRL = nsUrl)
        item.audioTimePitchAlgorithm = pitchAlgorithm()
        val center = NSNotificationCenter.defaultCenter
        itemObservers = listOf(
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) { _ ->
                onEnded?.invoke()
            },
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) { _ ->
                reportFailure(item.error?.localizedDescription)
            },
        )
        updateInfo(info)
        player.replaceCurrentItemWithPlayerItem(item)
        attachEffects(item)
        if (startPositionMs > 0) player.seekToTime(CMTimeMakeWithSeconds(startPositionMs / 1000.0, 1000))
        _status.value = PlaybackStatus(isBuffering = true, positionMs = startPositionMs)
        wantsPlay = playWhenReady
        if (playWhenReady) startPlayback()
        updateNowPlaying()
    }

    override fun updateInfo(info: NowPlayingInfo) {
        this.info = info
        if (artworkUrl != info.artworkUrl) {
            artwork = null
            artworkUrl = info.artworkUrl
            loadArtwork(info.artworkUrl)
        }
        updateNowPlaying()
    }

    override fun play() {
        wantsPlay = true
        AVAudioSession.sharedInstance().setActive(true, null)
        startPlayback()
        poll()
    }

    override fun pause() {
        wantsPlay = false
        player.pause()
        poll()
    }

    override fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
        _status.value = _status.value.copy(positionMs = positionMs)
        updateNowPlaying()
    }

    override fun stop() {
        wantsPlay = false
        player.pause()
        removeItemObservers()
        player.replaceCurrentItemWithPlayerItem(null)
        _status.value = PlaybackStatus()
    }

    private fun pitchAlgorithm() = if (pitch != 1f) AVAudioTimePitchAlgorithmVarispeed else AVAudioTimePitchAlgorithmTimeDomain

    private fun attachEffects(item: AVPlayerItem) {
        item.asset.loadTracksWithMediaType(AVMediaTypeAudio) { tracks: List<*>?, _: NSError? ->
            val track = tracks?.firstOrNull() as? AVAssetTrack ?: return@loadTracksWithMediaType
            dispatch_async(dispatch_get_main_queue()) {
                if (player.currentItem !== item) return@dispatch_async
                val tap = createSoundEffectsTap(effects) ?: return@dispatch_async
                val parameters = AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track)
                parameters.setAudioTapProcessor(tap)
                CFRelease(tap)
                item.audioMix = AVMutableAudioMix().apply { setInputParameters(listOf(parameters)) }
            }
        }
    }

    private fun startPlayback() {
        player.play()
        if (rate != 1f) player.setRate(rate)
    }

    private fun poll() {
        val item = player.currentItem ?: return
        if (item.status == AVPlayerItemStatusFailed) {
            reportFailure(item.error?.localizedDescription)
            return
        }
        val control = player.timeControlStatus
        val ready = item.status == AVPlayerItemStatusReadyToPlay
        val position = seconds(player.currentTime())?.let { (it * 1000).roundToLong() }
        val duration = seconds(item.duration)?.let { (it * 1000).roundToLong() }
        val next = PlaybackStatus(
            isPlaying = control == AVPlayerTimeControlStatusPlaying ||
                (wantsPlay && control == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate),
            isBuffering = !ready || control == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
            isReady = ready,
            positionMs = position ?: _status.value.positionMs,
            durationMs = duration ?: 0L,
        )
        val changed = next.isPlaying != _status.value.isPlaying || next.durationMs != _status.value.durationMs
        _status.value = next
        if (changed) updateNowPlaying()
    }

    private fun seconds(time: CValue<CMTime>): Double? = CMTimeGetSeconds(time).takeIf { it.isFinite() && it >= 0 }

    private fun reportFailure(message: String?) {
        if (failureReported) return
        failureReported = true
        _status.value = _status.value.copy(isPlaying = false, isBuffering = false, isReady = false)
        onError?.invoke(message ?: "playback failed")
    }

    private fun removeItemObservers() {
        itemObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        itemObservers = emptyList()
    }

    private fun updateNowPlaying() {
        val info = info ?: return
        val status = _status.value
        val map = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to info.title,
            MPMediaItemPropertyArtist to info.artist,
            MPMediaItemPropertyPlaybackDuration to NSNumber(double = status.durationMs.coerceAtLeast(info.durationMs) / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = status.positionMs / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double = if (status.isPlaying) rate.toDouble() else 0.0),
        )
        artwork?.let { map[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = map
    }

    private fun loadArtwork(url: String?) {
        val nsUrl = url?.let { NSURL.URLWithString(it) } ?: return
        NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, _, _ ->
            val image = data?.let { UIImage.imageWithData(it) } ?: return@dataTaskWithURL
            dispatch_async(dispatch_get_main_queue()) {
                if (artworkUrl != url) return@dispatch_async
                artwork = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                updateNowPlaying()
            }
        }.resume()
    }

    private fun registerRemoteCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.addTargetWithHandler { _ ->
            remoteCommands?.onPlay()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.addTargetWithHandler { _ ->
            remoteCommands?.onPause()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.togglePlayPauseCommand.addTargetWithHandler { _ ->
            if (wantsPlay) remoteCommands?.onPause() else remoteCommands?.onPlay()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.nextTrackCommand.addTargetWithHandler { _ ->
            remoteCommands?.onNext()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.previousTrackCommand.addTargetWithHandler { _ ->
            remoteCommands?.onPrevious()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            remoteCommands?.onSeek((position * 1000).roundToLong())
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 250L
    }
}
