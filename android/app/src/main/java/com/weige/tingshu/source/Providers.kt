package com.weige.tingshu.source

import com.weige.tingshu.model.Book
import com.weige.tingshu.model.BookDetail
import com.weige.tingshu.model.BookSource
import com.weige.tingshu.model.Chapter

/**
 * 统一的取书入口：内置接口源优先，其余交给规则引擎。
 *
 * 界面和播放服务只认这三四个方法，不用关心底下是原生接口还是 JSON 规则。
 * 以后要再加一个内置平台（猫耳之类），在这里加一个分支就行，规则源那套完全不用动。
 */
object Providers {

    fun isNative(sourceId: String): Boolean = sourceId == Ximalaya.SOURCE_ID

    /** 这个源能不能搜：内置接口源没有 search 规则，但照样能搜 */
    fun canSearch(source: BookSource): Boolean =
        isNative(source.id) || source.search != null

    suspend fun search(source: BookSource, keyword: String, page: Int): List<Book> =
        if (isNative(source.id)) {
            Ximalaya.search(keyword, page)
        } else {
            Sources.engine.search(source, keyword, page)
        }

    suspend fun browse(source: BookSource, categoryUrl: String, page: Int): List<Book> =
        Sources.engine.browse(source, categoryUrl, page)

    suspend fun detail(source: BookSource, book: Book): BookDetail =
        if (isNative(source.id)) {
            Ximalaya.detail(book)
        } else {
            Sources.engine.detail(source, book)
        }

    /**
     * 播放时要的直链。
     * 内置接口源的章节 url 本身就是 mp3，不用再发请求——
     * 这也是它比网页源快的地方：`ResolvingDataSource` 那一步是零成本。
     */
    suspend fun audioUrl(source: BookSource, chapter: Chapter): String =
        if (isNative(source.id)) {
            Ximalaya.audioOf(chapter)
        } else {
            Sources.engine.audioUrl(source, chapter)
        }
}
