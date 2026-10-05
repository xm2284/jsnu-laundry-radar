package com.jsnu.laundry.analytics

import android.content.Context
import android.util.Log
import com.jsnu.laundry.BuildConfig
import com.jsnu.laundry.push.UmengPushManager
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure

/**
 * 友盟 U-App 移动统计 + 合规初始化总控（统计/推送共用 common，初始化统一在这里）。
 *
 * 合规要求（硬性）：
 * - 用户同意隐私政策前只调用 [preInit]（预初始化，不采集个人信息），绝不调用正式 init；
 * - 用户点击「同意」后才 [agreeAndInit]（写同意标记 + UMConfigure.init + 注册推送）；
 * - 用户不同意则永不正式初始化，但洗衣核心功能照常可用；
 * - 客户端只使用：AppKey（U-App/U-Push 共用）+ Umeng Message Secret（U-Push 组件化 SDK 初始化参数）。
 *   「App Master Secret」是服务端 OpenAPI 凭据，本工程不持有、不写入客户端。
 */
object UmengAnalyticsManager {

    private const val TAG = "UMENG_ANALYTICS"
    private const val SP_NAME = "umeng_prefs"
    private const val KEY_AGREED = "privacy_agreed"

    // 自定义事件名（与需求一一对应，不要把动态值当事件名）
    const val EV_MANUAL_REFRESH = "manual_refresh"
    const val EV_START_WATCH = "start_watch"
    const val EV_STOP_WATCH = "stop_watch"
    const val EV_TRACK_MACHINE = "track_machine"
    const val EV_CANCEL_TRACKING = "cancel_tracking"
    const val EV_LAUNDRY_FINISHED = "laundry_finished"
    const val EV_OPEN_HAILE = "open_haile"
    const val EV_SET_ALARM = "set_alarm"

    @Volatile
    private var formalInitialized = false

    @Volatile
    private var appContext: Context? = null

    private fun appKey(): String = BuildConfig.UMENG_APPKEY.trim()
    private fun messageSecret(): String = BuildConfig.UMENG_MESSAGE_SECRET.trim()
    private fun channel(): String = BuildConfig.UMENG_CHANNEL.trim().ifBlank { "official" }

    /** 是否已同意隐私政策（SharedPreferences 同步读，供 Application 启动时判断） */
    fun isAgreed(context: Context): Boolean {
        val sp = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_AGREED, false)
    }

    /**
     * Application.onCreate 调用：
     * - 始终做一次 preInit（合规允许，不采集）；
     * - 若此前已同意隐私政策，则直接正式初始化。
     */
    fun onAppCreate(context: Context) {
        val app = context.applicationContext
        appContext = app
        runCatching {
            if (appKey().isBlank()) {
                Log.w(TAG, "未配置友盟 AppKey，跳过友盟初始化（不影响洗衣功能）")
                return
            }
            UMConfigure.preInit(app, appKey(), channel())
            if (isAgreed(app)) initInternal(app)
        }.onFailure { Log.w(TAG, "preInit failed: ${it.message}") }
    }

    /** 用户在首次隐私弹窗点击「同意」：落标记并正式初始化 */
    fun agreeAndInit(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AGREED, true).apply()
        initInternal(app)
    }

    /** 正式初始化（仅在同意后调用一次） */
    @Synchronized
    private fun initInternal(context: Context) {
        if (formalInitialized) return
        val app = context.applicationContext
        appContext = app
        if (appKey().isBlank()) {
            Log.w(TAG, "友盟 AppKey 为空，跳过正式初始化")
            return
        }
        runCatching {
            // 第 5 参是 Umeng Message Secret（U-Push 组件化 SDK 官方要求的客户端参数，非服务端 Master Secret）
            UMConfigure.init(
                app, appKey(), channel(),
                UMConfigure.DEVICE_TYPE_PHONE, messageSecret(),
            )
            // 仅 debug 包打开 SDK 日志，便于真机定位；release 关闭
            UMConfigure.setLogEnabled(BuildConfig.DEBUG)
            // 使用默认自动页面采集，不额外申请任何与统计无关的权限
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO)
            formalInitialized = true
            Log.i(TAG, "U-App initialized, channel=${channel()}")
            // 统计初始化完成后再注册基础推送
            UmengPushManager.register(context)
        }.onFailure { Log.w(TAG, "U-App init failed: ${it.message}") }
    }

    /** 是否已正式启用（同意 + init 成功 + AppKey 非空） */
    fun isEnabled(): Boolean = formalInitialized

    /**
     * 上报一个自定义事件（未初始化/未同意时安全 no-op）。
     * 只携带必要的非个人参数（机型类别/内部点位 id/版本），禁止上传身份信息。
     */
    @JvmOverloads
    fun event(name: String, params: Map<String, String?> = emptyMap()) {
        if (!formalInitialized) return
        val ctx = appContext ?: return
        runCatching {
            if (params.isEmpty()) {
                MobclickAgent.onEvent(ctx, name)
            } else {
                // 过滤掉 null/空值；onEventObject 形参为 Map<String, Any>
                val map = HashMap<String, Any>(params.size)
                params.forEach { (k, v) -> if (!v.isNullOrBlank()) map[k] = v }
                MobclickAgent.onEventObject(ctx, name, map)
            }
        }.onFailure { Log.w(TAG, "event $name failed: ${it.message}") }
    }
}
