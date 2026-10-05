package com.jsnu.laundry.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * 把桌面小组件「固定」到主屏幕：Android 8（API26）起支持 requestPinAppWidget，
 * 会弹系统确认框，用户点确认即添加；不支持的 ROM 返回 false，由 UI 引导手动添加。
 *
 * ColorOS 12+ 注意：未授予「创建桌面快捷方式」权限时 requestPinAppWidget 会静默失败
 * （返回 true 但不弹框）。因此调用方应在调用后延迟数秒检查 getAppWidgetIds 是否增加，
 * 未增加则判定失败并引导手动添加。
 */
object WidgetPinHelper {

    fun canPin(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val mgr = context.getSystemService(Context.APPWIDGET_SERVICE) as? AppWidgetManager ?: return false
        return runCatching { mgr.isRequestPinAppWidgetSupported }.getOrDefault(false)
    }

    /** 当前已固定到桌面的指定 provider 卡片数量 */
    fun pinnedCount(context: Context, providerClass: Class<out AppWidgetProvider>): Int {
        val mgr = context.getSystemService(Context.APPWIDGET_SERVICE) as? AppWidgetManager ?: return 0
        return runCatching { mgr.getAppWidgetIds(ComponentName(context, providerClass))?.size ?: 0 }.getOrDefault(0)
    }

    /**
     * 发起系统添加请求。
     * @return true=已发起请求（不代表用户已确认添加）；false=系统不支持
     */
    fun requestPin(context: Context, providerClass: Class<out AppWidgetProvider>): Boolean {
        if (!canPin(context)) return false
        val mgr = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val provider = ComponentName(context, providerClass)
        return runCatching { mgr.requestPinAppWidget(provider, null, null) }.isSuccess
    }

    /** 便捷方法：添加统计卡片（默认） */
    fun requestPinStats(context: Context): Boolean = requestPin(context, LaundryAppWidgetProvider::class.java)

    /** 便捷方法：添加洗衣中追踪卡片 */
    fun requestPinTracking(context: Context): Boolean = requestPin(context, TrackingAppWidgetProvider::class.java)
}
