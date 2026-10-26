package com.weige.tingshu.source

import org.jsoup.nodes.Element
import java.net.URI

/**
 * 书源规则引擎。
 *
 * 规则语法只有四种，够覆盖绝大多数站：
 * ```
 * .book-list li             取文本
 * a@href                    取属性（相对地址会自动补全）
 * .title@ownText            只要自己的文本，不含子节点
 * .intro@html               取内层 HTML（有些站简介里带 <br>）
 * regex:<pattern>           正则在整段 HTML 上跑，有捕获组就取第 1 组
 * ```
 * 选择器是 Jsoup 的 CSS 语法，浏览器控制台里怎么选，这里就怎么写。
 */
object Rules {

    private val PLAIN_PARTS = setOf("text", "ownText", "html", "outerHtml")
    private val ATTR_NAME = Regex("^[A-Za-z_:][-A-Za-z0-9_:.]*$")

    /** 把一个节点按规则取值；取不到返回空串，绝不抛异常（一个坏源不该让整个列表崩掉） */
    fun value(node: Element, rule: String, baseUrl: String = ""): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""

        if (r.startsWith("regex:")) {
            val body = node.outerHtml()
            return runCatching {
                val m = Regex(r.removePrefix("regex:"), RegexOption.DOT_MATCHES_ALL).find(body)
                    ?: return ""
                m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: m.value
            }.getOrDefault("").trim()
        }

        val at = r.lastIndexOf('@')
        val hasAt = at > 0 && (r.substring(at + 1) in PLAIN_PARTS || ATTR_NAME.matches(r.substring(at + 1)))
        val selector = if (hasAt) r.substring(0, at) else r
        val part = if (hasAt) r.substring(at + 1) else "text"

        val target = if (selector.isBlank()) node else node.selectFirst(selector) ?: return ""

        val raw = when (part) {
            "text" -> target.text()
            "ownText" -> target.ownText()
            "html" -> target.html()
            "outerHtml" -> target.outerHtml()
            else -> target.attr(part).ifBlank {
                // 懒加载图片常把真地址放 data-src / data-original
                when (part) {
                    "src" -> target.attr("data-src").ifBlank { target.attr("data-original") }
                    else -> ""
                }
            }
        }

        return if (part in PLAIN_PARTS) raw.trim() else {
            // 调用方没给基准地址时，用 Jsoup 解析时的 baseUri 兜底
            val base = baseUrl.ifBlank { node.baseUri() }
            absolutize(base, raw.trim())
        }
    }

    /** 列表：选出若干个节点 */
    fun list(root: Element, rule: String): List<Element> {
        if (rule.isBlank()) return emptyList()
        return runCatching { root.select(rule) }.getOrDefault(emptyList())
    }

    /** 相对地址补全。`//host/a` 这种协议相对地址也要照顾到。 */
    fun absolutize(baseUrl: String, link: String): String {
        val s = link.trim()
        if (s.isEmpty()) return ""
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (baseUrl.isBlank()) return s
        return runCatching {
            when {
                s.startsWith("//") -> URI(baseUrl).scheme + ":" + s
                else -> URI(baseUrl).resolve(s).toString()
            }
        }.getOrDefault(s)
    }

    /** `{{key}}` / `{{page}}` 占位替换 */
    fun template(url: String, keyword: String, page: Int): String {
        return url
            .replace("{{key}}", encode(keyword))
            .replace("{{keyword}}", encode(keyword))
            .replace("{{page}}", page.toString())
    }

    private fun encode(s: String): String =
        runCatching { java.net.URLEncoder.encode(s, "utf-8") }.getOrDefault(s)
}
