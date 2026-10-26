package com.weige.tingshu.model

/**
 * 一本书。id 由「源 + 详情页地址」拼出来，同一个源里唯一。
 * 章节不挂在 Book 上：整本书可能几百章，书架里不需要背着它们。
 */
data class Book(
    val id: String,
    val sourceId: String,
    val name: String,
    val author: String = "",
    val cover: String = "",
    val intro: String = "",
    val url: String = "",
    val lastChapter: String = "",
)

/**
 * 一章。
 *
 * [url] 是**章节页**地址，不是音频直链——音频地址通常带时效 token，
 * 必须到真正要播的那一刻再去解析（见 PlaybackService 里的 ResolvingDataSource）。
 */
data class Chapter(
    val bookId: String,
    val sourceId: String,
    val index: Int,
    val title: String,
    val url: String,
) {
    /** 给播放器当 mediaId 用，ChapterRegistry 靠它反查 */
    val mediaId: String get() = "$bookId#$index"

    /** 通知栏第二行 */
    fun label(): String = "第 ${index + 1} 章 · $title"
}

data class BookDetail(
    val book: Book,
    val chapters: List<Chapter>,
)

enum class PlayMode { SEQUENCE, LOOP_ONE, SHUFFLE }
