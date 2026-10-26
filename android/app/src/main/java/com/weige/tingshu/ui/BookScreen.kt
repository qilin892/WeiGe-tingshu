package com.weige.tingshu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weige.tingshu.AppViewModel

/**
 * 详情页：封面 + 简介 + 全部章节。
 *
 * 章节列表是有声书的主入口，所以「继续收听」直接定位到上次那一章，不用翻。
 */
@Composable
fun BookScreen(vm: AppViewModel) {
    val book = vm.book ?: return
    val chapters = vm.chapters
    val lastIndex = vm.progressOf(book.id).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
    val playingId = vm.play.chapter?.mediaId
    var expanded by remember(book.id) { mutableStateOf(false) }
    val onShelf = vm.onShelf(book)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconClick(Icons.AutoMirrored.Filled.ArrowBack, 40, Palette.Text2) { vm.closeBook() }
                Text(
                    "伟哥听书",
                    color = Palette.Text3,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Cover(book.cover, book.name, Modifier.size(96.dp), 12)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        book.name,
                        color = Palette.Text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 25.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 深链打开的书拿不到主播名（音轨接口只给 uid），宁可空着也别写「佚名」
                    if (book.author.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            book.author,
                            color = Palette.Text2,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (chapters.isEmpty()) "章节未加载" else "共 ${chapters.size} 章",
                        color = Palette.Text3,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.Accent)
                                .clickable { vm.playIndex(lastIndex) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Palette.OnAccent,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (vm.progressOf(book.id) > 0) "继续听" else "从头听",
                                    color = Palette.OnAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.Panel2)
                                .clickable { vm.toggleShelf(book) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (onShelf) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (onShelf) Palette.Ok else Palette.Text2,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (onShelf) "已在书架" else "加书架",
                                    color = if (onShelf) Palette.Ok else Palette.Text2,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (book.intro.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(
                        book.intro,
                        color = Palette.Text2,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        maxLines = if (expanded) 60 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (expanded) "收起" else "展开简介",
                        color = Palette.Accent,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("章节", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (vm.detailLoading) {
                    Text("加载中…", color = Palette.Text3, fontSize = 11.sp)
                }
            }
        }

        if (vm.detailLoading && chapters.isEmpty()) {
            item { Loading("正在解析章节") }
        } else if (chapters.isEmpty()) {
            item { Empty("没有章节", vm.detailMsg ?: "这个源可能失效了，换个源试试") }
        } else {
            items(chapters, key = { it.mediaId }) { ch ->
                ChapterRow(
                    index = ch.index,
                    title = ch.title,
                    active = playingId == ch.mediaId,
                    playing = vm.play.playing,
                    onClick = { vm.playIndex(ch.index) },
                )
            }
        }
    }
}
