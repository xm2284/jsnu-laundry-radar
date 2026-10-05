package com.jsnu.laundry.data.api

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 海乐生活公开状态接口（只读、免鉴权、无签名）。
 * BaseUrl: https://yshz-user.haier-ioc.com
 */
interface HaierApi {

    /** 获取某洗衣点设备列表（含洗衣机/洗鞋机/烘干机，按 floorCode 过滤楼层） */
    @POST("position/deviceDetailPage")
    suspend fun deviceDetailPage(@Body body: DeviceDetailReq): HaierResp<DeviceListData>

    /** 按坐标获取周边洗衣点（P2 选点辅助） */
    @POST("position/nearPosition")
    suspend fun nearPosition(@Body body: NearPositionReq): HaierResp<PositionData>
}
