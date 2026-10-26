package com.weige.tingshu.source

import com.weige.tingshu.model.BookSource
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * 网络源体检工具（不是断言测试，是把结果打出来看）。
 *
 * 用法：
 * ```
 * ./gradlew testDebugUnitTest --tests "*SourceProbeTest*" -i | grep PROBE
 * ```
 * 它读的是 App 内置的 `assets/sources.json`，按「分类 → 详情 → 章节 → 音频」
 * 四步真实跑一遍，每步报条数、耗时、第一行样例。
 *
 * 书源可用性天天变，这份清单比结论更有用的是工具：拿到任何新源，
 * 先粘进 assets 跑一次，就知道该不该花时间。
 */
class SourceProbeTest {

    private val fetcher = Fetcher()
    private val engine = SourceEngine(fetcher)

    @Test
    fun probe() {
        val file = listOf(
            File("src/main/assets/sources.json"),
            File("app/src/main/assets/sources.json"),
        ).firstOrNull { it.exists() } ?: run {
            println("PROBE  找不到 sources.json，工作目录=${File(".").absolutePath}")
            return
        }

        val sources = BookSourceJson.parseList(file.readText())
        println("PROBE 共 ${sources.size} 个规则源：${file.absolutePath}")

        sources.filter { it.sample == null }.forEach { src ->
            println("PROBE ──────────────────────────────")
            println("PROBE 【${src.name}】${src.baseUrl}")
            probeOne(src)
        }

        probeNative()
    }

    /** 内置接口源（写死在代码里那份）单独跑一遍，用的是 App 里同一个 Providers 入口 */
    private fun probeNative() {
        val src = BuiltinSources.ximalaya
        println("PROBE ──────────────────────────────")
        println("PROBE 【${src.name}】内置接口源，走 Providers / Ximalaya")

        val t0 = System.currentTimeMillis()
        val books = runCatching { runBlocking { Providers.search(src, "三国", 1) } }
        val ms = System.currentTimeMillis() - t0
        books.onFailure { println("PROBE   搜索 ✗ ${it.javaClass.simpleName}: ${it.message}") }
        books.onSuccess { list ->
            if (list.isEmpty()) {
                println("PROBE   搜索 ✗ 0 条")
                return
            }
            println("PROBE   搜索 ✓ ${list.size} 条 / ${ms}ms → ${list.first().name}（${list.first().author}）")

            val book = list.first()
            val t1 = System.currentTimeMillis()
            val detail = runCatching { runBlocking { Providers.detail(src, book) } }
            val ms1 = System.currentTimeMillis() - t1
            detail.onFailure { println("PROBE   章节 ✗ ${it.javaClass.simpleName}: ${it.message}") }
            detail.onSuccess { d ->
                if (d.chapters.isEmpty()) {
                    println("PROBE   章节 ✗ 0 章（可能是付费专辑，换个关键词试）")
                    return
                }
                println("PROBE   章节 ✓ ${d.chapters.size} 章 / ${ms1}ms → ${d.chapters.first().title}")

                val ch = d.chapters.first()
                val url = runCatching { runBlocking { Providers.audioUrl(src, ch) } }.getOrDefault("")
                if (url.isBlank()) {
                    println("PROBE   音频 ✗ 空地址")
                    return
                }
                println("PROBE   音频 ✓ 直链 → ${url.take(80)}")
                val ok = runCatching { fetcher.headOk(url) }.getOrDefault(false)
                println("PROBE   直链可达 ${if (ok) "✓" else "✗"}")
            }
        }
    }

    private fun probeOne(src: BookSource) {
        // 1. 入口：有分类先走分类，没有分类就搜一个固定词
        val cat = src.categories.firstOrNull()
        val label = when {
            cat != null -> "分类「${cat.name}」"
            src.search != null -> "搜索「三国」"
            else -> {
                println("PROBE   ✗ 既没有 categories 也没有 search，跳过")
                return
            }
        }

        val t0 = System.currentTimeMillis()
        val books = runCatching {
            runBlocking {
                if (cat != null) engine.browse(src, cat.url, 1) else engine.search(src, "三国", 1)
            }
        }
        val ms = System.currentTimeMillis() - t0

        books.onFailure { println("PROBE   $label ✗ ${it.javaClass.simpleName}: ${it.message}") }
        books.onSuccess { list ->
            if (list.isEmpty()) {
                println("PROBE   $label ✗ 0 条（页面取到了但规则没选中，或被验证码拦住）")
                return
            }
            println("PROBE   $label ✓ ${list.size} 条 / ${ms}ms → ${list.first().name}（${list.first().author}）")

            // 2. 详情 + 章节
            val book = list.first()
            val t1 = System.currentTimeMillis()
            val detail = runCatching { runBlocking { engine.detail(src, book) } }
            val ms1 = System.currentTimeMillis() - t1
            detail.onFailure { println("PROBE   详情 ✗ ${it.javaClass.simpleName}: ${it.message}") }
            detail.onSuccess { d ->
                if (d.chapters.isEmpty()) {
                    println("PROBE   章节 ✗ 0 章（检查 detail.chapters 规则）")
                    return
                }
                println("PROBE   章节 ✓ ${d.chapters.size} 章 / ${ms1}ms → ${d.chapters.first().title}")

                // 3. 音频直链
                val ch = d.chapters.first()
                val t2 = System.currentTimeMillis()
                val url = runCatching { runBlocking { engine.audioUrl(src, ch) } }
                val ms2 = System.currentTimeMillis() - t2
                url.onFailure { println("PROBE   音频 ✗ ${it.javaClass.simpleName}: ${it.message}") }
                url.onSuccess { u ->
                    if (u.isBlank()) {
                        println("PROBE   音频 ✗ 空地址（检查 audio.url 规则）")
                        return@onSuccess
                    }
                    println("PROBE   音频 ✓ ${ms2}ms → ${u.take(80)}")

                    // 4. 真去拉一下，确认不是 403 / 404
                    val head = runCatching { fetcher.headOk(u, src.audio.referer) }.getOrDefault(false)
                    println("PROBE   直链可达 ${if (head) "✓" else "✗（HEAD 可能不支持，用 GET 再验）"}")
                    if (!head) {
                        val bytes = runCatching { runBlocking { fetcher.bytes(u) } }.getOrNull()
                        println("PROBE   GET 回退 ${if (bytes != null && bytes.isNotEmpty()) "✓ ${bytes.size}B" else "✗"}")
                    }
                }
            }
        }
    }
}
