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
 * 桌面小组件（原生 RemoteViews 实现，国产 ROM 兼容性最好）。
 * 添加到桌面 / 系统周期更新时主动拉取一次当前点位并渲染。
 */
class LaundryAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WIDGET_SCOPE.launch {
            runCatching {
                LaundryRepositoryHolder.init(context)
                val prefs = PrefsStore(context)
                val point = prefs.activePoint.first()
                var res = LaundryRepositoryHolder.repository.fetch(point)
                if (res.devices.isEmpty()) {
                    res = LaundryRepositoryHolder.repository.fetch(point)
                }
                WidgetUpdater.update(context, res.devices, point)
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
                val prefs = PrefsStore(context)
                val point = prefs.activePoint.first()
                val res = LaundryRepositoryHolder.repository.fetch(point)
                WidgetUpdater.update(context, res.devices, point)
            }
            pending.finish()
        }
    }

    companion object {
        private val WIDGET_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
