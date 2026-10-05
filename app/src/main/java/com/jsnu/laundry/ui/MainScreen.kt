package com.jsnu.laundry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.ui.theme.StatusColors
import com.jsnu.laundry.viewmodel.DeviceFilter
import com.jsnu.laundry.viewmodel.LaundryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LaundryViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingScreen(viewModel = viewModel, onBack = { showSettings = false })
        return
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("JSNU洗衣雷达", fontWeight = FontWeight.Bold)
                        Text(
                            state.activePoint.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    // 点位切换（P2 多楼层）
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { menuOpen = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("切换点位")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            state.pref.watchPoints.forEachIndexed { index, p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (index == state.pref.activePointIndex) "✓ ${p.name}" else p.name)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        if (index != state.pref.activePointIndex) viewModel.setActivePoint(index)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.manualRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.manualRefresh() },
            modifier = Modifier.padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { WatchButtonCard(state = state, onToggle = { viewModel.toggleWatch() }) }
                item { StatBar(state = state) }
                item { FilterRow(state = state, onSelect = { viewModel.setFilter(it) }) }
                item { LastUpdatedLine(state = state) }

                when {
                    state.loading && state.devices.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(10.dp))
                                Text("正在获取设备…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    state.error != null && state.devices.isEmpty() -> item {
                        ErrorCard(error = state.error ?: "", onRetry = { viewModel.manualRefresh() })
                    }
                    state.devices.isEmpty() -> item {
                        ErrorCard(error = "当前点位暂无设备数据（接口返回 0 台）\n该楼栋/楼层可能没有洗衣机，或网络波动，可下拉重试、点右上角切换点位", onRetry = { viewModel.manualRefresh() })
                    }
                    else -> {
                        items(state.filteredDevices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                favourite = device.id in state.pref.favouriteIds,
                                tracked = device.id in state.pref.trackedIds,
                                waitMinutes = viewModel.waitMinutesOf(device),
                                onFav = { viewModel.toggleFavourite(device.id) },
                                onTracked = { viewModel.toggleTracked(device.id) },
                            )
                        }
                        if (state.filter != DeviceFilter.ALL && state.filteredDevices.isEmpty()) {
                            item { EmptyFilterHint(state.filter.label) }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "数据来自海乐生活公开接口，仅供个人查看，最终以海乐 App 为准",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 「我要洗衣」蹲守主按钮（带颜色/内容过渡动画） */
@Composable
fun WatchButtonCard(state: com.jsnu.laundry.viewmodel.UiState, onToggle: () -> Unit) {
    val watching = state.watching
    val bg by animateColorAsState(
        targetValue = if (watching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        label = "watchBg",
    )
    val fg by animateColorAsState(
        targetValue = if (watching) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "watchFg",
    )
    val btnBg by animateColorAsState(
        targetValue = if (watching) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        label = "watchBtnBg",
    )
    val btnFg by animateColorAsState(
        targetValue = if (watching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        label = "watchBtnFg",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        shadowElevation = 3.dp,
    ) {
        AnimatedVisibility(visible = true) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (watching) "正在为你蹲守 ${state.activePoint.name}…" else "点一下，开始替你盯机器",
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (watching) "空闲/可约瞬间通知 · 已用机器洗完也提醒 · 点下方按钮结束"
                    else "空闲/可约瞬间通知你，不轰炸、60分钟自动停",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnBg,
                        contentColor = btnFg,
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        if (watching) "■ 停止蹲守" else "我要洗衣",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 顶部统计条（数字带过渡动画） */
@Composable
fun StatBar(state: com.jsnu.laundry.viewmodel.UiState) {
    val c = state.counts
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBox("总数", state.totalCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatBox("空闲", (c[BizStatus.IDLE] ?: 0).toString(), StatusColors.idle, Modifier.weight(1f))
        StatBox("可约", (c[BizStatus.RESERVABLE] ?: 0).toString(), StatusColors.reservable, Modifier.weight(1f))
        StatBox("故障", (c[BizStatus.FAULT] ?: 0).toString(), StatusColors.fault, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "⚡ 最快可用：${state.fastestText}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(targetValue = value.toIntOrNull() ?: 0, label = "stat")
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(animated.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FilterRow(state: com.jsnu.laundry.viewmodel.UiState, onSelect: (DeviceFilter) -> Unit) {
    // 横向可滚动，保证每个标签按自身宽度排布、不会被压成竖排文字
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviceFilter.entries.forEach { f ->
            FilterChip(
                selected = state.filter == f,
                onClick = { onSelect(f) },
                label = { Text(f.label, maxLines = 1, softWrap = false) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun LastUpdatedLine(state: com.jsnu.laundry.viewmodel.UiState) {
    val text = if (state.lastUpdatedAt > 0) {
        "更新于 " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdatedAt))
    } else "等待首次刷新…"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null && state.devices.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text("· ${state.error}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyFilterHint(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            "当前没有「$label」的机器",
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
