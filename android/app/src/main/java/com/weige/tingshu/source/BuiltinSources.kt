package com.weige.tingshu.source

import com.weige.tingshu.model.BookSource

/**
 * 写死在 App 里的源。
 *
 * 这些源没有规则，代码就是它们的实现（见 [Providers] / [Ximalaya]）；
 * 这里只留一个壳，让「发现页的源筛选」和「书源页的列表」有东西可显示。
 * 好处是用户改不动、删不掉、也不存在配置错了就不工作的问题。
 */
object BuiltinSources {

    val ximalaya = BookSource(
        id = Ximalaya.SOURCE_ID,
        name = "喜马拉雅",
        version = 1,
        enabled = true,
        builtin = true,
        baseUrl = "https://www.ximalaya.com/",
    )

    val all: List<BookSource> = listOf(ximalaya)

    /** 已经下线的源：老版本存过它们的书架/进度，升级时要清掉，免得点开就是「源已删除」 */
    val retired: Set<String> = setOf("demo", "tingshuw")

    /**
     * 把内置源并进已保存的列表：
     *  - 剔掉已下线的源；
     *  - 缺了补上；
     *  - 有了就保留用户自己的启用/停用状态，其余字段以代码为准（改名、升版本自动生效）。
     */
    fun merge(list: List<BookSource>): List<BookSource> {
        val out = list.filterNot { it.id in retired }.toMutableList()
        all.forEach { builtin ->
            val i = out.indexOfFirst { it.id == builtin.id }
            if (i < 0) {
                out.add(0, builtin)
            } else {
                out[i] = builtin.copy(enabled = out[i].enabled)
            }
        }
        return out
    }
}
