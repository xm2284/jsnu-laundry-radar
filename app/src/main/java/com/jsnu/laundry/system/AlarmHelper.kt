package com.jsnu.laundry.system

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import com.jsnu.laundry.analytics.UmengAnalyticsManager

/**
 * 一键系统倒计时 / 定点闹钟（系统原生，无需额外权限）。
 * 直接拉起系统时钟 App 的「新建闹钟」界面，由用户确认一次；
 * 这是跨 ROM（含 OPPO/ColorOS）最稳的方式。
 */
object AlarmHelper {

    /** 倒计时：对应「还有 N 分钟」 */
    fun createTimer(context: Context, waitMinutes: Int?, message: String) {
        val minutes = if (waitMinutes == null || waitMinutes <= 0) 1 else waitMinutes.coerceAtLeast(1)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60L)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent, "无法打开系统倒计时")
    }

    /** 定点闹钟：对应 finishTime 时刻 */
    fun createAlarm(context: Context, finishTime: String?, message: String) {
        val t = finishTime?.let {
            runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(it)?.time
            }.getOrNull()
        } ?: 0L
        if (t <= 0) {
            Toast.makeText(context, "该机器暂无结束时间，请用倒计时", Toast.LENGTH_SHORT).show()
            return
        }
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent, "无法打开系统闹钟")
    }

    private fun startSafely(context: Context, intent: Intent, failMsg: String) {
        try {
            context.startActivity(intent)
            // 系统闹钟/倒计时真正拉起成功才上报 set_alarm（点击但失败不计）
            UmengAnalyticsManager.event(UmengAnalyticsManager.EV_SET_ALARM)
        } catch (e: Exception) {
            Toast.makeText(context, "$failMsg：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
