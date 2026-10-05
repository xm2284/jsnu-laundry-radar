package com.jsnu.laundry

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.prefs.LaundryPrefs
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.data.prefs.WatchPoint
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.system.QqLauncher
import com.jsnu.laundry.viewmodel.DeviceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.1.2 回归：通知修复、点位迁移、QQ 协议、已用标签、洗完提醒开关 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class V112LogicTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `默认点位是二组团4楼`() {
        val p = WatchPoint()
        assertEquals(24615L, p.positionId)
        assertEquals("4", p.floorCode)
    }

    @Test
    fun `洗完提醒总开关默认开启`() {
        assertTrue(LaundryPrefs().finishAlertEnabled)
    }

    @Test
    fun `关注筛选改名为已用且不竖排`() {
        assertEquals("已用", DeviceFilter.TRACKED.label)
    }

    @Test
    fun `老版本整栋默认点位自动迁移到4楼`() {
        val store = PrefsStore(context())
        val legacy = listOf(WatchPoint(positionId = 24615, floorCode = "", name = "二组团17-19号楼（全部楼层）"))
        val migrated = store.migrateLegacyDefault(legacy)
        assertEquals(1, migrated.size)
        assertEquals("4", migrated[0].floorCode)
    }

    @Test
    fun `用户自行添加过的点位不被迁移`() {
        val store = PrefsStore(context())
        val custom = listOf(
            WatchPoint(positionId = 24615, floorCode = "", name = "整栋"),
            WatchPoint(positionId = 24607, floorCode = "2", name = "一组团2楼"),
        )
        assertEquals(custom, store.migrateLegacyDefault(custom))
    }

    @Test
    fun `通知渠道升级为v2且为高优先级`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(NotifHelper.CHANNEL_ALERT)
        assertNotNull(ch)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, ch!!.importance)
        assertEquals("watch_alert_v2", NotifHelper.CHANNEL_ALERT)
    }

    @Test
    fun `关闭提示音时命中通知依然构造（回归通知全丢bug）`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val d = Device(id = 99L, name = "1号洗衣机", state = 1) // 空闲
        val muted = NotifHelper.hitNotification(ctx, d, sound = false)
        assertNotNull(muted)
        assertEquals(NotifHelper.CHANNEL_ALERT, muted.channelId)
        val loud = NotifHelper.hitNotification(ctx, d, sound = true)
        assertNotNull(loud)
    }

    @Test
    fun `首轮汇总通知与洗完通知可正常构造`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val list = listOf(
            Device(id = 1L, name = "A", state = 1),
            Device(id = 2L, name = "B", state = 2, enableReserve = true, reserveState = 1),
        )
        val summary = NotifHelper.summaryNotification(ctx, list, "二组团4楼", sound = true)
        assertNotNull(summary)
        val finish = NotifHelper.finishNotification(ctx, Device(id = 3L, name = "C", state = 1), sound = false)
        assertNotNull(finish)
        // 汇总文案含空闲/可约数量
        val text = summary.extras.getString("android.text") ?: ""
        assertTrue(text.contains("空闲"))
    }

    @Test
    fun `QQ首选协议是个人资料卡可加好友`() {
        val first = QqLauncher.schemes().first()
        assertTrue(first.startsWith("mqqapi://card/show_pslcard"))
        assertTrue(first.contains("uin=${QqLauncher.AUTHOR_QQ}"))
        assertTrue(first.contains("card_type=person"))
        assertEquals("2284517861", QqLauncher.AUTHOR_QQ)
    }

    @Test
    fun `空闲机器汇总最快为现在`() {
        assertEquals("现在", NotifHelper.fastestText(listOf(Device(id = 1L, state = 1))))
    }
}
