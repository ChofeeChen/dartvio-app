package com.dartvio.app.ui.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.DartVioApp
import com.dartvio.app.domain.achievement.AchievementGroup
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.achievement.AchievementSnapshot
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 成就墙（第③期 ③B，决策④ 主路径）。
 *
 * 三条设计取舍：
 * 1. **数据源只用 [com.dartvio.app.data.achievement.AchievementRepository.observeSnapshot]**，
 *    不自己算 —— 进度是派生的，任何第二套算法都会和结算页的判定出现分歧。
 *    顺带拿到「Room 变化即刷新」与「首帧历史追溯补算」两个特性。
 * 2. **不做解锁动画、不做全屏庆祝** —— 决策④把仪式感放在结算页的卡片上，
 *    成就墙是「回来看收藏」的地方，安静比热闹合适。
 * 3. **进度条自绘**，不用 material3 的进度组件：这里只需要一个填充比例，
 *    自绘零成本且不受组件 API 版本差异影响，样式也和全局卡片统一。
 */
@Composable
fun AchievementScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? DartVioApp
    val repository = app?.achievementRepository

    val snapshot by remember(repository) {
        repository?.observeSnapshot() ?: flowOf(AchievementSnapshot())
    }.collectAsState(initial = AchievementSnapshot())

    val snackbarHostState = remember { SnackbarHostState() }

    // 打开成就墙即视为「已查看」→ 「我的」Tab 红点熄灭
    LaunchedEffect(repository) {
        repository?.markAllViewed()
    }

    // 决策④ 兜底路径：首次历史追溯补算后提示一次。
    // 必须等首帧快照到达再读 —— 补算数量是在 refresh() 内写下的，
    // 提前读会读到 0（Room 查询还没返回）。total 稳定后本块不再重跑，
    // 加上仓库侧的消费式读取，提示天然只出现一次。
    LaunchedEffect(snapshot.total) {
        if (snapshot.total == 0) return@LaunchedEffect
        val pending = repository?.consumeBackfillAnnouncement() ?: 0
        if (pending > 0) {
            snackbarHostState.showSnackbar("已根据你的历史对局补算 $pending 项成就")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "成就",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回", color = Primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )

            if (snapshot.total == 0) {
                // 首帧快照未到达（本地 Room 查询，通常只有一帧）。给一个占位而不是空列表，
                // 避免用户误以为「一项成就都没有」。
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("正在统计…", color = TextSecondaryDark, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "overview") { AchievementOverview(snapshot) }

                    AchievementGroup.entries.forEach { group ->
                        val groupItems = sortedForDisplay(
                            snapshot.items.filter { it.definition.group == group }
                        )
                        if (groupItems.isEmpty()) return@forEach

                        item(key = "header_${group.name}") {
                            GroupHeader(
                                group = group,
                                unlocked = groupItems.count { it.unlocked },
                                total = groupItems.size,
                            )
                        }
                        items(groupItems, key = { it.id }) { progress ->
                            AchievementRow(progress)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 组内展示排序（决策④「按达成度排序」的落地口径）：
 *
 * 1. **已解锁在前** —— 成就墙首先是用户自己的战果陈列，不该把自己的勋章压在底下；
 * 2. 其余按 [AchievementProgress.ratio] 降序 —— 「最接近拿到的」排在前面，让墙面可行动；
 * 3. 同序保持 [com.dartvio.app.domain.achievement.AchievementCatalog] 原顺序
 *    （Kotlin 排序稳定），清单本身就是按难度递增排的，兜底顺序天然合理。
 *
 * 刻意**只在组内排序、不做全局排序**：分组是用户理解成就墙的主结构，
 * 全局排序会把同一组的成就拆散到各处，用户反而找不到「Cricket 还差什么」。
 */
private fun sortedForDisplay(items: List<AchievementProgress>): List<AchievementProgress> =
    items.sortedWith(
        compareByDescending<AchievementProgress> { it.unlocked }
            .thenByDescending { it.ratio }
    )

/** 顶部总览：已解锁计数 + 总进度 + 一句推进文案。 */
@Composable
private fun AchievementOverview(snapshot: AchievementSnapshot) {
    val ratio = if (snapshot.total <= 0) 0f else snapshot.unlockedCount.toFloat() / snapshot.total
    val remain = snapshot.total - snapshot.unlockedCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${snapshot.unlockedCount}",
                color = Primary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " / ${snapshot.total}",
                color = TextSecondaryDark,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "已解锁",
                color = TextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(ratio = ratio, color = Primary, height = 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (remain == 0) "全部成就已解锁，镖盘归你。" else "还差 $remain 项，继续打。",
            color = TextSecondaryDark,
            fontSize = 12.sp,
        )
    }
}

/** 分组标题：组名（用户语言）+ 该组进度。 */
@Composable
private fun GroupHeader(group: AchievementGroup, unlocked: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$unlocked/$total",
            color = if (unlocked == total) Primary else TextSecondaryDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 单条成就卡片。 */
@Composable
private fun AchievementRow(progress: AchievementProgress) {
    val unlocked = progress.unlocked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (unlocked) SurfaceDark else SurfaceVariantDark)
            .border(
                width = 1.dp,
                color = if (unlocked) Primary.copy(alpha = 0.35f) else Divider,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AchievementMedal(progress)

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = progress.definition.title,
                    color = if (unlocked) TextPrimaryDark else TextSecondaryDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                // 决策②：每条成就必须标注判定数据的可信度，与结算页卡片共用同一个徽标
                AchievementSourceBadge(progress.definition.dataSource)
            }

            Spacer(Modifier.height(3.dp))
            Text(
                text = progress.definition.description,
                color = TextSecondaryDark,
                fontSize = 11.sp,
            )

            Spacer(Modifier.height(6.dp))
            if (unlocked) {
                Text(
                    text = progress.unlockedAt
                        ?.let { "解锁于 ${formatUnlockedAt(it)}" }
                        ?: "已解锁",
                    color = Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                ProgressBar(ratio = progress.ratio, color = Secondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progress.progressText,
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * 左侧徽记：已解锁显示对勾，未解锁显示**达成百分比**。
 *
 * 用百分比而不是锁形图标，是为了让「按达成度排序」这件事在每张卡上自解释 ——
 * 用户一眼就能看出上面那张为什么排在这张前面。
 */
@Composable
private fun AchievementMedal(progress: AchievementProgress) {
    val unlocked = progress.unlocked
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (unlocked) Primary else SurfaceDark)
            .border(1.dp, if (unlocked) Primary else Divider, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (unlocked) "\u2713" else "${(progress.ratio * 100).roundToInt()}%",
            color = if (unlocked) OnPrimary else TextSecondaryDark,
            fontSize = if (unlocked) 22.sp else 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 自绘进度条：不依赖 material3 进度组件，样式与全局卡片统一。
 * [ratio] 会被夹到 0..1，调用方不必自己防御越界值。
 */
@Composable
private fun ProgressBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val safe = ratio.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(SurfaceVariantDark),
    ) {
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safe)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

/**
 * 解锁时刻（本地时区）。
 *
 * 刻意用 `SimpleDateFormat` 而非 `java.time`：本项目 `minSdk = 24` 且未启用
 * core library desugaring，`java.time` 在 24/25 上不可用。与全局卡片同源，
 * 文案里带上时间是为了让「补算」这件事可核对 —— 用户能看到这些成就是刚补发的。
 */
private fun formatUnlockedAt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
