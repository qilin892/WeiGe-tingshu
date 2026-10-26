package com.weige.tingshu.source

import android.content.Context
import com.weige.tingshu.model.BookSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * 书源仓库：内存一份、磁盘一份。
 *
 * 跟 tingshu 的「jar 丢进私有目录、启动时 DexClassLoader 加载」不同，
 * 这里存的是 JSON 规则，改完立即生效，不用打包、也不用重启。
 * 订阅更新留了 version 字段，以后要做「源列表托管在远端」直接用它比版本号。
 */
object Sources {

    private lateinit var app: Context

    val fetcher = Fetcher()
    val engine = SourceEngine(fetcher)

    private val _all = MutableStateFlow<List<BookSource>>(emptyList())
    val all: StateFlow<List<BookSource>> = _all.asStateFlow()

    private val _enabled = MutableStateFlow<List<BookSource>>(emptyList())
    val enabled: StateFlow<List<BookSource>> = _enabled.asStateFlow()

    private val file: File get() = File(app.filesDir, "sources.json")

    fun init(context: Context) {
        app = context.applicationContext
        if (_all.value.isNotEmpty()) return

        val loaded = if (file.exists()) {
            runCatching { BookSourceJson.parseList(file.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // 内置源（写死在代码里的接口源）始终在列表里，旧的已存数据也会被补上
        val list = BuiltinSources.merge(if (loaded.isEmpty()) seedFromAssets() else loaded)
        persist(list)
        publish(list)
    }

    fun get(id: String): BookSource? = _all.value.firstOrNull { it.id == id }

    fun upsert(source: BookSource) {
        val list = _all.value.toMutableList()
        val i = list.indexOfFirst { it.id == source.id }
        if (i >= 0) list[i] = source else list.add(source)
        persistAndPublish(list)
    }

    fun remove(id: String) {
        persistAndPublish(_all.value.filterNot { it.id == id })
    }

    fun setEnabled(id: String, on: Boolean) {
        persistAndPublish(_all.value.map { if (it.id == id) it.copy(enabled = on) else it })
    }

    /** 导入：返回成功条数；解析不出东西就抛异常，由 UI 报给用户 */
    fun importText(text: String): Int {
        val parsed = BookSourceJson.parseList(text)
        if (parsed.isEmpty()) throw IllegalArgumentException("没解析出任何书源")
        val list = _all.value.toMutableList()
        parsed.forEach { src ->
            val i = list.indexOfFirst { it.id == src.id }
            if (i >= 0) list[i] = src.copy(builtin = list[i].builtin) else list.add(src)
        }
        persistAndPublish(list)
        return parsed.size
    }

    fun exportJson(): String = BookSourceJson.toArray(_all.value).toString(2)

    private fun seedFromAssets(): List<BookSource> = runCatching {
        val text = app.assets.open("sources.json").bufferedReader().use { it.readText() }
        BookSourceJson.parseList(text).map { it.copy(builtin = true) }
    }.getOrDefault(emptyList())

    private fun persistAndPublish(list: List<BookSource>) {
        persist(list)
        publish(list)
    }

    private fun persist(list: List<BookSource>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(BookSourceJson.toArray(list).toString())
        }
    }

    private fun publish(list: List<BookSource>) {
        _all.value = list
        _enabled.value = list.filter { it.enabled }
    }

    /** 导出成数组给用户看 */
    fun dump(list: List<BookSource> = _all.value): String = JSONArray().apply {
        list.forEach { put(BookSourceJson.toJson(it)) }
    }.toString(2)
}
