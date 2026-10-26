package com.weige.tingshu.data

import android.content.Context
import android.content.SharedPreferences
import com.weige.tingshu.model.Book
import com.weige.tingshu.model.PlayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储：书架 / 收听进度 / 播放偏好。
 *
 * 有声书最不能少的就是进度记忆，所以这里独立成一个模块，
 * 不跟播放器耦合——播放器只负责「告诉我要存哪儿」。
 *
 * 键结构：
 *  - `shelf`     书架（Book 数组）
 *  - `last`      上次在听的书（Book）
 *  - `bk_<id>`   单本书的进度 { "idx": 3, "pos": {"3": 125000} }
 */
object LocalStore {

    private lateinit var prefs: SharedPreferences

    private val _shelf = MutableStateFlow<List<Book>>(emptyList())
    val shelf: StateFlow<List<Book>> = _shelf.asStateFlow()

    private val _lastBook = MutableStateFlow<Book?>(null)
    val lastBook: StateFlow<Book?> = _lastBook.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("ting", Context.MODE_PRIVATE)
        _shelf.value = loadBooks("shelf")
        _lastBook.value = loadBooks("last").firstOrNull()
    }

    // ---------- 书架 ----------

    fun isOnShelf(bookId: String): Boolean = _shelf.value.any { it.id == bookId }

    fun toggleShelf(book: Book): Boolean {
        val list = _shelf.value.toMutableList()
        val i = list.indexOfFirst { it.id == book.id }
        val added: Boolean
        if (i >= 0) {
            list.removeAt(i)
            added = false
        } else {
            list.add(0, book)
            added = true
        }
        persistBooks("shelf", list)
        _shelf.value = list
        return added
    }

    /** 开播时把这本书提到书架最前面（不在书架就不动） */
    fun touchShelf(book: Book) {
        val cur = _shelf.value
        if (cur.none { it.id == book.id }) return
        val list = mutableListOf(book)
        list.addAll(cur.filterNot { it.id == book.id })
        persistBooks("shelf", list)
        _shelf.value = list
    }

    // ---------- 上次在听 ----------

    fun setLastBook(book: Book) {
        persistBooks("last", listOf(book))
        _lastBook.value = book
    }

    /** 源下线后，把书架和「上次在听」里属于它的书清掉，免得点开就是「源已删除」 */
    fun dropBooksOf(sourceIds: Set<String>) {
        if (sourceIds.isEmpty() || !::prefs.isInitialized) return

        val kept = _shelf.value.filterNot { it.sourceId in sourceIds }
        if (kept.size != _shelf.value.size) {
            persistBooks("shelf", kept)
            _shelf.value = kept
        }

        val lastBook = _lastBook.value
        if (lastBook != null && lastBook.sourceId in sourceIds) {
            persistBooks("last", emptyList())
            _lastBook.value = null
        }
    }

    // ---------- 进度 ----------

    private fun bookKey(bookId: String) = "bk_$bookId"

    fun saveProgress(bookId: String, index: Int, positionMs: Long) {
        val obj = runCatching { JSONObject(prefs.getString(bookKey(bookId), "{}").orEmpty()) }
            .getOrDefault(JSONObject())
        runCatching {
            obj.put("idx", index)
            val pos = obj.optJSONObject("pos") ?: JSONObject()
            pos.put(index.toString(), positionMs.coerceAtLeast(0))
            obj.put("pos", pos)
            // 只留最近 40 章的进度，几百章的书全存下去 prefs 会很难看
            if (pos.length() > 60) {
                val keep = (index - 40..index + 20).filter { pos.has(it.toString()) }
                val trimmed = JSONObject()
                keep.forEach { trimmed.put(it.toString(), pos.optLong(it.toString())) }
                obj.put("pos", trimmed)
            }
            prefs.edit().putString(bookKey(bookId), obj.toString()).apply()
        }
    }

    fun lastIndex(bookId: String): Int =
        runCatching { JSONObject(prefs.getString(bookKey(bookId), "{}").orEmpty()).optInt("idx", 0) }
            .getOrDefault(0)

    fun position(bookId: String, index: Int): Long = runCatching {
        JSONObject(prefs.getString(bookKey(bookId), "{}").orEmpty())
            .optJSONObject("pos")
            ?.optLong(index.toString(), 0L) ?: 0L
    }.getOrDefault(0L)

    /** 这本书已经听过几章（书架卡片上显示「听到第 N 章」） */
    fun listenedCount(bookId: String): Int = lastIndex(bookId) + 1

    fun clearProgress(bookId: String) {
        prefs.edit().remove(bookKey(bookId)).apply()
    }

    // ---------- 偏好 ----------

    var dark: Boolean
        get() = prefs.getBoolean("dark", false)
        set(v) = prefs.edit().putBoolean("dark", v).apply()

    var playMode: PlayMode
        get() = runCatching { PlayMode.valueOf(prefs.getString("mode", "SEQUENCE")!!) }
            .getOrDefault(PlayMode.SEQUENCE)
        set(v) = prefs.edit().putString("mode", v.name).apply()

    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(v) = prefs.edit().putFloat("speed", v).apply()

    var sleepMinutes: Int
        get() = prefs.getInt("sleep", 0)
        set(v) = prefs.edit().putInt("sleep", v).apply()

    // ---------- JSON 串化 ----------

    private fun loadBooks(key: String): List<Book> = runCatching {
        val text = prefs.getString(key, "[]").orEmpty()
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Book(
                id = o.optString("id"),
                sourceId = o.optString("sourceId"),
                name = o.optString("name"),
                author = o.optString("author", ""),
                cover = o.optString("cover", ""),
                intro = o.optString("intro", ""),
                url = o.optString("url", ""),
                lastChapter = o.optString("lastChapter", ""),
            )
        }
    }.getOrDefault(emptyList())

    private fun persistBooks(key: String, list: List<Book>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("sourceId", b.sourceId)
                    put("name", b.name)
                    put("author", b.author)
                    put("cover", b.cover)
                    put("intro", b.intro)
                    put("url", b.url)
                    put("lastChapter", b.lastChapter)
                }
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
