package com.weige.tingshu.play

import android.graphics.Bitmap

/**
 * 通知栏要用、但不属于播放器状态的东西：副标题（章节名）和封面位图。
 *
 * 走自定义 Provider 而不是 `replaceMediaItem` 改 metadata，
 * 因为后者会让播放器重建媒体源、重新解析音频地址、进度条跟着跳。
 */
object NowPlayingState {

    @Volatile
    var subtitle: String = ""
        private set

    @Volatile
    private var coverId: String? = null

    @Volatile
    private var cover: Bitmap? = null

    @Volatile
    var onChanged: (() -> Unit)? = null

    fun setSubtitle(text: String): Boolean {
        if (text == subtitle) return false
        subtitle = text
        return true
    }

    fun setCover(mediaId: String, bitmap: Bitmap?) {
        coverId = mediaId
        cover = bitmap
    }

    fun coverOf(mediaId: String?): Bitmap? = if (mediaId != null && mediaId == coverId) cover else null

    fun reset() {
        subtitle = ""
        coverId = null
        cover = null
    }
}
