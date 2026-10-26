package com.weige.tingshu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.weige.tingshu.source.Covers
import com.weige.tingshu.play.PlayState

/** mm:ss / h:mm:ss */
fun fmtTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * 封面。没有图或者加载失败时退化成「首字 + 暗底」，比一个灰方块体面。
 *
 * [size] 是向 CDN 要的边长：列表用 300 就够（18KB），播放页要 960（108KB）。
 * 按用途分档才不会为了一个 56dp 的缩略图去下 100KB 的原图。
 */
@Composable
fun Cover(
    url: String,
    fallback: String,
    modifier: Modifier = Modifier,
    radius: Int = 10,
    size: Int = 300,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Palette.Panel2)
            .border(1.dp, Palette.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = Covers.at(url, size),
                contentDescription = fallback,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallback.take(1),
                color = Palette.Text3,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 书源筛选用的小胶囊 */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Palette.Accent else Palette.Panel2
    val fg = if (selected) Palette.OnAccent else Palette.Text2
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 章节行：左边序号，右边时长/播放中标记 */
@Composable
fun ChapterRow(
    index: Int,
    title: String,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            color = if (active) Palette.Accent else Palette.Text3,
            fontSize = 12.sp,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = title,
            color = if (active) Palette.Accent else Palette.Text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Icon(
                imageVector = if (playing) Icons.Filled.Headphones else Icons.Filled.Pause,
                contentDescription = null,
                tint = Palette.Accent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** 底部悬浮的小播放条，点一下进播放页 */
@Composable
fun MiniPlayer(
    play: PlayState,
    progress: Float,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    val ch = play.chapter ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Panel)
            .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(play.book?.cover.orEmpty(), play.book?.name.orEmpty(), Modifier.size(42.dp), 8)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ch.title,
                color = Palette.Text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${play.book?.name.orEmpty()} · ${fmtTime(play.positionMs)}",
                color = Palette.Text3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Panel2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(Palette.Accent),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        IconClick(
            icon = if (play.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            size = 34,
            tint = Palette.Accent,
            onClick = onToggle,
        )
        IconClick(
            icon = Icons.Filled.SkipNext,
            size = 30,
            tint = Palette.Text2,
            onClick = onNext,
        )
    }
}

@Composable
fun IconClick(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.62).dp))
    }
}

@Composable
fun Empty(
    title: String,
    hint: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Headphones,
            contentDescription = null,
            tint = Palette.Text3,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, color = Palette.Text2, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = Palette.Text3, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Accent)
                    .clickable { onAction() }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(action, color = Palette.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun Loading(text: String = "加载中") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = Palette.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.Text3, fontSize = 12.sp)
    }
}

/** 页面顶部的标题 + 说明 */
@Composable
fun PageHeader(title: String, sub: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Palette.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            if (!sub.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, color = Palette.Text3, fontSize = 11.sp)
            }
        }
        action?.invoke()
    }
}
