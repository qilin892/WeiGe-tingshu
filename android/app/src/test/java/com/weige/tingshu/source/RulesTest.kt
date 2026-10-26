package com.weige.tingshu.source

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则引擎的单测。
 *
 * 这部分是书源的「编译器」，一个取值 bug 会让所有源一起失效，
 * 所以它不依赖 Android，直接在 JVM 上跑。
 */
class RulesTest {

    private val html = """
        <html><head><meta charset="utf-8"></head><body>
          <div class="result-list">
            <li class="item">
              <a href="/book/123" class="title">活着</a>
              <span class="author">余华</span>
              <img class="pic" data-src="/img/123.jpg">
              <div class="desc">一个人和他<b>命运</b>之间的友情</div>
            </li>
            <li class="item">
              <a href="https://other.com/book/456" class="title">许三观卖血记</a>
              <span class="author">余华</span>
            </li>
          </div>
          <div id="info">时长：<em>12:34</em></div>
        </body></html>
    """.trimIndent()

    private val doc = Jsoup.parse(html, "https://demo.com/search")

    @Test
    fun `取文字`() {
        val first = Rules.list(doc, ".result-list li").first()
        assertEquals("活着", Rules.value(first, ".title@text"))
        assertEquals("余华", Rules.value(first, ".author@text"))
    }

    @Test
    fun `只取自己的文本`() {
        val first = Rules.list(doc, ".result-list li").first()
        // text() 会把子节点 b 的文字并进来
        assertEquals("一个人和他命运之间的友情", Rules.value(first, ".desc@text"))
        assertEquals("一个人和他之间的友情", Rules.value(first, ".desc@ownText"))
    }

    @Test
    fun `属性默认取 text 时不会串到属性上`() {
        val first = Rules.list(doc, ".result-list li").first()
        assertEquals("活着", Rules.value(first, "a"))
    }

    @Test
    fun `相对地址补全`() {
        val items = Rules.list(doc, ".result-list li")
        assertEquals("https://demo.com/book/123", Rules.value(items[0], "a@href"))
        // 绝对地址原样保留
        assertEquals("https://other.com/book/456", Rules.value(items[1], "a@href"))
    }

    @Test
    fun `懒加载图片回退到 data-src`() {
        val first = Rules.list(doc, ".result-list li").first()
        assertEquals("https://demo.com/img/123.jpg", Rules.value(first, "img@src"))
    }

    @Test
    fun `正则抽捕获组`() {
        assertEquals("12:34", Rules.value(doc, """regex:时长：<em>(\d+:\d+)</em>"""))
    }

    @Test
    fun `取不到返回空串而不是抛异常`() {
        val first = Rules.list(doc, ".result-list li").first()
        assertEquals("", Rules.value(first, ".nope@text"))
        assertEquals("", Rules.value(first, ""))
        assertEquals(emptyList<Any>(), Rules.list(doc, ".nope"))
    }

    @Test
    fun `模板占位与编码`() {
        val url = Rules.template("https://demo.com/s?q={{key}}&page={{page}}", "三体 全集", 2)
        assertTrue(url.contains("page=2"))
        assertTrue(url.contains("%E4%B8%89%E4%BD%93"))
        assertEquals("https://demo.com/s?q=&page=1", Rules.template("https://demo.com/s?q={{key}}&page={{page}}", "", 1))
    }

    @Test
    fun `协议相对地址按 https 补全`() {
        assertEquals(
            "https://cdn.demo.com/a.mp3",
            Rules.absolutize("https://demo.com/book/1", "//cdn.demo.com/a.mp3"),
        )
    }
}
