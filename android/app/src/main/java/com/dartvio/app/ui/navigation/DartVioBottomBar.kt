package com.dartvio.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/** 底部导航的一个 Tab。 */
private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Filled.Home),
    BottomTab(Routes.LOBBY, "大厅", Icons.Filled.Groups),
    BottomTab(Routes.STATS, "数据", Icons.Filled.BarChart),
    BottomTab(Routes.PROFILE, "我的", Icons.Filled.Person),
)

/**
 * 底部导航栏。仅在一级页面（[TopLevelRoutes]）显示；
 * 对局页、设置页、练习子页等二级页面会自动隐藏，避免误切中断流程。
 *
 * @param showAchievementBadge 「我的」Tab 是否有未查看的解锁成就（决策④ 兜底路径）。
 *        只在「我的」这一项上画点，不做角标数字 —— 成就是收藏品，不是待办清单，
 *        一个数字会把它变成压力源。
 */
@Composable
fun DartVioBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    showAchievementBadge: Boolean = false,
) {
    NavigationBar(
        containerColor = SurfaceDark,
        tonalElevation = 0.dp,
    ) {
        bottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(tab.route) },
                icon = {
                    if (showAchievementBadge && tab.route == Routes.PROFILE) {
                        BadgedIcon(icon = tab.icon, contentDescription = tab.label)
                    } else {
                        Icon(tab.icon, contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = OnPrimary,
                    selectedTextColor = Primary,
                    indicatorColor = Primary,
                    unselectedIconColor = TextSecondaryDark,
                    unselectedTextColor = TextSecondaryDark,
                ),
            )
        }
    }
}

/**
 * 带红点的 Tab 图标。
 *
 * 刻意自绘而不是用 material3 的 `Badge` / `BadgedBox`：这里只需要一个 7dp 的圆点，
 * 自绘零成本且不受组件版本与实验性 API 标注变化的影响。
 */
@Composable
private fun BadgedIcon(
    icon: ImageVector,
    contentDescription: String,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(Error)
        )
    }
}
