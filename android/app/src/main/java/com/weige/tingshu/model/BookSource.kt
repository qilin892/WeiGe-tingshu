package com.weige.tingshu.model

/**
 * 一个自定义书源。
 *
 * 结构对应 tingshu 里 `TingShu` 那套生命周期：搜索 / 详情 / 章节列表 / 音频地址，
 * 区别是不用写 Java 再打 jar —— 全部用 JSON 描述，App 里直接改。
 */
data class BookSource(
    val id: String,
    val name: String,
    val version: Int = 1,
    val enabled: Boolean = true,
    /** 内置源（来自 assets），删不掉，只能停用 */
    val builtin: Boolean = false,
    val baseUrl: String = "",
    /** 有些老站还是 gbk */
    val charset: String = "utf-8",
    val headers: Map<String, String> = emptyMap(),
    val search: SearchRule? = null,
    /** 分类页的列表规则。有些站的搜索被验证码挡住，只能靠分类翻 */
    val browse: BrowseRule? = null,
    val detail: DetailRule? = null,
    val audio: AudioRule = AudioRule(),
    val categories: List<CategoryRule> = emptyList(),
    /** 演示源：不走网络，直接给几本书，用来验证整条播放链路 */
    val sample: SampleData? = null,
)

data class CategoryRule(
    /** 分类名，App 里按名字展示并去重 */
    val name: String,
    /** 分类地址，支持 {{page}} 占位，例如 /book/13-{{page}}.html */
    val url: String,
)

/**
 * 搜索结果页规则。列表项先由 [list] 选出若干个节点，
 * 再在每个节点上用 [name] / [bookUrl] 等规则取值。
 */
data class SearchRule(
    /** 支持 {{key}} {{page}} 占位 */
    val url: String,
    val method: String = "GET",
    val list: String,
    val bookUrl: String = "a@href",
    val name: String = "a@text",
    val author: String = "",
    val cover: String = "",
    val intro: String = "",
    val lastChapter: String = "",
)

/** 分类列表页规则。字段含义同 [SearchRule]，只是入口换成分类地址。 */
data class BrowseRule(
    val list: String,
    val bookUrl: String = "a@href",
    val name: String = "a@text",
    val author: String = "",
    val cover: String = "",
    val intro: String = "",
    val lastChapter: String = "",
)

data class DetailRule(
    /**
     * 详情请求地址模板，支持 `{{bookUrl}}`。
     * 留空 = 直接请求 book.url（HTML 站就是这样）；
     * 接口源常用它把「专辑 id」拼成接口地址。
     */
    val url: String = "",
    val name: String = "",
    val author: String = "",
    val cover: String = "",
    val intro: String = "",
    val chapters: ChapterRule? = null,
)

data class ChapterRule(
    val list: String,
    val title: String = "a@text",
    /** 撑不住多值时用 `|` 分隔按顺序取第一个非空的，例如 `json:playUrl64|json:playPathAacv164` */
    val url: String = "a@href",
    /** 有的站章节是倒序的 */
    val reverse: Boolean = false,
    /** 接口源一页只给 20 条，上千章要靠翻页拿全 */
    val pages: PageRule? = null,
)

/**
 * 章节分页。先请求第一页，用 [max] 规则读出总页数，再从 [start] 翻到 [max]。
 * [url] 里的 `{{p}}` 是页码，`{{bookUrl}}` 是书标识。
 */
data class PageRule(
    val url: String,
    val max: String = "",
    val start: Int = 1,
    /** 上限保护：一章不落地地翻会让用户等太久 */
    val maxPages: Int = 60,
)

/**
 * 音频规则。
 * [url] 留空时直接把章节 url 当音频地址（适合章节链接本身就是 mp3 的站）。
 */
data class AudioRule(
    val url: String = "",
    val referer: String = "",
    val headers: Map<String, String> = emptyMap(),
)

data class SampleData(val books: List<SampleBook> = emptyList())

data class SampleBook(
    val name: String,
    val author: String = "",
    val cover: String = "",
    val intro: String = "",
    val chapters: List<SampleChapter> = emptyList(),
)

data class SampleChapter(val title: String, val url: String)
