package com.jsnu.laundry.data.stat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.DeviceCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.statDataStore: DataStore<Preferences> by preferencesDataStore(name = "laundry_stats")

/** 某小时的空闲快照（同小时内只保留最新一条） */
@Serializable
data class HourStat(
    val dateHour: String,   // yyyyMMddHH
    val idle: Int,
    val reservable: Int,
    val total: Int,
) {
    val freeRate: Float get() = if (total <= 0) 0f else (idle + reservable).toFloat() / total
}

/**
 * P2 空闲规律统计：每次成功拉取记录当前小时空闲/可约数，
 * 只保留最近 7 天，供"建议几点来洗"。
 */
class UsageStats(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val STATS = stringPreferencesKey("hour_stats")
    }

    val stats: Flow<List<HourStat>> = context.statDataStore.data.map { p ->
        p[Keys.STATS]?.let { runCatching { json.decodeFromString<List<HourStat>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun record(devices: List<Device>, nowMs: Long = System.currentTimeMillis()) {
        // 洗鞋机不参与空闲规律统计（用户要求：洗鞋机不检测）
        val washDevices = recordable(devices)
        if (washDevices.isEmpty()) return
        val key = DateTimeFormatter.ofPattern("yyyyMMddHH")
            .format(java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()))
        val idle = washDevices.count { it.bizStatus == BizStatus.IDLE }
        val reservable = washDevices.count { it.bizStatus == BizStatus.RESERVABLE }
        context.statDataStore.edit { p ->
            val cur = p[Keys.STATS]?.let { runCatching { json.decodeFromString<List<HourStat>>(it) }.getOrNull() } ?: emptyList()
            val next = cur.filterNot { it.dateHour == key } + HourStat(key, idle, reservable, washDevices.size)
            p[Keys.STATS] = json.encodeToString(prune(next))
        }
    }

    /** 只保留最近 7 天 */
    private fun prune(list: List<HourStat>): List<HourStat> {
        val cutoff = LocalDate.now().minusDays(7)
        return list.filter { s ->
            runCatching {
                LocalDate.parse(s.dateHour.take(8), DateTimeFormatter.BASIC_ISO_DATE).isAfter(cutoff)
            }.getOrDefault(true)
        }
    }
}

/** 参与统计的机器：排除洗鞋机（用户要求洗鞋机不检测） */
fun recordable(devices: List<Device>): List<Device> =
    devices.filter { it.category != DeviceCategory.SHOE }
