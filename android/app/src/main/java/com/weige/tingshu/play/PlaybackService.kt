package com.weige.tingshu.play

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.weige.tingshu.R
import com.weige.tingshu.source.Providers
import com.weige.tingshu.source.Sources
import kotlinx.coroutines.runBlocking

/**
 * 后台播放服务。
 *
 * 关键在 [ResolvingDataSource]：队列里每一章只带 `ts://chapter?id=<mediaId>`，
 * 播放器真要读这一章时才回调进来，现场按书源规则解析出音频直链。
 *
 * 有声书的章节地址基本都带时效 token，这个「要播才解析」的机制正好对上：
 * 既能一次性把整本书交给播放器（通知栏才有上一章/下一章），又不会因为提前解析而失效。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Sources.init(this)

        val baseFactory = DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory())

        val resolvingFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme != SCHEME) return@Factory dataSpec

            val mediaId = uri.getQueryParameter("id")
            val chapter = ChapterRegistry.get(mediaId)
            if (chapter == null) {
                Log.w(TAG, "队列里找不到章节 $mediaId")
                return@Factory dataSpec
            }
            val source = Sources.get(chapter.sourceId)
            if (source == null) {
                Log.w(TAG, "书源已删除：${chapter.sourceId}")
                return@Factory dataSpec
            }

            // 这里在播放器的加载线程上，会走一次网络；阻塞是预期行为
            val real = runCatching {
                runBlocking { Providers.audioUrl(source, chapter) }
            }.getOrNull()

            if (real.isNullOrBlank()) {
                Log.w(TAG, "解析音频失败：${chapter.title}")
                return@Factory dataSpec
            }
            Log.i(TAG, "解析到直链：${chapter.title} → ${real.take(60)}")

            val headers = LinkedHashMap<String, String>()
            source.audio.headers.forEach { (k, v) -> headers[k] = v }
            if (source.audio.referer.isNotBlank()) headers["Referer"] = source.audio.referer

            val next = dataSpec.withUri(Uri.parse(real))
            if (headers.isEmpty()) next else next.withRequestHeaders(headers)
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val launch = Intent(this, com.weige.tingshu.MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        // 注意：setMediaNotificationProvider 在 MediaSessionService 上，不在 Builder 上
        ensureChannel()
        val provider = TingshuNotificationProvider(this)
        setMediaNotificationProvider(provider)
        NowPlayingState.onChanged = { provider.refreshIfNeeded() }
    }

    /**
     * 自建播放通知渠道。
     *
     * 两个坑：
     *  - importance 用 LOW 会被系统归进静默分组，华为/荣耀锁屏直接不显示，
     *    所以用 DEFAULT + setSound(null) + 关振动，「可见但不打扰」。
     *  - channel 的 lockscreenVisibility 建好就改不动，只能换 id 重建。
     */
    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.deleteNotificationChannel("ts_playback") }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_playback),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notif_channel_playback_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        NowPlayingState.onChanged = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "PlaybackService"
        const val SCHEME = "ts"
        const val CHANNEL_ID = "ts_playback_v1"
        const val EXTRA_OPEN_PLAYER = "openPlayer"
    }
}
