package com.weige.tingshu.source

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 接口用的取值规则。
 *
 * 很多站已经不给 HTML 了（喜马拉雅、猫耳这类都是纯接口），所以规则里加一种写法：
 * ```
 * json:$.data.result.response.docs     列表：一路走到一个数组
 * json:title                           取字段
 * json:album.cover                     取嵌套字段
 * json:list.0.url                      数组下标也支持
 * ```
 * 值为 null / 不存在时返回空串，和 HTML 规则的行为保持一致。
 */
object JsonRules {

    fun isJson(rule: String): Boolean = rule.trim().startsWith("json:")

    fun parse(body: String): Any? {
        val t = body.trimStart()
        return runCatching {
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> JSONObject(t)
                else -> null
            }
        }.getOrNull()
    }

    /** 按规则取出一个对象数组；取到的如果是单个对象就包成一条 */
    fun array(root: Any?, rule: String): List<JSONObject> {
        val node = nav(root, pathOf(rule)) ?: return emptyList()
        return when (node) {
            is JSONArray -> (0 until node.length()).mapNotNull { node.optJSONObject(it) }
            is JSONObject -> listOf(node)
            else -> emptyList()
        }
    }

    fun value(node: Any?, rule: String): String {
        val v = nav(node, pathOf(rule)) ?: return ""
        return when (v) {
            is String -> v
            is JSONObject -> v.toString()
            is JSONArray -> v.toString()
            else -> if (v === JSONObject.NULL) "" else v.toString()
        }
    }

    /** `json:$.a.b` 和 `json:a.b` 等价；开头的 `$` / `.` 都不影响 */
    private fun pathOf(rule: String): String =
        rule.trim().removePrefix("json:").trim().removePrefix("$").removePrefix(".")

    private fun nav(node: Any?, path: String): Any? {
        if (path.isEmpty()) return node
        var cur: Any? = node
        for (seg in path.split('.')) {
            if (seg.isEmpty()) continue
            cur = when (val n = cur) {
                is JSONObject -> if (n.has(seg)) n.opt(seg) else null
                is JSONArray -> seg.toIntOrNull()?.let { i -> if (i in 0 until n.length()) n.opt(i) else null }
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }
}
