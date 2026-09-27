package com.dartvio.app.ui.practice

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.ImpactRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.domain.impact.ImpactCalculator
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.practice.formatMpr
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark

/**
 * 训练中心 · **单人训练**（C1 两级入口的第二级）。
 *
 * 这里就是改造前的那张「6 张卡」列表，一行逻辑未改，只是从一级页下沉为二级页：
 * 训练中心第一级现在只有「单人训练 / 双人对抗训练」两张卡 ——
 * 现有六项都不需要选对手，与「对抗」不是一类，混在一级会让人以为对抗练习也是单人练。
 */
@Composable
fun PracticeSoloScreen(
    onBack: (() -> Unit)? = null,
    onCountUp: () -> Unit = {},
    onRandomCheckout: () -> Unit = {},
    onNinetyNine: () -> Unit = {},
    onCricketMpr: () -> Unit = {},
    onAiDrill: () -> Unit = {},
    onImpact: () -> Unit = {},
) {
    val context = LocalContext.current
    val (checkoutAttempts, checkoutSuccesses) = remember {
        val prefs = context.getSharedPreferences("dartvio_practice", Context.MODE_PRIVATE)
        prefs.getInt("random_checkout_attempts", 0) to prefs.getInt("random_checkout_successes", 0)
    }
    val checkoutRate = if (checkoutAttempts == 0) 0 else checkoutSuccesses * 100 / checkoutAttempts
    val checkoutStats = if (checkoutAttempts > 0) "今日 $checkoutAttempts 次 · 成功率 $checkoutRate%" else null

    val ninetyNineBest = remember {
        val prefs = context.getSharedPreferences(NinetyNineStatsStore.PREFS_NAME, Context.MODE_PRIVATE)
        (1..20).maxOfOrNull { prefs.getInt(NinetyNineStatsStore.bestKey(it), 0) } ?: 0
    }
    val ninetyNineStats = if (ninetyNineBest > 0) "历史最佳 $ninetyNineBest 分" else null

    // 落点诊断的历史统计来自数据库（不是 SharedPreferences），因此走协程读；
    // 没数据时整行不显示，避免入口卡上出现「0 镖 · R95 —」这种噪声。
    val impactStats by produceState<String?>(initialValue = null, context) {
        value = loadImpactCardStats(context)
    }

    val mprStats = remember {
        val sessions = CricketMprStatsStore.sessions(context)
        val best = CricketMprStatsStore.best(context)
        if (sessions > 0) {
            "历史最佳 MPR ${formatMpr(best)} · 已练 $sessions 次"
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "单人训练",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("返回", color = Primary) }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = Primary,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            /*
             * 标题「选择训练项目」删掉了：顶栏已经写着「单人训练」，
             * 下面六张卡各自带标题，中间再来一句「请选择」只是重复。
             * 顺带把第一张卡顶到容器上沿 —— 早先它被标题 + 16dp spacer 压下去，
             * 六张卡的起始位置各不相同，看不出这是一列（2026-09-26 反馈）。
             */

            // 精准工坊**排在最前**：它是这一页唯一的诊断型训练，也是唯一带
            // 渐变描边 + 「特色」标记的卡。放在第四位时，用户要滑过三张
            // 「打靶得分」类的卡才看到它，而它解决的是完全不同的问题（准度诊断）。
            // 它也是唯一走 Accent 色的卡：差异化功能必须一眼看得出和别的不是一类。
            PracticeCard(
                title = "精准工坊",
                desc = "选一个目标点录每一镖，看误差热力图与偏移/散布诊断",
                stats = impactStats,
                accent = Accent,
                featured = true,
                onClick = onImpact,
            )
            Spacer(Modifier.height(12.dp))
            // 其余项目统一用同一支颜色（Secondary）：入口卡不是分类图例，
            // 六张卡各占一色只会让用户以为颜色有含义。
            PracticeCard(
                title = "Count Up",
                desc = "8 轮 24 镖，累计最高分",
                accent = Secondary,
                onClick = onCountUp,
            )
            Spacer(Modifier.height(12.dp))
            // 改名「结镖训练」（提示词 §五.1）：下面进的是一个入口页 ——
            // 路线学习（原随机结镖，行为不变）与极速挑战（新增计时）在里面选。
            // 刻意**不新增第二张卡**：它们是同一件事的两种练法，不是两个训练项目。
            PracticeCard(
                title = "结镖训练",
                desc = "路线学习 / 极速挑战，练最短结镖路线",
                stats = checkoutStats,
                accent = Secondary,
                enabled = true,
                onClick = onRandomCheckout,
            )
            Spacer(Modifier.height(12.dp))
            PracticeCard(
                title = "99 Darts",
                desc = "选定 1 个分区，99 镖专注度训练",
                stats = ninetyNineStats,
                accent = Secondary,
                onClick = onNinetyNine,
            )
            Spacer(Modifier.height(12.dp))
            PracticeCard(
                title = "Cricket MPR 挑战",
                desc = "10 轮标记率挑战，检验 Cricket 准度",
                stats = mprStats,
                accent = Secondary,
                onClick = onCricketMpr,
            )
            Spacer(Modifier.height(12.dp))
            PracticeCard(
                title = "AI 对战练习",
                desc = "选择难度与 DartBot 单独对抗",
                accent = Secondary,
                onClick = onAiDrill,
            )
        }
    }
}

/**
 * 落点诊断入口卡的统计行：累计镖数 + 最近 R95。
 *
 * 只统计**标准档**的窗内镖：跨档位的毫米数不可比，把 ±60 mm 的成绩和 ±30 mm 的混在一起算 R95
 * 会让这个数既不代表准度、也不代表难度。样本不足 [ImpactCalculator.MIN_BIAS_N] 时干脆不给 R95。
 */
private suspend fun loadImpactCardStats(context: Context): String? {
    val repository = ImpactRepository(DartVioDatabase.get(context).dartHitDao())
    val total = repository.totalDarts()
    if (total <= 0) return null
    val frames = repository.recentDarts(60)
        .filter { it.windowSpanMm == ImpactWindow.STANDARD_SPAN_MM.toFloat() }
        .mapNotNull { hit ->
            IntentTarget.fromStored(hit.intentNumber, hit.intentMultiplier)
                ?.let { target -> ImpactRepository.frameOf(hit, target) }
        }
    val stats = ImpactCalculator.of(frames)
    return if (stats == null || stats.n < ImpactCalculator.MIN_BIAS_N) {
        "历史已录 $total 镖"
    } else {
        "历史已录 $total 镖 · 最近 R95 ${(stats.r95 * 10).toInt() / 10.0} mm"
    }
}

@Composable
private fun PracticeCard(
    title: String,
    desc: String,
    stats: String? = null,
    accent: Color,
    /**
     * 差异化功能卡（精准工坊）：**渐变底 + 渐变描边 + 渐变色条 + 「特色」标记**，
     * 与用纯色的普通训练项目一眼区分。
     *
     * 不走「换一个新色相」的画法：品牌色只有 Primary / Secondary / Accent 三支，
     * 为一张卡新造第四色会让色卡失去约束，渐变仍在既有三色内。
     */
    featured: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val brandStroke = Brush.linearGradient(listOf(Primary, Accent))
    val brandFill = Brush.linearGradient(
        listOf(Primary.copy(alpha = 0.16f), Accent.copy(alpha = 0.16f))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    !enabled -> Modifier.background(SurfaceVariantDark, shape)
                    featured -> Modifier.background(brandFill, shape)
                    else -> Modifier.background(MaterialTheme.colorScheme.surface, shape)
                }
            )
            .then(
                if (featured) Modifier.border(2.dp, brandStroke, shape)
                else Modifier.border(1.5.dp, accent, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp, 48.dp)
                .background(
                    if (featured) Brush.verticalGradient(listOf(Primary, Accent)) else SolidColor(accent),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else TextDisabledDark,
                )
                if (featured) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "特色",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier
                            .background(Accent.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                desc,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats != null) {
                Text(
                    stats,
                    fontSize = 12.sp,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
