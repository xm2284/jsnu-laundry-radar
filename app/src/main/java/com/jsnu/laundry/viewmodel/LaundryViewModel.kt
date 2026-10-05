package com.jsnu.laundry.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jsnu.laundry.data.LaundryRepository
import com.jsnu.laundry.analytics.UmengAnalyticsManager
import com.jsnu.laundry.data.agc.AgcUserRepository
import com.jsnu.laundry.data.model.BizStatus
import com.jsnu.laundry.data.model.Device
import com.jsnu.laundry.data.model.WaitCalc
import com.jsnu.laundry.data.prefs.LaundryPrefs
import com.jsnu.laundry.data.prefs.PrefsStore
import com.jsnu.laundry.data.prefs.WatchPoint
import com.jsnu.laundry.data.stat.HourStat
import com.jsnu.laundry.data.stat.UsageStats
import com.jsnu.laundry.system.NotifHelper
import com.jsnu.laundry.watch.ApiFactory
import com.jsnu.laundry.watch.LaundryRepositoryHolder
import com.jsnu.laundry.watch.WatchService
import com.jsnu.laundry.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeviceFilter(val label: String) { ALL("全部"), IDLE("空闲"), RESERVABLE("可约"), FAV("收藏"), TRACKED("已用") }

data class UiState(
    val devices: List<Device> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val lastUpdatedAt: Long = 0L,
    val pref: LaundryPrefs = LaundryPrefs(),
    val activePoint: WatchPoint = WatchPoint(),
    val filter: DeviceFilter = DeviceFilter.ALL,
    val watching: Boolean = false,
    val refreshing: Boolean = false,
) {
    val filteredDevices: List<Device> get() = when (filter) {
        DeviceFilter.ALL -> devices
        DeviceFilter.IDLE -> devices.filter { it.bizStatus == BizStatus.IDLE }
        DeviceFilter.RESERVABLE -> devices.filter { it.bizStatus == BizStatus.RESERVABLE }
        DeviceFilter.FAV -> devices.filter { it.id in pref.favouriteIds }
        DeviceFilter.TRACKED -> devices.filter { it.id in pref.trackedIds }
    }

    val counts: Map<BizStatus, Int> get() = LaundryRepository.counts(devices)
    val fastestText: String get() = NotifHelper.fastestText(devices)
    val totalCount: Int get() = devices.size
}

class LaundryViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsStore(app)
    private val stats = UsageStats(app)
    private val repository: LaundryRepository by lazy {
        LaundryRepositoryHolder.init(app)
        LaundryRepositoryHolder.repository
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val watchPointsFlow = prefs.prefs
    private val statsFlow = stats.stats

    /** 空闲规律（P2） */
    val usageStats: StateFlow<List<HourStat>> = statsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    /** 华为 AGC 连接状态（匿名认证 + laundry 区 LaundryUser），设置页简单展示用 */
    val agcState = AgcUserRepository.state
    val agcUid = AgcUserRepository.uid
    fun retryAgc() = AgcUserRepository.retry(getApplication())

    /** 通用非个人事件参数：内部点位 id + App 版本（不上传位置/身份信息） */
    private fun baseParams(): Map<String, String?> {
        val p = _uiState.value.activePoint
        return mapOf(
            "point_id" to p.positionId.toString(),
            "app_version" to runCatching {
                getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0).versionName
            }.getOrNull(),
        )
    }

    private var pollJob: Job? = null
    private var isRefreshing = false

    init {
        // 组合配置 + 快照 + 服务状态；DataStore 流异常时兜底，避免 UI 状态冻结
        combine(
            watchPointsFlow.catch { emit(LaundryPrefs()) },
            repository.snapshot,
        ) { pref, snap ->
            _uiState.value = _uiState.value.copy(
                pref = pref,
                activePoint = pref.watchPoints.getOrNull(pref.activePointIndex) ?: WatchPoint(),
                devices = snap,
                watching = WatchService.isRunning,
            )
        }.launchIn(viewModelScope)
        startPolling()
    }

    /** 常规轮询：按配置间隔（默认60s）拉取，永不停止 */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refreshInternal(manual = false)
                val interval = _uiState.value.pref.pollIntervalSec.coerceIn(10, 3600) * 1000
                delay(interval)
            }
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            if (isRefreshing) return@launch
            refreshInternal(manual = true)
        }
    }

    private suspend fun refreshInternal(manual: Boolean) {
        if (manual) _uiState.value = _uiState.value.copy(refreshing = true)
        _uiState.value = _uiState.value.copy(loading = _uiState.value.devices.isEmpty())
        val point = _uiState.value.activePoint
        // 拉取（失败自动重试一次）
        var result = repository.fetch(point)
        if (result.error != null) {
            delay(800)
            result = repository.fetch(point)
        }
        // 成功但空：补拉一次（接口偶发返回空）
        if (result.error == null && result.devices.isEmpty()) {
            delay(1200)
            result = repository.fetch(point)
        }
        // 空/失败时回退到最近一次成功快照（WatchService 拉到过数据，首页也显示）
        val fallback = repository.snapshot.value
        val devices = if (result.devices.isNotEmpty()) result.devices
        else if (fallback.isNotEmpty()) fallback
        else emptyList()
        _uiState.value = _uiState.value.copy(
            loading = false,
            refreshing = false,
            devices = devices,
            error = result.error,
            lastUpdatedAt = repository.lastUpdatedAt,
        )
        if (result.error == null && result.devices.isNotEmpty()) {
            stats.record(result.devices)
            WidgetUpdater.update(getApplication(), result.devices, point)
            WidgetUpdater.updateTracking(getApplication())
            // 仅用户主动刷新上报 manual_refresh；自动轮询不统计（参数为非个人的内部点位/版本）
            if (manual) UmengAnalyticsManager.event(UmengAnalyticsManager.EV_MANUAL_REFRESH, baseParams())
        }
    }

    /** 我要洗衣 / 停止蹲守 */
    fun toggleWatch() {
        val context = getApplication<Application>()
        if (WatchService.isRunning) {
            WatchService.stop(context)
            _uiState.value = _uiState.value.copy(watching = false)
            UmengAnalyticsManager.event(UmengAnalyticsManager.EV_STOP_WATCH, baseParams())
        } else {
            if (!NotifHelper.hasNotifyPermission(context) || !NotifHelper.canPost(context)) {
                _uiState.value = _uiState.value.copy(error = "请先在系统设置中允许通知权限，蹲守提醒才能生效")
                return
            }
            WatchService.start(context)
            _uiState.value = _uiState.value.copy(watching = true)
            UmengAnalyticsManager.event(UmengAnalyticsManager.EV_START_WATCH, baseParams())
        }
    }

    fun setFilter(f: DeviceFilter) {
        _uiState.value = _uiState.value.copy(filter = f)
    }

    /** 收藏：先即时反馈，再异步持久化 */
    fun toggleFavourite(id: Long) {
        val pref = _uiState.value.pref
        val next = if (id in pref.favouriteIds) pref.favourites - id.toString() else pref.favourites + id.toString()
        _uiState.value = _uiState.value.copy(pref = pref.copy(favourites = next))
        viewModelScope.launch { prefs.toggleFavourite(id) }
    }

    fun setActivePoint(index: Int) {
        val pref = _uiState.value.pref
        if (index !in pref.watchPoints.indices || index == pref.activePointIndex) return
        // 乐观更新：立即切换当前点位并刷新，蹲守服务同步重置
        val nextPref = pref.copy(activePointIndex = index)
        repository.clearSnapshot()
        _uiState.value = _uiState.value.copy(
            pref = nextPref,
            activePoint = nextPref.watchPoints[index],
            devices = emptyList(),
            loading = true,
        )
        val context = getApplication<android.app.Application>()
        viewModelScope.launch {
            prefs.setActivePoint(index)
            refreshInternal(manual = true)
            WatchService.resync(context)
        }
    }

    fun addPoint(p: WatchPoint) {
        val pref = _uiState.value.pref
        // 已存在完全相同点位则直接切过去
        val existIndex = pref.watchPoints.indexOfFirst { it.positionId == p.positionId && it.floorCode == p.floorCode }
        if (existIndex >= 0) { setActivePoint(existIndex); return }
        val list = pref.watchPoints + p
        val newIndex = list.lastIndex
        val nextPref = pref.copy(watchPoints = list, activePointIndex = newIndex)
        repository.clearSnapshot()
        _uiState.value = _uiState.value.copy(
            pref = nextPref,
            activePoint = p,
            devices = emptyList(),
            loading = true,
        )
        val context = getApplication<android.app.Application>()
        viewModelScope.launch {
            prefs.setWatchPoints(list, newIndex)
            refreshInternal(manual = true)
            WatchService.resync(context)
        }
    }

    fun removePoint(index: Int) {
        val pref = _uiState.value.pref
        if (index !in pref.watchPoints.indices || pref.watchPoints.size <= 1) return
        val list = pref.watchPoints.toMutableList().apply { removeAt(index) }
        val active = pref.activePointIndex.coerceAtMost(list.lastIndex).let {
            if (index < pref.activePointIndex) pref.activePointIndex - 1 else it
        }.coerceIn(0, list.lastIndex)
        val nextPref = pref.copy(watchPoints = list, activePointIndex = active)
        _uiState.value = _uiState.value.copy(pref = nextPref, activePoint = list[active])
        viewModelScope.launch { prefs.setWatchPoints(list, active) }
    }

    fun setPollInterval(sec: Long) {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(pollIntervalSec = sec))
        viewModelScope.launch { prefs.setPollInterval(sec) }
    }

    fun setWatchTtl(min: Long) {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(watchTtlMin = min))
        viewModelScope.launch { prefs.setWatchTtl(min) }
    }

    fun setSound(enabled: Boolean) {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(soundEnabled = enabled))
        viewModelScope.launch { prefs.setSoundEnabled(enabled) }
    }

    fun setFinishAlert(enabled: Boolean) {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(finishAlertEnabled = enabled))
        viewModelScope.launch { prefs.setFinishAlertEnabled(enabled) }
    }

    fun setThemeMode(mode: Int) {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(themeMode = mode))
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setWashHours(start: Int, end: Int) {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(washStartHour = start, washEndHour = end))
        viewModelScope.launch { prefs.setWashHours(start, end) }
    }

    fun toggleTracked(id: Long) {
        val pref = _uiState.value.pref
        val adding = id !in pref.trackedIds
        val next = if (adding) pref.tracked + id.toString() else pref.tracked - id.toString()
        _uiState.value = _uiState.value.copy(pref = pref.copy(tracked = next))
        val context = getApplication<Application>()
        viewModelScope.launch {
            prefs.toggleTracked(id)
            // 标记「洗衣中追踪」卡片
            WidgetUpdater.updateTracking(context)
            val machineType = _uiState.value.devices.firstOrNull { it.id == id }?.category?.name
            val params = baseParams() + ("machine_type" to machineType)
            if (adding) {
                UmengAnalyticsManager.event(UmengAnalyticsManager.EV_TRACK_MACHINE, params)
                // 开始「洗衣中追踪」：服务没在跑就静默启动（只追踪这台、不轰炸其它空闲机），在跑就立刻刷新一轮
                if (!WatchService.isRunning && NotifHelper.hasNotifyPermission(context) && NotifHelper.canPost(context)) {
                    WatchService.startTrackingOnly(context)
                    _uiState.value = _uiState.value.copy(watching = true)
                } else {
                    WatchService.kick(context)
                }
            } else {
                UmengAnalyticsManager.event(UmengAnalyticsManager.EV_CANCEL_TRACKING, params)
                // 误触取消：撤掉该机器的「洗衣中」进行中通知，并让服务立刻刷新
                NotifHelper.cancelTrackingNotification(context, id)
                WatchService.kick(context)
            }
        }
    }

    /** 新手引导看完/跳过，只展示一次 */
    fun setOnboardingDone() {
        val pref = _uiState.value.pref
        _uiState.value = _uiState.value.copy(pref = pref.copy(hasSeenOnboarding = true))
        viewModelScope.launch { prefs.setSeenOnboarding(true) }
    }

    fun setHaierPackage(pkg: String) {
        viewModelScope.launch { prefs.setHaierPackage(pkg) }
    }

    fun waitMinutesOf(d: Device): Int? = WaitCalc.waitMinutes(d.finishTime)
}
