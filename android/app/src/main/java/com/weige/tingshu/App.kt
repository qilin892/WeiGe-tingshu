package com.weige.tingshu

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.weige.tingshu.ui.BookScreen
import com.weige.tingshu.ui.MiniPlayer
import com.weige.tingshu.ui.Palette
import com.weige.tingshu.ui.PlayerScreen
import com.weige.tingshu.ui.SearchScreen
import com.weige.tingshu.ui.ShelfScreen
import com.weige.tingshu.ui.TingTheme
import kotlinx.coroutines.delay

/**
 * 单 Activity + 三个页签，页面切换靠状态位（没有引 Navigation 库）。
 * 详情页和播放页是盖在上面的整屏层，所以进入时底部导航自然被挡住。
 */
@Composable
fun App(vm: AppViewModel) {
    TingTheme(dark = vm.dark) {
        val bg = Palette.Bg
        val view = LocalView.current

        LaunchedEffect(vm.dark) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            window.statusBarColor = bg.toArgb()
            window.navigationBarColor = bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !vm.dark
        }

        LaunchedEffect(vm.toast) {
            if (vm.toast != null) {
                delay(2200)
                vm.clearToast()
            }
        }

        // 返回键：先关播放页，再关详情页，最后才退出
        BackHandler(enabled = vm.showPlayer) { vm.closePlayer() }
        BackHandler(enabled = !vm.showPlayer && vm.book != null) { vm.closeBook() }

        Box(
            Modifier
                .fillMaxSize()
                .background(bg)
                .systemBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (vm.tab) {
                        0 -> ShelfScreen(
                            vm,
                            onSearch = { vm.selectTab(1) },
                            onToggleTheme = { vm.toggleDark() },
                        )

                        else -> SearchScreen(vm)
                    }
                }

                if (vm.play.chapter != null) {
                    val duration = vm.play.durationMs.coerceAtLeast(1)
                    MiniPlayer(
                        play = vm.play,
                        progress = vm.play.positionMs.toFloat() / duration,
                        onOpen = { vm.openPlayer() },
                        onToggle = { vm.togglePlay() },
                        onNext = { vm.next() },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                BottomBar(vm.tab) { vm.selectTab(it) }
            }

            // 详情页 / 播放页盖在最上层
            if (vm.book != null) {
                Box(Modifier.fillMaxSize().background(Palette.Bg)) {
                    BookScreen(vm)
                }
            }
            if (vm.showPlayer) {
                Box(Modifier.fillMaxSize()) {
                    PlayerScreen(vm)
                }
            }

            vm.toast?.let { msg ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        msg,
                        color = Palette.Text,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Palette.Panel2)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Bg)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("书架", Icons.Filled.LibraryBooks, tab == 0) { onSelect(0) }
        TabItem("发现", Icons.Filled.Explore, tab == 1) { onSelect(1) }
    }
}

@Composable
private fun RowScope.TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Palette.Accent else Palette.Text3,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) Palette.Accent else Palette.Text3,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .width(14.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Palette.Accent else Palette.Bg),
        )
    }
}
