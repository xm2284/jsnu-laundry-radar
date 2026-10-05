package com.jsnu.laundry.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jsnu.laundry.R
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.WaitCalc
import com.jsnu.laundry.watch.WatchActionReceiver
import com.jsnu.laundry.watch.WatchLogic

/** 通知渠道与通知构造（蹲守命中 + 洗完提醒 + 前台常驻） */
object NotifHelper {

    // v2：旧版渠道一旦被系统创建，重要性/声音无法再改，升级时换新 ID 并删除旧渠道
    const val CHANNEL_WATCH = "watch_status_v2"
    const val CHANNEL_ALERT = "watch_alert_v2"
    private val LEGACY_CHANNELS = listOf("watch_status", "watch_alert")

    const val ACTION_ALARM = "com.jsnu.laundry.action.WATCH_NOTIFY_ALARM"
    const val ACTION_HAIER = "com.jsnu.laundry.action.WATCH_NOTIFY_HAIER"
    const val ACTION_OPEN_APP = "com.jsnu.laundry.action.WATCH_NOTIFY_OPEN"

    const val EXTRA_DEVICE_NAME = "extra_device_name"
    const val EXTRA_FINISH_TIME = "extra_finish_time"
    const val EXTRA_WAIT_MIN = "extra_wait_min"

    const val NOTIF_ID_WATCH = 1001
    const val NOTIF_ID_SUMMARY = 1002
    private const val NOTIF_ID_FINISH_BASE = 2000

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 删除旧渠道，避免旧配置（低重要性/静音）锁死
        LEGACY_CHANNELS.forEach { runCatching { nm.deleteNotificationChannel(it) } }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "蹲守监控", NotificationManager.IMPORTANCE_LOW).apply {
                description = "「我要洗衣」蹲守时的常驻状态通知"
                setShowBadge(false)
                setSound(null, null) // 常驻静默
            }
        )
        val alertSound = defaultNotificationUri()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "洗衣提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "机器变为可约/空闲、或标记已用的机器洗完时提醒"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (alertSound != null) {
                    setSound(alertSound, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
            }
        )
    }

    /** 系统默认通知音（用户选项之一：调用系统自带提示音） */
    private fun defaultNotificationUri(): Uri? = runCatching {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }.getOrNull()

    fun hasNotifyPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean {
        return try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) {
            false
        }
    }

    /** 前台服务常驻通知：点位名 + N空闲 M可约 最快XX:XX */
    fun watchForegroundNotification(context: Context, devices: List<Device>, pointName: String = "当前点位"): Notification {
        val idle = devices.count { it.bizStatus == BizStatus.IDLE }
        val reservable = devices.count { it.bizStatus == BizStatus.RESERVABLE }
        val fault = devices.count { it.bizStatus == BizStatus.FAULT }
        val fastest = fastestText(devices)
        val main = Intent(context, WatchActionReceiver::class.java).setAction(ACTION_OPEN_APP)
        val piOpen = PendingIntent.getBroadcast(
            context, 9001, main, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setContentTitle("JSNU洗衣雷达 · 正在蹲守")
            .setContentText("$pointName：$idle 空闲 / $reservable 可约 / $fault 故障 · 最快$fastest")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(piOpen)
            .setColor(Color.rgb(18, 165, 148))
            .build()
    }

    /**
     * 蹲守首轮汇总通知：点「我要洗衣」后立刻收到一条，明确告知服务已启动 + 当前可用情况。
     * 解决「点了没反应/通知不见了」的体感问题。
     */
    fun summaryNotification(context: Context, devices: List<Device>, pointName: String, sound: Boolean = true): Notification {
        val idle = devices.count { it.bizStatus == BizStatus.IDLE }
        val reservable = devices.count { it.bizStatus == BizStatus.RESERVABLE }
        val pi = openAppPendingIntent(context, 9002)
        val text = if (devices.isEmpty()) "正在获取 $pointName 的机器状态，有空闲/可约会立刻提醒你"
        else "$pointName：当前 $idle 台空闲、$reservable 台可约，最快${fastestText(devices)}；之后一空出来立刻通知"
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("已开始蹲守 · $pointName")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(Color.rgb(18, 165, 148))
        if (!sound) builder.setSound(null)
        return builder.build()
    }

    private fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val i = Intent(context, WatchActionReceiver::class.java).setAction(ACTION_OPEN_APP)
        return PendingIntent.getBroadcast(
            context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 蹲守命中通知：机器名 + 剩余时间 + 【定闹钟】【去海乐】。
     * 每台机器用各自的 PendingIntent 请求码（否则多台通知的按钮会互相覆盖）；
     * autoCancel=false：通知一直保留在通知栏（用户手动划除），符合「多条并排、只要有后台就一直在」。
     * sound=false 时通知静音（关闭提示音后仍会收到，只是不响）。
     */
    fun hitNotification(context: Context, device: Device, sound: Boolean = true): Notification {
        val wait = WaitCalc.waitMinutes(device.finishTime)
        val waitText = when {
            device.bizStatus == BizStatus.IDLE -> "现在空闲，直接用"
            wait == null -> "查看剩余时间"
            wait <= 0 -> "马上就好"
            else -> "约 $wait 分钟后可用"
        }
        val base = Intent(context, WatchActionReceiver::class.java)
            .putExtra(EXTRA_DEVICE_NAME, device.name)
            .putExtra(EXTRA_FINISH_TIME, device.finishTime)
            .putExtra(EXTRA_WAIT_MIN, wait ?: 0)

        val rc = (device.id % 100000).toInt()
        val piAlarm = PendingIntent.getBroadcast(context, 3000 + rc, Intent(base).setAction(ACTION_ALARM), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piHaier = PendingIntent.getBroadcast(context, 4000 + rc, Intent(base).setAction(ACTION_HAIER), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piOpen = PendingIntent.getBroadcast(context, 5000 + rc, Intent(base).setAction(ACTION_OPEN_APP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("${device.name} · ${device.bizStatus.label}")
            .setContentText("$waitText · 点「去海乐」预约/支付")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false) // 一直保留，不自动消失
            .setOngoing(false)
            .setContentIntent(piOpen)
            .setColor(Color.rgb(18, 165, 148))
            .addAction(0, "定闹钟", piAlarm)
            .addAction(0, "去海乐", piHaier)
        if (!sound) builder.setSound(null)
        return builder.build()
    }

    /** 标记「已用」机器「洗完」通知：提醒拿衣服/晾晒，同样一直保留 */
    fun finishNotification(context: Context, device: Device, sound: Boolean = true): Notification {
        val base = Intent(context, WatchActionReceiver::class.java)
            .putExtra(EXTRA_DEVICE_NAME, device.name)
        val rc = (device.id % 100000).toInt()
        val piHaier = PendingIntent.getBroadcast(context, 6000 + rc, Intent(base).setAction(ACTION_HAIER), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piOpen = PendingIntent.getBroadcast(context, 7000 + rc, Intent(base).setAction(ACTION_OPEN_APP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("${device.name} 洗好了 🎉")
            .setContentText("衣服已洗完，记得去取、及时晾晒")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setContentIntent(piOpen)
            .setColor(Color.rgb(18, 165, 148))
            .addAction(0, "去海乐", piHaier)
        if (!sound) builder.setSound(null)
        return builder.build()
    }

    fun finishNotifId(deviceId: Long): Int = NOTIF_ID_FINISH_BASE + (deviceId % 1000).toInt()

    /** 蹲守命中（空闲/可约）通知 id：30000 段，与其它通知隔离避免互相覆盖 */
    fun hitNotifId(deviceId: Long): Int = 30000 + (deviceId % 100000).toInt()

    /** 「洗衣中」进行中通知 id：8000 段 */
    fun trackingNotifId(deviceId: Long): Int = 8000 + (deviceId % 100000).toInt()

    /**
     * 「标记已用·洗衣中」进行中通知：每分钟随轮询实时刷新预计完成时间/剩余分钟，
     * 走静默常驻渠道 + setOnlyAlertOnce，更新时不反复响；真正响的是洗完那一刻。带【定闹钟】【去海乐】。
     */
    fun trackingNotification(context: Context, device: Device): Notification {
        val wait = WaitCalc.waitMinutes(device.finishTime)
        val base = Intent(context, WatchActionReceiver::class.java)
            .putExtra(EXTRA_DEVICE_NAME, device.name)
            .putExtra(EXTRA_FINISH_TIME, device.finishTime)
            .putExtra(EXTRA_WAIT_MIN, wait ?: 0)
        val rc = (device.id % 100000).toInt()
        val piAlarm = PendingIntent.getBroadcast(context, 10000 + rc, Intent(base).setAction(ACTION_ALARM), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piHaier = PendingIntent.getBroadcast(context, 11000 + rc, Intent(base).setAction(ACTION_HAIER), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val piOpen = PendingIntent.getBroadcast(context, 12000 + rc, Intent(base).setAction(ACTION_OPEN_APP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setContentTitle("${device.name} · 洗衣中")
            .setContentText(WatchLogic.trackingText(device))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true) // 每分钟更新内容但不重复提示音
            .setOngoing(false)
            .setContentIntent(piOpen)
            .setColor(Color.rgb(18, 165, 148))
            .addAction(0, "定闹钟", piAlarm)
            .addAction(0, "去海乐", piHaier)
            .build()
    }

    /** 取消某台机器的「洗衣中」进行中通知（手动取消追踪 / 洗完时调用） */
    fun cancelTrackingNotification(context: Context, deviceId: Long) {
        if (!hasNotifyPermission(context)) return
        runCatching { NotificationManagerCompat.from(context).cancel(trackingNotifId(deviceId)) }
    }

    fun fastestText(devices: List<Device>): String {
        val usable = devices.filter { it.bizStatus == BizStatus.IDLE || it.bizStatus == BizStatus.RESERVABLE }
        if (usable.isEmpty()) return "—"
        val wait = usable.minOfOrNull { WaitCalc.waitMinutes(it.finishTime) ?: 0 } ?: 0
        return if (wait <= 0) "现在" else "${wait}分钟"
    }
}
