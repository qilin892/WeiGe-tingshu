package com.weige.tingshu.play

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.weige.tingshu.R

/**
 * 自定义播放通知。
 *
 * 默认 Provider 的文案全来自 `player.mediaMetadata`，想让它显示「第 N 章」以外的东西
 * 就只能 replaceMediaItem 改 metadata —— 那会让播放器重建媒体源、重新解析音频地址。
 * 这里让通知自己读 [NowPlayingState]，播放侧全程无感。
 */
class TingshuNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private var session: MediaSession? = null
    private var factory: MediaNotification.ActionFactory? = null
    private var lastSignature: String = ""

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        session = mediaSession
        factory = actionFactory
        lastSignature = signature()
        return MediaNotification(NOTIFICATION_ID, build())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false

    /** 副标题/封面变了才刷，签名相同直接返回，避免每 200ms 抖一次 */
    fun refreshIfNeeded() {
        val next = signature()
        if (next == lastSignature) return
        val n = runCatching { build() }.getOrNull() ?: return
        lastSignature = next
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    private fun signature(): String {
        val p = session?.player ?: return ""
        val id = p.currentMediaItem?.mediaId
        return "$id|${p.isPlaying}|${NowPlayingState.subtitle}|${NowPlayingState.coverOf(id) != null}"
    }

    private fun build(): Notification {
        val s = session ?: error("通知 session 还没就绪")
        val p = s.player
        val meta = p.mediaMetadata
        val mediaId = p.currentMediaItem?.mediaId
        val playing = p.isPlaying

        val title = meta.title?.toString().orEmpty().ifBlank { "伟哥听书" }
        val book = meta.artist?.toString().orEmpty()
        val sub = NowPlayingState.subtitle
        val text = if (sub.isBlank()) book else sub

        val builder = NotificationCompat.Builder(context, PlaybackService.CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(s.sessionActivity)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)

        NowPlayingState.coverOf(mediaId)?.let { builder.setLargeIcon(it) }
        if (sub.isNotBlank() && book.isNotBlank()) builder.setSubText(book)

        val f = factory ?: error("action factory 还没就绪")

        val prevIcon = androidx.media3.session.R.drawable.media3_icon_previous
        val playIcon = if (playing) {
            androidx.media3.session.R.drawable.media3_icon_pause
        } else {
            androidx.media3.session.R.drawable.media3_icon_play
        }
        val nextIcon = androidx.media3.session.R.drawable.media3_icon_next

        builder.addAction(
            f.createMediaAction(s, IconCompat.createWithResource(context, prevIcon), "上一章",
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM.toInt())
        )
        builder.addAction(
            f.createMediaAction(s, IconCompat.createWithResource(context, playIcon),
                if (playing) "暂停" else "播放", Player.COMMAND_PLAY_PAUSE.toInt())
        )
        builder.addAction(
            f.createMediaAction(s, IconCompat.createWithResource(context, nextIcon), "下一章",
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM.toInt())
        )

        builder.setStyle(MediaStyleNotificationHelper.MediaStyle(s).setShowActionsInCompactView(0, 1, 2))

        // 再盖一层展开视图：MediaStyle 的展开态没有大封面
        runCatching { builder.setCustomBigContentView(buildExpanded(s, f, title, text, mediaId, playing)) }

        return builder.build()
    }

    private fun buildExpanded(
        session: MediaSession,
        factory: MediaNotification.ActionFactory,
        title: String,
        subText: String,
        mediaId: String?,
        playing: Boolean,
    ): android.widget.RemoteViews {
        val rv = android.widget.RemoteViews(context.packageName, R.layout.notification_player)
        rv.setTextViewText(R.id.np_title, title)
        rv.setTextViewText(R.id.np_sub, subText)

        val playIcon = if (playing) {
            androidx.media3.session.R.drawable.media3_icon_pause
        } else {
            androidx.media3.session.R.drawable.media3_icon_play
        }
        rv.setImageViewResource(R.id.np_prev, androidx.media3.session.R.drawable.media3_icon_previous)
        rv.setImageViewResource(R.id.np_play, playIcon)
        rv.setImageViewResource(R.id.np_next, androidx.media3.session.R.drawable.media3_icon_next)

        NowPlayingState.coverOf(mediaId)?.let { rv.setImageViewBitmap(R.id.np_cover, it) }

        rv.setOnClickPendingIntent(
            R.id.np_prev,
            factory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM.toLong()),
        )
        rv.setOnClickPendingIntent(
            R.id.np_play,
            factory.createMediaActionPendingIntent(session, Player.COMMAND_PLAY_PAUSE.toLong()),
        )
        rv.setOnClickPendingIntent(
            R.id.np_next,
            factory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM.toLong()),
        )
        return rv
    }

    private companion object {
        const val NOTIFICATION_ID = 2001
    }
}
