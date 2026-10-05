package com.jsnu.laundry.data

import com.jsnu.laundry.data.api.DeviceDetailReq
import com.jsnu.laundry.data.api.DeviceDto
import com.jsnu.laundry.data.api.HaierApi
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.WaitCalc
import com.jsnu.laundry.data.prefs.WatchPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 拉取结果 */
data class FetchResult(
    val devices: List<Device>,
    val rawCount: Int,
    val error: String? = null,
)

/**
 * 数据仓库：拉取 -> 派生业务状态 -> 排序 -> 快照缓存 -> 边沿触发评估。
 * 纯逻辑部分（派生/排序/边沿）独立成静态方法，便于单元测试。
 */
class LaundryRepository(private val api: HaierApi) {

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<List<Device>>(emptyList())
    val snapshot: StateFlow<List<Device>> = _snapshot.asStateFlow()

    /** 上次成功拉取时间戳 */
    @Volatile var lastUpdatedAt: Long = 0L
        private set

    /** 切换点位时清空旧点位快照，避免旧数据闪回 */
    fun clearSnapshot() {
        _snapshot.value = emptyList()
        lastNotifiedAt.clear()
        lastUpdatedAt = 0L
    }

    /** 冷却：设备 id -> 上次"变可约/空闲"通知时间 */
    private val lastNotifiedAt = mutableMapOf<Long, Long>()

    /** 拉取某个点位设备并刷新快照；floorCode 空则返回全部设备 */
    suspend fun fetch(point: WatchPoint, pageSize: Int = 100): FetchResult = mutex.withLock {
        try {
            val resp = api.deviceDetailPage(
                DeviceDetailReq(
                    positionId = point.positionId,
                    categoryCode = point.categoryCode,
                    floorCode = if (point.floorCode.isBlank()) null else point.floorCode,
                    pageSize = pageSize,
                )
            )
            if (resp.code != 0) {
                return@withLock FetchResult(emptyList(), 0, resp.message ?: "接口返回异常 code=${resp.code}")
            }
            val items = resp.data?.items.orEmpty()
            val devices = sortDevices(items.map { it.toModel() })
            if (devices.isNotEmpty()) {
                // 只把「有数据」的快照写入缓存：偶发空响应时保留最近一次成功数据，供首页回退显示
                _snapshot.value = devices
            }
            lastUpdatedAt = System.currentTimeMillis()
            FetchResult(devices, items.size)
        } catch (e: Exception) {
            FetchResult(emptyList(), 0, e.message ?: "网络异常")
        }
    }

    /** 评估"新变为可约/空闲"的机器（边沿触发 + 30分钟冷却） */
    fun evaluateNewlyAvailable(previous: List<Device>, current: List<Device>, cooldownMs: Long = 30 * 60_000L): List<Device> {
        val prevStatus = previous.associate { it.id to it.bizStatus }
        val now = System.currentTimeMillis()
        return current.filter { d ->
            val before = prevStatus[d.id] ?: BizStatus.UNKNOWN
            val after = d.bizStatus
            val becameAvailable = before != BizStatus.IDLE && before != BizStatus.RESERVABLE &&
                (after == BizStatus.IDLE || after == BizStatus.RESERVABLE)
            if (!becameAvailable) return@filter false
            val last = lastNotifiedAt[d.id] ?: 0L
            (now - last >= cooldownMs).also { ok -> if (ok) lastNotifiedAt[d.id] = now }
        }
    }

    fun resetCooldown() {
        lastNotifiedAt.clear()
    }

    companion object {
        /** DTO -> 业务模型（id 为海乐数字主键，fallback deviceId） */
        fun DeviceDto.toModel(): Device = Device(
            id = id ?: deviceId ?: 0L,
            name = name ?: "未知机器",
            floorCode = floorCode ?: "",
            state = state,
            enableReserve = enableReserve,
            reserveState = reserveState,
            finishTime = finishTime,
            category = Device.categoryOf(categoryCode, name),
        )

        /** 按"最快可用"排序：IDLE 视为等待 0，排在同级最前 */
        fun sortDevices(devices: List<Device>): List<Device> {
            return devices.sortedWith(
                compareBy<Device>({ WaitCalc.sortRank(it.bizStatus) })
                    .thenBy {
                        when (it.bizStatus) {
                            BizStatus.IDLE -> 0
                            else -> WaitCalc.waitMinutes(it.finishTime) ?: Int.MAX_VALUE
                        }
                    }
                    .thenBy { it.name }
            )
        }

        /** 统计各类数量 */
        fun counts(devices: List<Device>): Map<BizStatus, Int> {
            val m = mutableMapOf<BizStatus, Int>()
            devices.forEach { d -> m[d.bizStatus] = (m[d.bizStatus] ?: 0) + 1 }
            return m
        }
    }
}
