package com.dartvio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dartvio.app.DartVioApp

/**
 * 应用根容器：统一承载底部导航栏 + NavHost。
 *
 * 底部栏只在 [TopLevelRoutes] 显示，进入对局 / 设置 / 练习子页时自动隐藏，
 * 这样既保证一级功能一键可达，又不会在对局中误触切页。
 */
@Composable
fun DartVioRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelRoutes

    val context = LocalContext.current
    val view = LocalView.current
    val useDarkSystemIcons = currentRoute != "theme-debug" && MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkSystemIcons
                isAppearanceLightNavigationBars = useDarkSystemIcons
            }
        }
    }
    val app = context.applicationContext as? DartVioApp
    var unseenAchievements by remember { mutableStateOf(0) }

    // 启动补算一次历史成就（决策③），且只在「尚未补算过」时执行。
    //
    // 目的是让「升级前就有历史数据的老用户」在成就记录首次落盘后立刻能看到「我的」Tab 红点 ——
    // 红点只读 SharedPreferences，不预先落盘就永远是空的。
    //
    // 这个调用对**全新用户完全无副作用**（无历史数据 ⇒ 无任何解锁被写入），
    // 因此不会抢占对局结算页 / 练习结果页的「新解锁」宣布机会；
    // 只有本来就该走「历史补算」路径的老用户会走到那条分支。
    LaunchedEffect(app) {
        app?.achievementRepository?.backfillIfNeeded()
    }

    // 红点判定：每次回到一级页面（含从成就墙返回「我的」）重算一次。
    // 只读 SharedPreferences，开销可忽略，所以不做任何缓存失效逻辑。
    LaunchedEffect(currentRoute, app) {
        if (currentRoute in TopLevelRoutes) {
            unseenAchievements = app?.achievementRepository?.countUnseen() ?: 0
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                DartVioBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { navController.navigateTopLevel(it) },
                    showAchievementBadge = unseenAchievements > 0
                )
            }
        }
    ) { innerPadding ->
        DartVioNavHost(
            navController = navController,
            modifier = Modifier.padding(
                // 二级页面不占位，保持原有全屏布局；一级页面为底部栏让位。
                bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp
            )
        )
    }
}

/**
 * 一级 Tab 切换：单例复用 + 保留 / 恢复各 Tab 自身的返回栈，
 * 避免反复点击同一 Tab 时无限制堆叠。
 */
fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
