package com.savoo.scclient.compat.media

import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.Uri
import com.savoo.scclient.player.AudioPlayer
import com.savoo.scclient.player.NowPlayingInfo
import com.savoo.scclient.player.PlaybackStatus
import com.savoo.scclient.player.RemoteCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

object C {
    const val INDEX_UNSET = -1
    const val TIME_UNSET = Long.MIN_VALUE + 1
}

object MimeTypes {
    const val APPLICATION_M3U8 = "application/x-mpegURL"
}

class PlaybackParameters(val speed: Float, val pitch: Float = 1f)

class PlaybackException(message: String?) : Exception(message)

class Timeline internal constructor(val windowCount: Int)

class MediaMetadata private constructor(
    val title: CharSequence?,
    val artist: CharSequence?,
    val artworkUri: Uri?,
) {
    class Builder {
        private var title: CharSequence? = null
        private var artist: CharSequence? = null
        private var artworkUri: Uri? = null
        fun setTitle(value: CharSequence?) = apply { title = value }
        fun setArtist(value: CharSequence?) = apply { artist = value }
        fun setArtworkUri(value: Uri?) = apply { artworkUri = value }
        fun build() = MediaMetadata(title, artist, artworkUri)
    }
}

class MediaItem private constructor(
    val mediaId: String,
    val localConfiguration: LocalConfiguration?,
    val mediaMetadata: MediaMetadata,
) {
    class LocalConfiguration internal constructor(val uri: Uri, val mimeType: String?)

    class Builder {
        private var mediaId = ""
        private var uri: Uri? = null
        private var mimeType: String? = null
        private var metadata = MediaMetadata.Builder().build()
        fun setMediaId(value: String) = apply { mediaId = value }
        fun setUri(value: String) = apply { uri = Uri.parse(value) }
        fun setUri(value: Uri) = apply { uri = value }
        fun setMimeType(value: String?) = apply { mimeType = value }
        fun setMediaMetadata(value: MediaMetadata) = apply { metadata = value }
        fun build() = MediaItem(mediaId, uri?.let { LocalConfiguration(it, mimeType) }, metadata)
    }
}

object Player {
    const val STATE_IDLE = 1
    const val STATE_BUFFERING = 2
    const val STATE_READY = 3
    const val STATE_ENDED = 4

    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2

    const val MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0
    const val MEDIA_ITEM_TRANSITION_REASON_AUTO = 1
    const val MEDIA_ITEM_TRANSITION_REASON_SEEK = 2
    const val MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3

    const val DISCONTINUITY_REASON_SEEK = 1

    class PositionInfo internal constructor(val mediaItem: MediaItem?, val positionMs: Long)

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: Int) {}
        fun onTimelineChanged(timeline: Timeline, reason: Int) {}
        fun onPlayerError(error: PlaybackException) {}
        fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
        fun onRepeatModeChanged(repeatMode: Int) {}
        fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {}
    }
}

class MediaController private constructor(private val engine: AudioPlayer) : RemoteCommandHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = ArrayList<Player.Listener>()
    private val items = ArrayList<MediaItem>()
    private var index = -1
    private var loadedUri: String? = null
    private var lastStatus = PlaybackStatus()
    private var state = Player.STATE_IDLE
    private var reportedPlaying = false

    var playWhenReady: Boolean = false
        private set

    var shuffleModeEnabled: Boolean = false

    var repeatMode: Int = Player.REPEAT_MODE_OFF
        set(value) {
            if (field == value) return
            field = value
            listeners.toList().forEach { it.onRepeatModeChanged(value) }
        }

    var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            engine.volume = field
        }

    var playbackParameters: PlaybackParameters = PlaybackParameters(1f)
        set(value) {
            field = value
            engine.rate = value.speed
            engine.pitch = value.pitch
            listeners.toList().forEach { it.onPlaybackParametersChanged(value) }
        }

    init {
        engine.remoteCommands = this
        engine.onEnded = { handleEnded() }
        engine.onError = { message -> if (!isPendingItem(currentMediaItem)) dispatchError(message) }
        scope.launch { engine.status.collect { onStatus(it) } }
    }

    val currentMediaItemIndex: Int get() = index
    val currentMediaItem: MediaItem? get() = items.getOrNull(index)
    val mediaItemCount: Int get() = items.size
    val isPlaying: Boolean get() = reportedPlaying
    val playbackState: Int get() = state
    val currentPosition: Long get() = if (loadedUri == null) 0L else lastStatus.positionMs
    val duration: Long get() = lastStatus.durationMs.takeIf { it > 0 && loadedUri != null } ?: C.TIME_UNSET

    fun getMediaItemAt(i: Int): MediaItem = items[i]

    fun addListener(listener: Player.Listener) {
        listeners += listener
    }

    fun removeListener(listener: Player.Listener) {
        listeners -= listener
    }

    private fun effectiveRepeat() = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else repeatMode

    val nextMediaItemIndex: Int
        get() = when {
            items.isEmpty() || index < 0 -> C.INDEX_UNSET
            index < items.lastIndex -> index + 1
            effectiveRepeat() == Player.REPEAT_MODE_ALL -> 0
            else -> C.INDEX_UNSET
        }

    val previousMediaItemIndex: Int
        get() = when {
            items.isEmpty() || index < 0 -> C.INDEX_UNSET
            index > 0 -> index - 1
            effectiveRepeat() == Player.REPEAT_MODE_ALL -> items.lastIndex
            else -> C.INDEX_UNSET
        }

    fun hasNextMediaItem(): Boolean = nextMediaItemIndex != C.INDEX_UNSET
    fun hasPreviousMediaItem(): Boolean = previousMediaItemIndex != C.INDEX_UNSET

    fun setMediaItems(newItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        val previous = currentMediaItem
        items.clear()
        items.addAll(newItems)
        index = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex)
        pendingStartMs = startPositionMs.coerceAtLeast(0L)
        loadedUri = null
        timelineChanged()
        if (currentMediaItem != null && currentMediaItem !== previous) {
            transition(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        }
    }

    private var pendingStartMs = 0L

    fun addMediaItems(at: Int, newItems: List<MediaItem>) {
        val insertAt = at.coerceIn(0, items.size)
        items.addAll(insertAt, newItems)
        if (index >= insertAt && index >= 0) index += newItems.size
        if (index < 0 && items.isNotEmpty()) index = 0
        timelineChanged()
    }

    fun replaceMediaItem(i: Int, item: MediaItem) {
        if (i !in items.indices) return
        items[i] = item
        timelineChanged()
    }

    fun removeMediaItem(i: Int) {
        if (i !in items.indices) return
        items.removeAt(i)
        when {
            items.isEmpty() -> {
                index = -1
                stop()
            }
            i < index -> index--
            i == index -> {
                index = index.coerceAtMost(items.lastIndex)
                loadedUri = null
                pendingStartMs = 0L
                transition(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                if (state != Player.STATE_IDLE) prepare()
            }
        }
        timelineChanged()
    }

    fun moveMediaItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        timelineChanged()
    }

    fun clearMediaItems() {
        items.clear()
        index = -1
        loadedUri = null
        timelineChanged()
    }

    fun prepare() {
        val item = currentMediaItem ?: return
        if (isPendingItem(item)) {
            engine.stop()
            loadedUri = null
            setState(Player.STATE_BUFFERING)
            return
        }
        val uri = item.localConfiguration?.uri?.toString() ?: return
        if (uri == loadedUri && state != Player.STATE_IDLE && state != Player.STATE_ENDED) return
        loadedUri = uri
        val start = pendingStartMs
        pendingStartMs = 0L
        setState(Player.STATE_BUFFERING)
        engine.load(uri, info(item), playWhenReady, start)
    }

    fun play() {
        playWhenReady = true
        if (state == Player.STATE_ENDED) seekTo(index.coerceAtLeast(0), 0L)
        if (loadedUri != null) engine.play()
        refreshPlaying()
    }

    fun pause() {
        playWhenReady = false
        engine.pause()
        refreshPlaying()
    }

    fun stop() {
        engine.stop()
        loadedUri = null
        setState(Player.STATE_IDLE)
        refreshPlaying()
    }

    fun release() {
        stop()
        listeners.clear()
    }

    fun seekTo(positionMs: Long) {
        val old = Player.PositionInfo(currentMediaItem, currentPosition)
        if (loadedUri == null) {
            pendingStartMs = positionMs.coerceAtLeast(0L)
        } else {
            engine.seekTo(positionMs.coerceAtLeast(0L))
        }
        if (state == Player.STATE_ENDED) setState(Player.STATE_READY)
        listeners.toList().forEach {
            it.onPositionDiscontinuity(old, Player.PositionInfo(currentMediaItem, positionMs), Player.DISCONTINUITY_REASON_SEEK)
        }
    }

    fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex !in items.indices) return
        if (mediaItemIndex == index) {
            val uri = currentMediaItem?.localConfiguration?.uri?.toString()
            if (uri != loadedUri) {
                loadedUri = null
                pendingStartMs = positionMs.coerceAtLeast(0L)
                if (state != Player.STATE_IDLE) prepare()
            } else {
                seekTo(positionMs)
            }
            return
        }
        moveTo(mediaItemIndex, positionMs, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    fun seekToNext() {
        val next = nextMediaItemIndex
        if (next != C.INDEX_UNSET) moveTo(next, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    fun seekToPrevious() {
        val prev = previousMediaItemIndex
        if (prev == C.INDEX_UNSET || currentPosition > MAX_SEEK_TO_PREVIOUS_MS) seekTo(0L)
        else moveTo(prev, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    }

    private fun moveTo(target: Int, positionMs: Long, reason: Int) {
        index = target
        loadedUri = null
        pendingStartMs = positionMs
        engine.stop()
        transition(reason)
        if (state != Player.STATE_IDLE) prepare()
    }

    private fun handleEnded() {
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            engine.seekTo(0L)
            if (playWhenReady) engine.play()
            transition(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            return
        }
        val next = nextMediaItemIndex
        if (next == C.INDEX_UNSET) {
            setState(Player.STATE_ENDED)
            refreshPlaying()
            return
        }
        moveTo(next, 0L, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
    }

    private fun onStatus(status: PlaybackStatus) {
        lastStatus = status
        if (loadedUri != null && state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
            setState(if (status.isBuffering || !status.isReady) Player.STATE_BUFFERING else Player.STATE_READY)
        }
        refreshPlaying()
    }

    private fun refreshPlaying() {
        val playing = playWhenReady && loadedUri != null && lastStatus.isPlaying && state == Player.STATE_READY
        if (playing != reportedPlaying) {
            reportedPlaying = playing
            listeners.toList().forEach { it.onIsPlayingChanged(playing) }
        }
    }

    private fun setState(newState: Int) {
        if (state == newState) return
        state = newState
        listeners.toList().forEach { it.onPlaybackStateChanged(newState) }
    }

    private fun transition(reason: Int) {
        val item = currentMediaItem
        item?.let { engine.updateInfo(info(it)) }
        listeners.toList().forEach { it.onMediaItemTransition(item, reason) }
    }

    private fun timelineChanged() {
        val timeline = Timeline(items.size)
        listeners.toList().forEach { it.onTimelineChanged(timeline, 0) }
    }

    private fun dispatchError(message: String) {
        loadedUri = null
        setState(Player.STATE_IDLE)
        refreshPlaying()
        val error = PlaybackException(message)
        listeners.toList().forEach { it.onPlayerError(error) }
    }

    private fun isPendingItem(item: MediaItem?) = item?.localConfiguration?.uri?.scheme == PENDING_SCHEME

    private fun info(item: MediaItem) = NowPlayingInfo(
        title = item.mediaMetadata.title?.toString().orEmpty(),
        artist = item.mediaMetadata.artist?.toString().orEmpty(),
        artworkUrl = item.mediaMetadata.artworkUri?.toString(),
        durationMs = 0L,
    )

    override fun onPlay() = play()
    override fun onPause() = pause()
    override fun onNext() = seekToNext()
    override fun onPrevious() = seekToPrevious()
    override fun onSeek(positionMs: Long) = seekTo(positionMs)

    class Builder(private val context: Context, private val token: SessionToken) {
        fun buildAsync(): ListenableFuture<MediaController> =
            ListenableFuture(shared ?: MediaController(KoinPlatform.getKoin().get()).also { shared = it })
    }

    companion object {
        const val PENDING_SCHEME = "rawresource"
        private const val MAX_SEEK_TO_PREVIOUS_MS = 3_000L
        private var shared: MediaController? = null
    }
}

class SessionToken(context: Context)

class ListenableFuture<T> internal constructor(private val value: T) {
    fun get(): T = value
    fun addListener(listener: () -> Unit, executor: Any?) {
        CoroutineScope(Dispatchers.Main).launch { listener() }
    }
}

object MoreExecutors {
    fun directExecutor(): Any? = null
}
