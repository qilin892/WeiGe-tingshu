package com.weige.tingshu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.weige.tingshu.data.LocalStore
import com.weige.tingshu.play.PlaybackService
import com.weige.tingshu.play.PlayerController
import com.weige.tingshu.source.Sources

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒了也能播，只是没通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Sources.init(applicationContext)
        LocalStore.init(applicationContext)

        setContent { App(vm) }

        PlayerController.connect(this)
        askNotification()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 通知栏点进来打开播放页；外部链接打开对应专辑 */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) {
            if (vm.play.chapter != null) vm.openPlayer()
        }

        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null && data.host == "album") {
            // weigetingshu://album/20665610
            vm.openAlbumById(data.lastPathSegment.orEmpty())
        }
    }

    private fun askNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!ok) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
