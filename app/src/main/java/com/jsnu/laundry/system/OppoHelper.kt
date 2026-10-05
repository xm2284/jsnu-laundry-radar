package com.jsnu.laundry.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 国产 ROM（重点 OPPO/ColorOS）保活引导。
 * ColorOS 的电池/自启动管理在系统应用「安全中心 / 手机管家」里，
 * 直接跳专属组件比通用 Intent 可靠；跳不到再回退应用详情页。
 */
object OppoHelper {

    val isOppo: Boolean
        get() = isBrand("oppo") || isBrand("realme") || isBrand("oneplus")

    fun isBrand(vararg names: String): Boolean {
        val b = Build.MANUFACTURER.lowercase()
        return names.any { b.contains(it) }
    }

    /** 是否已加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }

    /** 电池不优化：OPPO 走 ColorOS 电池管理，其他走系统请求 */
    fun openBatteryOptimize(context: Context) {
        if (isOppo) {
            for (target in OPPO_BATTERY_COMPONENTS) {
                if (startComponent(context, target)) return
            }
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (startSafely(context, intent)) return
        startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** OPPO 自启动管理 */
    fun openAutoStart(context: Context) {
        if (isOppo) {
            for (target in OPPO_AUTOSTART_COMPONENTS) {
                if (startComponent(context, target)) return
            }
        }
        openAppSettings(context)
    }

    /** OPPO 允许后台运行（后台耗电管理） */
    fun openBackgroundManagement(context: Context) {
        if (isOppo) {
            for (target in OPPO_BACKGROUND_COMPONENTS) {
                if (startComponent(context, target)) return
            }
        }
        openAppSettings(context)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        startSafely(context, intent)
    }

    private fun startComponent(context: Context, target: Pair<String, String>): Boolean = try {
        val intent = Intent().setComponent(ComponentName(target.first, target.second))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private fun startSafely(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    /** (包名, 组件) 候选，按 ColorOS 版本覆盖 */
    private val OPPO_AUTOSTART_COMPONENTS = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
    )

    private val OPPO_BATTERY_COMPONENTS = listOf(
        "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgauging.PowerUsageModelActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.batterydoctor.BatteryDoctorActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.batterydoctor.BatteryDoctorActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.battery.BatteryAppListActivity",
    )

    private val OPPO_BACKGROUND_COMPONENTS = listOf(
        "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgauging.PowerUsageModelActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.batterydoctor.BatteryDoctorActivity",
    )
}
