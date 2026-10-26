package com.weige.tingshu.play

import com.weige.tingshu.model.Book
import com.weige.tingshu.model.Chapter

/**
 * mediaId → Chapter 的反查表，给后台服务里的 ResolvingDataSource 用。
 *
 * 队列交给播放器时只有 mediaId，真实音频地址要等到「真要播这一章」时才解析，
 * 解析时会回调到后台线程，拿不到 ViewModel，所以这份表必须是全局单例。
 */
object ChapterRegistry {

    private val chapters = HashMap<String, Chapter>()
    private var currentBook: Book? = null

    fun putAll(book: Book, list: List<Chapter>) {
        currentBook = book
        chapters.clear()
        list.forEach { chapters[it.mediaId] = it }
    }

    fun get(mediaId: String?): Chapter? = if (mediaId == null) null else chapters[mediaId]

    fun book(): Book? = currentBook
}
