package com.jsnu.laundry

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jsnu.laundry.analytics.UmengAnalyticsManager
import com.jsnu.laundry.system.AlarmHelper
import com.jsnu.laundry.system.LaunchHaierHelper
import com.jsnu.laundry.ui.MainScreen
import com.jsnu.laundry.ui.OnboardingScreen
import com.jsnu.laundry.ui.theme.JSNULaundryTheme
import com.jsnu.laundry.viewmodel.LaundryViewModel
import com.jsnu.laundry.watch.PollWorker

class MainActivity : ComponentActivity() {

    private val viewModel: LaundryViewModel by viewModels()

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PollWorker.schedule(this)
        maybeRequestNotifPermission()
        handleWidgetIntent(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (state.pref.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            JSNULaundryTheme(darkTheme = darkTheme) {
                // 首次隐私确认：未同意过则弹窗，同意后才正式初始化友盟统计/推送；不同意则不初始化但洗衣照常用
                var showPrivacy by remember { mutableStateOf(!UmengAnalyticsManager.isAgreed(this@MainActivity)) }
                // 首次启动显示新手引导（可跳过），看完/跳过后只展示一次
                if (state.pref.hasSeenOnboarding) {
                    MainScreen(viewModel = viewModel)
                } else {
                    OnboardingScreen(onFinish = { viewModel.setOnboardingDone() })
                }
                if (showPrivacy) {
                    PrivacyConsentDialog(
                        onAgree = {
                            UmengAnalyticsManager.agreeAndInit(this@MainActivity)
                            showPrivacy = false
                        },
                        onDeny = {
                            // 不同意：本次不初始化友盟，核心洗衣功能仍可用；下次启动会再次询问
                            showPrivacy = false
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    /**
     * 桌面卡片按钮通过 PendingIntent.getActivity 打开本页并携带 action，
     * 在此统一处理（比 BroadcastReceiver 在 ColorOS 上更可靠，不会被后台限制拦截）。
     */
    private fun handleWidgetIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
        when (action) {
            ACTION_REFRESH -> viewModel.manualRefresh()
            ACTION_TOGGLE_WATCH -> viewModel.toggleWatch()
            ACTION_LAUNCH_HAIER -> {
                LaunchHaierHelper.launch(this)
                finish()
            }
            ACTION_WIDGET_ALARM -> {
                val tracked = viewModel.uiState.value.pref.trackedIds
                val device = viewModel.uiState.value.devices.firstOrNull { it.id in tracked }
                if (device != null) {
                    AlarmHelper.createAlarm(this, device.finishTime, "${device.name} · 洗完")
                }
            }
            ACTION_CANCEL_TRACK -> {
                val tracked = viewModel.uiState.value.pref.trackedIds
                val device = viewModel.uiState.value.devices.firstOrNull { it.id in tracked }
                if (device != null) viewModel.toggleTracked(device.id)
            }
        }
        intent.removeExtra(EXTRA_WIDGET_ACTION)
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "widget_action"
        const val ACTION_REFRESH = "refresh"
        const val ACTION_TOGGLE_WATCH = "toggle_watch"
        const val ACTION_LAUNCH_HAIER = "launch_haier"
        const val ACTION_WIDGET_ALARM = "widget_alarm"
        const val ACTION_CANCEL_TRACK = "cancel_track"
    }
}

/** 首次启动隐私确认弹窗：同意后才初始化友盟统计/推送 */
@androidx.compose.runtime.Composable
private fun PrivacyConsentDialog(onAgree: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("隐私与统计说明") },
        text = {
            Text(
                "JSNU洗衣雷达的洗衣机状态查询、蹲守提醒、洗衣追踪与桌面卡片均在本地运行，无需注册登录。\n\n" +
                    "为改进产品体验，本应用使用友盟 U-App 进行匿名的应用使用统计和功能使用分析（如启动次数、功能点击），" +
                    "并使用友盟 U-Push 接收版本更新/维护等通知。统计为匿名聚合数据，不会收集你的姓名、学号、手机号、宿舍等身份信息，" +
                    "也不会申请定位、通讯录等与统计无关的权限。\n\n" +
                    "点击「同意并继续」后我们才会初始化上述统计/推送能力；点击「暂不同意」则不初始化，洗衣等核心功能仍可正常使用。",
            )
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("同意并继续") } },
        dismissButton = { TextButton(onClick = onDeny) { Text("暂不同意") } },
    )
}
