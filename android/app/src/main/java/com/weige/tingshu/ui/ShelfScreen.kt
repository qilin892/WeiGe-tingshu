package com.weige.tingshu.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weige.tingshu.AppViewModel
import com.weige.tingshu.model.Book

/**
 * 书架（首页）。听书场景一进来就该看到「上次听到哪」，所以顶部先给一张继续收听卡。
 * 标题就是品牌名——App 里没有「关于」页，首页的抬头最该承担这件事。
 */
@Composable
fun ShelfScreen(
    vm: AppViewModel,
    onSearch: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val shelf = vm.shelf
    val last = vm.lastBook

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "伟哥听书",
            sub = if (shelf.isEmpty()) "有声小说 · 搜一本就能开始" else "书架 · ${shelf.size} 本在架",
            action = {
                IconClick(Icons.Filled.Search, 38, Palette.Text2, onSearch)
                IconClick(
                    if (vm.dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    38,
                    Palette.Text2,
                    onToggleTheme,
                )
            },
        )

        if (last != null) {
            ContinueCard(
                book = last,
                chapterIndex = vm.progressOf(last.id),
                onClick = { vm.openBook(last, autoPlay = true) },
            )
        }

        if (shelf.isEmpty()) {
            Empty(
                title = "书架空着",
                hint = "搜索书名或作者，把想听的书收进来。\n听过的进度会自动记住。",
                action = "去发现",
                onAction = onSearch,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shelf, key = { it.id }) { book ->
                    ShelfItem(
                        book = book,
                        chapterIndex = vm.progressOf(book.id),
                        onClick = { vm.openBook(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(book: Book, chapterIndex: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Panel)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book.cover, book.name, Modifier.size(64.dp), 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("继续收听", color = Palette.Accent, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                book.name,
                color = Palette.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "听到第 ${chapterIndex + 1} 章" + if (book.author.isNotBlank()) " · ${book.author}" else "",
                color = Palette.Text3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "继续收听",
                tint = Palette.OnAccent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ShelfItem(book: Book, chapterIndex: Int, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        Cover(book.cover, book.name, Modifier.fillMaxWidth().aspectRatio(0.72f), 10)
        Spacer(Modifier.height(7.dp))
        Text(
            book.name,
            color = Palette.Text,
            fontSize = 13.sp,
            maxLines = 2,
            lineHeight = 17.sp,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Headphones,
                contentDescription = null,
                tint = Palette.Text3,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                if (chapterIndex > 0) "第 ${chapterIndex + 1} 章" else "未开始",
                color = Palette.Text3,
                fontSize = 10.sp,
            )
        }
    }
}
