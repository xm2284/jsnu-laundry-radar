package com.jsnu.laundry.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jsnu.laundry.data.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "laundry_prefs")

/** 一个监控点位（P2 多楼层） */
@Serializable
data class WatchPoint(
    val positionId: Long = 24615,
    /** 楼层；空串=整栋全部楼层（部分设备未标楼层，全楼更全） */
    val floorCode: String = "4",
    val name: String = "二组团17-19号楼·4楼",
    val categoryCode: String? = null,
)

/** 用户配置 */
@Serializable
data class LaundryPrefs(
    val watchPoints: List<WatchPoint> = listOf(WatchPoint()),
    val activePointIndex: Int = 0,
    val pollIntervalSec: Long = 60,
    val watchTtlMin: Long = 60,
    val washCycleMin: Int = 35,
    val campusLng: Double = 117.18,
    val campusLat: Double = 34.198,
    val favourites: Set<String> = emptySet(),
    val soundEnabled: Boolean = true,
    val haierPackage: String = "com.yunshang.haile_life",
    /** 主题：0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    /** 常洗衣时间段（小时） */
    val washStartHour: Int = 20,
    val washEndHour: Int = 23,
    /** 关注的机器（洗完提醒） */
    val tracked: Set<String> = emptySet(),
    /** 洗完提醒总开关（关注机器洗完时通知拿/晒衣服） */
    val finishAlertEnabled: Boolean = true,
    /** 是否已看过新手引导（只在首次启动展示一次） */
    val hasSeenOnboarding: Boolean = false,
) {
    /** 收藏的设备 id（数字），由 favourites 字符串集转换 */
    val favouriteIds: Set<Long> get() = favourites.mapNotNull { it.toLongOrNull() }.toSet()

    /** 关注的设备 id（数字） */
    val trackedIds: Set<Long> get() = tracked.mapNotNull { it.toLongOrNull() }.toSet()

    /** 当前点位 */
    val activePoint: WatchPoint get() = watchPoints.getOrNull(activePointIndex) ?: WatchPoint()
}

class PrefsStore(private val context: Context) {

    private object Keys {
        val WATCH_POINTS = stringPreferencesKey("watch_points_json")
        val ACTIVE_INDEX = longPreferencesKey("active_point_index")
        val POLL_INTERVAL = longPreferencesKey("poll_interval_sec")
        val WATCH_TTL = longPreferencesKey("watch_ttl_min")
        val WASH_CYCLE = longPreferencesKey("wash_cycle_min")
        val CAMPUS_LNG = stringPreferencesKey("campus_lng")
        val CAMPUS_LAT = stringPreferencesKey("campus_lat")
        val FAVOURITES = stringSetPreferencesKey("favourites")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAIER_PACKAGE = stringPreferencesKey("haier_package")
        val THEME_MODE = longPreferencesKey("theme_mode")
        val WASH_START = longPreferencesKey("wash_start_hour")
        val WASH_END = longPreferencesKey("wash_end_hour")
        val TRACKED = stringSetPreferencesKey("tracked")
        val FINISH_ALERT = booleanPreferencesKey("finish_alert_enabled")
        val SEEN_ONBOARDING = booleanPreferencesKey("seen_onboarding")
    }

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val prefs: Flow<LaundryPrefs> = context.dataStore.data.map { p ->
        val rawPoints = p[Keys.WATCH_POINTS]?.let { s ->
            runCatching { json.decodeFromString<List<WatchPoint>>(s) }.getOrNull()
        } ?: listOf(WatchPoint())
        // 老版本默认点位是「二组团整栋（空楼层）」：自动迁移为优先适配的二组团 4 楼
        val points = migrateLegacyDefault(rawPoints)
        LaundryPrefs(
            watchPoints = if (points.isEmpty()) listOf(WatchPoint()) else points,
            activePointIndex = (p[Keys.ACTIVE_INDEX]?.toInt() ?: 0).coerceAtMost(points.lastIndex.coerceAtLeast(0)),
            pollIntervalSec = p[Keys.POLL_INTERVAL] ?: 60,
            watchTtlMin = p[Keys.WATCH_TTL] ?: 60,
            washCycleMin = p[Keys.WASH_CYCLE]?.toInt() ?: 35,
            campusLng = p[Keys.CAMPUS_LNG]?.toDoubleOrNull() ?: 117.18,
            campusLat = p[Keys.CAMPUS_LAT]?.toDoubleOrNull() ?: 34.198,
            favourites = p[Keys.FAVOURITES] ?: emptySet(),
            soundEnabled = p[Keys.SOUND] ?: true,
            haierPackage = p[Keys.HAIER_PACKAGE] ?: "com.yunshang.haile_life",
            themeMode = p[Keys.THEME_MODE]?.toInt() ?: 0,
            washStartHour = p[Keys.WASH_START]?.toInt() ?: 20,
            washEndHour = p[Keys.WASH_END]?.toInt() ?: 23,
            tracked = p[Keys.TRACKED] ?: emptySet(),
            finishAlertEnabled = p[Keys.FINISH_ALERT] ?: true,
            hasSeenOnboarding = p[Keys.SEEN_ONBOARDING] ?: false,
        )
    }

    /** 旧版「单个二组团整栋」默认点位 → 二组团 4 楼（用户自行添加过点位则不动） */
    internal fun migrateLegacyDefault(points: List<WatchPoint>): List<WatchPoint> {
        if (points.size == 1) {
            val only = points[0]
            if (only.positionId == 24615L && only.floorCode.isBlank()) {
                return listOf(WatchPoint(positionId = 24615, floorCode = "4", name = "二组团17-19号楼·4楼"))
            }
        }
        return points
    }

    val activePoint: Flow<WatchPoint> = prefs.map { it.watchPoints.getOrNull(it.activePointIndex) ?: WatchPoint() }

    suspend fun setWatchPoints(points: List<WatchPoint>, activeIndex: Int = 0) {
        context.dataStore.edit { p ->
            p[Keys.WATCH_POINTS] = json.encodeToString(points)
            p[Keys.ACTIVE_INDEX] = activeIndex.toLong()
        }
    }

    suspend fun setActivePoint(index: Int) {
        context.dataStore.edit { p -> p[Keys.ACTIVE_INDEX] = index.toLong() }
    }

    suspend fun setPollInterval(sec: Long) {
        context.dataStore.edit { p -> p[Keys.POLL_INTERVAL] = sec }
    }

    suspend fun setWatchTtl(min: Long) {
        context.dataStore.edit { p -> p[Keys.WATCH_TTL] = min }
    }

    suspend fun setWashCycle(min: Int) {
        context.dataStore.edit { p -> p[Keys.WASH_CYCLE] = min.toLong() }
    }

    suspend fun toggleFavourite(deviceId: Long) {
        val key = deviceId.toString()
        context.dataStore.edit { p ->
            val cur = p[Keys.FAVOURITES] ?: emptySet()
            p[Keys.FAVOURITES] = if (key in cur) cur - key else cur + key
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.SOUND] = enabled }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { p -> p[Keys.THEME_MODE] = mode.toLong() }
    }

    suspend fun setWashHours(start: Int, end: Int) {
        context.dataStore.edit { p ->
            p[Keys.WASH_START] = start.coerceIn(0, 23).toLong()
            p[Keys.WASH_END] = end.coerceIn(0, 24).toLong()
        }
    }

    suspend fun toggleTracked(deviceId: Long) {
        val key = deviceId.toString()
        context.dataStore.edit { p ->
            val cur = p[Keys.TRACKED] ?: emptySet()
            p[Keys.TRACKED] = if (key in cur) cur - key else cur + key
        }
    }

    /** 洗完后自动取消对某台机器的「洗衣中追踪」 */
    suspend fun removeTracked(deviceId: Long) {
        val key = deviceId.toString()
        context.dataStore.edit { p ->
            val cur = p[Keys.TRACKED] ?: emptySet()
            if (key in cur) p[Keys.TRACKED] = cur - key
        }
    }

    suspend fun setSeenOnboarding(seen: Boolean) {
        context.dataStore.edit { p -> p[Keys.SEEN_ONBOARDING] = seen }
    }

    suspend fun setFinishAlertEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.FINISH_ALERT] = enabled }
    }

    suspend fun setHaierPackage(pkg: String) {
        context.dataStore.edit { p -> p[Keys.HAIER_PACKAGE] = pkg }
    }

    suspend fun setCampus(lng: Double, lat: Double) {
        context.dataStore.edit { p ->
            p[Keys.CAMPUS_LNG] = lng.toString()
            p[Keys.CAMPUS_LAT] = lat.toString()
        }
    }

    companion object {
        fun categoryLabel(code: String?) = Device.categoryOf(code).label
    }
}
