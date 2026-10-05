package com.jsnu.laundry.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.watch.LaundryRepositoryHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 「洗衣中追踪」桌面卡片：显示被标记机器的实时倒计时、预计洗完时间，
 * 带定闹钟/去海乐/取消追踪按钮。Chronometer 由系统驱动，App 进程被杀也继续走时。
 */
class TrackingAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WIDGET_SCOPE.launch {
            runCatching {
                LaundryRepositoryHolder.init(context)
                WidgetUpdater.updateTracking(context)
            }
            pending.finish()
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pending = goAsync()
        WIDGET_SCOPE.launch {
            runCatching {
                LaundryRepositoryHolder.init(context)
                WidgetUpdater.updateTracking(context)
            }
            pending.finish()
        }
    }

    companion object {
        private val WIDGET_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
