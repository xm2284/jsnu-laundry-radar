package com.jsnu.laundry.data.agc

import android.content.Context
import android.util.Log
import com.huawei.agconnect.AGConnectInstance
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.agconnect.AGCRoutePolicy
import com.huawei.agconnect.auth.AGConnectAuth
import com.huawei.agconnect.cloud.database.AGConnectCloudDB
import com.huawei.agconnect.cloud.database.CloudDBZone
import com.huawei.agconnect.cloud.database.CloudDBZoneConfig
import com.huawei.agconnect.cloud.database.CloudDBZoneQuery
import com.huawei.agconnect.cloud.database.CloudDBZoneSnapshot
import com.huawei.hmf.tasks.Task
import com.jsnu.laundry.model.LaundryUser
import com.jsnu.laundry.model.ObjectTypeInfoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import kotlin.coroutines.resume

/**
 * 华为 AGC 匿名认证 + CloudDB 用户仓库（单例）。
 *
 * 正确链路（严格按需求）：
 *   App 启动（洗衣主流程照常）→ 后台异步初始化 AGC → 没有用户就匿名登录 → 拿 AGC UID
 *   → 打开存储区 [ZONE_NAME]（全小写 laundry）→ 按 uid 查 LaundryUser
 *   → 不存在则新建；存在则只更新 lastActiveAt/appVersion（不动 uid/createdAt/installId）。
 *
 * 硬性要求：任何一步失败都只记录日志、置为 FAILED，绝不抛到主线程、绝不影响洗衣主功能。
 */
object AgcUserRepository {

    private const val TAG_INIT = "AGC_INIT"
    private const val TAG_AUTH = "AGC_AUTH"
    private const val TAG_DB = "AGC_CLOUDDB"
    const val ZONE_NAME = "laundry"

    enum class State { IDLE, WORKING, READY, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _uid = MutableStateFlow("")
    /** 当前 AGC 匿名 UID（未登录为空） */
    val uid: StateFlow<String> = _uid.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    @Volatile private var instance: AGConnectInstance? = null
    @Volatile private var cloudDB: AGConnectCloudDB? = null
    @Volatile private var zone: CloudDBZone? = null

    /** Application.onCreate 调用：幂等、后台执行、失败自降级 */
    fun init(context: Context) {
        val app = context.applicationContext
        if (_state.value == State.WORKING || _state.value == State.READY) return
        _state.value = State.WORKING
        scope.launch {
            connectMutex.withLock { runCatching { connect(app) }.onFailure { fail(it) } }
        }
    }

    /** 失败后手动重试（设置页用） */
    fun retry(context: Context) = init(context.applicationContext)

    private fun fail(e: Throwable) {
        Log.w(TAG_INIT, "AGC unavailable: ${e.message}")
        _lastError.value = e.message
        _state.value = State.FAILED
    }

    private suspend fun connect(context: Context) {
        // 1) 全局初始化 CloudDB（重复初始化会抛，忽略）
        runCatching { AGConnectCloudDB.initialize(context) }

        // 2) 明确中国区，构建/复用 AGConnectInstance
        val agcInstance = instance ?: run {
            val options = AGConnectOptionsBuilder()
                .setRoutePolicy(AGCRoutePolicy.CHINA)
                .build(context)
            val ins = try {
                AGConnectInstance.buildInstance(options)
            } catch (e: Exception) {
                runCatching { AGConnectInstance.getInstance() }.getOrNull() ?: throw e
            }
            instance = ins
            ins
        }

        // 3) 匿名认证：已有用户直接复用，否则匿名登录拿 UID
        val auth = AGConnectAuth.getInstance(agcInstance)
        var currentUser = auth.currentUser
        if (currentUser == null) {
            Log.i(TAG_AUTH, "no signed user, signing in anonymously...")
            auth.signInAnonymously().await()
            currentUser = auth.currentUser
        }
        val agcUid = currentUser?.uid
        if (agcUid.isNullOrBlank()) {
            fail(IllegalStateException("匿名登录后 UID 仍为空"))
            return
        }
        _uid.value = agcUid
        Log.i(TAG_AUTH, "anonymous signed in, uid=${agcUid.take(8)}…")

        // 4) CloudDB 实例 + 注册对象类型（版本以 ObjectTypeInfoHelper 为准，须与控制台一致）
        val db = cloudDB ?: AGConnectCloudDB.getInstance(
            agcInstance, AGConnectAuth.getInstance(agcInstance)
        ).also {
            it.createObjectType(ObjectTypeInfoHelper.getObjectTypeInfo())
            cloudDB = it
        }

        // 5) 打开全小写 laundry 存储区（云缓存 + 公共 + 本地持久化）
        var z = zone
        if (z == null) {
            val config = CloudDBZoneConfig(
                ZONE_NAME,
                CloudDBZoneConfig.CloudDBZoneSyncProperty.CLOUDDBZONE_CLOUD_CACHE,
                CloudDBZoneConfig.CloudDBZoneAccessProperty.CLOUDDBZONE_PUBLIC,
            )
            config.setPersistenceEnabled(true)
            z = db.openCloudDBZone2(config, true).await()
            zone = z
            Log.i(TAG_DB, "zone '$ZONE_NAME' opened")
        }

        // 6) 按 UID 查询，存在则更新活跃信息，不存在则首次创建
        upsertLaundryUser(z, agcUid, context)

        _state.value = State.READY
        Log.i(TAG_INIT, "AGC ready, uid=${agcUid.take(8)}…")
    }

    private suspend fun upsertLaundryUser(z: CloudDBZone, agcUid: String, context: Context) {
        val policy = CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY
        val query = CloudDBZoneQuery.where(LaundryUser::class.java).equalTo("uid", agcUid)
        val snapshot: CloudDBZoneSnapshot<LaundryUser> = z.executeQuery(query, policy).await()
        val objects = snapshot.snapshotObjects
        val existing: LaundryUser? = if (objects.size() > 0) objects[0] else null
        snapshot.release()

        val now = Date()
        val record = if (existing != null) {
            // 已存在：只更新 lastActiveAt / appVersion，保留 uid/createdAt/installId
            existing.apply {
                lastActiveAt = now
                appVersion = appVersionName(context)
            }
        } else {
            // 首次创建：uid=AGC UID，installId=本机安装标识，createdAt/lastActiveAt=当前时间
            LaundryUser().apply {
                uid = agcUid
                installId = InstallId.get(context)
                createdAt = Date(InstallId.createdAt(context))
                lastActiveAt = now
                appVersion = appVersionName(context)
            }
        }
        val upserted = z.executeUpsert(record).await()
        Log.i(TAG_DB, "LaundryUser ${if (existing != null) "updated" else "created"}, rows=$upserted, uid=${agcUid.take(8)}…")
    }

    private fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** HMS Task → 挂起结果（失败以异常抛出，由上层 runCatching 统一降级） */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWith(Result.failure(e)) }
    }
}
