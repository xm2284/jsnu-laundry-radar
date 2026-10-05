package com.jsnu.laundry

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.watch.WatchLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v1.1.4 回归：洗衣中追踪规则、追踪通知、通知 id 隔离、引导标记/取消追踪持久化 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class V114LogicTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private fun futureDevice(id: Long, plusMin: Int) =
        Device(id = id, name = "${id}号", state = 2, enableReserve = false, finishTime = sdf.format(Date(System.currentTimeMillis() + plusMin * 60_000L)))

    @Test
    fun `有洗衣中追踪时固定60秒刷新`() {
        val now = 1_000_000L
        assertEquals(60_000L, WatchLogic.intervalMs(hasTracked = true, highFreqUntil = now + 999_999, now = now, configuredSec = 300))
        // 没有追踪：命中高频期 15s
        assertEquals(15_000L, WatchLogic.intervalMs(hasTracked = false, highFreqUntil = now + 10_000, now = now, configuredSec = 300))
        // 普通：用配置档位
        assertEquals(300_000L, WatchLogic.intervalMs(hasTracked = false, highFreqUntil = 0, now = now, configuredSec = 300))
    }

    @Test
    fun `蹲守可用机器排除我已标记在用的`() {
        val list = listOf(Device(id = 1, state = 1), Device(id = 2, state = 1), Device(id = 3, state = 1))
        val left = WatchLogic.excludeTracked(list, setOf(2L))
        assertEquals(listOf(1L, 3L), left.map { it.id })
    }

    @Test
    fun `洗完判定只在运行变空闲时成立`() {
        assertTrue(WatchLogic.justFinished(BizStatus.LOCKED, BizStatus.IDLE))
        assertTrue(WatchLogic.justFinished(BizStatus.RESERVABLE, BizStatus.IDLE))
        assertFalse(WatchLogic.justFinished(BizStatus.IDLE, BizStatus.IDLE))
        assertFalse(WatchLogic.justFinished(BizStatus.UNKNOWN, BizStatus.IDLE))
        assertFalse(WatchLogic.justFinished(BizStatus.LOCKED, BizStatus.RESERVABLE))
    }

    @Test
    fun `洗衣中通知正文含预计完成时间和剩余分钟`() {
        val t = WatchLogic.trackingText(futureDevice(1, 30))
        assertTrue(t.contains("预计"))
        assertTrue(t.contains("还剩"))
        assertTrue(t.contains("洗完"))
    }

    @Test
    fun `洗衣中通知为静默渠道只提醒一次且不自动消失`() {
        val ctx = context()
        NotifHelper.ensureChannels(ctx)
        val n = NotifHelper.trackingNotification(ctx, futureDevice(5, 20))
        assertEquals(NotifHelper.CHANNEL_WATCH, n.channelId) // 静默常驻渠道，每分钟更新不吵
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(0, n.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(2, n.actions.size)
        assertEquals("定闹钟", n.actions[0].title)
        assertEquals("去海乐", n.actions[1].title)
    }

    @Test
    fun `三类通知id分段互不冲突`() {
        val ids = setOf(
            NotifHelper.hitNotifId(42),
            NotifHelper.trackingNotifId(42),
            NotifHelper.finishNotifId(42),
        )
        assertEquals(3, ids.size)
    }

    @Test
    fun `洗衣中追踪期间豁免自动停止洗完才停`() {
        // 追踪中即使超过 TTL 也不停
        assertFalse(WatchLogic.shouldAutoStop(hasTracked = true, elapsedMs = 999_999, ttlMs = 60_000))
        // 无追踪且超 TTL 才停
        assertTrue(WatchLogic.shouldAutoStop(hasTracked = false, elapsedMs = 60_001, ttlMs = 60_000))
        assertFalse(WatchLogic.shouldAutoStop(hasTracked = false, elapsedMs = 30_000, ttlMs = 60_000))
    }

    @Test
    fun `新手引导默认未看并可置为已看`() = runBlocking {
        val store = PrefsStore(context())
        assertFalse(store.prefs.first().hasSeenOnboarding)
        store.setSeenOnboarding(true)
        assertTrue(store.prefs.first().hasSeenOnboarding)
        store.setSeenOnboarding(false) // 复位，避免污染其它用例
    }

    @Test
    fun `洗完后可移除某台追踪`() = runBlocking {
        val store = PrefsStore(context())
        store.toggleTracked(99L)
        assertTrue(store.prefs.first().trackedIds.contains(99L))
        store.removeTracked(99L)
        assertFalse(store.prefs.first().trackedIds.contains(99L))
    }
}
