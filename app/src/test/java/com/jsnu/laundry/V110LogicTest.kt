package com.jsnu.laundry

import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.DeviceCategory
import com.jsnu.laundry.data.prefs.BuiltinPoints
import com.jsnu.laundry.data.stat.HourStat
import com.jsnu.laundry.data.stat.recordable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.1.0 新增逻辑的纯逻辑测试（不依赖 Android 环境） */
class V110LogicTest {

    @Test
    fun `内置点位至少覆盖50个且positionId唯一`() {
        val ids = BuiltinPoints.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("内置点位应尽量多采集，>=50", BuiltinPoints.ALL.size >= 50)
        // 分组包含关键组团
        assertTrue(BuiltinPoints.GROUPED.any { it.first == "泉山·组团" })
        // 二组团在列表里（默认点位）
        assertTrue(BuiltinPoints.ALL.any { it.id == 24615L })
    }

    @Test
    fun `楼层label空串表示全部楼层`() {
        assertEquals("全部楼层", BuiltinPoints.floorLabel(""))
        assertEquals("4 楼", BuiltinPoints.floorLabel("4"))
    }

    @Test
    fun `洗鞋机不参与空闲统计`() {
        val devices = listOf(
            Device(id = 1L, name = "机1", floorCode = "", state = 1, category = DeviceCategory.WASHER),
            Device(id = 2L, name = "洗鞋机", floorCode = "", state = 3, category = DeviceCategory.SHOE),
        )
        val stat = recordable(devices)
        assertEquals(1, stat.size)
        assertTrue(stat.none { it.category == DeviceCategory.SHOE })
    }

    @Test
    fun `关注机器洗完边沿检测_运行变空闲触发`() {
        val before = Device(id = 7L, name = "关注机", floorCode = "", state = 2, enableReserve = false,
            category = DeviceCategory.WASHER) // 运行
        val after = Device(id = 7L, name = "关注机", floorCode = "", state = 1, category = DeviceCategory.WASHER) // 空闲
        assertEquals(BizStatus.LOCKED, before.bizStatus)
        assertEquals(BizStatus.IDLE, after.bizStatus)
        val changed = before.bizStatus != BizStatus.IDLE && after.bizStatus == BizStatus.IDLE
        assertTrue(changed)
    }

    @Test
    fun `空闲率计算在洗衣时段内推荐`() {
        // 构造：20-22 点空闲率高
        val stats = (0..23).map { h ->
            val idle = if (h in 20..22) 5 else 1
            HourStat("2026091${if (h < 10) "0$h" else h}", idle = idle, reservable = 1, total = 6)
        }
        val inWash = (20 until 23).filter { hour ->
            stats.firstOrNull { it.dateHour.takeLast(2).toInt() == hour }?.let { it.freeRate > 0 } ?: false
        }
        val best = inWash.maxByOrNull { h ->
            stats.firstOrNull { it.dateHour.takeLast(2).toInt() == h }!!.freeRate
        }
        // 20-22 点空闲率同为最高，返回第一个最大值 20
        assertEquals(20, best)
        assertTrue(best!! in 20..22)
    }

    @Test
    fun `主题与洗衣时段默认值`() {
        val p = com.jsnu.laundry.data.prefs.LaundryPrefs()
        assertEquals(0, p.themeMode)
        assertEquals(20, p.washStartHour)
        assertEquals(23, p.washEndHour)
        assertTrue(p.trackedIds.isEmpty())
        // 默认点位优先适配二组团 4 楼
        assertEquals("4", p.activePoint.floorCode)
    }
}
