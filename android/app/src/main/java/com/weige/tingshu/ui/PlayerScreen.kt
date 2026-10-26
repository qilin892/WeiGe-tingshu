package com.weige.tingshu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weige.tingshu.AppViewModel
import com.weige.tingshu.model.PlayMode

/**
 * 播放页。
 *
 * 有声书的控制重心不在「切歌」，而在「倍速 / 定时 / 跳章」，
 * 所以这三个比播放键更靠上，一眼能看到当前状态。
 */
@Composable
fun PlayerScreen(vm: AppViewModel) {
    val play = vm.play
    val ch = play.chapter
    var showChapters by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconClick(Icons.Filled.KeyboardArrowDown, 42, Palette.Text2) { vm.closePlayer() }
                Spacer(Modifier.weight(1f))
                Text(
                    "伟哥听书",
                    color = Palette.Text3,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                IconClick(Icons.Filled.FormatListBulleted, 42, Palette.Text2) { showChapters = true }
            }

            Spacer(Modifier.height(14.dp))

            // 封面
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Cover(
                    url = play.book?.cover.orEmpty(),
                    fallback = play.book?.name.orEmpty().ifBlank { "听" },
                    modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f),
                    radius = 16,
                    // 播放页封面要铺满约 300dp，3 倍屏就是 900px，取 960 那档
                    size = 960,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                ch?.title ?: "还没有播放内容",
                color = Palette.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    play.book?.name?.let { append(it) }
                    if (ch != null) {
                        if (isNotEmpty()) append(" · ")
                        append("第 ${ch.index + 1} 章")
                    }
                },
                color = Palette.Text3,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            ProgressBar(
                positionMs = play.positionMs,
                durationMs = play.durationMs,
                onSeek = { vm.seekTo(it) },
            )

            Spacer(Modifier.height(6.dp))

            // 主控制行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconClick(Icons.Filled.Replay10, 46, Palette.Text2) { vm.seekBy(-15_000) }
                IconClick(Icons.Filled.SkipPrevious, 52, Palette.Text) { vm.prev() }
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Accent)
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (play.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (play.playing) "暂停" else "播放",
                        tint = Palette.OnAccent,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconClick(Icons.Filled.SkipNext, 52, Palette.Text) { vm.next() }
                IconClick(Icons.Filled.Forward10, 46, Palette.Text2) { vm.seekBy(15_000) }
            }

            Spacer(Modifier.height(18.dp))

            // 辅助行：模式 / 倍速 / 定时
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    icon = when (play.playMode) {
                        PlayMode.SEQUENCE -> Icons.Filled.Repeat
                        PlayMode.LOOP_ONE -> Icons.Filled.RepeatOne
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                    },
                    label = when (play.playMode) {
                        PlayMode.SEQUENCE -> "顺序"
                        PlayMode.LOOP_ONE -> "单章"
                        PlayMode.SHUFFLE -> "随机"
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { vm.cyclePlayMode() },
                )
                AssistChip(
                    icon = null,
                    label = "${vm.trimSpeed(play.speed)}x",
                    modifier = Modifier.weight(1f),
                    onClick = { vm.setSpeed(nextSpeed(play.speed)) },
                )
                AssistChip(
                    icon = Icons.Filled.Timer,
                    label = sleepLabel(vm),
                    modifier = Modifier.weight(1f),
                    onClick = { showSleep = true },
                )
            }

            if (play.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    play.error ?: "",
                    color = Palette.Text3,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (showSleep) {
            SleepSheet(
                vm = vm,
                onClose = { showSleep = false },
            )
        }

        if (showChapters) {
            ChapterSheet(vm) { showChapters = false }
        }
    }
}

@Composable
private fun AssistChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Panel)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Palette.Text2, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = Palette.Text2, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * 进度条。
 *
 * 位置是 200ms 轮询来的，直接绑 Slider 会一格一格跳，
 * 用 animateFloatAsState 补间之后才是连续滚动。
 */
@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val duration = durationMs.coerceAtLeast(1L)
    val target = if (dragging) dragValue else positionMs.toFloat()
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(220, easing = LinearEasing),
        label = "position",
    )
    val frac = (shown / duration).coerceIn(0f, 1f)

    Column {
        Slider(
            value = frac,
            onValueChange = {
                dragging = true
                dragValue = it * duration
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Palette.Accent,
                activeTrackColor = Palette.Accent,
                inactiveTrackColor = Palette.Panel2,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                fmtTime(if (dragging) dragValue.toLong() else positionMs),
                color = Palette.Text3,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(fmtTime(durationMs), color = Palette.Text3, fontSize = 11.sp)
        }
    }
}

private fun nextSpeed(current: Float): Float {
    val steps = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    val i = steps.indexOfFirst { it == current }
    return if (i < 0) 1f else steps[(i + 1) % steps.size]
}

private fun sleepLabel(vm: AppViewModel): String {
    if (vm.sleepAtChapterEnd) return "本章后停"
    val left = vm.remainingSleepText()
    return if (left.isBlank()) "定时" else left
}

@Composable
private fun SleepSheet(vm: AppViewModel, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg.copy(alpha = 0.86f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Palette.Panel)
                .clickable(enabled = false) {}
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text("睡眠定时", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("到点自动暂停，适合睡前听", color = Palette.Text3, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            listOf(15, 30, 60, 90).forEach { m ->
                SheetItem("$m 分钟") {
                    vm.setSleep(m)
                    onClose()
                }
            }
            SheetItem("本章播完就停") {
                vm.stopAtChapterEnd()
                onClose()
            }
            SheetItem("关闭定时") {
                vm.setSleep(0)
                onClose()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChapterSheet(vm: AppViewModel, onClose: () -> Unit) {
    val chapters = vm.playingChapters
    val current = vm.play.chapter
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg.copy(alpha = 0.86f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(430.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Palette.Panel)
                .clickable(enabled = false) {}
                .padding(top = 16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vm.play.book?.name.orEmpty().ifBlank { "章节" },
                    color = Palette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text("${chapters.size} 章", color = Palette.Text3, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (chapters.isEmpty()) {
                Empty("没有章节", "先打开一本书再进播放页")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(chapters, key = { it.mediaId }) { ch ->
                        ChapterRow(
                            index = ch.index,
                            title = ch.title,
                            active = current?.mediaId == ch.mediaId,
                            playing = vm.play.playing,
                            onClick = {
                                vm.playChapterOfPlaying(ch)
                                onClose()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetItem(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 13.dp),
    ) {
        Text(text, color = Palette.Text, fontSize = 14.sp)
    }
}
