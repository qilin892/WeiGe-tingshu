package com.weige.tingshu.source

/**
 * 封面地址处理。
 *
 * 喜马拉雅接口返回的是**缩略图**：搜索给 `columns=290`，专辑音轨接口更狠，给 `columns=140`。
 * 播放页封面要铺到 ~300dp（3 倍屏就是 900px），直接用它必然糊。
 *
 * 好消息是同一张图能让 CDN 现场缩放——把 `!` 后面的参数换成需要的大小即可，
 * 而且去掉参数就是原图（实测 960×960）。所以：
 *
 *  - 存进 [com.weige.tingshu.model.Book.cover] 的一律是「基地址」（无参数）；
 *  - 界面按自己需要的边长调 [at]，列表用小图、播放页用大图，各自命中各自尺寸的缓存。
 *
 * 实测 300→18KB、640→54KB、960→108KB（webp），列表里用大图会白白多下几百 KB。
 */
object Covers {

    private const val XM_CDN = "xmcdn.com"

    /** 基地址：去掉 CDN 缩放参数，协议补全（接口里常写成 //imagev2...） */
    fun base(url: String): String {
        val u = url.trim()
        if (u.isEmpty()) return ""

        val noParam = if (isXm(u)) u.substringBefore('!') else u
        return when {
            noParam.startsWith("//") -> "https:$noParam"
            noParam.startsWith("http://") && isXm(noParam) -> noParam.replaceFirst("http://", "https://")
            else -> noParam
        }
    }

    /** 取指定边长的图；不是喜马拉雅 CDN（比如演示源、别的站）就原样返回 */
    fun at(url: String, size: Int): String {
        val b = base(url)
        if (b.isEmpty() || !isXm(b)) return b
        val s = size.coerceIn(120, 1200)
        return "$b!op_type=3&columns=$s&rows=$s&magick=webp"
    }

    private fun isXm(u: String) = u.contains(XM_CDN)
}
