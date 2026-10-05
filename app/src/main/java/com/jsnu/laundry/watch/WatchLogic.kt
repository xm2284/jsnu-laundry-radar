package com.jsnu.laundry.watch

import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.WaitCalc

/**
 * 蹲守 / 「标记已用·洗衣中追踪」的纯规则（无 Android 依赖，便于单测）。
 */
object WatchLogic {

    /** 标记已用后，为精准掌握洗完时间，固定每 1 分钟刷新一次 */
    const val TRACK_INTERVAL_MS = 60_000L

    /** 命中后短暂高频刷新间隔 */
    const val HIT_HIGH_FREQ_MS = 15_000L

    /**
     * 本轮轮询间隔：只要存在「洗衣中追踪」的机器就固定 60s；
     * 否则命中高频期用 15s；再否则用用户配置档位。
     */
    fun intervalMs(hasTracked: Boolean, highFreqUntil: Long, now: Long, configuredSec: Long): Long = when {
        hasTracked -> TRACK_INTERVAL_MS
        now < highFreqUntil -> HIT_HIGH_FREQ_MS
        else -> configuredSec.coerceIn(10, 3600) * 1000L
    }

    /** 蹲守「可用机器」需要排除用户已标记为自己在用（洗衣中）的机器，否则会重复通知可预约 */
    fun excludeTracked(devices: List<Device>, trackedIds: Set<Long>): List<Device> =
        devices.filter { it.id !in trackedIds }

    /** 是否到自动停止时间：存在「洗衣中追踪」机器时豁免（要追到洗完），否则超过 TTL 即停 */
    fun shouldAutoStop(hasTracked: Boolean, elapsedMs: Long, ttlMs: Long): Boolean =
        !hasTracked && elapsedMs >= ttlMs

    /** 是否「刚刚洗完」：此前在运行/被约/可约（非空闲、非未知），此刻变空闲 */
    fun justFinished(before: BizStatus, now: BizStatus): Boolean =
        before != BizStatus.IDLE && before != BizStatus.UNKNOWN && now == BizStatus.IDLE

    /**
     * 「洗衣中」进行中通知正文：预计 HH:mm 洗完 · 还剩 N 分钟（无法解析时给兜底文案）。
     */
    fun trackingText(device: Device, now: Long = System.currentTimeMillis()): String {
        val ft = device.finishTime
        val hhmm = finishHhmm(ft)
        val wait = WaitCalc.waitMinutes(ft, now)
        return when {
            hhmm != null && wait != null && wait > 0 -> "预计 $hhmm 洗完 · 还剩 $wait 分钟"
            hhmm != null -> "预计 $hhmm 洗完 · 即将完成"
            else -> "洗衣中，洗完会提醒你取衣"
        }
    }

    /** 从 yyyy-MM-dd HH:mm:ss 取 HH:mm */
    fun finishHhmm(finishTime: String?): String? = runCatching {
        if (finishTime.isNullOrBlank()) null else finishTime.substring(11, 16)
    }.getOrNull()

    /** 距离洗完还剩多少毫秒；无法解析或已过点返回 null */
    fun remainingMs(finishTime: String?, now: Long = System.currentTimeMillis()): Long? = runCatching {
        if (finishTime.isNullOrBlank()) return null
        val t = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(finishTime)?.time ?: return null
        val diff = t - now
        if (diff <= 0) 0L else diff
    }.getOrNull()
}
