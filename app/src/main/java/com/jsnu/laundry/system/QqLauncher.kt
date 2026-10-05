package com.jsnu.laundry.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 联系作者 QQ（协议来自 GitHub 上安卓 QQ scheme 实测汇总）：
 * 1) mqqapi://card/show_pslcard 打开「个人资料卡」，可直接点加好友（个人号最稳）
 * 2) mqqwpa 临时会话兜底（需对方开启临时会话）
 * 3) 浏览器 wpa 链接兜底
 * 4) 都失败则复制 QQ 号并提示
 */
object QqLauncher {

    const val AUTHOR_QQ = "2284517861"

    /** 按优先级排列的唤起协议（测试可直接断言，防止写错） */
    internal fun schemes(): List<String> = listOf(
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$AUTHOR_QQ&card_type=person&source=sharecard",
        "mqqapi://card/show_pslcard?uin=$AUTHOR_QQ",
        "mqqwpa://im/chat?chat_type=wpa&uin=$AUTHOR_QQ&version=1&src_type=web",
    )

    fun launch(context: Context) {
        for (s in schemes()) {
            val ok = runCatching {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(s))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setPackage("com.tencent.mobileqq")
                context.startActivity(i)
            }.isSuccess
            if (ok) return
        }
        val webOk = runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wpa.qq.com/msgrd?v=3&uin=$AUTHOR_QQ&site=qq&menu=yes")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!webOk) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("qq", AUTHOR_QQ))
            Toast.makeText(context, "未检测到 QQ，QQ号 $AUTHOR_QQ 已复制", Toast.LENGTH_LONG).show()
        }
    }
}
