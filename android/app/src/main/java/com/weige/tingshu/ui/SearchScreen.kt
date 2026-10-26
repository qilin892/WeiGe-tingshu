package com.weige.tingshu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weige.tingshu.AppViewModel
import com.weige.tingshu.model.Book

/**
 * 发现页 = 搜索页。
 *
 * 源只有一个（内置的喜马拉雅），所以没有「选源」这一步：
 * 输入关键词 → 搜专辑 → 点开听。收费专辑在数据层就被滤掉了，这里不用再判断。
 */
@Composable
fun SearchScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "发现",
            sub = "伟哥听书 · 内容来自喜马拉雅，只显示免费专辑",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                value = vm.query,
                onValueChange = { vm.query = it },
                onSubmit = { vm.search() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Palette.Accent)
                    .clickable { vm.search() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("搜索", color = Palette.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(6.dp))

        when {
            vm.searching -> Loading("正在搜索")
            vm.results.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        "${vm.results.size} 个免费专辑 · 付费内容已隐藏",
                        color = Palette.Text3,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
                    )
                }
                items(vm.results, key = { it.id }) { b -> ResultRow(b, vm) }
            }

            else -> Empty(
                title = if (vm.searchMsg == null) "搜一本想听的" else "没有结果",
                hint = vm.searchMsg
                    ?: "输入书名或主播，例如「三国演义」「单田芳」。\n结果里的「+」直接加到书架。",
            )
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Palette.Panel)
            .border(1.dp, Palette.Line, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Text3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp),
            cursorBrush = SolidColor(Palette.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                kb?.hide()
                onSubmit()
            }),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("书名 / 主播", color = Palette.Text3, fontSize = 14.sp)
                }
                inner()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultRow(book: Book, vm: AppViewModel) {
    val onShelf = vm.onShelf(book)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.openBook(book) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book.cover, book.name, Modifier.size(56.dp), 8)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.name,
                color = Palette.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    if (book.author.isNotBlank()) append(book.author)
                    if (book.lastChapter.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("更新到 ${book.lastChapter}")
                    }
                }.ifBlank { "喜马拉雅" },
                color = Palette.Text3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconClick(
            icon = if (onShelf) Icons.Filled.Check else Icons.Filled.Add,
            size = 34,
            tint = if (onShelf) Palette.Ok else Palette.Accent,
            onClick = { vm.toggleShelf(book) },
        )
    }
}
