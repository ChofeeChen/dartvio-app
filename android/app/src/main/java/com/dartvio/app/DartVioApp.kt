package com.dartvio.app

import android.app.Application
import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.achievement.AchievementRepository
import com.dartvio.app.data.room.OnlineRoomLink
import com.dartvio.app.net.online.OnlineConfig
import com.dartvio.app.data.beta.BetaCrashLog
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.data.room.PlayerProfileStore
import com.dartvio.app.data.telemetry.UsageReporter
import com.dartvio.app.data.vision.VisionCapabilityChecker
import com.dartvio.app.data.vision.VisionSettings
import com.dartvio.app.domain.vision.VisionCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级容器。本期只承载对局历史的数据库与仓库，
 * 后续（M5 在线同步等）需要的依赖也挂在这里。
 */
class DartVioApp : Application() {

    /*
     * 进程级后台 scope。项目此前没有统一的 scope 约定（各处都传 viewModelScope），
     * 但匿名统计必须活在 Application 层、且不随任何页面的生命周期结束，
     * 所以这里建一个。SupervisorJob：上报失败不该牵连同 scope 内的其他协程。
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 只有 Beta 试用包装崩溃留痕：它记的是「朋友手机上发生了什么」，
        // 正式包既不需要、也不该在用户不知情时留下这种东西。
        if (BuildConfig.BETA_DEMO) BetaCrashLog.install(this)
        // 昵称 / 头像必须在**任何房间 UI 之前**灌进 LocalUser：房间里显示的房主名、成员名、
        // 默认房间名都读它，而它此前只在切换到在线数据源时同步 —— 于是没联机时停在默认的「我」，
        // 表现为「我在『我的』里改了昵称，房间卡上还是错的」（2026-09-26 真机反馈）。
        // 改昵称的那条写入路径（`ProfileScreen`）也会当场同步，这里是冷启动那一次。
        PlayerProfileStore.hydrateLocalUser(this)
        // 联机默认走云端：只要这一版配了后端，房间数据源就从在线仓库读。
        // 不切的话 RoomRepositoryProvider 停在 LocalRoomRepository —— 大厅列表显示的是内置演示房，
        // 「创建比赛」也把房建在本地，表现为「A 建了房，B 怎么刷新都看不到」（2026-09-26 真机实测）。
        if (OnlineConfig.isConfigured) OnlineRoomLink.enable(this)
        // 匿名统计：**必须**放在最后，且失败完全静默 —— 它不能拖慢启动，
        // 更不能因为没网 / 后端没配好而影响任何人打开 App（见 UsageReporter 的三条约束）。
        applicationScope.launch { UsageReporter.reportOnce(this@DartVioApp) }
    }

    val database: DartVioDatabase by lazy { DartVioDatabase.get(this) }

    val matchRepository: MatchRepository by lazy { MatchRepository(database.matchRecordDao()) }

    /**
     * 本机玩家的稳定身份 ID（③B 前置 P0）。
     *
     * 只缓存 **ID**，不缓存整份档案：昵称与头像随时可编辑，任何缓存副本都会过期；
     * ID 一旦生成就不再变化，缓存它是安全的，而且对局落库路径（每次结算都要读一次）
     * 不该为此多做一次 prefs 读取。
     *
     * 首次访问即建档（幂等），因此不存在「还没建档就落库」的中间态。
     */
    val localProfileId: String by lazy { ProfileStore.ensure(this).profileId }

    /** 本地成就读写入口（第③期 ③A）。 */
    val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(this, database.matchRecordDao())
    }

    /**
     * 手机视觉计分的 app 级偏好（M12 前置改造）。
     * 说明：目前仅提供开关与标定残差读写；识别管线（CameraX + LiteRT）待 PoC 通过后接入。
     */
    val visionSettings: VisionSettings by lazy { VisionSettings(this) }

    /** 设备能力门槛判定结果（M12 F12.5：Android 10+ / RAM ≥ 4GB / 有摄像头）。 */
    val visionCapability: VisionCapability by lazy { VisionCapabilityChecker.check(this) }
}
