package com.jsnu.laundry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.DeviceCategory
import com.jsnu.laundry.system.AlarmHelper
import com.jsnu.laundry.system.LaunchHaierHelper
import com.jsnu.laundry.ui.theme.StatusColors

/** 单台机器卡片 */
@Composable
fun DeviceCard(
    device: Device,
    favourite: Boolean,
    tracked: Boolean,
    waitMinutes: Int?,
    onFav: () -> Unit,
    onTracked: () -> Unit,
) {
    val context = LocalContext.current
    val status = device.bizStatus
    val rawColor = statusColor(status)
    val highlighted = status == BizStatus.RESERVABLE
    // 只有「正在洗/被约/可约」（有预计洗完时间、能据此追踪和定闹钟）才允许标记已用；
    // 空闲/故障/未知不显示（空闲机没在洗，标记无意义）。已在追踪中的机器始终显示按钮以便取消。洗鞋机不参与。
    val canTrack = tracked || (
        device.category != DeviceCategory.SHOE &&
            status in setOf(BizStatus.RESERVABLE, BizStatus.TAKEN, BizStatus.LOCKED)
        )
    // 被我标记在用：状态统一显示为「洗衣中」（主色），不再显示可预约等
    val badgeText = if (tracked) "洗衣中" else status.label
    val color = if (tracked) MaterialTheme.colorScheme.primary else rawColor

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.border(2.dp, color, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态色条
            Box(
                Modifier
                    .size(width = 5.dp, height = 68.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocalLaundryService,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 普通洗衣机不重复标注，仅洗鞋机/烘干机显示类型
                    if (device.category != DeviceCategory.WASHER) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            device.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 状态 badge + 剩余时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        badgeText,
                        color = color,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (status != BizStatus.FAULT && status != BizStatus.UNKNOWN) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (tracked) "我在用 · 洗完提醒取衣" else waitText(status, waitMinutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 准确的预计洗完时间（醒目展示）；被追踪机器优先展示
                val showFinish = tracked || status == BizStatus.TAKEN || status == BizStatus.LOCKED || status == BizStatus.RESERVABLE
                if (showFinish) {
                    device.finishTime?.let { ft ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "预计 ${hhmm(ft)} 洗完",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canTrack) {
                        // 明确的「标记已用 / 洗衣中」文字按钮，再点一次即取消（误触可删除）
                        TextButton(
                            onClick = onTracked,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                if (tracked) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                                contentDescription = if (tracked) "洗衣中，点此取消追踪" else "标记为已用，洗完提醒取衣",
                                tint = if (tracked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                if (tracked) "洗衣中" else "标记已用",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (tracked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (tracked) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    IconButton(onClick = onFav, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "收藏",
                            tint = if (favourite) Color(0xFFFFB020) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                ActionButtons(context, device, status, waitMinutes)
            }
        }
    }
}

/** 从 yyyy-MM-dd HH:mm:ss 取 HH:mm */
private fun hhmm(finishTime: String): String = runCatching {
    finishTime.substring(11, 16)
}.getOrElse { finishTime }

@Composable
private fun ActionButtons(
    context: android.content.Context,
    device: Device,
    status: BizStatus,
    waitMinutes: Int?,
) {
    when (status) {
        BizStatus.IDLE -> {
            SmallButton("去海乐", primary = true) {
                LaunchHaierHelper.launch(context)
            }
        }
        BizStatus.RESERVABLE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("定闹钟", primary = false) {
                    AlarmHelper.createAlarm(context, device.finishTime, "${device.name} · 可约")
                }
                SmallButton("去海乐", primary = true) {
                    LaunchHaierHelper.launch(context)
                }
            }
        }
        BizStatus.TAKEN, BizStatus.LOCKED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("定闹钟", primary = false) {
                    AlarmHelper.createAlarm(context, device.finishTime, "${device.name} · 洗完")
                }
                SmallButton("去海乐", primary = true) {
                    LaunchHaierHelper.launch(context)
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun SmallButton(text: String, primary: Boolean, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun waitText(status: BizStatus, waitMinutes: Int?): String = when {
    status == BizStatus.IDLE -> "现在空闲，直接用"
    waitMinutes == null -> "等待中"
    waitMinutes <= 0 -> "即将完成"
    else -> "约 $waitMinutes 分钟后可用"
}

internal fun statusColor(status: BizStatus): Color = when (status) {
    BizStatus.IDLE -> StatusColors.idle
    BizStatus.RESERVABLE -> StatusColors.reservable
    BizStatus.TAKEN -> StatusColors.taken
    BizStatus.LOCKED -> StatusColors.locked
    BizStatus.FAULT -> StatusColors.fault
    else -> StatusColors.unknown
}
