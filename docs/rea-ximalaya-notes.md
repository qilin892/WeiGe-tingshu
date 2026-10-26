# REA 能不能帮我逆向喜马拉雅接口

调研时间：2026-10-25 · 结论：**能，但没必要，而且这台机器现在缺前置条件。**

---

## 一、REA 是什么

[`morluto/rea`](https://github.com/morluto/rea) = **Reverse Engineer Anything**，不是喜马拉雅相关项目。
它是一套**给 AI 用的逆向工具编排层**（MCP 服务 + CLI），本地跑 `npx rea-agents setup` 安装。

它自己不提供分析引擎，只是把「你机器上已有的工具」编排起来：

| 它编排什么 | 具体命令（节选自 `rea --help`） |
|---|---|
| 原生二进制 | `analyze` / `function` / `decompile` / `analyze-function` |
| 反汇编引擎 | `execute-rizin-command`、`debug-with-rizin`、`debug-with-gdb`、`cutter-command` |
| Android | `decode-android-resources`（调 apktool）、`capture-adb-screen`、`collect-adb-bugreport` |
| JS / 网页 | `analyze-web-bundle`、`export-web-scripts`、`analyze-javascript-application`、`capture-web-screenshot` |
| 跨版本对比 | `compare-application-versions`、`compare-source-to-bundle` |

## 二、这台机器上的实测状态

装了 `rea-agents@6.7.0`（138 个包，34s），CLI 能跑。`rea doctor` 的结果：

```
healthy: false
  ✓ node    22.22.2
  ✓ host    win32 x64
  ✗ hopper           missing_analysis_engine
  ✗ ghidra           GHIDRA_INSTALL_DIR is not set
  ✗ ida-registration IDA MCP registration is not configured
  ✗ jadx             REA_JADX_MCP_JAR 未设置（REA 不下载、不安装）
  ✗ kuna             REA_KUNA_COMMAND 未设置
  ✗ registration:workbuddy   ~/.workbuddy/mcp.json 里没注册
```

**换句话说：编排层装好了，引擎一个都没有。** 它明确写了不下载不安装这些工具——Ghidra 解压版、jadx-headless-mcp 的 jar，都得自己准备好并配环境变量。
另外它是 MCP 服务，要写进 `~/.workbuddy/mcp.json` 并由你在客户端点「信任」才生效，我这边写了配置也不会自动被加载。

## 三、逆向喜马拉雅，分三层看

| 层 | 是什么 | 现状 | 需要什么 |
|---|---|---|---|
| ① 接口地址与参数 | `/revision/search`、`mobwsa…playlist/album/page` | ✅ **已经通了，不用逆**。今天实测：搜索、音轨列表、免费 MP3 直链全部明文，连 header 都不用带签名 | 无 |
| ② 付费音轨的 `ep` 解密 | mpay 返回 `seed/fileId/buyKey/ep`，要算成 `sign/buy_key/token/timestamp` | ✅ **别人已经逆完**：`jing332/xmlydownloader` 的 `pc_decrypt.go` 是纯 Go 实现（RC4 变体 + 自定义码表），不依赖 unidbg | 只需移植，不用重逆 |
| ③ App 端风控 / native 签名 | 设备指纹、so 里的加密、抓包对抗 | ❓ 没验证过，也没必要验证 | APK + Ghidra/IDA + **可 root 设备**（华为零售机没有）+ Frida |

结论很直接：**你要的那层（① 免费接口、② 付费解密）都不需要 REA。**
REA 的价值在 ③，而 ③ 恰恰是这台机器做不了、也不建议做的部分。

## 四、如果一定要用 REA，最轻的一条路

不需要 root、不需要 Ghidra，走**网页端 JS 静态分析**：

```bash
rea analyze-web-bundle https://www.ximalaya.com      # 抓 bundle + 静态重建
rea export-web-scripts <bundle-dir>                  # 导出脚本，grep 签名逻辑
```

这条路能回答的问题是：「网页端调接口时到底加了什么 header / 算了什么哈希」。
前置：需要 Playwright 浏览器可用（REA 自带 `capture-browser-scenario`），本机未验证。

## 五、我的建议

1. **别为了逆而逆**：现在 App 用的 `mobwsa` 接口明文可用，付费那层的解密代码也现成，重逆一遍只会得到同样的结论。
2. **想扩内容量，瓶颈不在技术**：付费/VIP 音轨要账号 Cookie，`mpay` 那条还带每天 250 条的限制，原作者 README 直接写着「喜马拉雅已开始封禁账号」。这是账号风险 + 版权问题，不是逆向能解决的。
3. **REA 值得留着**：等哪天接口开始加签名、或者要分析别的 App 时，把 Ghidra / jadx 配上就有用。现在装好放着，成本是 28MB。

---

## 附：今天的实测记录（可复现）

```bash
# ① 明文可用（App 就在用这个）
curl -A "ting_6.3.60(sdk,Android16)" \
  "http://mobwsa.ximalaya.com/mobile/playlist/album/page?albumId=20665610&pageId=1"
# → 114 条，playUrl64 有值，206 audio/mpeg

# ② 付费接口返回完整参数（未带 cookie）
TS=$(date +%s)
curl -A "ting_6.3.60(sdk,Android16)" \
  "https://mpay.ximalaya.com/mobile/track/pay/18124606/$TS?device=pc&isBackend=true&_=$TS"
# → ret=0, isAuthorized=true, domain/seed/fileId/buyKey/duration/ep 齐全
# 注意路径是 {trackId}/{ts}，两个都是数字，别当成 albumId/trackId（我第一次就传错了，得到 ret:76）
```
