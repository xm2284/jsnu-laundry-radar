package com.jsnu.laundry.watch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager 周期兜底：15 分钟一次后台刷新缓存 + 小组件。
 * （真正的高频蹲守由前台服务 WatchService 承担）
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            LaundryRepositoryHolder.init(applicationContext)
            val prefs = PrefsStore(applicationContext)
            val point = runCatching { prefs.activePoint.first() }.getOrElse { com.jsnu.laundry.data.prefs.WatchPoint() }
            val result = LaundryRepositoryHolder.repository.fetch(point)
            if (result.error != null) return Result.retry()
            WidgetUpdater.update(applicationContext, result.devices, point)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val NAME = "jsnu_laundry_poll"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
