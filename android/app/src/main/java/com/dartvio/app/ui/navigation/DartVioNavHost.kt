package com.dartvio.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.domain.model.Player
import com.dartvio.app.ui.achievement.AchievementScreen
import com.dartvio.app.ui.game.GameScreen
import com.dartvio.app.ui.home.HomeScreen
import com.dartvio.app.ui.leaderboard.LeaderboardScreen
import com.dartvio.app.ui.lobby.CreateRoomScreen
import com.dartvio.app.ui.lobby.LobbyScreen
import com.dartvio.app.ui.lobby.OnlineDiagnosticsScreen
import com.dartvio.app.ui.lobby.OnlineScreen
import com.dartvio.app.ui.lobby.RoomMatchResultScreen
import com.dartvio.app.ui.lobby.RoomMatchScreen
import com.dartvio.app.ui.lobby.RoomWaitingScreen
import com.dartvio.app.ui.lobby.SpectateScreen
import com.dartvio.app.ui.practice.CountUpScreen
import com.dartvio.app.ui.practice.CricketMprScreen
import com.dartvio.app.ui.practice.ImpactPracticeScreen
import com.dartvio.app.ui.practice.ImpactReportScreen
import com.dartvio.app.ui.practice.ImpactSession
import com.dartvio.app.ui.practice.ImpactSetupScreen
import com.dartvio.app.ui.practice.NinetyNineScreen
import com.dartvio.app.ui.practice.NinetyNineSetupScreen
import com.dartvio.app.ui.practice.PracticeModeScreen
import com.dartvio.app.ui.practice.CheckoutRushReportScreen
import com.dartvio.app.ui.practice.CheckoutRushScreen
import com.dartvio.app.ui.practice.CheckoutRushSessionStore
import com.dartvio.app.ui.practice.CheckoutTrainingEntryScreen
import com.dartvio.app.ui.practice.PracticeSoloScreen
import com.dartvio.app.ui.practice.newRushSessionId
import com.dartvio.app.ui.practice.RandomCheckoutScreen
import com.dartvio.app.ui.practice.versus.VersusBattleScreen
import com.dartvio.app.ui.practice.versus.VersusListScreen
import com.dartvio.app.ui.practice.versus.VersusReportScreen
import com.dartvio.app.ui.practice.versus.VersusSession
import com.dartvio.app.ui.practice.versus.VersusSetupScreen
import com.dartvio.app.ui.profile.FeedbackScreen
import com.dartvio.app.ui.profile.PrivacyPolicyScreen
import com.dartvio.app.ui.profile.ProfileScreen
import com.dartvio.app.ui.setup.CricketGamesScreen
import com.dartvio.app.ui.setup.CricketSettingsScreen
import com.dartvio.app.ui.setup.MatchSetupScreen
import com.dartvio.app.ui.setup.SetupSession
import com.dartvio.app.ui.setup.VersusMode
import com.dartvio.app.ui.setup.X01DetailSettingsScreen
import com.dartvio.app.ui.setup.X01SetupScreen
import com.dartvio.app.ui.stats.StatsScreen
import com.dartvio.app.BuildConfig
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.ui.theme.LocalThemeStore
import com.dartvio.app.ui.theme.ThemePalette
import com.dartvio.app.ui.theme.ThemeDebugScreen
import com.dartvio.app.ui.theme.ThemeSettingsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导航图。阶段一：首页 → X01/Cricket 设置 → 详细设置 → 对局。
 * 由于 GameScreen 需要传入具体 config/players，这里用 MatchSession 缓存临时对象。
 *
 * NavController 由 [DartVioRoot] 持有，以便统一管理底部导航栏的显示与切换。
 */
@Composable
fun DartVioNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                onSelectGame = { type ->
                    when (type) {
                        MatchType.X01 -> navController.navigate(Routes.X01_SETUP)
                        // Cricket 之下还有一层玩法选择（standard / Tactics / 随机目标…），
                        // 先进游戏选择页，不再直落设置页。
                        MatchType.CRICKET -> navController.navigate(Routes.CRICKET_GAMES)
                        else -> navController.navigate(Routes.setup(type.name))
                    }
                },
                onOpenPractice = { navController.navigate(Routes.PRACTICE) }
            )
        }

        // ===== 大厅（M6 比赛大厅与房间，一级 Tab） =====
        composable(Routes.LOBBY) {
            LobbyScreen(
                onOpenCreateRoom = { navController.navigate(Routes.LOBBY_CREATE) },
                onJoinRoom = { roomId -> navController.navigate(Routes.lobbyRoom(roomId)) },
                onSpectate = { roomId -> navController.navigate(Routes.lobbySpectate(roomId)) },
                onStartPractice = { navController.navigate(Routes.PRACTICE) },
                onOpenOnline = { navController.navigate(Routes.LOBBY_ONLINE) }
            )
        }

        // ===== 联机入口（只看连接情况） =====
        composable(Routes.LOBBY_ONLINE) {
            OnlineScreen(
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate(Routes.LOBBY_ONLINE_DIAGNOSTICS) }
            )
        }

        // ===== 联机诊断（在线链路逐层排查；临时保留，验收后可删） =====
        composable(Routes.LOBBY_ONLINE_DIAGNOSTICS) {
            OnlineDiagnosticsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ===== F6.2 创建房间 =====
        composable(Routes.LOBBY_CREATE) {
            CreateRoomScreen(
                onBack = { navController.popBackStack() },
                onCreated = { roomId ->
                    navController.navigate(Routes.lobbyRoom(roomId)) {
                        popUpTo(Routes.LOBBY) { inclusive = false }
                    }
                }
            )
        }

        // ===== F6.3 房间等候 =====
        composable(
            route = Routes.LOBBY_ROOM,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomWaitingScreen(
                roomId = roomId,
                onLeave = { navController.popBackStack(Routes.LOBBY, inclusive = false) },
                // 等候页也给一个 WiFi 入口：这一页要停留几十秒等人，
                // 「是我没连上，还是没人来」必须能当场查，而不是退回大厅再猜。
                onOpenOnline = { navController.navigate(Routes.LOBBY_ONLINE) },
                onMatchStarted = { id ->
                    // 进入比分视图；等候页出栈，避免返回时重复触发开局跳转。
                    //
                    // 「哪一页」由数据源回答（hasAuthoritativeMatch）：它同时取决于本机是不是联机
                    // 与该玩法有没有权威对局 —— 这两件事都只有数据源知道。判错的两种表现用户都看得见：
                    // 点进去一个不会响应的键盘，或明明能打却被按在只读比分板上。
                    val route = if (RoomRepositoryProvider.current.hasAuthoritativeMatch(id)) {
                        Routes.lobbyMatch(id)
                    } else {
                        Routes.lobbySpectate(id)
                    }
                    navController.navigate(route) {
                        popUpTo(Routes.LOBBY) { inclusive = false }
                    }
                }
            )
        }

        // ===== F6.6 仅比分观战 =====
        composable(
            route = Routes.LOBBY_SPECTATE,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            SpectateScreen(
                roomId = roomId,
                onBack = { navController.popBackStack(Routes.LOBBY, inclusive = false) }
            )
        }

        // ===== M5 联机对局（能录镖的那一页；与观战页共用同一份权威帧） =====
        composable(
            route = Routes.LOBBY_MATCH,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomMatchScreen(
                roomId = roomId,
                // 对局结束进结算页，并把对局页弹出栈：结算页返回时回到等候页，
                // 而不是回到一个「已经打完、键盘不再响应」的对局页。
                onMatchOver = { id ->
                    navController.navigate(Routes.result(id)) {
                        popUpTo(Routes.LOBBY_MATCH) { inclusive = true }
                    }
                },
                /*
                 * 退出对局页 = **真的离开房间**（2026-09-27 真机反馈）。
                 *
                 * 此前这一路只做 `popBackStack`：服务端那间房还挂在那儿（房主离开留下
                 * 「已结束」的同名卡、客人离开留下「等候中 1/2 人」），而它已经没人能接手了 ——
                 * 点进去只有回合记录，没有任何下一步。现在退出与「等候页退出」走同一条路：
                 * 通知服务端，房间进入 30 秒宽限，之后置 END 并从大厅消失。
                 */
                onExit = {
                    RoomRepositoryProvider.current.leaveRoom(roomId, LocalUser.ID)
                    navController.popBackStack(Routes.LOBBY, inclusive = false)
                }
            )
        }

        // ===== M5 联机结算（T9：赛制结束后的总结页；「再来一局」由房主发起） =====
        composable(
            route = Routes.LOBBY_RESULT,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            RoomMatchResultScreen(
                roomId = roomId,
                // 「再来一局」之后回到等候页：结算页自己出栈，
                // 否则返回键会把人送回一个已经结束的对局的结算页。
                onBackToRoom = { id ->
                    navController.navigate(Routes.room(id)) {
                        popUpTo(Routes.LOBBY_RESULT) { inclusive = true }
                    }
                },
                onExit = { navController.popBackStack(Routes.LOBBY, inclusive = false) }
            )
        }

        // ===== Cricket 二级游戏选择页（首页 CRICKET 入口） =====
        composable(Routes.CRICKET_GAMES) {
            CricketGamesScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.CRICKET_SETTINGS) },
                onStart = { config, players ->
                    MatchSession.pendingConfig = config
                    MatchSession.pendingPlayers = players
                    navController.navigate(Routes.GAME)
                }
            )
        }

        // ===== Cricket 游戏设置页（独立页面，保存后返回游戏选择页） =====
        composable(Routes.CRICKET_SETTINGS) {
            // 与游戏选择页是同一份会话状态：设置页改完返回，主页面立刻反映。
            val state = remember { SetupSession.cricketState() }
            CricketSettingsScreen(
                state = state,
                onBack = { navController.popBackStack() }
            )
        }

        // ===== X01 设置页（主） =====
        composable(Routes.X01_SETUP) {
            // 状态挂在 SetupSession 上：去「游戏设置」页往返一趟后，
            // 对战模式与已选对手都不会被重置。
            val state = remember { SetupSession.x01State() }
            X01SetupScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    MatchSession.detailConfig = state.config
                    navController.navigate(Routes.X01_DETAIL)
                },
                onStart = { config, players ->
                    MatchSession.pendingConfig = config
                    MatchSession.pendingPlayers = players
                    navController.navigate(Routes.GAME)
                }
            )
        }

        // ===== X01 详细设置页 =====
        composable(Routes.X01_DETAIL) {
            val initial = MatchSession.detailConfig ?: MatchConfig(targetScore = 501)
            val state = SetupSession.x01State()
            X01DetailSettingsScreen(
                initialConfig = initial,
                // 对战模式读会话状态（玩家在上一页选的）：本页的「AI 对手」段按它决定是否置灰，
                // 不复制一份本地状态，避免两个页面各记一份而分叉。
                versus = state.versus,
                // AI 难度也挂在同一份会话状态上：本页改完返回后，主设置页组装名单立刻用新值，
                // 因此这里传「值 + 回调」而不是让本页自己存一份。
                aiDifficulty = state.aiDifficulty,
                onAiDifficultyChange = { state.aiDifficulty = it },
                onBack = { navController.popBackStack() },
                onApply = { updated ->
                    MatchSession.detailConfig = updated
                    // 回填到会话状态（而不是留在 MatchSession 里等主设置页自己捡）：
                    // 这样「改完设置 → 返回」就能看到摘要已更新，也不依赖主设置页被重建。
                    SetupSession.x01State().applyConfig(updated)
                    navController.popBackStack()
                }
            )
        }

        // ===== 通用设置页（Cricket 等） =====
        composable(
            route = Routes.SETUP,
            arguments = listOf(navArgument("matchType") { type = NavType.StringType })
        ) { entry ->
            val typeName = entry.arguments?.getString("matchType") ?: MatchType.X01.name
            val matchType = MatchType.valueOf(typeName)

            MatchSetupScreen(
                matchType = matchType,
                onBack = { navController.popBackStack() },
                onStart = { config, players ->
                    MatchSession.pendingConfig = config
                    MatchSession.pendingPlayers = players
                    navController.navigate(Routes.GAME)
                }
            )
        }

        composable(Routes.GAME) {
            val config = MatchSession.pendingConfig
            val players = MatchSession.pendingPlayers
            if (config != null && players != null) {
                GameScreen(
                    config = config,
                    players = players,
                    onExit = {
                        MatchSession.clear()
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            } else {
                // 兜底：会话缓存丢失（如进程被回收后恢复到 game 路由）时返回首页，避免白屏
                LaunchedEffect(Unit) {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("对局数据已失效，正在返回首页…")
                }
            }
        }

        // ===== 数据（一级 Tab） =====
        // 「我的」页里的统计 / 成就 / 排行榜 / 清除数据入口已整体并入这里（2026-09-26）。
        composable(Routes.STATS) {
            StatsScreen(
                onBack = null,
                onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) }
            )
        }

        // ===== 训练中心（M11 第③期，二级页：由首页进入） =====
        // C1：第一级只有「单人训练 / 双人对抗训练」两张卡，项目列表下沉到各自子页。
        composable(Routes.PRACTICE) {
            PracticeModeScreen(
                onBack = { navController.popBackStack() },
                onSolo = { navController.navigate(Routes.PRACTICE_SOLO) },
                onVersus = { navController.navigate(Routes.PRACTICE_VERSUS_LIST) }
            )
        }

        // ===== 单人训练（原训练中心 6 项，整体下沉为第二级） =====
        composable(Routes.PRACTICE_SOLO) {
            PracticeSoloScreen(
                onBack = { navController.popBackStack() },
                onCountUp = { navController.navigate(Routes.PRACTICE_COUNT_UP) },
                onRandomCheckout = { navController.navigate(Routes.PRACTICE_CHECKOUT) },
                onNinetyNine = { navController.navigate(Routes.PRACTICE_NINETY_NINE_SETUP) },
                onCricketMpr = { navController.navigate(Routes.PRACTICE_CRICKET_MPR) },
                onAiDrill = { navController.navigate(Routes.PRACTICE_AI) },
                onImpact = {
                    ImpactSession.clear()
                    navController.navigate(Routes.PRACTICE_IMPACT_SETUP)
                }
            )
        }

        // ===== 双人对抗训练 · 模式列表 =====
        composable(Routes.PRACTICE_VERSUS_LIST) {
            VersusListScreen(
                onBack = { navController.popBackStack() },
                onPickMode = { modeKey -> navController.navigate(Routes.practiceVersusSetup(modeKey)) }
            )
        }

        // ===== 双人对抗训练 · 对局配置 =====
        composable(
            route = Routes.PRACTICE_VERSUS_SETUP,
            arguments = listOf(navArgument("modeKey") { type = NavType.StringType })
        ) { entry ->
            val modeKey = entry.arguments?.getString("modeKey").orEmpty()
            VersusSetupScreen(
                modeKey = modeKey,
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(Routes.PRACTICE_VERSUS) }
            )
        }

        // ===== 双人对抗训练 · 对局 =====
        composable(Routes.PRACTICE_VERSUS) {
            VersusBattleScreen(
                onExit = {
                    VersusSession.clear()
                    navController.popBackStack(Routes.PRACTICE_VERSUS_LIST, inclusive = false)
                },
                onFinish = {
                    navController.navigate(Routes.PRACTICE_VERSUS_REPORT) {
                        // 对局页出栈：战报里「再来一局」时不该还能返回上一局的对局页。
                        popUpTo(Routes.PRACTICE_VERSUS) { inclusive = true }
                    }
                }
            )
        }

        // ===== 双人对抗训练 · 战报 =====
        composable(Routes.PRACTICE_VERSUS_REPORT) {
            VersusReportScreen(
                onExit = {
                    VersusSession.clear()
                    navController.popBackStack(Routes.PRACTICE_VERSUS_LIST, inclusive = false)
                },
                onRematch = { navController.navigate(Routes.PRACTICE_VERSUS) }
            )
        }

        // ===== 我的（一级 Tab） =====
        composable(Routes.PROFILE) {
            ProfileScreen(
                onOpenSettings = { navController.navigate("settings") },
                onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }

        /*
         * 隐私声明二级页。
         *
         * 无条件注册（与「我的反馈」同理）：入口那侧不设开关，路由就必须常驻，
         * 否则一次改动就会把点击变成运行时崩溃。
         */
        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        /*
         * 「我的反馈」二级页。
         *
         * 与上面的 settings 同理：**无条件注册**路由，显示与否只由「我的」页那一侧决定
         * （那里判的是「采纳清单是不是空的」）。注册一条没人去的路由没有代价。
         */
        composable(Routes.FEEDBACK) {
            FeedbackScreen(onBack = { navController.popBackStack() })
        }

        /*
         * 设置 / 外观 / 主题调试面板。
         *
         * ★ 这里**无条件注册**这三条路由，不再套 `BuildConfig.DEBUG`（或 FULL_ENTRIES）。
         * 原因：上面 ProfileScreen 无条件拿到了 `onOpenSettings`（它内部才判断是否显示
         * 「设置」这一项）。一旦「显示入口」与「注册路由」用两个判据，任何一处改动
         * 都会让点击变成 `IllegalArgumentException: navigation destination ... is not a
         * direct child` —— 而且是运行时崩溃，编译期毫无线索。
         *
         * 注册一条没人去的路由没有代价；少注册一条却有代价。所以判据只留在入口那侧。
         */
        composable("settings") {
            ThemeSettingsPage(
                title = "设置",
                entry = "外观与主题",
                onOpen = { navController.navigate("appearance") },
                onBack = { navController.popBackStack() },
                // 版本信息的主入口（2026-09-27 反馈）：与主流 App 一致，放在设置页而不是「我的」页。
                showAbout = true,
            )
        }
        composable("appearance") {
            val store = LocalThemeStore.current
            ThemeSettingsPage("外观与主题", "主题调试面板", { navController.navigate("theme-debug") }, { navController.popBackStack() },
                onRestore = { withContext(Dispatchers.IO) { store.apply(ThemePalette()) } })
        }
        composable("theme-debug") {
            val store = LocalThemeStore.current
            ThemeDebugScreen(initial = store.palette.value ?: ThemePalette(),
                onApply = { withContext(Dispatchers.IO) { store.apply(it) } },
                onBack = { navController.popBackStack() })
        }

        // ===== 成就墙（M9 第③期，二级页：由「我的」进入） =====
        composable(Routes.ACHIEVEMENTS) {
            AchievementScreen(onBack = { navController.popBackStack() })
        }

        // ===== 本地排行榜（第③期 ③B，二级页：由「我的」进入） =====
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(onBack = { navController.popBackStack() })
        }

        // ===== Count Up 练习（M11 §6.2） =====
        composable(Routes.PRACTICE_COUNT_UP) {
            CountUpScreen(
                onExit = { navController.popBackStack() }
            )
        }

        // ===== 结镖训练 · 入口（路线学习 / 极速挑战） =====
        composable(Routes.PRACTICE_CHECKOUT) {
            CheckoutTrainingEntryScreen(
                onBack = { navController.popBackStack() },
                // 路线学习 = 既有的随机结镖练习页，行为一行未改，只是从列表卡下沉成子模式。
                onRouteStudy = { navController.navigate(Routes.PRACTICE_RANDOM_CHECKOUT) },
                onStartRush = { kind, difficulty ->
                    CheckoutRushSessionStore.start(kind, difficulty, newRushSessionId())
                    navController.navigate(Routes.PRACTICE_CHECKOUT_RUSH)
                }
            )
        }

        // ===== 极速挑战 · 训练页 =====
        composable(Routes.PRACTICE_CHECKOUT_RUSH) {
            CheckoutRushScreen(
                onExit = {
                    CheckoutRushSessionStore.clear()
                    navController.popBackStack(Routes.PRACTICE_CHECKOUT, inclusive = false)
                },
                onFinish = { navController.navigate(Routes.PRACTICE_CHECKOUT_REPORT) }
            )
        }

        // ===== 极速挑战 · 会话报告 =====
        composable(Routes.PRACTICE_CHECKOUT_REPORT) {
            CheckoutRushReportScreen(
                sessionId = CheckoutRushSessionStore.sessionId.orEmpty(),
                difficultyLabel = CheckoutRushSessionStore.difficulty.label,
                onExit = {
                    CheckoutRushSessionStore.clear()
                    navController.popBackStack(Routes.PRACTICE_CHECKOUT, inclusive = false)
                },
                onRetry = {
                    // 开一个**新会话**：复用旧 sessionId 会让新一批题目混进上一次的统计里。
                    CheckoutRushSessionStore.start(
                        CheckoutRushSessionStore.kind,
                        CheckoutRushSessionStore.difficulty,
                        newRushSessionId(),
                    )
                    navController.navigate(Routes.PRACTICE_CHECKOUT_RUSH) {
                        popUpTo(Routes.PRACTICE_CHECKOUT_REPORT) { inclusive = true }
                    }
                }
            )
        }

        // ===== 随机结镖练习（M11 §6.3）=====
        // 路由常量保留：现在是「结镖训练 → 路线学习」这一支，供入口页跳转。
        composable(Routes.PRACTICE_RANDOM_CHECKOUT) {
            RandomCheckoutScreen(
                onExit = { navController.popBackStack() }
            )
        }

        // ===== 99 Darts 设置页 =====
        composable(Routes.PRACTICE_NINETY_NINE_SETUP) {
            NinetyNineSetupScreen(
                onBack = { navController.popBackStack() },
                onStart = { sector ->
                    navController.navigate(Routes.practiceNinetyNine(sector))
                }
            )
        }

        // ===== 99 Darts 练习页 =====
        composable(
            route = Routes.PRACTICE_NINETY_NINE,
            arguments = listOf(navArgument("sector") { type = NavType.IntType })
        ) { entry ->
            val sector = entry.arguments?.getInt("sector") ?: 20
            NinetyNineScreen(
                sector = sector,
                onExit = { navController.popBackStack() }
            )
        }

        // ===== 精准工坊 · 选目标（M11 新增子练习） =====
        composable(Routes.PRACTICE_IMPACT_SETUP) {
            ImpactSetupScreen(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(Routes.PRACTICE_IMPACT) }
            )
        }

        // ===== 精准工坊 · 逐镖点录 =====
        composable(Routes.PRACTICE_IMPACT) {
            ImpactPracticeScreen(
                // 中途退出：会话状态一起清掉，避免下一次「开始这一组」继承上一组的 id。
                onExit = {
                    ImpactSession.clear()
                    navController.popBackStack(Routes.PRACTICE, inclusive = false)
                },
                onFinish = { navController.navigate(Routes.PRACTICE_IMPACT_REPORT) }
            )
        }

        // ===== 精准工坊 · 轮后报告 =====
        composable(Routes.PRACTICE_IMPACT_REPORT) {
            ImpactReportScreen(
                onExit = {
                    ImpactSession.clear()
                    navController.popBackStack(Routes.PRACTICE, inclusive = false)
                },
                onNextRound = {
                    navController.navigate(Routes.PRACTICE_IMPACT) {
                        // 报告页出栈：录下一组时不能从它再返回上一组的报告。
                        popUpTo(Routes.PRACTICE_IMPACT_REPORT) { inclusive = true }
                    }
                }
            )
        }

        // ===== Cricket MPR 挑战（M11 Cricket 练习） =====
        composable(Routes.PRACTICE_CRICKET_MPR) {
            CricketMprScreen(
                onExit = { navController.popBackStack() }
            )
        }

        // ===== AI 对战练习（M11 DartBot 对战） =====
        composable(Routes.PRACTICE_AI) {
            MatchSetupScreen(
                matchType = MatchType.X01,
                screenTitle = "AI 对战练习",
                initialVersus = VersusMode.HUMAN_VS_AI,
                // 练习入口锁死「真人 vs AI」：不显示对战模式二选一（只留挑对手）。
                versusLocked = true,
                selectableTypes = listOf(MatchType.X01, MatchType.CRICKET),
                onBack = { navController.popBackStack() },
                onStart = { config, players ->
                    MatchSession.pendingConfig = config
                    MatchSession.pendingPlayers = players
                    navController.navigate(Routes.GAME)
                }
            )
        }
    }
}

object Routes {
    // ---- 一级页面（显示底部导航栏） ----
    const val HOME = "home"
    const val LOBBY = "lobby"
    const val STATS = "stats"
    const val PROFILE = "profile"

    // ---- 二级页面（隐藏底部导航栏） ----
    const val X01_SETUP = "x01_setup"
    const val X01_DETAIL = "x01_detail"
    const val SETUP = "setup/{matchType}"
    const val CRICKET_GAMES = "cricket_games"
    const val CRICKET_SETTINGS = "cricket_settings"
    const val GAME = "game"
    const val PRACTICE = "practice"
    // 训练中心两级入口（C1）：一级只有「单人训练 / 双人对抗训练」两张卡，二级才是项目列表。
    const val PRACTICE_SOLO = "practice/solo"
    // 结镖训练（提示词 §五.1）：用户侧「随机结镖」改名，列表不新增第二张卡 ——
    // 路线学习与极速挑战是同一件事的两种练法，入口页里选，不是两张训练卡。
    const val PRACTICE_CHECKOUT = "practice/checkout"
    const val PRACTICE_CHECKOUT_RUSH = "practice/checkout_rush"
    const val PRACTICE_CHECKOUT_REPORT = "practice/checkout_rush_report"
    const val PRACTICE_COUNT_UP = "practice/count_up"
    // 保留旧路由：它现在是「结镖训练 → 路线学习」这一支，行为与改名前一模一样。
    const val PRACTICE_RANDOM_CHECKOUT = "practice/random_checkout"
    const val PRACTICE_NINETY_NINE_SETUP = "practice/ninety_nine"
    const val PRACTICE_NINETY_NINE = "practice/ninety_nine/{sector}"
    const val PRACTICE_CRICKET_MPR = "practice/cricket_mpr"
    const val PRACTICE_IMPACT_SETUP = "practice/impact_setup"
    const val PRACTICE_IMPACT = "practice/impact_practice"
    const val PRACTICE_IMPACT_REPORT = "practice/impact_report"
    const val PRACTICE_AI = "practice/ai"
    // 双人对抗训练（N6 四条路由）：列表 → 配置 → 对局 → 战报。
    const val PRACTICE_VERSUS_LIST = "practice/versus_list"
    const val PRACTICE_VERSUS_SETUP = "practice/versus_setup/{modeKey}"
    const val PRACTICE_VERSUS = "practice/versus"
    const val PRACTICE_VERSUS_REPORT = "practice/versus_report"
    const val ACHIEVEMENTS = "achievements"
    const val LEADERBOARD = "leaderboard"

    /** 反馈闭环（2026-09-26）：「我的」页 → 我的反馈。 */
    const val FEEDBACK = "feedback"

    /** 隐私声明（2026-09-27）：政策正文承诺了「之后可在『我的』页查看」，必须有落地页。 */
    const val PRIVACY = "privacy"

    // ---- 大厅子页（M6） ----
    const val LOBBY_CREATE = "lobby/create"
    const val LOBBY_ONLINE = "lobby/online"

    /** 联机诊断（T4：脱离 IDE 后排障的唯一抓手）。 */
    const val LOBBY_ONLINE_DIAGNOSTICS = "lobby/online_diagnostics"
    const val LOBBY_ROOM = "lobby/room/{roomId}"
    const val LOBBY_SPECTATE = "lobby/spectate/{roomId}"
    const val LOBBY_MATCH = "lobby/match/{roomId}"

    /** 联机结算（M5 T9：赛制结束后的总结页）。 */
    const val LOBBY_RESULT = "lobby/result/{roomId}"

    fun room(roomId: String) = "lobby/room/$roomId"
    fun result(roomId: String) = "lobby/result/$roomId"

    fun setup(matchType: String) = "setup/$matchType"
    fun practiceNinetyNine(sector: Int) = "practice/ninety_nine/$sector"
    fun practiceVersusSetup(modeKey: String) = "practice/versus_setup/$modeKey"
    fun lobbyRoom(roomId: String) = "lobby/room/$roomId"
    fun lobbySpectate(roomId: String) = "lobby/spectate/$roomId"
    fun lobbyMatch(roomId: String) = "lobby/match/$roomId"
}

/** 一级路由集合：只有这些页面显示底部导航栏。 */
val TopLevelRoutes: Set<String> = setOf(
    Routes.HOME,
    Routes.LOBBY,
    Routes.STATS,
    Routes.PROFILE,
)

/** 临时会话缓存，用于在导航间传递对局参数。 */
object MatchSession {
    var pendingConfig: MatchConfig? = null
    var pendingPlayers: List<Player>? = null

    /** 详细设置页的暂存配置（返回主设置页时回填）。 */
    var detailConfig: MatchConfig? = null

    fun clear() {
        pendingConfig = null
        pendingPlayers = null
        detailConfig = null
    }
}
