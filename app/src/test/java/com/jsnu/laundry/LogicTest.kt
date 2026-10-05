package com.jsnu.laundry

import com.jsnu.laundry.data.api.DeviceDetailReq
import com.jsnu.laundry.data.api.DeviceListData
import com.jsnu.laundry.data.api.HaierApi
import com.jsnu.laundry.data.api.HaierResp
import com.jsnu.laundry.data.api.NearPositionReq
import com.jsnu.laundry.data.api.PositionData
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.WaitCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 状态派生规则测试（与实测规则严格一致） */
class BizStatusTest {

    private fun device(
        state: Int,
        enableReserve: Boolean = false,
        reserveState: Int = -1,
        finishTime: String? = null,
    ) = Device(
        id = 1L,
        state = state,
        enableReserve = enableReserve,
        reserveState = reserveState,
        finishTime = finishTime,
    )

    @Test
    fun `state1 is IDLE`() {
        assertEquals(BizStatus.IDLE, device(state = 1).bizStatus)
    }

    @Test
    fun `state2 reservable when enableReserve and reserveState1`() {
        assertEquals(BizStatus.RESERVABLE, device(2, enableReserve = true, reserveState = 1).bizStatus)
    }

    @Test
    fun `state2 taken when enableReserve and reserveState0`() {
        assertEquals(BizStatus.TAKEN, device(2, enableReserve = true, reserveState = 0).bizStatus)
    }

    @Test
    fun `state2 locked when not enableReserve`() {
        assertEquals(BizStatus.LOCKED, device(2, enableReserve = false).bizStatus)
    }

    @Test
    fun `state3 is FAULT`() {
        assertEquals(BizStatus.FAULT, device(3).bizStatus)
    }

    @Test
    fun `unknown state is UNKNOWN`() {
        assertEquals(BizStatus.UNKNOWN, device(0).bizStatus)
    }
}

/** 等待时长计算测试 */
class WaitCalcTest {

    private fun fmt(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private val now = 1_700_000_000_000L

    @Test
    fun `null finishTime returns null`() {
        assertNull(WaitCalc.waitMinutes(null, now))
    }

    @Test
    fun `blank finishTime returns null`() {
        assertNull(WaitCalc.waitMinutes("", now))
    }

    @Test
    fun `malformed finishTime returns null`() {
        assertNull(WaitCalc.waitMinutes("not-a-time", now))
    }

    @Test
    fun `future finishTime returns ceil minutes`() {
        // 2分30秒后 -> ceil = 3
        assertEquals(3, WaitCalc.waitMinutes(fmt(now + 150_000), now))
        // 正好 5 分钟 -> 5
        assertEquals(5, WaitCalc.waitMinutes(fmt(now + 300_000), now))
    }

    @Test
    fun `past finishTime returns 0`() {
        assertEquals(0, WaitCalc.waitMinutes(fmt(now - 10_000), now))
    }
}

/** 排序与统计测试 */
class SortTest {

    private fun d(id: String, state: Int, enableReserve: Boolean = false, reserveState: Int = -1, finishTime: String? = null) =
        Device(id = id.hashCode().toLong(), name = id, state = state, enableReserve = enableReserve, reserveState = reserveState, finishTime = finishTime)

    private val now = System.currentTimeMillis()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Test
    fun `sort puts available first then taken then fault`() {
        val fault = d("f", 3)
        val taken = d("t", 2, true, 0, fmt.format(Date(now + 120_000)))
        val idle = d("i", 1)
        val reservable = d("r", 2, true, 1, fmt.format(Date(now + 60_000)))

        val sorted = com.jsnu.laundry.data.LaundryRepository.sortDevices(listOf(fault, taken, idle, reservable))
        assertEquals(listOf("i", "r", "t", "f"), sorted.map { it.name })
    }

    @Test
    fun `sort by wait minutes ascending`() {
        val later = d("later", 2, true, 1, fmt.format(Date(now + 600_000)))
        val sooner = d("sooner", 2, true, 1, fmt.format(Date(now + 60_000)))
        val sorted = com.jsnu.laundry.data.LaundryRepository.sortDevices(listOf(later, sooner))
        assertEquals(listOf("sooner", "later"), sorted.map { it.name })
    }

    @Test
    fun `counts derive correctly`() {
        val list = listOf(
            d("i1", 1),
            d("i2", 1),
            d("r", 2, true, 1),
            d("f", 3),
        )
        val c = com.jsnu.laundry.data.LaundryRepository.counts(list)
        assertEquals(2, c[BizStatus.IDLE])
        assertEquals(1, c[BizStatus.RESERVABLE])
        assertEquals(1, c[BizStatus.FAULT])
    }
}

/** 边沿触发 + 冷却测试 */
class EdgeTriggerTest {

    private class FakeApi : HaierApi {
        override suspend fun deviceDetailPage(body: DeviceDetailReq): HaierResp<DeviceListData> =
            HaierResp(code = 0, data = DeviceListData())
        override suspend fun nearPosition(body: NearPositionReq): HaierResp<PositionData> =
            HaierResp(code = 0, data = PositionData())
    }

    private fun d(id: String, state: Int, enableReserve: Boolean = false, reserveState: Int = -1) =
        Device(id = id.hashCode().toLong(), name = id, state = state, enableReserve = enableReserve, reserveState = reserveState)

    @Test
    fun `running to reservable triggers hit`() {
        val repo = com.jsnu.laundry.data.LaundryRepository(FakeApi())
        val prev = listOf(d("a", 2, true, 0), d("b", 2, true, 1))
        val curr = listOf(d("a", 2, true, 1), d("b", 2, true, 1))
        val hits = repo.evaluateNewlyAvailable(prev, curr, cooldownMs = 0)
        assertEquals(listOf("a"), hits.map { it.name })
    }

    @Test
    fun `fault to idle triggers hit`() {
        val repo = com.jsnu.laundry.data.LaundryRepository(FakeApi())
        val prev = listOf(d("a", 3))
        val curr = listOf(d("a", 1))
        val hits = repo.evaluateNewlyAvailable(prev, curr, cooldownMs = 0)
        assertEquals(listOf("a"), hits.map { it.name })
    }

    @Test
    fun `no change does not trigger`() {
        val repo = com.jsnu.laundry.data.LaundryRepository(FakeApi())
        val prev = listOf(d("a", 2, true, 1))
        val curr = listOf(d("a", 2, true, 1))
        assertTrue(repo.evaluateNewlyAvailable(prev, curr, cooldownMs = 0).isEmpty())
    }

    @Test
    fun `same machine cooled within 30min does not re-trigger`() {
        val repo = com.jsnu.laundry.data.LaundryRepository(FakeApi())
        val prev = listOf(d("a", 2, true, 0))
        val curr = listOf(d("a", 2, true, 1))
        assertEquals(1, repo.evaluateNewlyAvailable(prev, curr, cooldownMs = 30 * 60_000L).size)
        // 第二次同状态变化：被冷却拦截
        val prev2 = listOf(d("a", 2, true, 0))
        assertTrue(repo.evaluateNewlyAvailable(prev2, curr, cooldownMs = 30 * 60_000L).isEmpty())
    }

    @Test
    fun `back to running then available again after cooldown triggers`() {
        val repo = com.jsnu.laundry.data.LaundryRepository(FakeApi())
        val running = listOf(d("a", 2, true, 0))
        val avail = listOf(d("a", 2, true, 1))
        assertEquals(1, repo.evaluateNewlyAvailable(running, avail, cooldownMs = 0).size)
        // avail -> running：变不可用，不触发
        assertTrue(repo.evaluateNewlyAvailable(avail, running, cooldownMs = 0).isEmpty())
        // 冷却 0 时再次 running -> avail：再次触发
        assertEquals(1, repo.evaluateNewlyAvailable(running, avail, cooldownMs = 0).size)
    }

    @Test
    fun `new machine appears available triggers`() {
        val repo = com.jsnu.laundry.data.LaundryRepository(FakeApi())
        val prev = emptyList<Device>()
        val curr = listOf(d("new", 1))
        val hits = repo.evaluateNewlyAvailable(prev, curr, cooldownMs = 0)
        assertEquals(listOf("new"), hits.map { it.name })
    }
}

/** 接口真实 JSON 解析回归测试（防止 id/deviceId 等字段类型再次出错） */
class RealApiJsonTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** 2026-09-10 实测 deviceDetailPage 返回片段 */
    private val realJson = """
        {"code":0,"message":"success","data":{"page":1,"pageSize":100,"total":6,"items":[
        {"id":10703313,"name":"4层4号","imei":"869994064551066","floorCode":"4","state":2,"enableReserve":true,"reserveState":0,"lastMaintenanceTime":null,"finishTime":"2026-09-10 23:36:33","deviceId":50841801},
        {"id":10314244,"name":"18#4楼1号机","imei":"861251052270102","floorCode":"4","state":2,"enableReserve":true,"reserveState":1,"lastMaintenanceTime":null,"finishTime":"2026-09-10 23:16:29","deviceId":50841611},
        {"id":10314141,"name":"18#4楼5号机","imei":"861251052277180","floorCode":"4","state":1,"enableReserve":true,"reserveState":0,"lastMaintenanceTime":null,"finishTime":null,"deviceId":50841610},
        {"id":10314308,"name":"18#4楼2号机","imei":"861251052740682","floorCode":"4","state":3,"enableReserve":true,"reserveState":0,"lastMaintenanceTime":null,"finishTime":null,"deviceId":50841612}
        ]}}
    """.trimIndent()

    @Test
    fun `real json parses and id is Long`() {
        val resp = json.decodeFromString<HaierResp<DeviceListData>>(realJson)
        assertEquals(0, resp.code)
        val items = resp.data?.items.orEmpty()
        assertEquals(4, items.size)
        assertEquals(10703313L, items[0].id)
        assertEquals(50841801L, items[0].deviceId)
        assertEquals("4层4号", items[0].name)
        // 按业务映射规则转换并验证状态派生
        val models = items.map { it.toModel2() }
        assertEquals(BizStatus.TAKEN, models[0].bizStatus)
        assertEquals(BizStatus.RESERVABLE, models[1].bizStatus)
        assertEquals(BizStatus.IDLE, models[2].bizStatus)
        assertEquals(BizStatus.FAULT, models[3].bizStatus)
        assertEquals(10703313L, models[0].id)
    }

    private fun com.jsnu.laundry.data.api.DeviceDto.toModel2() = Device(
        id = id ?: deviceId ?: 0L,
        name = name ?: "未知机器",
        floorCode = floorCode ?: "",
        state = state,
        enableReserve = enableReserve,
        reserveState = reserveState,
        finishTime = finishTime,
        category = Device.categoryOf(categoryCode, name),
    )
}
