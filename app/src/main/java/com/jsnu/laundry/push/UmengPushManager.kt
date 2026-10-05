package com.jsnu.laundry.push

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jsnu.laundry.MainActivity
import com.umeng.message.PushAgent
import com.umeng.message.UmengNotificationClickHandler
import com.umeng.message.api.UPushRegisterCallback
import com.umeng.message.entity.UMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 友盟 U-Push 基础通道（本轮不接 OPPO/vivo/小米/华为厂商通道）。
 *
 * 仅实现：U-Push 后台人工下发通知 → 本机接收并展示 → 点击打开 JSNU洗衣雷达 主页面。
 * 本地的蹲守/洗完/追踪通知仍走原有 Android 本地通知，与本推送互不替代。
 */
object UmengPushManager {

    private const val TAG = "UMENG_PUSH"

    private val _registered = MutableStateFlow(false)
    /** 基础推送是否已成功注册（拿到 deviceToken） */
    val registered: StateFlow<Boolean> = _registered.asStateFlow()

    private val _deviceToken = MutableStateFlow<String?>(null)
    val deviceToken: StateFlow<String?> = _deviceToken.asStateFlow()

    @Volatile
    private var registerStarted = false

    /** 在友盟 common 正式 init 之后调用 */
    fun register(context: Context) {
        val app = context.applicationContext
        if (registerStarted) return
        registerStarted = true
        runCatching {
            val agent = PushAgent.getInstance(app)
            // 推送日志由 UMConfigure.setLogEnabled 统一控制（PushAgent 无独立开关）
            // 通知点击统一回到主页面（App 在运行也会经 singleTask 的 onNewIntent 合理处理）
            agent.notificationClickHandler = object : UmengNotificationClickHandler() {
                override fun handleMessage(ctx: Context?, msg: UMessage?) {
                    launchMain(ctx ?: app)
                }

                override fun dealWithCustomAction(ctx: Context?, msg: UMessage?) {
                    launchMain(ctx ?: app)
                }
            }
            agent.register(object : UPushRegisterCallback {
                override fun onSuccess(deviceToken: String?) {
                    _registered.value = true
                    _deviceToken.value = deviceToken
                    Log.i(TAG, "U-Push registered, token=${deviceToken?.take(8)}…")
                }

                override fun onFailure(code: String?, desc: String?) {
                    _registered.value = false
                    Log.w(TAG, "U-Push register failed: code=$code desc=$desc")
                }
            })
        }.onFailure {
            registerStarted = false
            Log.w(TAG, "U-Push register error: ${it.message}")
        }
    }

    private fun launchMain(context: Context) {
        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}
