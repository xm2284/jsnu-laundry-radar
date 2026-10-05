package com.jsnu.laundry

import android.app.Application
import com.jsnu.laundry.analytics.UmengAnalyticsManager
import com.jsnu.laundry.data.agc.AgcUserRepository
import com.jsnu.laundry.system.NotifHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LaundryApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotifHelper.ensureChannels(this)

        // 友盟 U-App/U-Push：只做合规预初始化；已同意隐私政策才正式初始化（内部再注册基础推送）。
        // 任何异常都不影响洗衣主流程。
        runCatching { UmengAnalyticsManager.onAppCreate(this) }

        // 华为 AGC：后台异步匿名认证 + 打开 laundry 区 + 建/更 LaundryUser；失败自降级、不阻塞启动。
        runCatching { AgcUserRepository.init(this) }
    }
}
