package com.jsnu.laundry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 新手引导单页数据 */
data class GuidePage(
    val emoji: String,
    val title: String,
    val body: String,
)

/**
 * 首次启动新手引导：主打「洗衣痛点」与「和海乐 App 最不一样的地方」，
 * 末页致谢。全程处理状态栏 / 导航栏安全区，底部按钮不与系统三键重叠。
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val isLast = index == GUIDE_PAGES.lastIndex
    val page = GUIDE_PAGES[index]

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 顶部：跳过（避开状态栏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onFinish) { Text("跳过") }
            }

            // 中部：图标 + 标题 + 正文（垂直居中，可随页数伸缩）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(page.emoji, fontSize = 56.sp) }
                    Spacer(Modifier.height(26.dp))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp,
                    ) {
                        Text(
                            page.body,
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 26.sp,
                        )
                    }
                }
            }

            // 底部：圆点 + 上一步/下一步（避开系统导航栏 / 手势条，绝不重叠）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GUIDE_PAGES.indices.forEach { i ->
                        val active = i == index
                        Box(
                            modifier = Modifier
                                .size(if (active) 10.dp else 8.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (index > 0) {
                        OutlinedButton(
                            onClick = { index-- },
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) { Text("上一步") }
                    }
                    Button(
                        onClick = { if (isLast) onFinish() else index++ },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text(if (isLast) "开始使用" else "下一步", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** 引导页文案：痛点 → 三大差异点 → 个性化 → 致谢 */
val GUIDE_PAGES = listOf(
    GuidePage(
        emoji = "🧺",
        title = "还在白跑洗衣房？",
        body = "抱着衣服下楼，结果机器全在洗；海乐要自己一遍遍刷，不知道哪台快空、也不知道还要等多久。\n\n洗衣雷达帮你「盯着」整层楼，有空机第一时间告诉你。",
    ),
    GuidePage(
        emoji = "📡",
        title = "雷达扫描 · 预约信息不漏",
        body = "自动按你选的频率轮询整栋 / 整层，哪台空闲、哪台可预约下一轮、最快几点能用，一眼看清；名额刚放出立刻通知，不用守在海乐里反复刷新。",
    ),
    GuidePage(
        emoji = "⏰",
        title = "一键闹钟 · 秒开海乐",
        body = "每台机器都能【定闹钟】——按预计洗完时间一键设好系统闹钟；【去海乐】直接拉起海乐生活下单。\n\n自己投了一台就点「标记已用」，每分钟刷新进度、实时显示预计洗完时间，洗完提醒你取衣晾晒。",
    ),
    GuidePage(
        emoji = "✨",
        title = "更多个性化，等你探索",
        body = "· 桌面小卡片：不打开 App 也能看空闲数量\n· 洗衣中实时进度与洗完提醒\n· 浅色 / 深色主题、常洗衣时段\n· 近 7 天空闲规律，挑人少的时间去\n· 内置泉山校区 51 个点位，自由切换",
    ),
    GuidePage(
        emoji = "🙏",
        title = "致谢与说明",
        body = "特别感谢海乐生活提供公开状态接口；产品思路借鉴了清华大学 thu.services（thuservices）「全校洗衣机状态」开源项目 (≧∇≦)\n\n本 App 只读公开状态，不登录、不下单、不支付，数据仅存本机。\n\n默认点位已设为「二组团 4 楼」，随时可切换。",
    ),
)
