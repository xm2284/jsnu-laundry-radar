package com.jsnu.laundry.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jsnu.laundry.MainActivity
import com.jsnu.laundry.system.AlarmHelper
import com.jsnu.laundry.system.LaunchHaierHelper
import com.jsnu.laundry.system.NotifHelper

/** 通知上的按钮：定闹钟 / 去海乐 / 打开 App（复制按钮已按用户要求移除） */
class WatchActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotifHelper.ACTION_ALARM -> {
                val name = intent.getStringExtra(NotifHelper.EXTRA_DEVICE_NAME) ?: "机器"
                val finish = intent.getStringExtra(NotifHelper.EXTRA_FINISH_TIME)
                if (finish.isNullOrBlank()) {
                    val wait = intent.getIntExtra(NotifHelper.EXTRA_WAIT_MIN, 0)
                    AlarmHelper.createTimer(context, wait, "$name · JSNU洗衣雷达")
                } else {
                    AlarmHelper.createAlarm(context, finish, "$name · 洗完提醒")
                }
            }
            NotifHelper.ACTION_HAIER -> {
                LaunchHaierHelper.launch(context)
            }
            NotifHelper.ACTION_OPEN_APP -> {
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}
