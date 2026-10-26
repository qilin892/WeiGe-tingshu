package com.weige.tingshu.play

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.weige.tingshu.data.LocalStore
import com.weige.tingshu.model.Book
import com.weige.tingshu.model.Chapter
import com.weige.tingshu.model.PlayMode
import com.weige.tingshu.source.Covers
import com.weige.tingshu.source.Sources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** 播放器对外的完整状态快照，UI 只读这个 */
data class PlayState(
    val book: Book? = null,
    val chapter: Chapter? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val error: String? = null,
)

/**
 * MediaController 的薄封装。
 *
 * 控制器是异步连上 Service 的，所以对外只暴露 StateFlow，UI 不关心连接时机；
 * 位置没有回调，靠 200ms 轮询补。
 */
object PlayerController {

    /** 播放器自己切章（通知栏/耳机键/自动连播）时回调 */
    var onChapterChanged: ((Chapter) -> Unit)? = null

    /** 整个队列播完 */
    var onQueueEnded: (() -> Unit)? = null

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()

    private var tickerOn = false
    private var pendingMode = PlayMode.SEQUENCE
    private var pendingSpeed = 1f
    private var lastSaveAt = 0L
    private val artworkDone = mutableSetOf<String>()

    /** 控制器还没连上时用户就点了播放：先记下来，连上立刻执行 */
    private var pendingAction: (() -> Unit)? = null

    fun connect(context: Context) {
        if (controller != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c

            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(
                        playing = isPlaying,
                        buffering = if (isPlaying) false else _state.value.buffering,
                    )
                    if (!isPlaying) saveNow()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val chapter = ChapterRegistry.get(mediaItem?.mediaId) ?: return
                    // 不能在这里写死 buffering = true：自动连播时播放器多半已经预缓冲好，
                    // 不会再来一次 onPlaybackStateChanged，写死就没人改回来，转圈停不下来。
                    _state.value = _state.value.copy(
                        chapter = chapter,
                        positionMs = LocalStore.position(chapter.bookId, chapter.index),
                        buffering = c.playbackState == Player.STATE_BUFFERING,
                        error = null,
                    )
                    NowPlayingState.setSubtitle("")
                    onChapterChanged?.invoke(chapter)
                    prefetchArtwork(chapter)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.value = _state.value.copy(playing = false, buffering = false)
                        onQueueEnded?.invoke()
                        return
                    }
                    // duration 刚重建媒体源时是 C.TIME_UNSET（负数），写成 0 会让进度条闪一下，
                    // 所以只在有效时才覆盖。
                    val d = c.duration
                    _state.value = _state.value.copy(
                        buffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = if (d > 0) d else _state.value.durationMs,
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(
                        error = "播放失败：${error.errorCodeName}",
                        buffering = false,
                        playing = false,
                    )
                }
            })

            _state.value = _state.value.copy(playing = c.isPlaying, speed = pendingSpeed)
            c.setPlaybackSpeed(pendingSpeed)
            applyPlayMode()
            startTicker()
            pendingAction?.invoke()
            pendingAction = null
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startTicker() {
        if (tickerOn) return
        tickerOn = true
        scope.launch {
            while (tickerOn) {
                controller?.let { c ->
                    val pb = c.playbackState
                    val buffering = pb == Player.STATE_BUFFERING
                    if (c.isPlaying || buffering) {
                        val pos = c.currentPosition
                        val d = c.duration
                        _state.value = _state.value.copy(
                            positionMs = if (pos >= 0) pos else _state.value.positionMs,
                            durationMs = if (d > 0) d else _state.value.durationMs,
                        )
                    }
                    // 回调偶尔会丢，200ms 兜底校正一次；值不变就不写，避免无谓重组
                    if (_state.value.buffering != buffering) {
                        _state.value = _state.value.copy(buffering = buffering)
                    }
                    if (c.isPlaying) {
                        val now = System.currentTimeMillis()
                        if (now - lastSaveAt > 5000) {
                            lastSaveAt = now
                            saveNow()
                        }
                    }
                }
                delay(200)
            }
        }
    }

    /** 把「第几章 + 播到哪儿」写进本地；位置为 0 也要写，否则「听到第几章」不会更新 */
    fun saveNow() {
        val ch = _state.value.chapter ?: return
        LocalStore.saveProgress(ch.bookId, ch.index, _state.value.positionMs)
        _state.value.book?.let { LocalStore.setLastBook(it) }
    }

    /**
     * 整本书入队。
     *
     * 一次把全部章节交给播放器，通知栏才有上一章/下一章；
     * 每章的 URI 只是个标识（`ts://chapter?id=...`），真实音频地址
     * 由 [PlaybackService] 里的 ResolvingDataSource 在要播的那一刻解析。
     */
    fun playQueue(book: Book, chapters: List<Chapter>, index: Int, startMs: Long = 0L) {
        val c = controller ?: run {
            pendingAction = { playQueue(book, chapters, index, startMs) }
            return
        }
        if (chapters.isEmpty()) return

        val idx = index.coerceIn(0, chapters.size - 1)
        ChapterRegistry.putAll(book, chapters)

        val items = chapters.map { ch ->
            MediaItem.Builder()
                .setUri(
                    Uri.Builder()
                        .scheme(PlaybackService.SCHEME)
                        .authority("chapter")
                        .appendQueryParameter("id", ch.mediaId)
                        .build()
                )
                .setMediaId(ch.mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ch.title)
                        .setArtist(book.name)
                        .setAlbumTitle(book.author)
                        .apply { if (book.cover.isNotBlank()) setArtworkUri(Uri.parse(Covers.at(book.cover, 320))) }
                        .build()
                )
                .build()
        }

        val ch = chapters[idx]
        _state.value = _state.value.copy(
            book = book,
            chapter = ch,
            positionMs = startMs.coerceAtLeast(0),
            durationMs = 0,
            error = null,
            buffering = true,
        )

        c.setMediaItems(items, idx, startMs.coerceAtLeast(0))
        c.setPlaybackSpeed(pendingSpeed)
        c.prepare()
        c.play()
        applyPlayMode()

        LocalStore.setLastBook(book)
        LocalStore.touchShelf(book)
        prefetchArtwork(ch)
    }

    fun setPlayMode(mode: PlayMode) {
        pendingMode = mode
        _state.value = _state.value.copy(playMode = mode)
        applyPlayMode()
    }

    private fun applyPlayMode() {
        val c = controller ?: return
        when (pendingMode) {
            PlayMode.SEQUENCE -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_OFF
            }

            PlayMode.LOOP_ONE -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ONE
            }

            PlayMode.SHUFFLE -> {
                // 随机必须配 ALL，否则摇到末尾就停
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
        }
    }

    fun setSpeed(speed: Float) {
        pendingSpeed = speed
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
        LocalStore.speed = speed
    }

    /** 封面给通知栏用：不进 MediaMetadata，避免触发媒体源重建 */
    private fun prefetchArtwork(chapter: Chapter) {
        val book = _state.value.book ?: return
        // 通知大图最终缩到 512，所以取 640 那档就够，别下 108KB 的原图
        val url = Covers.at(book.cover, 640)
        if (url.isBlank()) return
        if (!artworkDone.add(book.id)) return

        scope.launch(Dispatchers.IO) {
            val raw = runCatching {
                val req = Request.Builder().url(url)
                    .header("User-Agent", com.weige.tingshu.source.Fetcher.UA)
                    .build()
                Sources.fetcher.client.newCall(req).execute().use { it.body?.bytes() }
            }.getOrNull() ?: return@launch

            val bmp = runCatching { decodeScaled(raw, 512) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                NowPlayingState.setCover(chapter.mediaId, bmp)
                NowPlayingState.onChanged?.invoke()
            }
        }
    }

    private fun decodeScaled(raw: ByteArray, maxSide: Int): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / sample > maxSide * 2) sample *= 2

        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null
        if (maxOf(bmp.width, bmp.height) <= maxSide) return bmp

        val ratio = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        return android.graphics.Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt().coerceAtLeast(1),
            (bmp.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            saveNow()
        } else {
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
        saveNow()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    /** 边界要问播放器：随机模式下队列下标和播放顺序无关 */
    fun hasNext(): Boolean = controller?.hasNextMediaItem() ?: false

    fun hasPrevious(): Boolean = controller?.hasPreviousMediaItem() ?: false

    fun seekTo(ms: Long) = controller?.seekTo(ms.coerceAtLeast(0))

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0))
    }

    /** 在同一本书里跳章：队列下标就是章节下标，直接 seek */
    fun playChapter(chapter: Chapter) {
        val c = controller ?: return
        if (c.mediaItemCount <= chapter.index) return
        if (c.getMediaItemAt(chapter.index).mediaId != chapter.mediaId) return
        c.seekTo(chapter.index, 0)
        c.play()
    }
}
