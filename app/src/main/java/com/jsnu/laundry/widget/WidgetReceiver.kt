package com.jsnu.laundry.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.watch.LaundryRepositoryHolder
import com.jsnu.laundry.watch.WatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 小组件快捷动作：
 * REFRESH_WIDGET -> 立即拉取并刷新小组件
 * TOGGLE_WATCH  -> 切换「我要洗衣」蹲守开关
 */
class WidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            runCatching {
                LaundryRepositoryHolder.init(context)
                val prefs = PrefsStore(context)
                when (intent.action) {
                    ACTION_REFRESH -> {
                        val point = prefs.activePoint.first()
                        val res = LaundryRepositoryHolder.repository.fetch(point)
                        WidgetUpdater.update(context, res.devices, point)
                    }
                    ACTION_TOGGLE_WATCH -> {
                        if (WatchService.isRunning) {
                            WatchService.stop(context)
                        } else {
                            WatchService.start(context)
                        }
                        val point = prefs.activePoint.first()
                        val res = LaundryRepositoryHolder.repository.fetch(point)
                        WidgetUpdater.update(context, res.devices, point)
                    }
                }
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.jsnu.laundry.action.REFRESH_WIDGET"
        const val ACTION_TOGGLE_WATCH = "com.jsnu.laundry.action.TOGGLE_WATCH"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
