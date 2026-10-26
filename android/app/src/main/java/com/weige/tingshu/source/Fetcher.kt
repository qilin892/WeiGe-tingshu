package com.weige.tingshu.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 一次抓取的结果：正文 + 重定向之后的真实地址（相对链接要用它当 base） */
data class Page(val body: String, val url: String, val contentType: String = "")

/**
 * 极简抓取层。
 *
 * 只用 OkHttp 一个依赖，不做缓存池——书源换得勤，缓存反倒容易串味。
 * 网络请求全部丢到 IO 线程，规则解析留在调用方线程。
 */
class Fetcher {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String = "",
        charset: String = "utf-8",
    ): Page = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        // 有些站什么都不带就返回 403，给个像浏览器的 UA 兜底
        builder.header("User-Agent", UA)
        builder.header("Accept", "*/*")
        if (referer.isNotBlank()) builder.header("Referer", referer)
        headers.forEach { (k, v) -> if (v.isNotBlank()) builder.header(k, v) }

        client.newCall(builder.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val cs = when {
                charset.isNotBlank() && !charset.equals("auto", true) -> charset
                else -> sniffCharset(bytes)
            }
            val body = String(bytes, java.nio.charset.Charset.forName(cs))
            Page(body, resp.request.url.toString(), resp.header("Content-Type").orEmpty())
        }
    }

    /** 从 HTML 头部的 meta 猜编码，猜不到按 utf-8 */
    private fun sniffCharset(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 2048), java.nio.charset.Charset.forName("iso-8859-1"))
        val m = Regex("charset=[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(head)
        val name = m?.groupValues?.get(1)?.lowercase()
        return when (name) {
            "gb2312", "gbk" -> "gbk"
            "big5" -> "big5"
            else -> "utf-8"
        }
    }

    fun headOk(url: String, referer: String = ""): Boolean = runCatching {
        val b = Request.Builder().url(url).head().header("User-Agent", UA)
        if (referer.isNotBlank()) b.header("Referer", referer)
        client.newCall(b.build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** 封面等二进制，给通知栏用 */
    suspend fun bytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
                .execute().use { it.body?.bytes() }
        }.getOrNull()
    }

    companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
