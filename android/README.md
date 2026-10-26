# 伟哥听书 · Android

免费有声小说的手机端播放器。**书源自己写、自己装**，App 本身不含任何站点。

技术栈沿用 [伟哥音乐](https://github.com/qilin892/WeiGe-music-player) 那一套（Kotlin + Compose + Media3），
播放内核、通知、进度记忆都是同一个路子；数据层换成了「书源规则 + 章节队列」。

---

## 一、做了什么

| 能力 | 说明 |
|---|---|
| 品牌 | 首页抬头、详情页、播放页都写着「伟哥听书」；没有独立的「关于」页 |
| 书架 | 收藏、继续收听卡、每本书记住听到第几章 |
| 搜索 | 只搜内置的喜马拉雅接口，付费专辑在数据层就滤掉，列表里不会出现 |
| 章节列表 | 专辑音轨自动翻页取全（一页 20 条，最多 60 页），点哪章听哪章 |
| 后台播放 | `MediaSessionService` 接管通知栏、锁屏、耳机键 |
| 断点续播 | 按 `书Id + 章节序号` 存毫秒级进度，退出、切章、每 5 秒各存一次 |
| 倍速 | 0.5× ~ 3×，重进 App 仍是上次的档位 |
| 睡眠定时 | 15/30/60/90 分钟，或「本章播完就停」 |
| 播放模式 | 顺序 / 单章循环 / 随机 |
| 深链 | `weigetingshu://album/<albumId>` 直接打开专辑，可分享 |
| 源体检 | `./gradlew testDebugUnitTest --tests "*SourceProbeTest*"` 跑「搜索 → 章节 → 音频 → 直链可达」 |
| 深浅色 | 夜间暖黑 + 琥珀，白天米白 + 深琥珀（首页右上角切换） |

**没做**（下一步）：整本批量下载、离线缓存清理、搜索翻页。

> v1.3.0 起**移除了「书源」页**：源只有一个且写死在代码里，留个管理页只会让用户以为需要配置。
> 规则源那套引擎还在代码里（`SourceEngine` + `JsonRules` + `BookSourceJson`），但界面不暴露。

### 图标

**一本书，封面上刻着 W**——W 是「伟」的首字母，3:4 的圆角封面 + 右上角三条书页线说明它是本书。
暖橙 `#FF7A18` 配 `#0A0908` 底色，跟界面同一套色。

- `res/drawable/ic_launcher_foreground.xml`：几何（108×108 坐标系，落在 66dp 安全区内）
- `res/drawable/ic_launcher_monochrome.xml`：Android 13 主题图标用的单色层（书画描边，不然实心封面会把 W 糊成一坨）
- `tools/render_icon.py`：同一套坐标的本地预览，能看「圆形遮罩下会不会被裁」——改图标先跑它，别直接装到手机上试

---

## 二、书源：为什么不是 jar

[tingshu](https://github.com/eprendre/tingshu) 的自定义源是「写一个 `TingShu` 子类 → 打成 jar → d8 转 dex → 丢进私有目录 → 启动时 DexClassLoader 加载」。
那套机制对源作者要求高（得有 JDK、会 Kotlin、会打包），而且 Android 高版本对动态加载越来越紧。

这里保留它**同样的生命周期**（搜索 / 详情 / 章节列表 / 音频地址 + version 版本号），
但把「代码」换成「规则」：

- 一个源 = 一段 JSON，App 里直接改、直接试，改完立即生效；
- 规则只有四种写法，会 CSS 选择器就能写（浏览器 F12 里怎么选，这里就怎么写）；
- 分发方式从「发 jar」变成「发一段 JSON / 一个链接」。

规则语法：

| 写法 | 含义 |
|---|---|
| `.book-list li` | 取该节点的文字 |
| `a@href` | 取属性，相对地址自动按页面地址补全 |
| `.intro@ownText` | 只要自己的文本，不含子节点 |
| `.intro@html` | 取内层 HTML |
| `regex:(\d+)` | 在整段 HTML 上跑正则，有捕获组取第 1 组 |

图片懒加载会自动回退到 `data-src` / `data-original`。

### 源字段

```jsonc
{
  "id": "my_source",              // 唯一，章节里靠它回查源
  "name": "我的源",
  "version": 1,                   // 留给以后做远端订阅比版本
  "enabled": true,
  "baseUrl": "https://例子.com",   // 相对链接的基准，也是默认 Referer
  "charset": "utf-8",             // 老站可能是 gbk
  "headers": {},                  // 额外的请求头

  "search": {
    "url": "https://例子.com/search?q={{key}}&page={{page}}",
    "list": ".result-list li",    // 列表项选择器
    "bookUrl": "a@href",
    "name": ".title@text",
    "author": ".author@text",
    "cover": "img@data-src",
    "intro": ".desc@text",
    "lastChapter": ".new@text"
  },

  "detail": {
    "name": "h1@text",
    "author": ".author@text",
    "cover": ".cover img@src",
    "intro": ".intro@text",
    "chapters": {
      "list": "#playlist li",
      "title": "a@text",
      "url": "a@href",
      "reverse": false            // 章节倒序的站打开
    }
  },

  "audio": {
    // 留空 = 章节链接本身就是要播的音频地址
    "url": "audio@src",
    "referer": "https://例子.com/",
    "headers": {}
  }
}
```

`{{key}}` 会被 URL 编码后替换成关键词，`{{page}}` 换成页码。

### 两种源：网页 和 JSON 接口

规则按**响应正文**自动分派：以 `{` / `[` 开头的按 JSON 解析，其余按 HTML。所以一个源里
「搜索走接口、章节走网页」也能混着写。

JSON 接口源的规则以 `json:` 开头，走 `$` 路径：

```jsonc
"search": {
  "url": "https://www.ximalaya.com/revision/search?core=album&kw={{key}}&page={{page}}&rows=20",
  "list": "json:$.data.result.response.docs",   // 一路走到数组
  "name": "json:title",
  "bookUrl": "json:id",
  "cover": "json:cover_path",                    // //img.cdn/... 会自动补 https:
  "author": "json:nickname"
},
"detail": {
  // 把「专辑 id」拼成接口地址；留空则直接请求 book.url
  "url": "http://mobwsa.ximalaya.com/mobile/playlist/album/page?albumId={{bookUrl}}&pageId=1",
  "chapters": {
    "list": "json:$.list",
    "title": "json:title",
    // 一个字段可能为空，用 | 按顺序回退
    "url": "json:playUrl64|json:playPathAacv164|json:playUrl32",
    // 一页只给 20 条，按 maxPageId 翻完（上限 60 页）
    "pages": {
      "url": "http://mobwsa.ximalaya.com/mobile/playlist/album/page?albumId={{bookUrl}}&pageId={{p}}",
      "max": "json:$.maxPageId"
    }
  }
},
"audio": { "url": "" }   // 空 = 章节 url 本身就是音频地址
```

值里出现 `|` 时按顺序取第一个非空的（`regex:` 规则不拆，正则里本来就有 `|`）。

### 内置源：只有喜马拉雅，写死在代码里

App 不依赖任何规则源就能用。搜索、章节、播放全部走内置接口（`source/Ximalaya.kt`），
`source/Providers.kt` 是统一入口，界面和播放服务只认它——以后再加平台只需在这里加一个分支。

| 环节 | 接口 | 拿什么 |
|---|---|---|
| 搜索 | `www.ximalaya.com/revision/search?core=album&kw=..` | `data.result.response.docs[]`：id / title / nickname / cover_path / intro / is_paid |
| 章节 | `mobwsa.ximalaya.com/mobile/playlist/album/page?albumId=..&pageId=..` | `list[]`：title / playUrl64 / playPathAacv164 / duration；一页 20 条，按 `maxPageId` 并发翻完（60 页封顶） |
| 播放 | 同上返回的 `playUrl64` | **不带时效**的 mp3 直链，206 audio/mpeg |

**收费的一律不显示**：
- 搜索层：`is_paid=true` 或 `vipFreeType≠0` 的专辑直接剔掉（实测收费专辑这两项都是 true/1，免费专辑都是 false/0）；
- 章节层：`playUrl64 / playPathAacv164 / playUrl32` 全空的音轨跳过，付费轨天然被过滤；
- 万一撞上整本都收费，详情页会明确写「这本没有免费音轨（付费或会员专辑）」，而不是干瞪眼。

**封面按用途分档取图**（`source/Covers.kt`）：

接口给的是缩略图——搜索 `columns=290`，专辑音轨接口更狠，只有 `columns=140`，铺到播放页必然糊。
同一张图能让 CDN 现场缩放，所以 `Book.cover` 只存**基地址**（去掉 `!` 后面的参数），界面自己按需要取：

| 场景 | 边长 | 实测体积 |
|---|---|---|
| 列表 / 书架 / 详情小图 | 300 | ~10KB（webp） |
| 通知栏大图 | 640 | ~54KB |
| 播放页（铺满约 300dp，3 倍屏 900px） | 960 | ~108KB（同图上限 960×960） |

不去参数直接拿原图是 960×960 / 288KB，列表里用它会白下几百 KB。
老版本存下来的带参 URL 也不用迁移——`Covers.at()` 会先剥参数再拼，自动就升级了。

**外部链接**：`weigetingshu://album/<albumId>` 直接打开某张专辑，方便分享，也方便真机调试（adb 打不出中文）。

规则源这套机制仍然保留（`assets/sources.json` 现在是空的）：想接别家网站在「书源」页粘 JSON 就行，
不用改 App。之前踩过的站点都记在下面，别再重复试。

### 那些试过的站（2026-10 实测）

| 站 | 结果 |
|---|---|
| 听书网 tingshuw / 全民听书 qmtsw | 分类/详情/章节/音频能用，**搜索是验证码**，源站还偶发 520；只适合当备用 |
| 5tps.com | 已跳转到 tingyou.fm，JS 单页 |
| pingshu8 | JS cookie 挑战 |
| ting55 / 6yueting / tingshubao / ysts8 / tingzh / xytingshu / 230ts | 超时或 5xx |
| 28ts / bbting | 域名停放待售 |
| tingchina | 域名被劫持 |

传统听书站在 2026 年基本清空，现在能稳定的只有接口。

### 怎么判断一个源还能不能用

别靠肉眼看页面，直接跑体检：

```bash
./gradlew testDebugUnitTest --tests "*SourceProbeTest*"
# 看 app/build/test-results/testDebugUnitTest/TEST-*SourceProbeTest.xml 里的 PROBE 行
```

它先跑内置接口源（搜索「三国」→ 章节 → 音频 → 直链可达），再跑 `assets/sources.json` 里的每个规则源，
每步报条数、耗时、第一行样例。拿到任何新源，先粘进 assets 跑一次再决定值不值得花时间。

### 音频地址什么时候解析

**要播的那一刻才解析**，不是入队时。

原因有两个：网页源的章节音频地址基本都带时效 token；而且提前解析整本书要发几百个请求。
实现上是 Media3 的 `ResolvingDataSource`——队列里每章只有一个标识 `ts://chapter?id=<bookId>#<index>`，
播放器真去读这章时才回调进来换成 http 地址，顺便把 Referer / headers 带上。

内置接口源是个例外：它在取章节时就把直链拿到了，所以这一步是零成本（不额外发请求）。

好处是通知栏能拿到**完整队列**（有上一章/下一章），又不用提前解析。

---

## 三、架构

```
Compose UI (App.kt + ui/*)
   │ 读 vm.xxx（mutableStateOf）/ 调 vm.fun()
   ▼
AppViewModel ─────────────► PlayerController（object 单例）
   │ 读 play（StateFlow）        │ MediaController 转发命令
   ▼                            ▼
书架/搜索/书源            PlaybackService（MediaSessionService）
                                │
                          ExoPlayer + ResolvingDataSource
                                │
                    Sources.engine.audioUrl() ← 书源规则
```

几个刻意的选择：

- **不引 Navigation 库**：页面就是 `tab` / `book` / `showPlayer` 三个状态位。
- **PlayerController 是 object**，`MediaController` 异步连上，对外只暴露 `StateFlow<PlayState>`。
- **位置靠轮询**：ExoPlayer 没有位置回调，200ms 读一次写进 StateFlow。
- **通知文案不进 metadata**：章节副标题、封面放 `NowPlayingState`，自定义 `MediaNotification.Provider` 读它；
  走 `replaceMediaItem` 改 metadata 会重建媒体源、重新解析地址、进度条抖。

### 文件索引

| 路径（`app/src/main/java/com/weige/tingshu/`） | 职责 |
|---|---|
| `App.kt` | 根布局：两个页签（书架 / 发现）+ 迷你播放条 + 详情/播放整屏层 |
| `AppViewModel.kt` | 全部界面状态、搜索、详情、播放动作 |
| `model/Book.kt` | `Book` / `Chapter` / `PlayMode` |
| `model/BookSource.kt` | 书源结构（规则源用，界面已不暴露） |
| **`source/Ximalaya.kt`** | **内置的喜马拉雅客户端：搜索 / 音轨翻页 / 直链 / 收费过滤** |
| **`source/Providers.kt`** | 统一取书入口，界面与播放服务只认它 |
| **`source/BuiltinSources.kt`** | 写死在代码里的源壳 + 已下线源的清理名单 |
| `source/JsonRules.kt` | JSON 接口取值规则（`json:$.a.b`） |
| `source/Rules.kt` | HTML 规则引擎（CSS / 属性 / 正则 / 绝对化） |
| `source/Fetcher.kt` | OkHttp 抓取 + 编码嗅探 |
| `source/SourceEngine.kt` | 规则 → 书 / 章 / 音频地址（规则源用） |
| `source/Sources.kt` | 源仓库：内存 + `sources.json` |
| `data/LocalStore.kt` | 书架、进度、偏好（SharedPreferences） |
| `play/PlaybackService.kt` | `MediaSessionService` + `ResolvingDataSource` + 通知渠道 |
| `play/PlayerController.kt` | `MediaController` 封装、`PlayState`、倍速/模式/封面预取 |
| `play/TingshuNotificationProvider.kt` | 自定义播放通知 |
| `play/ChapterRegistry.kt` | mediaId → Chapter 反查 |
| `ui/ShelfScreen.kt` | 首页：品牌名 + 继续收听 + 书架网格 |
| `ui/SearchScreen.kt` | 发现：搜索框 + 结果 |
| `ui/BookScreen.kt` | 详情 + 章节列表 |
| `ui/PlayerScreen.kt` | 播放页：进度、控制、模式/倍速/定时、章节面板 |

---

## 四、构建

```bash
./gradlew assembleDebug          # 调试包
./gradlew assembleRelease        # 正式包（需自备签名，否则 R8 打了没签）
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

环境：JDK 17、Android SDK（compileSdk 35）、minSdk 26。

---

## 五、踩过的坑（都在代码注释里标了）

- `setMediaNotificationProvider` 在 `MediaSessionService` 上，**不在** `MediaSession.Builder` 上。
- 通知渠道 `IMPORTANCE_LOW` 会被归进静默分组，华为/荣耀**锁屏直接不显示**；要 `DEFAULT` + `setSound(null)`。
- 渠道的 `importance` / `lockscreenVisibility` 建好后改不动，只能换 id 重建。
- `duration` 在媒体源重建的瞬间是 `C.TIME_UNSET`（负数），写成 0 会让进度条闪一下——无效就保留旧值。
- 切章回调里不能写死 `buffering = true`：自动连播时播放器多半已预缓冲，不会再来一次状态回调，转圈就停不下来。
- 边界判断问 `hasNextMediaItem()`，不要用队列下标（随机模式下两者无关）。
- 位置是轮询来的，Slider 直接绑会一格一格跳，要 `animateFloatAsState` 补间。
