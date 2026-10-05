package com.jsnu.laundry.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jsnu.laundry.analytics.UmengAnalyticsManager

/**
 * 一键拉起海乐生活 App。
 *
 * 关键：targetSdk 30+ 受 Android 11 包可见性限制，必须在 AndroidManifest 用 <queries>
 * 声明目标包，否则 getLaunchIntentForPackage / getPackageInfo 会返回空、误判「未安装」跳商店。
 * 启动按「启动入口 Intent → 查 Launcher Activity → 显式组件」三重兜底，全部失败才去商店。
 */
object LaunchHaierHelper {

    /** 候选包名：海乐生活（用户端）优先，其它历史/分身包名兜底 */
    private val CANDIDATE_PACKAGES = listOf(
        "com.yunshang.haile_life",
        "com.yunshang.haileshenghuo",
        "com.yunshang.campuswashingbusiness",
    )

    /** 供测试/诊断使用的候选包名（官方用户端排第一） */
    fun candidatePackages(): List<String> = CANDIDATE_PACKAGES

    fun launch(context: Context) {
        // 统一埋点：用户点击「去海乐」（一处覆盖首页/设备卡/通知等所有入口）
        UmengAnalyticsManager.event(UmengAnalyticsManager.EV_OPEN_HAILE)
        for (pkg in CANDIDATE_PACKAGES) {
            val intent = buildLaunchIntent(context, pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (ok) return
        }
        openMarketSearch(context)
    }

    /** 构造某包名的启动 Intent，三重方式依次尝试 */
    private fun buildLaunchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        // 1) 标准启动入口
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
        // 2) 查询带 LAUNCHER 的 Activity（部分 App getLaunchIntentForPackage 返回 null 但能查到）
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        val resolved = runCatching { pm.queryIntentActivities(query, 0) }.getOrNull()
        val ri = resolved?.firstOrNull()
        if (ri != null) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
            }
        }
        // 3) 确认已安装但前两法失败：用包名级 MAIN intent 让系统解析
        return if (isInstalled(context, pkg)) {
            Intent(Intent.ACTION_MAIN).setPackage(pkg)
        } else null
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** 商店搜索（不依赖包名精确匹配，OPPO 应用市场也能搜到） */
    private fun openMarketSearch(context: Context) {
        val uris = listOf(
            Uri.parse("market://details?id=com.yunshang.haile_life"),
            Uri.parse("market://search?q=海乐生活"),
            Uri.parse("https://sj.qq.com/appdetail/com.yunshang.haile_life"),
        )
        for (uri in uris) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // 尝试下一个
            } catch (_: Exception) {
                // 尝试下一个
            }
        }
        Toast.makeText(context, "未安装海乐生活，请先去应用商店下载", Toast.LENGTH_LONG).show()
    }
}
