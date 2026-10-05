package com.jsnu.laundry.data.agc

import android.content.Context
import java.util.UUID

/**
 * 本机安装实例 ID（installId）。
 *
 * 与华为 AGC 匿名 UID 的区别：
 * - installId：本 App 在本机的一次安装标识，首次生成后用 SharedPreferences 持久化，之后一直复用；
 *   卸载重装会重新生成（符合预期）。它不是云端身份，也不用于 CloudDB 权限判断。
 * - AGC UID：匿名登录后由 AGC 下发，是 LaundryUser 的主键与云端身份。
 */
object InstallId {

    private const val SP_NAME = "agc_install_prefs"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_CREATED_AT = "install_created_at"

    @Volatile
    private var cached: String? = null

    /** 返回（必要时生成并持久化）本机 installId */
    fun get(context: Context): String {
        cached?.let { return it }
        val sp = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        var id = sp.getString(KEY_INSTALL_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "")
            sp.edit()
                .putString(KEY_INSTALL_ID, id)
                .putLong(KEY_CREATED_AT, System.currentTimeMillis())
                .apply()
        }
        cached = id
        return id
    }

    /** 首次安装时间（毫秒），无记录时回退为当前时间 */
    fun createdAt(context: Context): Long {
        val sp = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val v = sp.getLong(KEY_CREATED_AT, 0L)
        return if (v > 0L) v else System.currentTimeMillis()
    }
}
