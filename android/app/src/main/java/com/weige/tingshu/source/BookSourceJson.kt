package com.weige.tingshu.source

import com.weige.tingshu.model.AudioRule
import com.weige.tingshu.model.BookSource
import com.weige.tingshu.model.BrowseRule
import com.weige.tingshu.model.CategoryRule
import com.weige.tingshu.model.ChapterRule
import com.weige.tingshu.model.DetailRule
import com.weige.tingshu.model.PageRule
import com.weige.tingshu.model.SampleBook
import com.weige.tingshu.model.SampleChapter
import com.weige.tingshu.model.SampleData
import com.weige.tingshu.model.SearchRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * 书源 JSON 的读写。
 *
 * 兼容三种导入形态，用户从哪儿粘都能进：
 *  1. 单个源 `{...}`
 *  2. 源数组 `[{...},{...}]`
 *  3. 带订阅信息的包裹 `{"version":1,"sources":[...]}`
 */
object BookSourceJson {

    fun parseList(text: String): List<BookSource> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()

        val arr: JSONArray = when {
            t.startsWith("[") -> JSONArray(t)
            t.startsWith("{") -> {
                val o = JSONObject(t)
                when {
                    o.has("sources") -> o.getJSONArray("sources")
                    o.has("name") || o.has("search") || o.has("detail") -> JSONArray().put(o)
                    else -> JSONArray()
                }
            }

            else -> JSONArray()
        }

        val out = ArrayList<BookSource>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseOne(o)?.let { out.add(it) }
        }
        return out
    }

    fun parseOne(o: JSONObject): BookSource? {
        val name = o.optString("name", "").trim()
        if (name.isEmpty()) return null
        val id = o.optString("id", "").trim().ifEmpty {
            "src_" + Integer.toHexString(name.hashCode())
        }

        val search = o.optJSONObject("search")?.let { s ->
            val url = s.optString("url", "")
            val list = s.optString("list", "")
            if (url.isBlank() || list.isBlank()) null else SearchRule(
                url = url,
                method = s.optString("method", "GET"),
                list = list,
                bookUrl = s.optString("bookUrl", "a@href"),
                name = s.optString("name", "a@text"),
                author = s.optString("author", ""),
                cover = s.optString("cover", ""),
                intro = s.optString("intro", ""),
                lastChapter = s.optString("lastChapter", ""),
            )
        }

        val browse = o.optJSONObject("browse")?.let { b ->
            val list = b.optString("list", "")
            if (list.isBlank()) null else BrowseRule(
                list = list,
                bookUrl = b.optString("bookUrl", "a@href"),
                name = b.optString("name", "a@text"),
                author = b.optString("author", ""),
                cover = b.optString("cover", ""),
                intro = b.optString("intro", ""),
                lastChapter = b.optString("lastChapter", ""),
            )
        }

        val detail = o.optJSONObject("detail")?.let { d ->
            DetailRule(
                url = d.optString("url", ""),
                name = d.optString("name", ""),
                author = d.optString("author", ""),
                cover = d.optString("cover", ""),
                intro = d.optString("intro", ""),
                chapters = d.optJSONObject("chapters")?.let { c ->
                    val list = c.optString("list", "")
                    if (list.isBlank()) null else ChapterRule(
                        list = list,
                        title = c.optString("title", "a@text"),
                        url = c.optString("url", "a@href"),
                        reverse = c.optBoolean("reverse", false),
                        pages = c.optJSONObject("pages")?.let { pg ->
                            PageRule(
                                url = pg.optString("url", ""),
                                max = pg.optString("max", ""),
                                start = pg.optInt("start", 1),
                                maxPages = pg.optInt("maxPages", 60),
                            )
                        },
                    )
                },
            )
        }

        val audio = o.optJSONObject("audio")?.let { a ->
            AudioRule(
                url = a.optString("url", ""),
                referer = a.optString("referer", ""),
                headers = a.optJSONObject("headers").toStringMap(),
            )
        } ?: AudioRule()

        val cats = ArrayList<CategoryRule>()
        o.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val cn = c.optString("name", "").trim()
                val cu = c.optString("url", "").trim()
                if (cn.isNotEmpty() && cu.isNotEmpty()) cats.add(CategoryRule(cn, cu))
            }
        }

        return BookSource(
            id = id,
            name = name,
            version = o.optInt("version", 1),
            enabled = o.optBoolean("enabled", true),
            builtin = o.optBoolean("builtin", false),
            baseUrl = o.optString("baseUrl", ""),
            charset = o.optString("charset", "utf-8"),
            headers = o.optJSONObject("headers").toStringMap(),
            search = search,
            browse = browse,
            detail = detail,
            audio = audio,
            categories = cats,
            sample = o.optJSONObject("sample")?.let { s ->
                val books = ArrayList<SampleBook>()
                s.optJSONArray("books")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val b = arr.optJSONObject(i) ?: continue
                        val chs = ArrayList<SampleChapter>()
                        b.optJSONArray("chapters")?.let { cs ->
                            for (j in 0 until cs.length()) {
                                val c = cs.optJSONObject(j) ?: continue
                                chs.add(
                                    SampleChapter(
                                        c.optString("title", "第 ${j + 1} 章"),
                                        c.optString("url", ""),
                                    )
                                )
                            }
                        }
                        books.add(
                            SampleBook(
                                name = b.optString("name", ""),
                                author = b.optString("author", ""),
                                cover = b.optString("cover", ""),
                                intro = b.optString("intro", ""),
                                chapters = chs,
                            )
                        )
                    }
                }
                SampleData(books)
            },
        )
    }

    fun toJson(s: BookSource): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("version", s.version)
        put("enabled", s.enabled)
        if (s.baseUrl.isNotBlank()) put("baseUrl", s.baseUrl)
        if (s.charset != "utf-8") put("charset", s.charset)
        if (s.headers.isNotEmpty()) put("headers", JSONObject(s.headers as Map<*, *>))
        s.search?.let {
            put(
                "search", JSONObject().apply {
                    put("url", it.url)
                    put("list", it.list)
                    if (it.bookUrl != "a@href") put("bookUrl", it.bookUrl)
                    if (it.name != "a@text") put("name", it.name)
                    if (it.author.isNotBlank()) put("author", it.author)
                    if (it.cover.isNotBlank()) put("cover", it.cover)
                    if (it.intro.isNotBlank()) put("intro", it.intro)
                    if (it.lastChapter.isNotBlank()) put("lastChapter", it.lastChapter)
                }
            )
        }
        s.browse?.let {
            put(
                "browse", JSONObject().apply {
                    put("list", it.list)
                    if (it.bookUrl != "a@href") put("bookUrl", it.bookUrl)
                    if (it.name != "a@text") put("name", it.name)
                    if (it.author.isNotBlank()) put("author", it.author)
                    if (it.cover.isNotBlank()) put("cover", it.cover)
                    if (it.intro.isNotBlank()) put("intro", it.intro)
                    if (it.lastChapter.isNotBlank()) put("lastChapter", it.lastChapter)
                }
            )
        }
        s.detail?.let {
            put(
                "detail", JSONObject().apply {
                    if (it.url.isNotBlank()) put("url", it.url)
                    if (it.name.isNotBlank()) put("name", it.name)
                    if (it.author.isNotBlank()) put("author", it.author)
                    if (it.cover.isNotBlank()) put("cover", it.cover)
                    if (it.intro.isNotBlank()) put("intro", it.intro)
                    it.chapters?.let { c ->
                        put(
                            "chapters", JSONObject().apply {
                                put("list", c.list)
                                if (c.title != "a@text") put("title", c.title)
                                if (c.url != "a@href") put("url", c.url)
                                if (c.reverse) put("reverse", true)
                                c.pages?.let { pg ->
                                    put(
                                        "pages", JSONObject().apply {
                                            put("url", pg.url)
                                            if (pg.max.isNotBlank()) put("max", pg.max)
                                            if (pg.start != 1) put("start", pg.start)
                                            if (pg.maxPages != 60) put("maxPages", pg.maxPages)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
        if (s.audio.url.isNotBlank() || s.audio.headers.isNotEmpty()) {
            put(
                "audio", JSONObject().apply {
                    if (s.audio.url.isNotBlank()) put("url", s.audio.url)
                    if (s.audio.referer.isNotBlank()) put("referer", s.audio.referer)
                    if (s.audio.headers.isNotEmpty()) put("headers", JSONObject(s.audio.headers as Map<*, *>))
                }
            )
        }
        if (s.categories.isNotEmpty()) {
            put(
                "categories", JSONArray().apply {
                    s.categories.forEach { put(JSONObject().put("name", it.name).put("url", it.url)) }
                }
            )
        }
        if (s.sample != null) {
            put(
                "sample", JSONObject().apply {
                    put(
                        "books", JSONArray().apply {
                            s.sample.books.forEach { b ->
                                put(
                                    JSONObject().apply {
                                        put("name", b.name)
                                        if (b.author.isNotBlank()) put("author", b.author)
                                        if (b.cover.isNotBlank()) put("cover", b.cover)
                                        if (b.intro.isNotBlank()) put("intro", b.intro)
                                        put(
                                            "chapters", JSONArray().apply {
                                                b.chapters.forEach { c ->
                                                    put(JSONObject().put("title", c.title).put("url", c.url))
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    fun toArray(list: List<BookSource>): JSONArray = JSONArray().apply { list.forEach { put(toJson(it)) } }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val m = LinkedHashMap<String, String>()
        keys().forEach { k -> m[k] = optString(k, "") }
        return m
    }
}
