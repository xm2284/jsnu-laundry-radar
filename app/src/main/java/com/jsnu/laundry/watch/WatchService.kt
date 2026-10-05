package com.jsnu.laundry.watch

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jsnu.laundry.data.LaundryRepository
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.DeviceCategory
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.data.prefs.WatchPoint
import com.jsnu.laundry.analytics.UmengAnalyticsManager
import com.jsnu.laundry.data.stat.UsageStats
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 「我要洗衣」蹲守 + 关注机器前台服务：
 * - 前台常驻（dataSync 类型），按轮询间隔刷新（命中后 15s 高频 1 分钟）
 * - 蹲守边沿触发：非可约/空闲 -> 可约/空闲 才通知；首轮只建基线不轰炸；洗鞋机不参与
 * - 关注机器：运行 -> 空闲 时通知「洗好了，记得拿/晾晒」
 * - 停止 / 超时(watchTtlMin) 自动结束
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: kotlinx.coroutines.Job? = null
    private lateinit var prefs: PrefsStore
    private lateinit var stats: UsageStats
    private var lastDevices: List<Device> = emptyList()
    private var firstRound = true
    /** 仅因「标记已用·洗衣中追踪」而启动时为 true：首轮只建基线、不逐台通知其它空闲机器 */
    private var quietFirstRound = false
    private val lastFinishedNotifiedAt = mutableMapOf<Long, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = PrefsStore(this)
        stats = UsageStats(this)
        LaundryRepositoryHolder.init(this)
        NotifHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_WATCH) {
            quietFirstRound = intent.getBooleanExtra(EXTRA_QUIET_FIRST, false)
        }
        when (intent?.action) {
            ACTION_STOP_WATCH -> {
                stopSelf()
                return START_NOT_STICKY
            }
            // 点位切换：重置基线并立即拉取新点位（首页/通知实时跟随）
            ACTION_RESYNC -> {
                resetBaseline()
                kickLoop()
            }
            // 标记/取消「洗衣中」：只立即刷新一轮，不重置蹲守基线
            ACTION_KICK -> kickLoop()
        }
        startWatchLoop()
        return START_STICKY
    }

    /** 点位变化时调用：清掉旧点位基线，避免误触发；下一轮对新点位重新建基线并发汇总 */
    private fun resetBaseline() {
        lastDevices = emptyList()
        firstRound = true
        lastFinishedNotifiedAt.clear()
    }

    private var kickSignal = 0L
    private fun kickLoop() { kickSignal = System.currentTimeMillis() }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startWatchLoop() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            val pref = runCatching { prefs.prefs.first() }.getOrElse { com.jsnu.laundry.data.prefs.LaundryPrefs() }
            startForegroundCompat(pref.activePoint)
            val repo = LaundryRepositoryHolder.repository
            var startedAt = System.currentTimeMillis()
            var highFreqUntil = 0L
            var lastPointKey: String? = null
            var lastPref: com.jsnu.laundry.data.prefs.LaundryPrefs? = null
            while (true) {
                val prefNow = runCatching { prefs.prefs.first() }.getOrElse { lastPref ?: com.jsnu.laundry.data.prefs.LaundryPrefs() }
                lastPref = prefNow
                val point = prefNow.activePoint
                // 点位在设置/首页被切换：自动重置基线
                val pointKey = "${point.positionId}_${point.floorCode}"
                if (lastPointKey != null && lastPointKey != pointKey) {
                    resetBaseline()
                    startedAt = System.currentTimeMillis()
                }
                lastPointKey = pointKey
                val nowMs = System.currentTimeMillis()
                // 有「洗衣中追踪」的机器时固定 60s 精准刷新，否则命中高频 15s，再否则用配置档位
                val hasTracked = prefNow.trackedIds.isNotEmpty()
                val interval = WatchLogic.intervalMs(hasTracked, highFreqUntil, nowMs, prefNow.pollIntervalSec)
                val result = repo.fetch(point)
                if (result.error == null && result.devices.isNotEmpty()) {
                    val washDevices = result.devices.filter { it.category != DeviceCategory.SHOE }
                    stats.record(washDevices)
                    val trackedIds = prefNow.trackedIds
                    val beforeDevices = lastDevices

                    if (firstRound) {
                        // 首轮：建立基线；正常蹲守把「当前就空闲/可约」且「不是我自己标记在用」的机器逐台通知；
                        // 仅「标记已用」静默启动时不轰炸其它机器
                        firstRound = false
                        val usableNow = if (quietFirstRound) emptyList()
                        else WatchLogic.excludeTracked(
                            washDevices.filter {
                                it.bizStatus == BizStatus.IDLE || it.bizStatus == BizStatus.RESERVABLE
                            },
                            trackedIds
                        )
                        if (NotifHelper.hasNotifyPermission(this@WatchService)) {
                            usableNow.forEach { d ->
                                runCatching {
                                    NotificationManagerCompat.from(this@WatchService).notify(
                                        NotifHelper.hitNotifId(d.id),
                                        NotifHelper.hitNotification(this@WatchService, d, sound = prefNow.soundEnabled)
                                    )
                                }
                            }
                        }
                    } else {
                        // 蹲守命中：排除我已标记「洗衣中」的机器（自己在用，不需要再被通知可约）
                        val hits = WatchLogic.excludeTracked(
                            repo.evaluateNewlyAvailable(lastDevices, result.devices)
                                .filter { it.category != DeviceCategory.SHOE },
                            trackedIds
                        )
                        if (hits.isNotEmpty()) {
                            hits.forEach { d ->
                                if (NotifHelper.hasNotifyPermission(this@WatchService)) {
                                    runCatching {
                                        NotificationManagerCompat.from(this@WatchService).notify(
                                            NotifHelper.hitNotifId(d.id),
                                            NotifHelper.hitNotification(this@WatchService, d, sound = prefNow.soundEnabled)
                                        )
                                    }
                                }
                            }
                            highFreqUntil = System.currentTimeMillis() + 60_000L // 命中后高频 1 分钟
                        }
                    }

                    // 「标记已用·洗衣中」追踪：进行中每分钟刷新预计完成时间；洗完发取衣提醒并自动取消追踪
                    handleTracking(result.devices, beforeDevices, trackedIds, prefNow.finishAlertEnabled, prefNow.soundEnabled)

                    lastDevices = result.devices
                }
                updateForegroundNotification(result.devices.ifEmpty { lastDevices }, point.name)
                WidgetUpdater.update(this@WatchService, result.devices.ifEmpty { lastDevices }, point)
                // 同步刷新「洗衣中追踪」卡片（倒计时/预计时间）
                WidgetUpdater.updateTracking(this@WatchService)
                val ttl = prefNow.watchTtlMin * 60_000L
                // 还有「洗衣中追踪」的机器时豁免自动停止，直到洗完；追踪清空后再按 TTL 结束
                if (WatchLogic.shouldAutoStop(hasTracked, System.currentTimeMillis() - startedAt, ttl)) {
                    Log.i(TAG, "蹲守超时 $ttl ms，自动停止")
                    stopSelf()
                    break
                }
                // 可被点位切换打断的等待
                val waitedStart = System.currentTimeMillis()
                while (System.currentTimeMillis() - waitedStart < interval) {
                    if (kickSignal > waitedStart) break
                    delay(1000L)
                }
            }
        }
    }

    /**
     * 处理「标记已用·洗衣中」追踪：
     * - 仍在洗：每分钟实时刷新一条「洗衣中·预计 HH:mm·还剩 N 分钟」静默通知（带定闹钟/去海乐）
     * - 刚变空闲（洗完）：发高优先级取衣提醒，撤掉进行中通知，并自动取消该机器的追踪
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun handleTracking(
        devices: List<Device>,
        beforeDevices: List<Device>,
        trackedIds: Set<Long>,
        finishAlertEnabled: Boolean,
        sound: Boolean,
    ) {
        if (trackedIds.isEmpty()) return
        val nm = NotificationManagerCompat.from(this)
        val canNotify = NotifHelper.hasNotifyPermission(this)
        val nowMs = System.currentTimeMillis()
        devices.filter { it.id in trackedIds && it.category != DeviceCategory.SHOE }.forEach { d ->
            val before = beforeDevices.firstOrNull { it.id == d.id }?.bizStatus ?: BizStatus.UNKNOWN
            if (d.bizStatus == BizStatus.IDLE) {
                val cooled = nowMs - (lastFinishedNotifiedAt[d.id] ?: 0L) >= 30 * 60_000L
                if (finishAlertEnabled && WatchLogic.justFinished(before, BizStatus.IDLE) && cooled) {
                    lastFinishedNotifiedAt[d.id] = nowMs
                    // 一台追踪中的洗衣机洗完（与是否有通知权限无关，事件本身成立）
                    UmengAnalyticsManager.event(
                        UmengAnalyticsManager.EV_LAUNDRY_FINISHED,
                        mapOf("machine_type" to d.category.name),
                    )
                    if (canNotify) runCatching {
                        nm.notify(NotifHelper.finishNotifId(d.id), NotifHelper.finishNotification(this, d, sound))
                    }
                }
                // 已空闲：撤掉进行中通知，并自动结束对它的追踪
                runCatching { nm.cancel(NotifHelper.trackingNotifId(d.id)) }
                scope.launch { runCatching { prefs.removeTracked(d.id) } }
            } else {
                if (canNotify) runCatching {
                    nm.notify(NotifHelper.trackingNotifId(d.id), NotifHelper.trackingNotification(this, d))
                }
            }
        }
    }

    private suspend fun startForegroundCompat(point: WatchPoint) {        val notif = NotifHelper.watchForegroundNotification(this, emptyList(), point.name)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotifHelper.NOTIF_ID_WATCH, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotifHelper.NOTIF_ID_WATCH, notif)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun updateForegroundNotification(devices: List<Device>, pointName: String) {
        val nm = NotificationManagerCompat.from(this)
        if (!NotifHelper.hasNotifyPermission(this)) return
        runCatching { nm.notify(NotifHelper.NOTIF_ID_WATCH, NotifHelper.watchForegroundNotification(this, devices, pointName)) }
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        LaundryRepositoryHolder.repository.resetCooldown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchService"
        @Volatile
        var isRunning: Boolean = false
            private set
        const val ACTION_START_WATCH = "com.jsnu.laundry.action.START_WATCH"
        const val ACTION_STOP_WATCH = "com.jsnu.laundry.action.STOP_WATCH"
        const val ACTION_RESYNC = "com.jsnu.laundry.action.RESYNC"
        const val ACTION_KICK = "com.jsnu.laundry.action.KICK"
        const val EXTRA_QUIET_FIRST = "extra_quiet_first"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatchService::class.java)
                    .setAction(ACTION_START_WATCH)
                    .putExtra(EXTRA_QUIET_FIRST, false)
            )
        }

        /** 仅为「标记已用·洗衣中追踪」启动：首轮不逐台通知其它空闲机器，只静默追踪被标记机器 */
        fun startTrackingOnly(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatchService::class.java)
                    .setAction(ACTION_START_WATCH)
                    .putExtra(EXTRA_QUIET_FIRST, true)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }

        /** 点位切换后通知正在蹲守的服务立即重置基线并拉取新点位 */
        fun resync(context: Context) {
            if (!isRunning) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WatchService::class.java).setAction(ACTION_RESYNC)
                )
            }
        }

        /** 标记/取消「洗衣中」后让正在运行的服务立刻刷新一轮（不重置基线） */
        fun kick(context: Context) {
            if (!isRunning) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WatchService::class.java).setAction(ACTION_KICK)
                )
            }
        }
    }
}

/** 轻量单例：App 内共享 Repository（避免重复创建） */
object LaundryRepositoryHolder {
    lateinit var repository: LaundryRepository
        private set

    fun init(context: Context) {
        if (!::repository.isInitialized) {
            repository = LaundryRepository(ApiFactory.create(context))
        }
    }
}
