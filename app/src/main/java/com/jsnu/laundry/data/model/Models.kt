package com.jsnu.laundry.data.model

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/** 业务状态（与海乐实测字段派生的规则，务必与网页版一致） */
enum class BizStatus(val label: String) {
    IDLE("空闲"),            // state==1，现在直接用
    RESERVABLE("可预约"),     // state==2 && enableReserve && reserveState==1
    TAKEN("已被约"),          // state==2 && enableReserve && reserveState==0
    LOCKED("运行不可约"),     // state==2 && !enableReserve
    FAULT("故障"),            // state==3
    UNKNOWN("未知"),
}

/** 设备类型 */
enum class DeviceCategory(val label: String, val code: String) {
    WASHER("洗衣机", "00"),
    SHOE("洗鞋机", "01"),
    DRYER("烘干机", "02"),
    OTHER("其他", ""),
}

/** 业务层设备模型 */
@Serializable
data class Device(
    val id: Long = 0L,
    val name: String = "未知机器",
    val floorCode: String = "",
    val state: Int = -1,
    val enableReserve: Boolean = false,
    val reserveState: Int = -1,
    val finishTime: String? = null,
    val category: DeviceCategory = DeviceCategory.WASHER,
) {
    val bizStatus: BizStatus get() = deriveBizStatus(state, enableReserve, reserveState)

    companion object {
        fun deriveBizStatus(state: Int, enableReserve: Boolean, reserveState: Int): BizStatus = when {
            state == 1 -> BizStatus.IDLE
            state == 2 && enableReserve && reserveState == 1 -> BizStatus.RESERVABLE
            state == 2 && enableReserve && reserveState == 0 -> BizStatus.TAKEN
            state == 2 && !enableReserve -> BizStatus.LOCKED
            state == 3 -> BizStatus.FAULT
            else -> BizStatus.UNKNOWN
        }

        /**
         * 设备类型。海乐 deviceDetailPage 接口实测**不返回 categoryCode**，
         * 因此优先用 code，缺失时按设备名称识别（洗鞋/烘干），点位内设备默认按洗衣机处理。
         */
        fun categoryOf(code: String?, name: String? = null): DeviceCategory = when (code) {
            "00" -> DeviceCategory.WASHER
            "01" -> DeviceCategory.SHOE
            "02" -> DeviceCategory.DRYER
            else -> categoryFromName(name)
        }

        /** 按名称兜底识别：含「洗鞋」是洗鞋机，含「烘」是烘干/洗烘机，其余默认洗衣机 */
        fun categoryFromName(name: String?): DeviceCategory = when {
            name == null -> DeviceCategory.WASHER
            name.contains("洗鞋") -> DeviceCategory.SHOE
            name.contains("烘干") || name.contains("洗烘") || name.contains("烘衣") -> DeviceCategory.DRYER
            else -> DeviceCategory.WASHER
        }
    }
}

/** 等待时长与可用性排序辅助 */
object WaitCalc {
    private val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

    /** 返回等待分钟数；无法解析或已过时返回 null（视为"看实时"） */
    fun waitMinutes(finishTime: String?, now: Long = System.currentTimeMillis()): Int? {
        if (finishTime.isNullOrBlank()) return null
        return try {
            val t = fmt.parse(finishTime)?.time ?: return null
            val diff = t - now
            if (diff <= 0) 0 else ceil(diff / 60_000.0).toInt()
        } catch (_: Exception) {
            null
        }
    }

    /** 排序优先级：IDLE/RESERVABLE(0) -> TAKEN/LOCKED(1) -> FAULT(2) */
    fun sortRank(biz: BizStatus): Int = when (biz) {
        BizStatus.IDLE, BizStatus.RESERVABLE -> 0
        BizStatus.TAKEN, BizStatus.LOCKED -> 1
        BizStatus.FAULT -> 2
        else -> 3
    }
}
