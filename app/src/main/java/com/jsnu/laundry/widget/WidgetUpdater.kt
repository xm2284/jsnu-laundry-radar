package com.jsnu.laundry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.jsnu.laundry.MainActivity
import com.jsnu.laundry.R
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.data.prefs.WatchPoint
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.watch.LaundryRepositoryHolder
import com.jsnu.laundry.watch.WatchLogic
import kotlinx.coroutines.flow.first

/** 小组件 UI 状态 */
data class WidgetUiState(
    val pointName: String = "当前点位",
    val idle: Int = 0,
    val reservable: Int = 0,
    val fault: Int = 0,
    val fastest: String = "—",
) {
    companion object {
        fun from(devices: List<Device>, pointName: String): WidgetUiState = WidgetUiState(
            pointName = pointName,
            idle = devices.count { it.bizStatus == BizStatus.IDLE },
            reservable = devices.count { it.bizStatus == BizStatus.RESERVABLE },
            fault = devices.count { it.bizStatus == BizStatus.FAULT },
            fastest = NotifHelper.fastestText(devices),
        )
    }
}

/** 计算小组件状态并用原生 RemoteViews 刷新所有桌面实例 */
object WidgetUpdater {

    // ========== 统计卡片（widget_laundry） ==========

    suspend fun update(context: android.content.Context, devices: List<Device>, point: WatchPoint) {
        render(context, WidgetUiState.from(devices, point.name))
    }

    fun render(context: android.content.Context, state: WidgetUiState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, LaundryAppWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildViews(context, state)
        ids.forEach { id -> runCatching { manager.updateAppWidget(id, views) } }
    }

    private fun buildViews(context: android.content.Context, state: WidgetUiState): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_laundry)
        rv.setTextViewText(R.id.widget_point, state.pointName)
        rv.setTextViewText(R.id.widget_idle_num, state.idle.toString())
        rv.setTextViewText(R.id.widget_res_num, state.reservable.toString())
        rv.setTextViewText(R.id.widget_fault_num, state.fault.toString())

        // 点击卡片整体打开 App
        rv.setOnClickPendingIntent(R.id.widget_root, activityPi(context, 8001, null))

        // 三个按钮：刷新 / 蹲守 / 去海乐（全部用 getActivity，ColorOS 上比 Broadcast 可靠）
        rv.setOnClickPendingIntent(R.id.widget_btn_refresh, activityPi(context, 8002, MainActivity.ACTION_REFRESH))
        rv.setOnClickPendingIntent(R.id.widget_btn_watch, activityPi(context, 8003, MainActivity.ACTION_TOGGLE_WATCH))
        rv.setOnClickPendingIntent(R.id.widget_btn_haier, activityPi(context, 8004, MainActivity.ACTION_LAUNCH_HAIER))
        return rv
    }

    // ========== 洗衣中追踪卡片（widget_tracking） ==========

    /** 刷新所有「洗衣中追踪」卡片实例：从 prefs 读追踪 id、从 repository 快照读设备 */
    suspend fun updateTracking(context: android.content.Context) {
        val prefs = PrefsStore(context)
        val laundryPrefs = prefs.prefs.first()
        val trackedIds = laundryPrefs.trackedIds
        val devices = LaundryRepositoryHolder.repository.snapshot.value
        val point = laundryPrefs.activePoint
        val tracked = devices.firstOrNull { it.id in trackedIds }
        renderTracking(context, tracked, point.name, trackedIds.size)
    }

    fun renderTracking(
        context: android.content.Context,
        tracked: Device?,
        pointName: String,
        trackedCount: Int,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TrackingAppWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildTrackingViews(context, tracked, pointName, trackedCount)
        ids.forEach { id -> runCatching { manager.updateAppWidget(id, views) } }
    }

    private fun buildTrackingViews(
        context: android.content.Context,
        tracked: Device?,
        pointName: String,
        trackedCount: Int,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_tracking)
        rv.setTextViewText(R.id.widget_tracking_point, pointName)

        if (tracked != null) {
            // 有追踪中的机器：显示详情 + 倒计时 + 按钮栏
            rv.setViewVisibility(R.id.widget_tracking_content, android.view.View.VISIBLE)
            rv.setViewVisibility(R.id.widget_tracking_empty, android.view.View.GONE)
            rv.setViewVisibility(R.id.widget_tracking_btn_bar, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.widget_tracking_machine, tracked.name)
            // 预计洗完时间
            val finishText = tracked.finishTime?.let { WatchLogic.finishHhmm(it) } ?: "—"
            rv.setTextViewText(R.id.widget_tracking_finish, "预计 $finishText 洗完")
            // Chronometer 实时倒计时（系统驱动，App 进程被杀也继续走）
            val remainingMs = WatchLogic.remainingMs(tracked.finishTime)
            if (remainingMs != null && remainingMs > 0) {
                rv.setChronometer(
                    R.id.widget_tracking_countdown,
                    SystemClock.elapsedRealtime() + remainingMs,
                    "还剩 %s",
                    true,
                )
                rv.setChronometerCountDown(R.id.widget_tracking_countdown, true)
            } else {
                rv.setChronometer(R.id.widget_tracking_countdown, SystemClock.elapsedRealtime(), "已完成", false)
                rv.setChronometerCountDown(R.id.widget_tracking_countdown, false)
            }
            // 按钮：定闹钟 / 去海乐 / 取消追踪
            rv.setOnClickPendingIntent(R.id.widget_tracking_btn_alarm, activityPi(context, 8101, MainActivity.ACTION_WIDGET_ALARM))
            rv.setOnClickPendingIntent(R.id.widget_tracking_btn_haier, activityPi(context, 8102, MainActivity.ACTION_LAUNCH_HAIER))
            rv.setOnClickPendingIntent(R.id.widget_tracking_btn_cancel, activityPi(context, 8103, MainActivity.ACTION_CANCEL_TRACK))
        } else {
            // 无追踪：显示空状态，隐藏按钮栏
            rv.setViewVisibility(R.id.widget_tracking_content, android.view.View.GONE)
            rv.setViewVisibility(R.id.widget_tracking_empty, android.view.View.VISIBLE)
            rv.setViewVisibility(R.id.widget_tracking_btn_bar, android.view.View.GONE)
            val emptyText = if (trackedCount > 0) "追踪中暂无数据，打开 App 查看" else "暂无洗衣中追踪\n在首页标记已用的机器会显示在这里"
            rv.setTextViewText(R.id.widget_tracking_empty, emptyText)
            rv.setOnClickPendingIntent(R.id.widget_tracking_empty, activityPi(context, 8100, null))
        }
        return rv
    }

    // ========== 工具 ==========

    /** 构造打开 MainActivity 并携带 action 的 PendingIntent */
    private fun activityPi(context: android.content.Context, requestCode: Int, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) intent.putExtra(MainActivity.EXTRA_WIDGET_ACTION, action)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
