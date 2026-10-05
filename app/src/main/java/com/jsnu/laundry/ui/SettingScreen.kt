package com.jsnu.laundry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.jsnu.laundry.analytics.UmengAnalyticsManager
import com.jsnu.laundry.data.agc.AgcUserRepository
import com.jsnu.laundry.push.UmengPushManager
import com.jsnu.laundry.data.prefs.BuiltinPoints
import com.jsnu.laundry.data.prefs.WatchPoint
import com.jsnu.laundry.data.stat.HourStat
import com.jsnu.laundry.system.OppoHelper
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.viewmodel.LaundryViewModel
import com.jsnu.laundry.widget.WidgetPinHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(viewModel: LaundryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats by viewModel.usageStats.collectAsStateWithLifecycle()
    val agcState by viewModel.agcState.collectAsStateWithLifecycle()
    val pushRegistered by UmengPushManager.registered.collectAsStateWithLifecycle()
    val appContext = LocalContext.current
    // 友盟统计是否已（同意隐私后）正式启用；普通布尔，进入页面读一次
    val analyticsEnabled = remember { UmengAnalyticsManager.isEnabled() }
    val notifyEnabled = remember { NotifHelper.hasNotifyPermission(appContext) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CloudServiceStatusCard(
                agcState = agcState,
                analyticsEnabled = analyticsEnabled,
                pushRegistered = pushRegistered,
                notifyEnabled = notifyEnabled,
                onRetryAgc = { viewModel.retryAgc() },
            )
            UsageStatsCard(stats, state.pref.washStartHour, state.pref.washEndHour)
            SectionCard("主题") {
                ThemeSelector(mode = state.pref.themeMode, onChange = { viewModel.setThemeMode(it) })
            }
            SectionCard("监控点位") {
                state.pref.watchPoints.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (p.floorCode.isBlank()) "整栋全部楼层"
                                else "${p.floorCode} 楼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (i == state.pref.activePointIndex) {
                            Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        } else {
                            TextButton(onClick = { viewModel.setActivePoint(i) }) { Text("设为当前") }
                        }
                        if (state.pref.watchPoints.size > 1) {
                            TextButton(onClick = { viewModel.removePoint(i) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                AddPointButton(viewModel)
            }
            DesktopWidgetSection()
            SectionCard("蹲守与提醒") {
                ChoiceRow("轮询间隔", listOf(10L to "10秒", 30L to "30秒", 60L to "60秒", 120L to "2分", 300L to "5分"), state.pref.pollIntervalSec) {
                    viewModel.setPollInterval(it)
                }
                ChoiceRow("蹲守自动停止", listOf(15L to "15分", 30L to "30分", 60L to "60分", 120L to "2小时"), state.pref.watchTtlMin) {
                    viewModel.setWatchTtl(it)
                }
                SwitchRow(
                    title = "洗完提醒",
                    subtitle = "点机器卡片上的铃铛标记「已用」，洗完自动通知取衣/晾晒（蹲守期间生效）",
                    checked = state.pref.finishAlertEnabled,
                    onChange = { viewModel.setFinishAlert(it) },
                )
                SwitchRow(
                    title = "提示音",
                    subtitle = "关闭后仍有通知，只震动/静默提醒",
                    checked = state.pref.soundEnabled,
                    onChange = { viewModel.setSound(it) },
                )
            }
            SectionCard("常洗衣时间段") {
                Text(
                    "告诉 App 你一般几点洗衣服，空闲规律会在你的时段内推荐最佳时段。点击时间用系统时钟选择器调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimePickButton("开始", state.pref.washStartHour) { h ->
                        viewModel.setWashHours(h, state.pref.washEndHour.coerceAtLeast(h + 1))
                    }
                    Text("~", style = MaterialTheme.typography.bodyMedium)
                    TimePickButton("结束", state.pref.washEndHour) { h ->
                        viewModel.setWashHours(state.pref.washStartHour.coerceAtMost(h - 1), h)
                    }
                }
            }
            SectionCard("保活引导（OPPO/国产ROM）") {
                Text(
                    "蹲守依赖后台服务。在 OPPO 上请务必开启：自启动 + 允许后台运行 + 电池不优化 + 通知权限，否则服务会被省电策略杀掉。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val oppo = OppoHelper.isOppo
                if (oppo) {
                    Text(
                        "检测到 ${android.os.Build.MANUFACTURER} 设备，已提供专属入口。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { OppoHelper.openAutoStart(appContext) }) { Text("自启动") }
                    OutlinedButton(onClick = { OppoHelper.openBackgroundManagement(appContext) }) { Text("后台运行") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { OppoHelper.openBatteryOptimize(appContext) }) {
                        Text(if (OppoHelper.isIgnoringBatteryOptimizations(appContext)) "电池已不优化 ✓" else "电池不优化")
                    }
                    OutlinedButton(onClick = { openAppNotifSettings(appContext) }) { Text("通知权限") }
                }
            }
            SectionCard("关于") {
                Text("JSNU 洗衣雷达 v${com.jsnu.laundry.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "只读海乐生活公开状态接口，不登录、不代下单、不代支付；洗衣机状态仅存本机。\n" +
                        "为统计使用情况，App 会生成一个随机匿名 ID 上报「启动/刷新/蹲守/追踪/洗完」等不含个人信息的事件到华为云数据库，不采集手机号、位置等任何个人身份信息，也无需注册。\n" +
                        "感谢海乐生活提供公开接口，思路借鉴清华大学 thu.services（thuservices）「全校洗衣机状态」开源项目 (≧∇≦)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("作者：", style = MaterialTheme.typography.bodyMedium)
                    Text("小明同学", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { openQQ(appContext) }) {
                        Text("QQ 2284517861", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "获取最新版安装包、查看开源仓库或反馈问题：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { openContactPage(appContext) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("获取最新版 / 联系作者") }
            }
        }
    }
}

@Composable
internal fun ThemeSelector(mode: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (m, label) ->
            FilterChip(
                selected = mode == m,
                onClick = { onChange(m) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** 离散档位选择（轮询间隔 / 蹲守时长） */
@Composable
private fun ChoiceRow(label: String, options: List<Pair<Long, String>>, current: Long, onSelect: (Long) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        // 横向可滚动、单行不换行：选项再多（如轮询 5 档）也不会把最后一个挤成竖排
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    label = { Text(text, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 系统闹钟式时间选择器（24 小时制，只取整点） */
@Composable
private fun TimePickButton(label: String, hour: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(onClick = {
        android.app.TimePickerDialog(ctx, { _, h, _ -> onPick(h) }, hour, 0, true).show()
    }) { Text("$label ${"%02d".format(hour)}:00") }
}

/**
 * 云服务状态卡（仅展示连接/启用状态，不做全校统计大屏）：
 * - 华为云：AGC 匿名认证 + laundry 区 LaundryUser 是否已连通
 * - 友盟统计：同意隐私后是否已正式启用
 * - 消息推送：基础通道是否注册 + 系统通知权限是否开启
 * 全校总人数 / DAU 等应在华为 AGC、友盟后台查看，客户端不展示这些它无法安全获得的数据。
 */
@Composable
internal fun CloudServiceStatusCard(
    agcState: AgcUserRepository.State,
    analyticsEnabled: Boolean,
    pushRegistered: Boolean,
    notifyEnabled: Boolean,
    onRetryAgc: () -> Unit,
) {
    SectionCard("云服务") {
        val agcText = when (agcState) {
            AgcUserRepository.State.READY -> "已连接"
            AgcUserRepository.State.WORKING -> "连接中…"
            AgcUserRepository.State.FAILED -> "暂未连接（离线不影响洗衣）"
            AgcUserRepository.State.IDLE -> "待连接"
        }
        CloudStatusRow(
            label = "华为云",
            value = agcText,
            ok = agcState == AgcUserRepository.State.READY,
            pending = agcState == AgcUserRepository.State.WORKING,
            actionText = if (agcState == AgcUserRepository.State.FAILED) "重试" else null,
            onAction = onRetryAgc,
        )
        CloudStatusRow(
            label = "友盟统计",
            value = if (analyticsEnabled) "已启用" else "未启用",
            ok = analyticsEnabled,
        )
        CloudStatusRow(
            label = "消息推送",
            value = when {
                !notifyEnabled -> "通知权限未开启"
                pushRegistered -> "已启用"
                else -> "注册中…"
            },
            ok = notifyEnabled && pushRegistered,
            pending = notifyEnabled && !pushRegistered,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "全校使用人数、活跃等统计请在华为 AGC / 友盟后台查看；云服务异常不会影响洗衣、蹲守、通知等本地功能。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CloudStatusRow(
    label: String,
    value: String,
    ok: Boolean,
    pending: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        val color = when {
            ok -> MaterialTheme.colorScheme.primary
            pending -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.error
        }
        if (pending) {
            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = color)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text(actionText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 空闲规律（强化）：实时统计 + 当前时段 + 洗衣时段内最佳 */
@Composable
internal fun UsageStatsCard(stats: List<HourStat>, washStart: Int = 20, washEnd: Int = 23) {
    val perHour = IntArray(24)
    val perHourCount = IntArray(24)
    stats.forEach { s ->
        runCatching {
            val h = s.dateHour.takeLast(2).toInt()
            if (h in 0..23) {
                perHour[h] += (s.freeRate * 100).toInt()
                perHourCount[h]++
            }
        }
    }
    val avg = IntArray(24) { if (perHourCount[it] > 0) perHour[it] / perHourCount[it] else -1 }
    val nowHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date()).toIntOrNull() ?: -1
    val nowRate = if (nowHour in 0..23 && avg[nowHour] >= 0) avg[nowHour] else -1

    // 最佳时段：优先洗衣时段内空闲率最高的；时段外整体最优作备选
    val inWash = (washStart until washEnd).filter { avg[it] >= 0 }
    val bestHour = inWash.maxByOrNull { avg[it] } ?: (0..23).maxByOrNull { avg[it] } ?: -1
    val bestIsWash = bestHour in (washStart until washEnd)

    SectionCard("空闲规律 · 建议几点来洗") {
        if (stats.isEmpty()) {
            Text(
                "数据积累中：使用几天后这里会显示各时段空闲概率，帮你避开高峰期（洗鞋机不参与统计）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val primaryColor = MaterialTheme.colorScheme.primary
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallStatBox("当前时段", if (nowRate >= 0) "$nowRate%" else "—", if (nowRate >= 40) primaryColor else MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SmallStatBox("数据天数", "${stats.map { it.dateHour.take(8) }.distinct().size} 天", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                val barW = size.width / 24
                avg.forEachIndexed { h, v ->
                    val ratio = if (v < 0) 0f else v / 100f
                    val barH = size.height * ratio.coerceIn(0.06f, 1f)
                    val inWashRange = h in (washStart until washEnd)
                    val color = when {
                        h == bestHour -> primaryColor
                        inWashRange -> primaryColor.copy(alpha = 0.7f)
                        else -> primaryColor.copy(alpha = 0.3f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(h * barW + barW * 0.2f, size.height - barH),
                        size = Size(barW * 0.6f, barH),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
                // 洗衣时段底纹
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.06f),
                    topLeft = Offset(washStart * barW, 0f),
                    size = Size((washEnd - washStart) * barW, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            Text(
                if (bestHour >= 0) {
                    if (bestIsWash) "你的洗衣时段（${washStart}:00-${washEnd}:00）最佳：$bestHour 点（空闲率最高）"
                    else "整体最佳：$bestHour 点；洗衣时段内数据较少，可多用几天"
                } else "暂无明显规律，继续积累数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SmallStatBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** 桌面卡片入口：一键添加（统计/追踪）/ 开权限 / 手动教程，添加后校验是否真正成功 */
@Composable
private fun DesktopWidgetSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    fun openAppDetail() = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { toast("请手动到 设置→应用管理→洗衣雷达→权限 开启「创建桌面快捷方式」") }

    /** 发起添加并在 3 秒后校验是否真正成功（ColorOS 可能静默不弹框） */
    fun pinAndCheck(providerClass: Class<out android.appwidget.AppWidgetProvider>, label: String) {
        if (!WidgetPinHelper.canPin(context)) {
            toast("系统不支持一键添加，请用手动教程")
            showGuide = true
            return
        }
        val before = WidgetPinHelper.pinnedCount(context, providerClass)
        val ok = WidgetPinHelper.requestPin(context, providerClass)
        if (ok) {
            toast("已发起添加「$label」，请在系统弹窗点「添加」")
            showMenu = false
            // 3 秒后检查：如果数量没增加，说明系统可能没弹框（ColorOS 限制），引导手动添加
            scope.launch {
                delay(3000)
                val after = WidgetPinHelper.pinnedCount(context, providerClass)
                if (after <= before) {
                    android.widget.Toast.makeText(context, "系统未弹出添加框（ColorOS 限制），请用手动教程添加", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } else {
            toast("发起失败，请开权限或用手动教程")
        }
    }

    SectionCard("桌面卡片") {
        Text(
            "桌面有两个卡片可选：「洗衣雷达卡片」看空闲/可约统计，「洗衣中追踪」看倒计时。ColorOS 上一键添加可能不弹框，推荐手动添加。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { showMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text("添加桌面卡片")
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("关闭") } },
            title = { Text("添加桌面卡片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "有两个卡片可选，可同时放桌面。OPPO/ColorOS 若一键添加没反应，是系统限制，请直接用手动教程。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { pinAndCheck(com.jsnu.laundry.widget.LaundryAppWidgetProvider::class.java, "洗衣雷达卡片") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("① 一键添加「统计卡片」") }
                    OutlinedButton(
                        onClick = { pinAndCheck(com.jsnu.laundry.widget.TrackingAppWidgetProvider::class.java, "洗衣中追踪") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("② 一键添加「追踪卡片」") }
                    OutlinedButton(onClick = { openAppDetail() }, modifier = Modifier.fillMaxWidth()) {
                        Text("③ 开启「创建桌面快捷方式」权限")
                    }
                    OutlinedButton(
                        onClick = { showGuide = true; showMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("④ 手动添加教程（推荐）") }
                }
            },
        )
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            confirmButton = {
                TextButton(onClick = {
                    showGuide = false
                    openAppDetail()
                }) { Text("去开权限") }
            },
            dismissButton = { TextButton(onClick = { showGuide = false }) { Text("知道了") } },
            title = { Text("手动添加桌面卡片") },
            text = {
                Text(
                    "OPPO/ColorOS 手动添加步骤：\n" +
                        "1. 回到手机桌面，双指捏合（或长按空白处）进入桌面编辑；\n" +
                        "2. 点击底部工具栏的「插件」（部分版本叫「卡片」/「窗口小部件」）；\n" +
                        "3. 在列表里找到「JSNU洗衣雷达」，展开后有两个卡片：\n" +
                        "   • 洗衣雷达卡片（统计空闲/可约/故障）\n" +
                        "   • 洗衣中追踪（标记已用后显示倒计时）\n" +
                        "4. 长按卡片拖到桌面即可，两个可以同时放。\n\n" +
                        "若列表里没有或拖不进去：点「去开权限」，在应用信息→权限里把「创建桌面快捷方式」设为允许后重试。"
                )
            },
        )
    }
}

/** 添加点位：对话框内直接点选（分组→楼栋→楼层），不用弹出菜单，避免部分 ROM 上点不开 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddPointButton(viewModel: LaundryViewModel) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加点位（内置全校楼栋）") }
    if (open) {
        var advanced by remember { mutableStateOf(false) }
        var group by remember { mutableStateOf(BuiltinPoints.GROUPED.firstOrNull()?.first ?: "") }
        var point by remember { mutableStateOf(BuiltinPoints.ALL.firstOrNull()?.id ?: 24615) }
        var floor by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var pid by remember { mutableStateOf("24615") }

        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(if (advanced) "添加监控点位（高级）" else "添加监控点位") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!advanced) {
                        Text("① 选片区", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BuiltinPoints.GROUPED.forEach { (g, items) ->
                                FilterChip(
                                    selected = group == g,
                                    onClick = {
                                        group = g
                                        point = BuiltinPoints.ALL.firstOrNull { it.group == g }?.id ?: point
                                    },
                                    label = { Text("$g(${items.size})") },
                                )
                            }
                        }
                        Text("② 选楼栋", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BuiltinPoints.ALL.filter { it.group == group }.forEach { item ->
                                FilterChip(
                                    selected = point == item.id,
                                    onClick = { point = item.id },
                                    label = { Text(item.name) },
                                )
                            }
                        }
                        Text("③ 选楼层", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BuiltinPoints.FLOORS.forEach { f ->
                                FilterChip(
                                    selected = floor == f,
                                    onClick = { floor = f },
                                    label = { Text(BuiltinPoints.floorLabel(f)) },
                                )
                            }
                        }
                        val selectedName = BuiltinPoints.ALL.firstOrNull { it.id == point }?.name
                        Text(
                            "已选：$selectedName · ${BuiltinPoints.floorLabel(floor)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { advanced = true }) { Text("手填 positionId（高级）") }
                    } else {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称，如 二组团4楼") }, singleLine = true)
                        OutlinedTextField(value = pid, onValueChange = { pid = it }, label = { Text("positionId") }, singleLine = true)
                        OutlinedTextField(value = floor, onValueChange = { floor = it }, label = { Text("楼层（留空=全部）") }, singleLine = true)
                        TextButton(onClick = { advanced = false }) { Text("返回内置楼栋选择") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pos = if (advanced) pid.toLongOrNull() else point
                    if (pos != null) {
                        val pointName = if (advanced) {
                            name.ifBlank { "点位 $pos" }
                        } else {
                            val b = BuiltinPoints.ALL.firstOrNull { it.id == pos }
                            "${b?.name ?: "点位 $pos"} · ${BuiltinPoints.floorLabel(floor)}"
                        }
                        viewModel.addPoint(WatchPoint(positionId = pos, floorCode = floor.trim(), name = pointName))
                        open = false
                    }
                }) { Text("添加并切换") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

private fun openQQ(context: Context) {
    com.jsnu.laundry.system.QqLauncher.launch(context)
}

/** 「获取最新版 / 联系作者」落地页（GitHub Pages），由 laundry-contact 仓库提供 */
private const val CONTACT_URL = "https://xm2284.github.io/laundry-contact/"

private fun openContactPage(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(CONTACT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 通知权限：跳到本应用通知设置页 */
private fun openAppNotifSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        OppoHelper.openAppSettings(context)
    }
}
