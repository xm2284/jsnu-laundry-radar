package com.jsnu.laundry.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 海乐统一响应包装：code==0 表示成功 */
@Serializable
data class HaierResp<T>(
    val code: Int = -1,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
data class DeviceListData(
    val items: List<DeviceDto> = emptyList(),
    val total: Long = 0,
)

/** 设备原始字段（与海乐 deviceDetailPage 接口实测一致：id/deviceId 为数字） */
@Serializable
data class DeviceDto(
    val id: Long? = null,
    @SerialName("deviceId") val deviceId: Long? = null,
    val name: String? = null,
    @SerialName("floorCode") val floorCode: String? = null,
    /** 1 空闲 / 2 运行 / 3 故障 */
    val state: Int = -1,
    @SerialName("enableReserve") val enableReserve: Boolean = false,
    /** 1 当前可预约下一轮 / 0 不可预约 */
    @SerialName("reserveState") val reserveState: Int = -1,
    /** 当前轮洗完时间 yyyy-MM-dd HH:mm:ss */
    @SerialName("finishTime") val finishTime: String? = null,
    /** 00 洗衣机 / 01 洗鞋机 / 02 烘干机 */
    @SerialName("categoryCode") val categoryCode: String? = null,
)

/** 设备详情请求体 */
@Serializable
data class DeviceDetailReq(
    val positionId: Long,
    val categoryCode: String? = null,
    val floorCode: String? = null,
    val page: Int = 1,
    val pageSize: Int = 100,
)

/** 附近洗衣房请求体（用于按坐标搜楼，P2 选点辅助） */
@Serializable
data class NearPositionReq(
    val lng: Double,
    val lat: Double,
    val page: Int = 1,
    val pageSize: Int = 100,
)

@Serializable
data class PositionData(
    val items: List<PositionDto> = emptyList(),
)

@Serializable
data class PositionDto(
    val id: Long = 0,
    val name: String? = null,
    val address: String? = null,
)
