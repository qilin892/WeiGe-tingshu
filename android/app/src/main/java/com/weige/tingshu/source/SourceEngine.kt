package com.weige.tingshu.source

import com.weige.tingshu.model.Book
import com.weige.tingshu.model.BookDetail
import com.weige.tingshu.model.BookSource
import com.weige.tingshu.model.BrowseRule
import com.weige.tingshu.model.Chapter
import com.weige.tingshu.model.SampleBook
import com.weige.tingshu.model.SearchRule
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 把「书源规则」变成真实的书 / 章 / 音频地址。
 *
 * 两种响应都能吃：
 *  - HTML：规则是 CSS 选择器，交给 [Rules]
 *  - JSON 接口：规则以 `json:` 开头，交给 [JsonRules]
 *
 * 判断方式是看正文——以 `{` / `[` 开头的当 JSON，其余按 HTML 解析。
 * 所以同一个源里「搜索走接口、章节走网页」也不会打架。
 */
class SourceEngine(private val fetcher: Fetcher) {

    // ---------- 搜索 ----------

    suspend fun search(source: BookSource, keyword: String, page: Int = 1): List<Book> {
        source.sample?.let { sample ->
            val kw = keyword.trim()
            return sample.books
                .filter { kw.isEmpty() || it.name.contains(kw) || it.author.contains(kw) }
                .map { it.toBook(source) }
        }

        val rule = source.search ?: return emptyList()
        val url = abs(source, Rules.template(rule.url, keyword, page))
        val p = fetcher.get(url, source.headers, source.baseUrl, source.charset)
        return toBooks(listItems(p, rule.list), p.url, ListRules.of(rule), source)
    }

    // ---------- 分类浏览 ----------

    /**
     * 分类浏览。搜索接口被验证码挡死的站只能走这里，
     * 分类地址里的 `{{page}}` 由调用方按页码替换。
     */
    suspend fun browse(source: BookSource, categoryUrl: String, page: Int = 1): List<Book> {
        val rules = source.browse?.let { ListRules.of(it) }
            ?: source.search?.let { ListRules.of(it) }
            ?: return emptyList()

        val url = abs(source, Rules.template(categoryUrl, "", page))
        val p = fetcher.get(url, source.headers, source.baseUrl, source.charset)
        return toBooks(listItems(p, rules.list), p.url, rules, source)
    }

    // ---------- 详情 + 章节 ----------

    suspend fun detail(source: BookSource, book: Book): BookDetail {
        source.sample?.let { sample ->
            val s = sample.books.firstOrNull { it.name == book.name }
                ?: return BookDetail(book, emptyList())
            val chapters = s.chapters.mapIndexed { i, c ->
                Chapter(book.id, source.id, i, c.title, c.url)
            }
            return BookDetail(
                book.copy(author = s.author.ifBlank { book.author }, cover = s.cover, intro = s.intro),
                chapters,
            )
        }

        val detail = source.detail
        val pageUrl = detail?.url?.takeIf { it.isNotBlank() }
            ?.let { abs(source, it.replace("{{bookUrl}}", book.url)) }
            ?: book.url
        if (pageUrl.isBlank()) return BookDetail(book, emptyList())

        val p = fetcher.get(pageUrl, source.headers, source.baseUrl, source.charset)
        val json = JsonRules.parse(p.body)

        val merged = if (json != null) {
            book.copy(
                name = pickJson(json, detail?.name).ifBlank { book.name },
                author = pickJson(json, detail?.author).ifBlank { book.author },
                cover = pickJson(json, detail?.cover).ifBlank { book.cover },
                intro = pickJson(json, detail?.intro).ifBlank { book.intro },
            )
        } else {
            val doc = parseHtml(p.body, p.url)
            book.copy(
                name = Rules.value(doc, detail?.name.orEmpty(), p.url).ifBlank { book.name },
                author = Rules.value(doc, detail?.author.orEmpty(), p.url).ifBlank { book.author },
                cover = Rules.value(doc, detail?.cover.orEmpty(), p.url).ifBlank { book.cover },
                intro = Rules.value(doc, detail?.intro.orEmpty(), p.url).ifBlank { book.intro },
            )
        }

        val cr = detail?.chapters ?: return BookDetail(merged, emptyList())

        val items = ArrayList<Item>()
        items.addAll(listItems(p, cr.list))

        // 接口源一页只给 20 条，按 maxPageId 把剩下的翻完
        val pages = cr.pages
        if (pages != null && json != null) {
            val max = pickJson(json, pages.max).toIntOrNull() ?: pages.start
            val last = minOf(max, pages.start + pages.maxPages - 1)
            if (last > pages.start) {
                for (n in (pages.start + 1)..last) {
                    val u = abs(
                        source,
                        pages.url.replace("{{p}}", n.toString()).replace("{{bookUrl}}", book.url),
                    )
                    runCatching {
                        val np = fetcher.get(u, source.headers, source.baseUrl, source.charset)
                        items.addAll(listItems(np, cr.list))
                    }
                }
            }
        }

        var pairs = items.mapNotNull { item ->
            val title = pick(item, cr.title, p.url)
            val url = pick(item, cr.url, p.url)
            if (title.isBlank() || url.isBlank()) null else title to url
        }
        if (cr.reverse) pairs = pairs.reversed()
        // 分页偶尔会重叠，同一地址只留第一条
        val seen = HashSet<String>()
        pairs = pairs.filter { seen.add(it.second) }

        val chapters = pairs.mapIndexed { i, pair -> Chapter(book.id, source.id, i, pair.first, pair.second) }
        return BookDetail(merged, chapters)
    }

    // ---------- 音频 ----------

    /**
     * 章节 → 音频直链。
     * 地址可能带时效 token，所以每次播放都重新解析。
     * 规则留空则表示章节 url 本身就是音频地址（接口源常见）。
     */
    suspend fun audioUrl(source: BookSource, chapter: Chapter): String {
        val rule = source.audio
        if (rule.url.isBlank()) return chapter.url

        val p = fetcher.get(chapter.url, source.headers, source.baseUrl, source.charset)
        val json = JsonRules.parse(p.body)
        if (json != null) return pickJson(json, rule.url)

        val doc = parseHtml(p.body, p.url)
        return Rules.value(doc, rule.url, p.url)
    }

    // ---------- 内部：HTML / JSON 统一成 Item ----------

    private sealed interface Item

    private class HtmlItem(val el: Element) : Item

    private class JsonItem(val obj: JSONObject) : Item

    private fun listItems(p: Page, rule: String): List<Item> {
        val json = JsonRules.parse(p.body)
        return if (json != null) {
            JsonRules.array(json, rule).map { JsonItem(it) }
        } else {
            Rules.list(parseHtml(p.body, p.url), rule).map { HtmlItem(it) }
        }
    }

    /** 一条规则取一个值；`a|b` 表示按顺序取第一个非空的（regex 规则里可能自带 `|`，不拆） */
    private fun pick(item: Item, rule: String, base: String): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""
        if (r.startsWith("regex:") || !r.contains('|')) return one(item, r, base)
        for (part in r.split('|')) {
            val v = one(item, part.trim(), base)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun one(item: Item, rule: String, base: String): String = when (item) {
        is HtmlItem -> if (JsonRules.isJson(rule)) "" else Rules.value(item.el, rule, base)
        // 接口里图片常写成 //imagev2.xmcdn.com/... ，补个 scheme 才能给 Coil 加载
        is JsonItem -> JsonRules.value(item.obj, rule).let {
            if (it.startsWith("//")) "https:$it" else it
        }
    }

    private fun pickJson(root: Any?, rule: String?): String {
        if (rule.isNullOrBlank()) return ""
        return JsonRules.value(root, rule)
    }

    private fun toBooks(items: List<Item>, base: String, r: ListRules, source: BookSource): List<Book> =
        items.mapNotNull { item ->
            val bookUrl = pick(item, r.bookUrl, base)
            val name = pick(item, r.name, base)
            if (name.isBlank()) return@mapNotNull null
            Book(
                id = bookId(source, bookUrl.ifBlank { name }),
                sourceId = source.id,
                name = name,
                author = pick(item, r.author, base),
                cover = pick(item, r.cover, base),
                intro = pick(item, r.intro, base),
                url = bookUrl,
                lastChapter = pick(item, r.lastChapter, base),
            )
        }

    private data class ListRules(
        val list: String,
        val bookUrl: String,
        val name: String,
        val author: String,
        val cover: String,
        val intro: String,
        val lastChapter: String,
    ) {
        companion object {
            fun of(r: SearchRule) = ListRules(r.list, r.bookUrl, r.name, r.author, r.cover, r.intro, r.lastChapter)
            fun of(r: BrowseRule) = ListRules(r.list, r.bookUrl, r.name, r.author, r.cover, r.intro, r.lastChapter)
        }
    }

    private fun parseHtml(body: String, baseUri: String): Document =
        runCatching { Jsoup.parse(body, baseUri) }.getOrElse { Jsoup.parse(body) }

    /** 规则里的地址可能是相对路径，统一补全后再发请求 */
    private fun abs(source: BookSource, url: String): String =
        Rules.absolutize(source.baseUrl, url)

    private fun SampleBook.toBook(source: BookSource) = Book(
        id = bookId(source, name),
        sourceId = source.id,
        name = name,
        author = author,
        cover = cover,
        intro = intro,
        url = "",
    )

    private fun bookId(source: BookSource, key: String): String {
        val safe = key.hashCode().toUInt().toString(16)
        return "${source.id}_$safe"
    }
}
