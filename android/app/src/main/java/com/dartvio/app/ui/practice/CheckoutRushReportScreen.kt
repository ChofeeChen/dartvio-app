package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.CheckoutRushRepository
import com.dartvio.app.domain.practice.CheckoutRushStatistics
import com.dartvio.app.domain.practice.CheckoutSolver
import com.dartvio.app.domain.practice.RushAttemptRecord
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning

/**
 * 极速结镖 · 会话报告（提示词 §九）。
 *
 * ## 排版层次：四段，从上到下「越往下越细」
 * 1. **总览** —— 一次训练成不成，看这一块就够了（题数 / 成功率 / 无提示成功率 / 连续成功）
 * 2. **速度** —— 本模式的主指标（最快无提示成功 / 中位 / 平均）
 * 3. **质量** —— 错在哪（镖数分布 / 爆分原因 / 最常失败目标）
 * 4. **明细** —— 逐题留痕，用于回看某一题具体怎么打的
 *
 * 「录入用时」单独放在最末的交互分析块，并**明写不计成绩**：
 * 它和训练成绩不是一回事，混进速度块会让人以为练的是手速而不是结镖。
 *
 * ## 为什么报告只读数据库
 * 与 `VersusReportScreen` 同款约定：「再练一次」之后回退栈可能把这一页带回来，
 * 那时内存里的会话已经是下一次训练了 —— 只有按 `sessionId` 回库读才不会串。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutRushReportScreen(
    sessionId: String,
    difficultyLabel: String,
    onExit: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val stats by produceState<CheckoutRushStatistics?>(
        initialValue = null,
        sessionId,
        context,
    ) {
        value = CheckoutRushRepository(context).statisticsOf(sessionId)
    }
    val records by produceState<List<RushAttemptRecord>>(
        initialValue = emptyList(),
        sessionId,
        context,
    ) {
        value = CheckoutRushRepository(context).sessionRecords(sessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "会话报告",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                )
            },
            navigationIcon = { TextButton(onClick = onExit) { Text("完成", color = Primary) } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark,
                navigationIconContentColor = Primary,
                titleContentColor = TextPrimaryDark,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(RushPagePadding)
        ) {
            val s = stats
            if (s == null) {
                Text("正在生成报告…", fontSize = 13.sp, color = TextSecondaryDark)
            } else {
                Text(
                    "难度：$difficultyLabel · 共 ${s.scoredAttempts} 题"
                        + if (s.skippedCount > 0) " · 跳过 ${s.skippedCount}" else ""
                        + if (s.abortedCount > 0) " · 中断 ${s.abortedCount}" else "",
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                )

                Spacer(Modifier.height(12.dp))
                RushCard(title = "① 总览") {
                    Spacer(Modifier.height(8.dp))
                    StatLine("题数（计入分母）", "${s.scoredAttempts}")
                    StatLine("成功题数", "${s.successCount}")
                    StatLine("成功率（含提示）", "${s.successRatePct}%", emphasize = true)
                    StatLine(
                        "无提示成功率",
                        "${s.unhintedSuccessRatePct}%（${s.unhintedSuccessCount}/${s.unhintedAttempts}）",
                        emphasize = true,
                    )
                    StatLine("使用提示题数", "${s.hintedAttempts}（成功率 ${s.hintedSuccessRatePct}%）")
                    StatLine("最长连续成功", "${s.longestSuccessStreak} 题")
                }

                Spacer(Modifier.height(12.dp))
                RushCard(title = "② 速度", note = "只统计完成投掷的题目，跳过与中断不计入") {
                    Spacer(Modifier.height(8.dp))
                    StatLine(
                        "最快无提示成功",
                        s.fastestUnhintedSuccessMs?.let(::formatReportMs) ?: "—",
                        emphasize = true,
                    )
                    StatLine("投掷用时中位数", s.medianThrowMs?.let(::formatReportMs) ?: "—")
                    StatLine("投掷用时平均", s.avgThrowMs?.let(::formatReportMs) ?: "—")
                }

                Spacer(Modifier.height(12.dp))
                RushCard(title = "③ 质量") {
                    Spacer(Modifier.height(8.dp))
                    val dist = s.checkoutDartDistribution
                    if (dist.isEmpty()) {
                        StatLine("结镖镖数分布", "本会话没有成功结镖")
                    } else {
                        (1..3).forEach { n ->
                            val c = dist[n] ?: 0
                            if (c > 0) StatLine("$n 镖结镖", "$c 次")
                        }
                    }
                    StatLine("爆分次数", "${s.bustCount}")
                    s.bustReasons.forEach { (reason, count) ->
                        StatLine("　${reason.label}", "$count 次")
                    }
                    StatLine("最常失败目标", s.mostFailedTarget?.let { "$it 分" } ?: "—（本会话没有失败题）")
                }

                Spacer(Modifier.height(12.dp))
                RushCard(
                    title = "④ 录入交互（不计成绩）",
                    note = "用于分析录入方式效率，永不参与训练成绩与个人最佳",
                ) {
                    Spacer(Modifier.height(8.dp))
                    StatLine("平均录入用时", s.avgInputMs?.let(::formatReportMs) ?: "—")
                    s.avgInputMsBySource.forEach { (source, ms) ->
                        StatLine("　${source.label}", formatReportMs(ms))
                    }
                }

                if (records.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    RushCard(title = "⑤ 逐题明细", note = "按做题顺序，记录本会话每一次尝试") {
                        Spacer(Modifier.height(8.dp))
                        records.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${r.target} → ${r.result.label}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimaryDark,
                                    )
                                    Text(
                                        buildString {
                                            if (r.darts.isEmpty()) append("未录入") else append(CheckoutSolver.formatRoute(r.darts))
                                            if (r.routeHintUsed) append(" · 已用提示")
                                            if (r.timingInvalidated) append(" · 不计最佳")
                                            if (r.retryOfAttemptId != 0L) append(" · 重做")
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatReportMs(r.throwElapsedMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (r.isSuccess) Success else Warning,
                                    )
                                    Text(
                                        "${r.difficulty.label}",
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark,
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }

        // 按钮钉底：报告滚它的，动作不跟着滚出去。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(RushPagePadding)
        ) {
            RushPrimaryButton("再练一次（同设置）", onClick = onRetry)
            Spacer(Modifier.height(10.dp))
            RushSecondaryButton("返回训练中心", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondaryDark)
        Text(
            value,
            fontSize = if (emphasize) 14.sp else 12.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasize) Accent else TextPrimaryDark,
        )
    }
}

private fun formatReportMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    return "${safe / 1000}.${(safe % 1000) / 100} s"
}
