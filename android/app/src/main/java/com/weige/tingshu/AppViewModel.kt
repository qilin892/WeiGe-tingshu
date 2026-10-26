package com.weige.tingshu

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weige.tingshu.data.LocalStore
import com.weige.tingshu.model.Book
import com.weige.tingshu.model.BookSource
import com.weige.tingshu.model.Chapter
import com.weige.tingshu.model.PlayMode
import com.weige.tingshu.play.PlayState
import com.weige.tingshu.play.PlayerController
import com.weige.tingshu.source.BuiltinSources
import com.weige.tingshu.source.Providers
import com.weige.tingshu.source.Sources
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全部界面状态。
 *
 * 不引 Navigation 库，页面就是几个状态位：tab 决定底部页签，
 * book 不为空显示详情，showPlayer 为 true 盖一页播放器。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- 全局 ----------
    var dark by mutableStateOf(false)
        private set
    var tab by mutableStateOf(0)
        private set

    /** 一次性提示，App 里 2 秒后自动清掉 */
    var toast by mutableStateOf<String?>(null)
        private set

    // ---------- 书架 ----------
    var shelf by mutableStateOf<List<Book>>(emptyList())
        private set
    var lastBook by mutableStateOf<Book?>(null)
        private set

    // ---------- 搜索 ----------
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<Book>>(emptyList())
        private set
    var searchMsg by mutableStateOf<String?>(null)
        private set
    var sources by mutableStateOf<List<BookSource>>(emptyList())
        private set

    // ---------- 详情 ----------
    var book by mutableStateOf<Book?>(null)
        private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailMsg by mutableStateOf<String?>(null)
        private set

    // ---------- 播放 ----------
    var play by mutableStateOf(PlayState())
        private set
    var showPlayer by mutableStateOf(false)
        private set
    var playingChapters by mutableStateOf<List<Chapter>>(emptyList())
        private set

    /** 睡眠定时：到点时间戳，0 表示没开 */
    var sleepUntil by mutableStateOf(0L)
        private set
    var sleepAtChapterEnd by mutableStateOf(false)
        private set

    /** 播完本章就停（章节列表里点了「听本章」时用不到，保留给定时器） */
    private var sleepAtEnd = false
    private var sleepJob: Job? = null
    private var autoPlayOnLoad = false
    private var pendingOpenPlayer = false

    init {
        val ctx = getApplication<Application>()
        LocalStore.init(ctx)
        Sources.init(ctx)
        // 演示源、备用源已经下线：书架和「继续收听」里残留的条目一起清掉
        LocalStore.dropBooksOf(BuiltinSources.retired)
        PlayerController.connect(ctx)

        dark = LocalStore.dark
        PlayerController.setPlayMode(LocalStore.playMode)
        PlayerController.setSpeed(LocalStore.speed)

        viewModelScope.launch {
            PlayerController.state.collect {
                play = it
                // 从通知栏点进来的：状态要等控制器连上才有，所以挂个标记等它
                if (pendingOpenPlayer && it.chapter != null) {
                    pendingOpenPlayer = false
                    showPlayer = true
                }
            }
        }
        viewModelScope.launch { LocalStore.shelf.collect { shelf = it } }
        viewModelScope.launch { LocalStore.lastBook.collect { lastBook = it } }
        // 源是写死的，这里只是把它读到内存，界面不再有「书源管理」入口
        viewModelScope.launch { Sources.all.collect { sources = it } }

        PlayerController.onChapterChanged = {
            if (sleepAtEnd) {
                PlayerController.pause()
                sleepAtEnd = false
                sleepAtChapterEnd = false
                say("本章播完，已暂停")
            }
        }
    }

    // ---------- 小工具 ----------

    fun say(msg: String) {
        toast = msg
    }

    fun clearToast() {
        toast = null
    }

    fun toggleDark() {
        dark = !dark
        LocalStore.dark = dark
    }

    fun selectTab(i: Int) {
        tab = i
        book = null
    }

    // ---------- 书架 ----------

    fun onShelf(b: Book): Boolean = shelf.any { it.id == b.id }

    fun toggleShelf(b: Book) {
        val added = LocalStore.toggleShelf(b)
        say(if (added) "已加入书架" else "已移出书架")
    }

    fun listenedCount(bookId: String): Int = LocalStore.listenedCount(bookId)

    fun progressOf(bookId: String): Int = LocalStore.lastIndex(bookId)

    // ---------- 搜索 ----------

    fun search() {
        val kw = query.trim()
        if (kw.isEmpty()) {
            searchMsg = "输入书名或主播再搜"
            return
        }
        // 源是写死的：只要启用就用它搜，界面上没有源可勾
        val srcs = sources.filter { it.enabled && Providers.canSearch(it) }
        if (srcs.isEmpty()) {
            searchMsg = "内置源被停用了，重启一下 App 即可恢复"
            return
        }

        searching = true
        results = emptyList()
        searchMsg = null

        viewModelScope.launch {
            val acc = ArrayList<Book>()
            var failed = 0
            srcs.forEach { src ->
                runCatching { Providers.search(src, kw, 1) }
                    .onSuccess { acc.addAll(it) }
                    .onFailure { failed++ }
            }
            results = acc.distinctBy { it.id }
            searching = false
            searchMsg = when {
                acc.isEmpty() && failed > 0 -> "没搜到结果，其中 $failed 个源请求失败"
                acc.isEmpty() -> "没搜到，换个关键词或换一个源"
                else -> null
            }
        }
    }

    // ---------- 详情 ----------

    fun openBook(b: Book, autoPlay: Boolean = false) {
        book = b
        chapters = emptyList()
        detailMsg = null
        detailLoading = true
        autoPlayOnLoad = autoPlay
        showPlayer = false

        viewModelScope.launch {
            val src = Sources.get(b.sourceId)
            if (src == null) {
                detailMsg = "书源已被删除，换一个源搜这本书"
                detailLoading = false
                return@launch
            }
            runCatching { Providers.detail(src, b) }
                .onSuccess { d ->
                    book = d.book
                    chapters = d.chapters
                    if (d.chapters.isEmpty()) {
                        detailMsg = if (Providers.isNative(b.sourceId)) {
                            "这本没有免费音轨（付费或会员专辑），换一本试试"
                        } else {
                            "没解析到章节，检查这个源 detail.chapters 的规则"
                        }
                    } else if (autoPlay) {
                        playIndex(LocalStore.lastIndex(d.book.id).coerceIn(0, d.chapters.size - 1), openPlayer = true)
                    }
                }
                .onFailure { e ->
                    detailMsg = "加载失败：${e.message ?: e.javaClass.simpleName}"
                }
            detailLoading = false
            autoPlayOnLoad = false
        }
    }

    fun closeBook() {
        book = null
        chapters = emptyList()
        detailMsg = null
    }

    /** 外部链接 `weigetingshu://album/<id>` 直接打开某张专辑 */
    fun openAlbumById(albumId: String) {
        if (albumId.isBlank()) return
        val src = BuiltinSources.ximalaya
        tab = 1
        openBook(
            Book(
                id = "${src.id}_$albumId",
                sourceId = src.id,
                name = "加载中…",
                url = albumId,
            )
        )
    }

    // ---------- 播放 ----------

    fun playIndex(index: Int, openPlayer: Boolean = true) {
        val b = book ?: return
        if (index !in chapters.indices) return
        val start = LocalStore.position(b.id, index)
        playingChapters = chapters
        PlayerController.playQueue(b, chapters, index, start)
        if (openPlayer) showPlayer = true
    }

    fun playChapterOfPlaying(ch: Chapter) {
        PlayerController.playChapter(ch)
    }

    fun togglePlay() = PlayerController.toggle()

    fun pause() = PlayerController.pause()

    fun next() = PlayerController.next()

    fun prev() = PlayerController.previous()

    fun seekTo(ms: Long) = PlayerController.seekTo(ms)

    fun seekBy(delta: Long) = PlayerController.seekBy(delta)

    fun openPlayer() {
        if (play.chapter == null) {
            pendingOpenPlayer = true
        } else {
            showPlayer = true
        }
    }

    fun closePlayer() {
        showPlayer = false
    }

    fun cyclePlayMode() {
        val next = when (play.playMode) {
            PlayMode.SEQUENCE -> PlayMode.LOOP_ONE
            PlayMode.LOOP_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SEQUENCE
        }
        PlayerController.setPlayMode(next)
        LocalStore.playMode = next
        say(
            when (next) {
                PlayMode.SEQUENCE -> "顺序播放"
                PlayMode.LOOP_ONE -> "单章循环"
                PlayMode.SHUFFLE -> "随机播放"
            }
        )
    }

    fun setSpeed(s: Float) {
        PlayerController.setSpeed(s)
        say("${trimSpeed(s)}x")
    }

    /** 睡眠定时：0 关，正数是分钟数 */
    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepAtEnd = false
        sleepAtChapterEnd = false
        if (minutes <= 0) {
            sleepUntil = 0
            say("已关闭定时")
            return
        }
        val ms = minutes * 60_000L
        sleepUntil = System.currentTimeMillis() + ms
        say("$minutes 分钟后暂停")
        sleepJob = viewModelScope.launch {
            delay(ms)
            PlayerController.pause()
            sleepUntil = 0
            say("定时结束，已暂停")
        }
    }

    /** 本章播完就停（跟分钟定时互斥） */
    fun stopAtChapterEnd() {
        sleepJob?.cancel()
        sleepUntil = 0
        sleepAtEnd = true
        sleepAtChapterEnd = true
        say("本章播完就停")
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepUntil = 0
        sleepAtEnd = false
        sleepAtChapterEnd = false
        say("已取消定时")
    }

    fun remainingSleepText(): String {
        if (sleepUntil <= 0) return ""
        val left = sleepUntil - System.currentTimeMillis()
        if (left <= 0) return ""
        val m = (left / 60_000).toInt()
        return if (m >= 1) "${m}分后停" else "不到1分"
    }

    fun trimSpeed(s: Float): String =
        if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()
}
