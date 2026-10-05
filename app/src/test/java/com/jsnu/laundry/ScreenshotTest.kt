package com.jsnu.laundry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.DeviceCategory
import com.jsnu.laundry.data.prefs.LaundryPrefs
import com.jsnu.laundry.data.stat.HourStat
import com.jsnu.laundry.ui.DeviceCard
import com.jsnu.laundry.ui.FilterRow
import com.jsnu.laundry.ui.OnboardingScreen
import com.jsnu.laundry.ui.SettingScreen
import com.jsnu.laundry.ui.StatBar
import com.jsnu.laundry.ui.WatchButtonCard
import com.jsnu.laundry.ui.theme.JSNULaundryTheme
import com.jsnu.laundry.viewmodel.DeviceFilter
import com.jsnu.laundry.viewmodel.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 视觉验证：Robolectric + Roborazzi 在 JVM 渲染 Compose 界面并截图。
 * 运行：gradle :app:testDebugUnitTest --tests "com.jsnu.laundry.ScreenshotTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun futureTime(min: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis() + min * 60_000))

    private val fakeDevices = listOf(
        Device(id = 1L, name = "18#4楼1号机", floorCode = "4", state = 1, category = DeviceCategory.WASHER),
        Device(id = 2L, name = "18#4楼2号机", floorCode = "4", state = 2, enableReserve = true, reserveState = 1,
            finishTime = futureTime(30), category = DeviceCategory.WASHER),
        Device(id = 3L, name = "18#4楼3号机", floorCode = "4", state = 2, enableReserve = true, reserveState = 0,
            finishTime = futureTime(60), category = DeviceCategory.WASHER),
        Device(id = 4L, name = "18#4楼5号机", floorCode = "4", state = 2, enableReserve = false,
            finishTime = futureTime(90), category = DeviceCategory.WASHER),
        Device(id = 5L, name = "4层4号", floorCode = "4", state = 3, category = DeviceCategory.WASHER),
        Device(id = 6L, name = "18#4楼洗鞋机", floorCode = "4", state = 1, category = DeviceCategory.SHOE),
    )

    private fun uiState(watching: Boolean = false) = UiState(
        devices = fakeDevices,
        loading = false,
        pref = LaundryPrefs(favourites = setOf("2"), tracked = setOf("2")),
        activePoint = com.jsnu.laundry.data.prefs.WatchPoint(name = "二组团17-19号楼（全部楼层）"),
        filter = DeviceFilter.ALL,
        watching = watching,
        lastUpdatedAt = System.currentTimeMillis(),
    )

    @Test
    fun `主界面预览`() {
        val state = uiState()
        composeRule.setContent {
            JSNULaundryTheme {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WatchButtonCard(state = state, onToggle = {})
                    StatBar(state = state)
                    FilterRow(state = state, onSelect = {})
                    Text("更新于 15:32:00", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    fakeDevices.forEach { d ->
                        DeviceCard(
                            device = d,
                            favourite = d.id in state.pref.favouriteIds,
                            tracked = d.id in state.pref.trackedIds,
                            waitMinutes = com.jsnu.laundry.data.model.WaitCalc.waitMinutes(d.finishTime),
                            onFav = {},
                            onTracked = {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/main_screen.png")
    }

    @Test
    fun `蹲守模式预览`() {
        val state = uiState(watching = true)
        composeRule.setContent {
            JSNULaundryTheme {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatchButtonCard(state = state, onToggle = {})
                    StatBar(state = state)
                    fakeDevices.take(2).forEach { d ->
                        DeviceCard(
                            device = d,
                            favourite = false,
                            tracked = d.id in state.pref.trackedIds,
                            waitMinutes = com.jsnu.laundry.data.model.WaitCalc.waitMinutes(d.finishTime),
                            onFav = {},
                            onTracked = {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/watch_mode.png")
    }

    @Test
    fun `设置页统计预览`() {
        val stats = (0..23).map { h ->
            HourStat("2026091${if (h < 10) "0$h" else h}", idle = (h % 4), reservable = 2, total = 6)
        }
        composeRule.setContent {
            JSNULaundryTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(14.dp)) {
                        com.jsnu.laundry.ui.UsageStatsCard(stats, washStart = 20, washEnd = 23)
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/stats.png")
    }

    @Test
    fun `设置页全页预览`() {
        val vm = com.jsnu.laundry.viewmodel.LaundryViewModel(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        composeRule.setContent {
            JSNULaundryTheme {
                com.jsnu.laundry.ui.SettingScreen(viewModel = vm, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/settings_full.png")
    }

    @Test
    fun `添加点位对话框预览`() {
        val vm = com.jsnu.laundry.viewmodel.LaundryViewModel(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        composeRule.setContent {
            JSNULaundryTheme {
                com.jsnu.laundry.ui.SettingScreen(viewModel = vm, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("＋ 添加点位（内置全校楼栋）").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/add_point_dialog.png")
    }

    @Test
    fun `深色主题预览`() {
        val state = uiState()
        composeRule.setContent {
            JSNULaundryTheme(darkTheme = true) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WatchButtonCard(state = state, onToggle = {})
                    StatBar(state = state)
                    fakeDevices.take(3).forEach { d ->
                        DeviceCard(
                            device = d,
                            favourite = d.id in state.pref.favouriteIds,
                            tracked = d.id in state.pref.trackedIds,
                            waitMinutes = com.jsnu.laundry.data.model.WaitCalc.waitMinutes(d.finishTime),
                            onFav = {},
                            onTracked = {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/dark_theme.png")
    }

    @Test
    fun `新手引导预览_标记已用页`() {
        composeRule.setContent {
            JSNULaundryTheme {
                OnboardingScreen(onFinish = {})
            }
        }
        composeRule.waitForIdle()
        // 翻到第 3 页（标记已用 = 洗衣中追踪）
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding.png")
    }
}
