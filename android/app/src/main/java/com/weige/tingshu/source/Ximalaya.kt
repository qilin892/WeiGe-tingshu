package com.weige.tingshu.source

import com.weige.tingshu.model.Book
import com.weige.tingshu.model.BookDetail
import com.weige.tingshu.model.Chapter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * 喜马拉雅客户端，**直接写在 App 里**，不走规则源。
 *
 * 为什么要内置一份：
 *  - 网页源靠不住。2026 年老牌听书站基本全关，剩下几个 CMS 站的搜索还挂着验证码；
 *  - 这个接口给的是**不带时效的直链**，不用「要播才解析」那一套，链路最短；
 *  - 写死之后用户不用配任何东西，装上就能搜、就能放。
 *
 * 三个动作对应三个接口：
 *  搜索  /revision/search?core=album&kw=..        → data.result.response.docs[]
 *  音轨  mobwsa.ximalaya.com/..album/page?albumId= → list[]（一页 20 条，按 maxPageId 翻）
 *  播放  list[].playUrl64 就是 mp3 直链，付费轨这个字段为空，直接丢掉
 */
object Ximalaya {

    const val SOURCE_ID = "ximalaya"

    private const val SEARCH_API =
        "https://www.ximalaya.com/revision/search" +
            "?core=album&kw=%s&page=%d&spellchecker=true&rows=20&condition=relation&device=iPhone"

    private const val TRACK_API =
        "http://mobwsa.ximalaya.com/mobile/playlist/album/page?albumId=%s&pageId=%d"

    /** 一页 20 条，60 页封顶（1200 章），再长的书也不至于让用户干等 */
    private const val MAX_PAGES = 60

    private fun headers() = mapOf(
        "Accept" to "application/json",
        "Referer" to "https://www.ximalaya.com/",
    )

    // ---------- 搜索 ----------

    suspend fun search(keyword: String, page: Int = 1): List<Book> {
        val url = SEARCH_API.format(encode(keyword), page)
        val body = Sources.fetcher.get(url, headers(), "https://www.ximalaya.com/").body
        val json = JsonRules.parse(body) ?: return emptyList()

        return JsonRules.array(json, "json:$.data.result.response.docs").mapNotNull { doc ->
            val albumId = JsonRules.value(doc, "json:id")
            val title = JsonRules.value(doc, "json:title")
            if (albumId.isBlank() || title.isBlank()) return@mapNotNull null

            // 收费专辑的音轨地址全是空的，点进去只能看到「没有可听的」，不如根本不显示。
            // 判据：is_paid=true，或带会员免费标记（vipFreeType≠0）。
            // 免费专辑这三项都是 false / 0，误杀风险很低。
            if (isPaidAlbum(doc)) return@mapNotNull null

            Book(
                id = bookId(albumId),
                sourceId = SOURCE_ID,
                name = title,
                author = JsonRules.value(doc, "json:nickname"),
                cover = Covers.base(JsonRules.value(doc, "json:cover_path")),
                intro = JsonRules.value(doc, "json:intro"),
                url = albumId,
            )
        }
    }

    // ---------- 专辑 → 章节 ----------

    suspend fun detail(book: Book): BookDetail {
        val albumId = book.url.ifBlank { book.id.removePrefix("${SOURCE_ID}_") }
        if (albumId.isBlank()) return BookDetail(book, emptyList())

        val first = runCatching { fetchTracks(albumId, 1) }.getOrNull()
            ?: return BookDetail(book, emptyList())
        if (first.list.isEmpty()) return BookDetail(book, emptyList())

        // 专辑名/封面从第一条音轨里拿，比搜索结果的更准
        var meta = book
        first.list.firstOrNull()?.let { t ->
            val title = JsonRules.value(t, "json:albumTitle")
            val cover = Covers.base(JsonRules.value(t, "json:albumImage"))
            if (title.isNotBlank()) meta = meta.copy(name = title)
            if (cover.isNotBlank()) meta = meta.copy(cover = cover)
        }

        val docs = ArrayList<JSONObject>()
        docs.addAll(first.list)

        val last = minOf(first.maxPage, MAX_PAGES)
        if (last > 1) {
            // 并发抓，4 页一组；顺序靠 page 号还原
            val rest = coroutineScope {
                (2..last).chunked(4).flatMap { group ->
                    group.map { p -> async { p to runCatching { fetchTracks(albumId, p) }.getOrNull() } }
                        .awaitAll()
                }
            }.sortedBy { it.first }

            rest.forEach { (_, pageData) -> pageData?.list?.let { docs.addAll(it) } }
        }

        val chapters = docs.mapNotNull { d ->
            val title = JsonRules.value(d, "json:title")
            if (title.isBlank()) return@mapNotNull null
            // 免费轨四个字段里至少有一个有值；付费/会员轨全空，直接跳过
            val url = listOf("playUrl64", "playPathAacv164", "playUrl32", "playPathAacv224")
                .map { JsonRules.value(d, "json:$it") }
                .firstOrNull { it.isNotBlank() }
                ?: return@mapNotNull null
            title to fixScheme(url)
        }

        val list = chapters.mapIndexed { i, (t, u) ->
            Chapter(bookId = meta.id, sourceId = SOURCE_ID, index = i, title = t, url = u)
        }
        return BookDetail(meta, list)
    }

    /** 音频就是章节自身携带的地址，不需要二次请求 */
    fun audioOf(chapter: Chapter): String = chapter.url

    // ---------- 内部 ----------

    private class TrackPage(val list: List<JSONObject>, val maxPage: Int)

    private suspend fun fetchTracks(albumId: String, page: Int): TrackPage {
        val body = Sources.fetcher.get(
            TRACK_API.format(albumId, page),
            headers(),
            "https://www.ximalaya.com/",
        ).body
        val json = JsonRules.parse(body) ?: return TrackPage(emptyList(), 1)
        val max = JsonRules.value(json, "json:$.maxPageId").toIntOrNull() ?: 1
        return TrackPage(JsonRules.array(json, "json:$.list"), max)
    }

    private fun bookId(albumId: String) = "${SOURCE_ID}_$albumId"

    /**
     * 是不是收费专辑。
     *
     * 实测（2026-10）：
     *  - 收费：is_paid=true，vipFreeType=1，price_type_id=1，price_types 里 price>0
     *  - 免费：is_paid=false，vipFreeType=0，price_type_id=0，price_types=[]
     * 所以只看 is_paid 和 vipFreeType 就够，字段缺失时按免费处理（宁可多显示一本，也别把免费的藏了）。
     */
    private fun isPaidAlbum(doc: JSONObject): Boolean {
        if (JsonRules.value(doc, "json:is_paid") == "true") return true
        val vipFreeType = JsonRules.value(doc, "json:vipFreeType")
        return vipFreeType.isNotBlank() && vipFreeType != "0"
    }

    /** 接口里的图片常写成 //imagev2.xmcdn.com/... */
    private fun fixScheme(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    private fun encode(s: String): String =
        runCatching { java.net.URLEncoder.encode(s, "utf-8") }.getOrDefault(s)
}
