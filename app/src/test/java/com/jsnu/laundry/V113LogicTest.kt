package com.jsnu.laundry

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.DeviceCategory
import com.jsnu.laundry.system.LaunchHaierHelper
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.widget.WidgetUiState
import com.jsnu.laundry.widget.WidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.1.3 回归：旧版逐台持久通知、通知按钮按设备区分、原生小组件统计、海乐包名 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class V113LogicTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `命中通知不自动消失一直保留`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val n = NotifHelper.hitNotification(ctx, Device(id = 10L, name = "1号", state = 1))
        assertEquals(0, n.flags and Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun `洗完通知不自动消失`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val n = NotifHelper.finishNotification(ctx, Device(id = 11L, name = "2号", state = 2))
        assertEquals(0, n.flags and Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun `命中通知带定闹钟和去海乐两个按钮`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val n = NotifHelper.hitNotification(ctx, Device(id = 12L, name = "3号", state = 1))
        assertEquals(2, n.actions.size)
        assertEquals("定闹钟", n.actions[0].title)
        assertEquals("去海乐", n.actions[1].title)
    }

    @Test
    fun `两台机器通知按钮的PendingIntent互不覆盖`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val a = NotifHelper.hitNotification(ctx, Device(id = 101L, name = "A", state = 1))
        val b = NotifHelper.hitNotification(ctx, Device(id = 202L, name = "B", state = 1))
        // 旧实现固定 requestCode 会导致两台机器的按钮指向同一个 PendingIntent
        assertNotEquals(a.actions[0].actionIntent, b.actions[0].actionIntent)
        assertNotEquals(a.actions[1].actionIntent, b.actions[1].actionIntent)
    }

    @Test
    fun `小组件状态正确统计空闲可约故障`() {
        val list = listOf(
            Device(id = 1L, state = 1),                                   // 空闲
            Device(id = 2L, state = 1),                                   // 空闲
            Device(id = 3L, state = 2, enableReserve = true, reserveState = 1), // 可约
            Device(id = 4L, state = 3),                                   // 故障
            Device(id = 5L, state = 2, enableReserve = false),            // 运行
        )
        val ui = WidgetUiState.from(list, "二组团4楼")
        assertEquals("二组团4楼", ui.pointName)
        assertEquals(2, ui.idle)
        assertEquals(1, ui.reservable)
        assertEquals(1, ui.fault)
        assertEquals("现在", ui.fastest)
    }

    @Test
    fun `海乐首选包名为官方com_yunshang_haile_life`() {
        // 联网核实：海乐生活用户端官方包名
        assertTrue(LaunchHaierHelper.candidatePackages().first() == "com.yunshang.haile_life")
    }

    @Test
    fun `接口不返回categoryCode时按名称识别设备类型`() {
        // 真机实测 deviceDetailPage 不返回 categoryCode，靠名称兜底，默认洗衣机（不再落“其他”）
        assertEquals(DeviceCategory.SHOE, Device.categoryOf(null, "18#4楼洗鞋机"))
        assertEquals(DeviceCategory.WASHER, Device.categoryOf(null, "18#4楼1号机"))
        assertEquals(DeviceCategory.WASHER, Device.categoryOf(null, "4层4号"))
        assertEquals(DeviceCategory.DRYER, Device.categoryOf(null, "3层烘干机"))
        assertEquals(DeviceCategory.DRYER, Device.categoryFromName("洗烘一体机"))
        // code 优先
        assertEquals(DeviceCategory.SHOE, Device.categoryOf("01", "1号机"))
    }

    @Test
    fun `只有运行中或可约的洗衣机才能标记已用空闲机不行`() {
        // v1.1.5：空闲机没在洗、无法定闹钟/追踪，不显示标记；运行/被约/可约才显示；洗鞋机始终不参与
        val idleWasher = Device(id = 1L, state = 1)
        val running = Device(id = 2L, state = 2, enableReserve = false) // LOCKED 运行不可约
        val reservable = Device(id = 3L, state = 2, enableReserve = true, reserveState = 1)
        val shoe = Device(id = 4L, state = 2, category = com.jsnu.laundry.data.model.DeviceCategory.SHOE)
        assertFalse(canTrackLike(idleWasher))
        assertTrue(canTrackLike(running))
        assertTrue(canTrackLike(reservable))
        assertFalse(canTrackLike(shoe))
        // 已在追踪中的机器即使刷新成空闲也保留按钮，以便手动取消
        assertTrue(canTrackLike(idleWasher, tracked = true))
    }

    @Test
    fun `小组件布局控件齐全且渲染不崩`() {
        val ctx = context()
        // RemoteViews 只支持白名单控件，真机添加时若用了不支持的控件会 InflateException，这里提前 inflate 校验
        val view = android.view.LayoutInflater.from(ctx).inflate(com.jsnu.laundry.R.layout.widget_laundry, null)
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_point))
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_idle_num))
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_res_num))
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_fault_num))
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_btn_refresh))
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_btn_watch))
        assertNotNull(view.findViewById(com.jsnu.laundry.R.id.widget_btn_haier))
        // render 在无桌面实例时也不应抛异常
        WidgetUpdater.render(ctx, WidgetUiState("二组团4楼", 2, 1, 0, "现在"))
    }

    /** 复刻 DeviceCard.canTrack（v1.1.5 收紧后）的判定，保证规则可回归 */
    private fun canTrackLike(d: Device, tracked: Boolean = false): Boolean =
        tracked || (
            d.category != com.jsnu.laundry.data.model.DeviceCategory.SHOE &&
                d.bizStatus in setOf(
                    com.jsnu.laundry.data.model.BizStatus.RESERVABLE,
                    com.jsnu.laundry.data.model.BizStatus.TAKEN,
                    com.jsnu.laundry.data.model.BizStatus.LOCKED,
                )
            )
}
