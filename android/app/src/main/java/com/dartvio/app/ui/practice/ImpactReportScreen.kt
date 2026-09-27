package com.dartvio.app.ui.practice

import androidx.compose.ui.graphics.toArgb
import com.dartvio.app.ui.theme.DrawingColors
import com.dartvio.app.ui.theme.drawingColors
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.domain.impact.HeatmapGrid
import com.dartvio.app.domain.impact.ImpactCalculator
import com.dartvio.app.domain.impact.ImpactFrame
import com.dartvio.app.domain.impact.ImpactFrames
import com.dartvio.app.domain.impact.ImpactStats
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.TrendDirection
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.domain.vision.Point2
import com.dartvio.app.ui.components.TrendLineChart
import com.dartvio.app.ui.components.drawBoardViewportIn
import kotlin.math.cos
import kotlin.math.sin
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.HeatCold
import com.dartvio.app.ui.theme.HeatHot
import com.dartvio.app.ui.theme.HeatMid
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning

// ------------------------------------------------------------------ 报告页字阶
//
// 全页只有**这七个字号 / 四档颜色**，任何一处文字都必须落在其中一级：
// 此前各卡自定字号 + 自定颜色，同一份语义（图注、脚注、指标值）在不同卡上长得不一样，
// 读者会把这种差异读成「这两种信息的地位不同」（2026-09-26 反馈「文字杂乱」）。
//
// 层级：页面标题 > 卡片标题 > 卡片内结论句 > 正文 > 正文强调 > 图注 > 脚注。
// 颜色与层级**绑定**：标题用主文本色，正文用次文本色，图注 / 脚注逐级降到 disabled，
// 不再出现「正文用了脚注色」这种错位。
private val TextPageTitle = 22.sp
private val TextCardTitle = 14.sp
private val TextConclusion = 15.sp
private val TextBody = 12.sp
private val TextBodyStrong = 13.sp
private val TextCaption = 11.sp
private val TextFootnote = 10.sp
private val TextMetricValue = 17.sp

/** 层级 1：页面标题。 */
@Composable
private fun PageTitle(text: String) {
    Text(text, color = TextPrimaryDark, fontSize = TextPageTitle, fontWeight = FontWeight.Bold)
}

/** 层级 2：卡片标题。全页卡片标题一律同一字号同一色。 */
@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = TextPrimaryDark, fontSize = TextCardTitle, fontWeight = FontWeight.Bold)
}

/** 层级 3：卡片内的结论句（摘要 headline、疗程结论这类「读完这句就够了」的话）。 */
@Composable
private fun ConclusionText(text: String, color: Color = TextPrimaryDark) {
    Text(text, color = color, fontSize = TextConclusion, fontWeight = FontWeight.Bold)
}

/** 层级 4：正文。 */
@Composable
private fun BodyText(text: String, color: Color = TextSecondaryDark, bold: Boolean = false) {
    Text(
        text,
        color = color,
        fontSize = TextBody,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}

/** 层级 5：正文强调（结论徽语、警告句）——比正文大半号，仍然是正文色阶。 */
@Composable
private fun BodyStrongText(text: String, color: Color = TextPrimaryDark) {
    Text(text, color = color, fontSize = TextBodyStrong, fontWeight = FontWeight.Bold)
}

/** 层级 6：图注 / 表注。 */
@Composable
private fun CaptionText(text: String, color: Color = TextSecondaryDark) {
    Text(text, color = color, fontSize = TextCaption)
}

/** 层级 7：脚注（口径、边界说明）。 */
@Composable
private fun FootnoteText(text: String) {
    Text(text, color = TextDisabledDark, fontSize = TextFootnote)
}

/**
 * 精准工坊 · 轮后报告（设计 §5.4）。
 *
 * ## 阅读层次（2026-09-18 重排，2026-09-26 再调顺序）
 *
 * 之前是一串平铺的 ①~⑩ 卡片：每张卡标题 + 图 + 一大段说明，读者要自己判断「先读哪张」，
 * 而且编号本身会让人以为漏一张卡。现在按**结论 → 图 → 数字 → 追溯**四层重排：
 *
 * - **摘要**（[SummaryCard]）：一句话结论 + 三个核心数字 + 「下一轮只做这一件事」；
 *   这三个原本分别埋在 ④ 与「趋势」卡里，现在提到第一屏。
 * - **看图**（三张图）：先看**真实位置**（绝对落点），再看误差结构（热力图 / 出框方向）——
 *   「镖落在盘上哪儿」比「相对瞄点偏了多少」更直觉，而后者要先把前者看懂才有意义。
 *   每张图只留**一行图注**，长解释收进标题右侧的「怎么读」。
 * - **数字**：诊断结论、本轮目标、趋势 —— 指标统一用三栏对齐的 [MetricRow]。
 * - **进阶 / 明细**：默认折叠，需要再翻。
 *
 * 两块硬约束贯穿全页：
 * ① 出框镖**只进计数、不进 σ / R95 / KDE**；② 样本不够就只说「还差几镖」，
 * 数字结论的闸门在 ViewModel（[ImpactReportUiState.statsReady]），不靠 UI 藏。
 */
@Composable
fun ImpactReportScreen(
    onExit: () -> Unit,
    onNextRound: () -> Unit,
    viewModel: ImpactReportViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var advancedExpanded by remember { mutableStateOf(false) }
    var detailExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            // 自绘头部必须自己吃状态栏内边距（`enableEdgeToEdge`），否则标题会压到状态栏上与时间重叠。
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onExit) {
                Text("返回", color = Primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            PageTitle("这一轮的结果")
        }

        if (!state.loaded) return@Column
        if (!state.hasSession || state.dartCount == 0) {
            EmptyReport(onExit)
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- 第一层：结论 ----
            SummaryCard(state, onToggleMerge = { viewModel.toggleCrossMerged() })
            NoticeStack(state)

            // ---- 第二层：看图（长解释收进「怎么读」）----
            //
            // 顺序：**先看真实位置，再看误差结构**。绝对落点回答「镖落在盘上哪儿」，
            // 不需要任何换算；热力图回答「相对瞄点偏了多少」，那要先把前一件事看懂才有意义。
            Spacer(Modifier.height(12.dp))
            FigureCard(
                title = "绝对落点",
                caption = "命中 ${state.hitCount} / ${state.dartCount} 镖（含出框镖的外推点）。",
                howTo = "这张图是标准盘上的**真实位置**：绿点 = 命中目标环、红点 = 未命中。" +
                    "下面两张是「相对瞄点」的图，口径不同，别混着读。"
            ) {
                AbsoluteScatter(state.sessionPoints)
            }

            Spacer(Modifier.height(12.dp))
            FigureCard(
                title = "误差热力图",
                caption = "中心 = 瞄点；虚线圈 = R95；浅灰线 = 同比例的镖靶轮廓。",
                howTo = "上下方向：上 = 偏外、下 = 偏内；左右 = 切向（与录制页同朝向）。" +
                    "图中心的十字就是瞄点，镖靶轮廓（分区线 / 环线）按同一比例叠在下面，" +
                    "用来给热点一个「落在哪条环上」的参照 —— 它们刻意很淡，不抢热点的位置。"
            ) {
                if (state.statsReady && state.frames.isNotEmpty()) {
                    ErrorHeatmap(
                        frames = state.frames,
                        halfSpanMm = state.heatHalfSpanMm(),
                        stats = state.stats,
                        target = state.target
                    )
                } else {
                    NotEnough(state)
                }
            }

            Spacer(Modifier.height(12.dp))
            FigureCard(
                title = "出框方向",
                caption = if (state.missCount == 0) {
                    "这一轮没有出框镖 —— 窗口把落点全兜住了。"
                } else {
                    "出框 ${state.missCount} / ${state.dartCount} 镖" +
                        "（${(state.missCount * 100 / state.dartCount)}%）。"
                },
                howTo = "条带内半 = 轻微出框、外半 = 远出框 / 靶外。出框镖**只进这个计数**，" +
                    "绝不动 σ / R95 / 热力图。"
            ) {
                if (state.missCount == 0) {
                    BodyStrongText("窗口把落点全兜住了。", Success)
                } else {
                    MissDirectionDiagram(state.bandTotals, state.inWindow)
                    if (state.suggestWide) {
                        Spacer(Modifier.height(6.dp))
                        CaptionText(
                            "出框超过 20% ⇒ 下一轮切「宽松」档，先把落点看全再收窗口。",
                            Warning
                        )
                    }
                }
            }

            // ---- 第三层：数字 ----
            Spacer(Modifier.height(12.dp))
            ConclusionCard(state)

            Spacer(Modifier.height(12.dp))
            GoalCard(state)

            Spacer(Modifier.height(12.dp))
            TrendCard(state)

            Spacer(Modifier.height(12.dp))
            OverlayCard(state)

            // ---- 第四层：进阶 / 明细（折叠）----
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { advancedExpanded = !advancedExpanded },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
            ) {
                Text(
                    if (advancedExpanded) "收起进阶分析" else "展开进阶分析（收益 / 指纹 / 干预 / 回合）",
                    fontWeight = FontWeight.Bold
                )
            }
            if (advancedExpanded) {
                Spacer(Modifier.height(12.dp))
                SectionCard("改瞄点值不值") {
                    ImpactValueCard(
                        cev = state.cev,
                        finishRates = state.finishRates,
                        target = state.target
                    )
                }
                Spacer(Modifier.height(12.dp))
                SectionCard("投掷指纹（同档位 · 跨目标）") {
                    val fingerprint = state.fingerprint
                    if (fingerprint == null) {
                        BodyText("这一档位还不足 30 镖，暂不出指纹 —— 占比在小样本上会来回跳。")
                    } else {
                        ImpactFingerprintBar(fingerprint)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionCard("干预对照") {
                    ImpactInterventionTable(state.intervention)
                }
                Spacer(Modifier.height(12.dp))
                SectionCard("三镖回合（完整回合）") {
                    val advice = state.roundAdvice
                    if (advice == null) {
                        BodyText("还没有完整回合可统计。")
                    } else {
                        MetricRow(
                            "完整回合",
                            "${state.roundMetrics.completeRounds} 个",
                            "三镖全中 ${state.roundMetrics.perfectRounds} 个"
                        )
                        if (state.roundMetrics.completeRounds > 0) {
                            MetricRow(
                                "回合内散布",
                                "均值 ${mm(state.roundMetrics.roundSpread)}",
                                "最紧 ${mm(state.roundMetrics.roundSpreadMin)} / 最散 ${mm(state.roundMetrics.roundSpreadMax)}"
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                            BodyText(advice.text, if (advice.ready) Secondary else TextSecondaryDark)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            DetailCard(state, detailExpanded) { detailExpanded = !detailExpanded }

            Spacer(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                Text("返回")
            }
            Button(
                onClick = { viewModel.beginNextRound(onReady = onNextRound) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("下一轮（同目标）", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 摘要卡：**一眼给人一轮的结论**，而不是把人丢进十张卡里自己找。
 *
 * 三个核心数字（平均偏差 / R95 / 综合偏离）用等宽三栏：[StatTile]，
 * 之前它们散落在一段文字里，`label` 与数值之间没有对齐关系。
 */
@Composable
private fun SummaryCard(state: ImpactReportUiState, onToggleMerge: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "目标 ${state.target.label} · ${ImpactWindowLabel(state.spanMm)} · ${state.dartCount} 镖",
                color = TextSecondaryDark,
                fontSize = TextBody,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            MergeChip(state.crossMerged, onToggleMerge)
        }

        Spacer(Modifier.height(10.dp))
        ConclusionText(state.headline ?: "这一轮还没攒够样本，先看下面三张图找疑点。")
        state.scatterAdvice?.let {
            Spacer(Modifier.height(4.dp))
            CaptionText(it, Secondary)
        }

        val stats = state.stats
        if (state.statsReady && stats != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("平均偏差", mm(stats.bias))
                StatTile("R95", mm(stats.r95))
                StatTile("综合偏离", mm(stats.rmse))
            }
            Spacer(Modifier.height(6.dp))
            FootnoteText("n = ${stats.n}　脚注：σ / R95 含手点误差，属上限估计。")
        } else {
            Spacer(Modifier.height(10.dp))
            NotEnough(state)
        }

        if (state.nextAction.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            TakeawayBar(state.nextAction)
        }
    }
}

/** 「下一轮只做这一件事」：单独成条，不混在结论正文里。 */
@Composable
private fun TakeawayBar(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CaptionText("下一步", Accent)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = Accent,
            fontSize = TextBodyStrong,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 三栏等宽的数字块：数值比标签大一号，竖排居中，列与列对齐。 */
@Composable
private fun RowScope.StatTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Accent, fontSize = TextMetricValue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        CaptionText(label)
    }
}

/** 三条口径提示合并成一摞：原来各自占一整行，把结论挤出首屏。 */
@Composable
private fun NoticeStack(state: ImpactReportUiState) {
    val notices = buildList {
        if (state.crossMerged) add("跨目标合并：各目标的系统偏移被平均掉，只看随机散布倾向。")
        if (state.mixedSpans.size > 1) {
            val other = state.mixedSpans.filter { it != state.spanMm.toFloat() }
                .joinToString(" / ") { "${it.toInt()} mm" }
            add("「${state.target.label}」还有 $other 档位的成绩，本页只统计本档（跨档位不可比）。")
        }
        if (state.historyLimited) {
            add("历史镖数超过 ${com.dartvio.app.data.ImpactRepository.HISTORY_LIMIT}，趋势只用了最近这一段。")
        }
    }
    notices.forEach {
        Spacer(Modifier.height(6.dp))
        Notice(it)
    }
}

/**
 * 图卡：一行图注 + 折叠的「怎么读」。
 *
 * ## 「怎么读」为什么写在**标题那一行**
 *
 * 早先它是图下面单独一个按钮，占一整行、压在极需要空间的图下方，而且在不同图卡上
 * 位置忽左忽右。移到标题行**右端**之后：它成了标题的附属（读作「这张图的说明」），
 * 每张图都在同一个位置，眼睛不用扫两遍。
 *
 * 图下的长说明原本和图表同等醒目，读图的人第一眼看到的是四行文；现在默认只留一句图注。
 */
@Composable
private fun FigureCard(
    title: String,
    caption: String,
    howTo: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var howToOpen by remember { mutableStateOf(false) }
    SectionCard(
        title = title,
        action = {
            // 「怎么读这张图」/「收起读法」：标签本身就把状态说清楚了，不用再加一个状态字。
            ReportLink(text = howToLabel(howToOpen)) { howToOpen = !howToOpen }
        }
    ) {
        if (howToOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Warning.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                CaptionText(howTo, TextDisabledDark)
            }
            Spacer(Modifier.height(8.dp))
        }
        content()
        Spacer(Modifier.height(6.dp))
        CaptionText(caption)
    }
}

/**
 * 卡片里的**文本型操作**（「怎么读这张图」/「32 镖」）。
 *
 * 用文本按钮而不是带边框的 chip：它是标题行里的一个**附属动作**，
 * 画成 chip 会在视觉上与卡片本身争主次 —— chip 的层级跟卡片标题差不多。
 */
@Composable
private fun ReportLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        CaptionText(text, Secondary)
    }
}

private fun howToLabel(open: Boolean): String = if (open) "收起读法" else "怎么读这张图"

@Composable
private fun ConclusionCard(state: ImpactReportUiState) {
    SectionCard(title = "诊断结论") {
        val stats = state.stats
        if (stats == null || !state.statsReady) {
            NotEnough(state)
        } else {
            MetricRow("平均偏差", mm(stats.bias), "≈ ${oneDecimal(stats.bias / ImpactCalculator.RING_WIDTH_MM)} 环宽")
            MetricRow("σ 径向 / 切向", "${mm(stats.sigmaRad)} / ${mm(stats.sigmaTan)}", "")
            MetricRow("R95", mm(stats.r95), "≈ ${oneDecimal(stats.r95 / ImpactCalculator.RING_WIDTH_MM)} 环宽")
            MetricRow("综合偏离", mm(stats.rmse), "n = ${stats.n}")
            if (state.neighborTop.isNotEmpty()) {
                MetricRow(
                    "最常落到的区",
                    state.neighborTop.joinToString("、") { "${it.first}（${it.second}）" },
                    ""
                )
            }
            Spacer(Modifier.height(6.dp))
            FootnoteText("脚注：σ / R95 已含手点误差，属**上限估计**；均值类结论（平均偏差）对零均值噪声稳健。")
        }
    }
}

/** 本轮目标：没设目标时**不留空卡**，改写一句说明。 */
@Composable
private fun GoalCard(state: ImpactReportUiState) {
    SectionCard(title = "本轮目标") {
        if (state.prescription == null) {
            BodyText("这一轮没设目标 —— 只给描述性结论。下一轮开局可以选一个，否则结束只剩感觉。")
        } else {
            ImpactPrescriptionCard(
                prescription = state.prescription,
                verdict = state.verdict
            )
        }
    }
}

@Composable
private fun TrendCard(state: ImpactReportUiState) {
    SectionCard(title = "趋势（同一目标 · 同一档位）") {
        val trend = state.trend
        if (trend == null || state.trendRounds.size < 2) {
            BodyText(
                "至少需要 3 轮以上（每轮 ≥ ${ImpactCalculator.MIN_BIAS_N} 镖）才能比趋势。" +
                    "现在有 ${state.trendRounds.size} 轮。"
            )
            return@SectionCard
        }
        val tone = when (trend.direction) {
            TrendDirection.IMPROVED -> Success
            TrendDirection.WORSENED -> Error
            TrendDirection.NOT_ENOUGH -> Secondary
            TrendDirection.NOISE -> TextPrimaryDark
        }
        val recent = state.trendRounds.takeLast(6)
        // 曲线先看形状：一条向下走的心电图比六行数字更早说清「有没有在变好」，
        // 而数字留着回答「变好了多少」——两者不是同一个问题，别互相替代。
        val points = recent.mapNotNull { it.stats?.rmse }
        if (points.size >= 2) {
            TrendLineChart(values = points, color = tone, guideColor = Divider, background = SurfaceVariantDark)
            Spacer(Modifier.height(8.dp))
        }
        recent.forEachIndexed { i, round ->
            val stats = round.stats
            if (stats != null) {
                MetricRow(
                    "第 ${state.trendRounds.size - (recent.size - 1) + i} 轮",
                    "偏离 ${mm(stats.rmse)} / R95 ${mm(stats.r95)}",
                    "命中 ${(round.hitRate * 100).toInt()}%"
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BodyStrongText(trend.summary, tone)
    }
}

// 趋势折线（最近若干轮的「综合偏离」）—— 画法在 `ui.components.TrendLineChart`，
// 报告页与数据页共用同一份：为什么画线而不是柱子、为什么要有基线和数据点，理由不写两遍。

@Composable
private fun OverlayCard(state: ImpactReportUiState) {
    SectionCard(title = "新旧叠加（此前 3 轮 vs 最近 3 轮）") {
        val overlay = state.overlay
        if (overlay == null) {
            BodyText("还没有可对比的两轮数据。")
        } else {
            ImpactOverlayChart(overlay)
        }
    }
}

/** 明细默认折叠：30 镖逐行明细放在尾巴上，不挡结论。 */
@Composable
private fun DetailCard(state: ImpactReportUiState, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardTitle("全部明细", modifier = Modifier.weight(1f))
            ReportLink(text = if (expanded) "收起" else "${state.detailRows.size} 镖", onClick = onToggle)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            state.detailRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${row.index}",
                        color = TextDisabledDark,
                        fontSize = TextBody,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        row.main,
                        color = TextPrimaryDark,
                        fontSize = TextBody,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.30f)
                    )
                    Text(
                        row.secondary,
                        color = TextSecondaryDark,
                        fontSize = TextBody,
                        modifier = Modifier.weight(0.44f)
                    )
                    Text(
                        row.highlight.orEmpty(),
                        color = Accent,
                        fontSize = TextCaption,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.26f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            FootnoteText("「最紧 / 最散回合」与「三镖全中」按三镖回合（同一次训练内）标出。")
        }
    }
}

/** 热力图半跨：取两轴较大者的一半，保证窗口（含切向邻居）全部落在图内。 */
private fun ImpactReportUiState.heatHalfSpanMm(): Double =
    maxOf(ImpactWindow.spanX(target, spanMm), ImpactWindow.spanY(spanMm)) / 2.0

@Composable
private fun EmptyReport(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        ConclusionText("这一轮没有记录到镖")
        Spacer(Modifier.height(8.dp))
        BodyText("可能是中途退出了，或者一镖都没点。回到训练中心重新开一轮即可。")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Text("返回")
        }
    }
}

@Composable
private fun NotEnough(state: ImpactReportUiState) {
    val n = state.stats?.n ?: 0
    Text(
        when {
            n == 0 -> "这一轮没有落在窗口内的镖，无法给出分布结论（出框镖只计数，不进散布）。"
            n < ImpactCalculator.MIN_BIAS_N ->
                "窗内只有 $n 镖，少于 ${ImpactCalculator.MIN_BIAS_N} 镖不给任何数字结论 —— " +
                    "现在给出的任何一个数都可能是运气。"
            else -> "窗内 $n 镖：只够给平均偏差（系统偏移），散布与趋势要到 " +
                "${ImpactCalculator.MIN_FULL_N} 镖才成立。"
        },
        color = Warning,
        fontSize = TextBodyStrong
    )
}

/**
 * 统一的分节卡：全页所有二级内容都在这套卡里，**左右边界与标题字号一致**。
 *
 * 卡间距由调用方给（统一 `12.dp`），卡内不再自带纵向外边距 —— 之前卡自带
 * `padding(vertical = 6.dp)`，两层间距叠加后段距不可控。
 *
 * [action] 是标题行右侧的**附属操作**（图卡的「怎么读」、明细的展开开关）。
 * 放在这里而不是让每张卡自己拼一行：两者的对齐、字号、留白就只有一处定义，
 * 不会出现「这张卡的链接贴着标题、那张卡的盯在卡右下角」。
 */
@Composable
private fun SectionCard(
    title: String,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(title, modifier = Modifier.weight(1f))
            if (action != null) action()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * 指标行：**标签 / 数值 / 附注三栏固定比例**，上下行的三列各自对齐成三条竖线。
 *
 * 之前标签固定 `96.dp`、数值紧跟其后、附注再跟在后面 —— 数值一变长，附注就左右乱跳。
 */
@Composable
private fun MetricRow(label: String, value: String, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = TextSecondaryDark,
            fontSize = TextBody,
            modifier = Modifier.weight(0.32f)
        )
        Text(
            value,
            color = TextPrimaryDark,
            fontSize = TextBody,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            hint,
            color = TextDisabledDark,
            fontSize = TextCaption,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.34f)
        )
    }
}

@Composable
private fun Notice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Warning.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        CaptionText(text, Warning)
    }
}

@Composable
private fun MergeChip(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (active) Accent.copy(alpha = 0.18f) else SurfaceVariantDark,
                RoundedCornerShape(8.dp)
            )
            .border(
                if (active) 1.5.dp else 1.dp,
                if (active) Accent else Divider,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            if (active) "跨目标合并 · 开" else "跨目标合并 · 关",
            color = if (active) Accent else TextSecondaryDark,
            fontSize = TextCaption,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 冷 → 中 → 热。 */
private fun heatColor(t: Float): Color = if (t < 0.5f) {
    lerp(HeatCold, HeatMid, t * 2f)
} else {
    lerp(HeatMid, HeatHot, (t - 0.5f) * 2f)
}

/**
 * 叠在热力图上的镖靶线条**有多淡**（alpha）。
 *
 * 轮廓只是给热点一个「落在哪条环上」的参照，主体永远是热点的位置；把它画实，
 * 读者的眼睛会先去找线条交点而不是热点，整张图就白画了。
 */
private const val HEATMAP_BOARD_LINE_ALPHA = 0.16f

/** 环线的采样段数：够圆，又不至于在每次重绘时多算几百个点。 */
private const val BOARD_ARC_STEPS = 240

/**
 * 误差热度图：+x = 切向、+y = 径向（偏外朝上）。
 *
 * 点集**只含窗内镖**（调用方已过滤），出框点不进 KDE —— 否则会沿窗口边界堆出假高峰。
 *
 * 底层另叠一层**同比例的镖靶局部轮廓**（环线 + 分区射线，见 [DrawScope.drawBoardOutline]）：
 * 热力图的坐标是把靶面旋转到「径向朝上」之后的误差坐标，与「镖落在盘上哪儿」隔了一层换算；
 * 有了这层轮廓，热点与双 / 三倍环的位置关系能直接看出来。
 */
@Composable
private fun ErrorHeatmap(
    frames: List<ImpactFrame>,
    halfSpanMm: Double,
    stats: ImpactStats?,
    target: IntentTarget
) {
    val Accent = com.dartvio.app.ui.theme.Accent
    val Divider = com.dartvio.app.ui.theme.Divider
    val Error = com.dartvio.app.ui.theme.Error
    val Primary = com.dartvio.app.ui.theme.Primary
    val Secondary = com.dartvio.app.ui.theme.Secondary
    val Success = com.dartvio.app.ui.theme.Success
    val SurfaceVariantDark = com.dartvio.app.ui.theme.SurfaceVariantDark
    val TextDisabledDark = com.dartvio.app.ui.theme.TextDisabledDark
    val TextPrimaryDark = com.dartvio.app.ui.theme.TextPrimaryDark
    val TextSecondaryDark = com.dartvio.app.ui.theme.TextSecondaryDark
    val Warning = com.dartvio.app.ui.theme.Warning

    val grid = remember(frames, halfSpanMm) {
        HeatmapGrid.render(frames.map { Point2(it.eTan, it.eRad) }, halfSpanMm)
    }
    val cells = HeatmapGrid.cellCount(halfSpanMm)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
    ) {
        val side = minOf(size.width, size.height)
        val scale = (side / (2 * halfSpanMm)).toFloat()
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val cellPx = (HeatmapGrid.DEFAULT_CELL_MM * scale).toFloat()
        for (row in 0 until cells) {
            for (col in 0 until cells) {
                val v = grid[row * cells + col]
                if (v <= 0.03f) continue
                val xMm = col * HeatmapGrid.DEFAULT_CELL_MM - halfSpanMm
                val yMm = row * HeatmapGrid.DEFAULT_CELL_MM - halfSpanMm
                val left = ox + side / 2f + (xMm.toFloat() * scale) - cellPx / 2f
                val top = oy + side / 2f - (yMm.toFloat() * scale) - cellPx / 2f
                drawRect(
                    color = heatColor(v).copy(alpha = (0.15f + 0.75f * v)),
                    topLeft = Offset(left, top),
                    size = Size(cellPx + 0.6f, cellPx + 0.6f)
                )
            }
        }
        // 坐标轴（径向 / 切向零线）：0 在正中，一眼看出「平均偏哪边」。
        val cx = ox + side / 2f
        val cy = oy + side / 2f
        // 镖靶轮廓垫在**热点之下**：线要很淡，先被视为背景，读者才会在轮廓之上读热点。
        drawBoardOutline(target = target, cx = cx, cy = cy, scale = scale, lineColor = Divider)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        drawLine(Divider, Offset(ox, cy), Offset(ox + side, cy), 1.5f, pathEffect = dash)
        drawLine(Divider, Offset(cx, oy), Offset(cx, oy + side), 1.5f, pathEffect = dash)
        // R95 圈（相对质心）。
        if (stats != null && stats.n >= ImpactCalculator.MIN_BIAS_N) {
            val centerX = cx + (stats.biasTan.toFloat() * scale)
            val centerY = cy - (stats.biasRad.toFloat() * scale)
            drawCircle(
                color = Accent,
                radius = (stats.r95 * scale).toFloat(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.6.dp.toPx(), pathEffect = dash)
            )
            drawCircle(Accent, radius = 3.dp.toPx(), center = Offset(centerX, centerY))
        }
        drawCircle(TextPrimaryDark, radius = 2.dp.toPx(), center = Offset(cx, cy))
    }
}

/**
 * 热力图上的**镖靶局部轮廓**（同比例，[HEATMAP_BOARD_LINE_ALPHA] 这么淡）。
 *
 * ## 坐标怎么写出来的
 *
 * 热力图画的是误差：`eRad` 沿**径向单位向量**、`eTan` 沿**切向单位向量**（见 [ImpactFrames]），
 * 原点则是瞄点 [IntentTarget.anchorMm] —— 也就是目标分区的中心线与所在环的中线交点。
 * 所以把一个靶面点 `p` 画到热力图上，只需把它相对锚点的偏移投到这两个单位向量上：
 *
 * ```
 * d = p − anchor；x = 中心 + (d·切向)·比例；y = 中心 − (d·径向)·比例
 * ```
 *
 * 这个变换是**刚体旋转**（两个单位向量正交），所以直线仍旧是直线；
 * 环（靶心圆）在这个窗里是曲率很小的一段，用多段线近似足够。
 *
 * ## 为什么这层轮廓有用
 *
 * 「偏外 6mm」这句话本身没有参照；画出双 / 三倍环与分区线之后，
 * 用户才知道热点是压在环上、还是已经越过了环 —— 那才是改动作时真正要想的事。
 */
private fun DrawScope.drawBoardOutline(
    target: IntentTarget,
    cx: Float,
    cy: Float,
    scale: Float,
    // 颜色由调用方在 @Composable 里取好再传进来：Canvas 的绘制回调不是可组合作用域，
    // 而主题色（`MaterialTheme.colorScheme.*`）只能在那里读 —— 反过来在本函数里读会编译不过。
    lineColor: Color
) {
    val anchor = target.anchorMm()
    val radial = ImpactFrames.radialUnit(target)
    val tangent = ImpactFrames.tangentUnit(target)
    val color = lineColor.copy(alpha = HEATMAP_BOARD_LINE_ALPHA)
    val width = 1f

    /** 靶面 mm → 画布偏移（dt = 切向分量、dr = 径向分量，见上方注释）。 */
    fun point(xMm: Double, yMm: Double): Offset {
        val dx = xMm - anchor.x
        val dy = yMm - anchor.y
        val eTan = dx * tangent.x + dy * tangent.y
        val eRad = dx * radial.x + dy * radial.y
        return Offset(cx + (eTan * scale).toFloat(), cy - (eRad * scale).toFloat())
    }

    // 1) 分区射线：顺时针侧 / 逆时针侧的分区边界（它们到窗中心的距离相等，因此对称）。
    //    角度自 12 点方向顺时针，分区中心在 k·18°，边界因此落在 18k−9°（见 BoardGeometry.sectorAt）。
    for (k in 0 until BoardGeometry.SECTOR_COUNT) {
        val theta = Math.toRadians(k * BoardGeometry.SECTOR_ANGLE_DEG - BoardGeometry.SECTOR_ANGLE_DEG / 2)
        val inner = BoardGeometry.OUTER_BULL_RADIUS_MM
        drawLine(
            color = color,
            start = point(sin(theta) * inner, cos(theta) * inner),
            end = point(sin(theta) * BoardGeometry.DOUBLE_OUTER_RADIUS_MM, cos(theta) * BoardGeometry.DOUBLE_OUTER_RADIUS_MM),
            strokeWidth = width
        )
    }

    // 2) 环线：六条参考圆（牛眼内外 + 三倍环内外 + 双倍环内外）。
    val radii = doubleArrayOf(
        BoardGeometry.INNER_BULL_RADIUS_MM,
        BoardGeometry.OUTER_BULL_RADIUS_MM,
        BoardGeometry.TRIPLE_INNER_RADIUS_MM,
        BoardGeometry.TRIPLE_OUTER_RADIUS_MM,
        BoardGeometry.DOUBLE_INNER_RADIUS_MM,
        BoardGeometry.DOUBLE_OUTER_RADIUS_MM
    )
    radii.forEach { radiusMm ->
        var previous: Offset? = null
        for (step in 0..BOARD_ARC_STEPS) {
            val theta = Math.toRadians(step * 360.0 / BOARD_ARC_STEPS)
            val current = point(sin(theta) * radiusMm, cos(theta) * radiusMm)
            previous?.let { drawLine(color = color, start = it, end = current, strokeWidth = width) }
            previous = current
        }
    }
}

/**
 * 出框方向示意图：窗口矩形四条边按计数上色，中心写窗内镖数。
 *
 * 方向固定在**屏幕四边**（与录制页一致），所以这张图不需要旋转就能和「点的时候手往哪偏」对上。
 */
@Composable
private fun MissDirectionDiagram(bandTotals: Map<Int, Int>, inWindow: Int) {
    val Accent = com.dartvio.app.ui.theme.Accent
    val Divider = com.dartvio.app.ui.theme.Divider
    val Error = com.dartvio.app.ui.theme.Error
    val Primary = com.dartvio.app.ui.theme.Primary
    val Secondary = com.dartvio.app.ui.theme.Secondary
    val Success = com.dartvio.app.ui.theme.Success
    val SurfaceVariantDark = com.dartvio.app.ui.theme.SurfaceVariantDark
    val TextDisabledDark = com.dartvio.app.ui.theme.TextDisabledDark
    val TextPrimaryDark = com.dartvio.app.ui.theme.TextPrimaryDark
    val TextSecondaryDark = com.dartvio.app.ui.theme.TextSecondaryDark
    val Warning = com.dartvio.app.ui.theme.Warning

    val max = (bandTotals.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
    ) {
        val thickness = 16.dp.toPx()
        val m = 6.dp.toPx()
        val paint = Paint().apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            color = TextPrimaryDark.toArgb()
            textSize = 12.sp.toPx()
        }
        val w = size.width
        val h = size.height
        val edges = listOf(
            1 to Rect2(m + thickness, m, w - m - thickness, m + thickness),
            3 to Rect2(m + thickness, h - m - thickness, w - m - thickness, h - m),
            4 to Rect2(m, m + thickness, m + thickness, h - m - thickness),
            2 to Rect2(w - m - thickness, m + thickness, w - m, h - m - thickness)
        )
        edges.forEach { (band, r) ->
            val count = bandTotals[band] ?: 0
            val ratio = count.toFloat() / max
            drawRect(
                color = if (count == 0) Divider else heatColor(ratio).copy(alpha = 0.35f + 0.6f * ratio),
                topLeft = Offset(r.left, r.top),
                size = Size(r.right - r.left, r.bottom - r.top)
            )
            if (count > 0) {
                val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    "$count",
                    (r.left + r.right) / 2f,
                    (r.top + r.bottom) / 2f + baseline,
                    paint
                )
            }
        }
        // 中央：窗内镖数 + 靶心方向提示。
        val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText("窗内 $inWindow", w / 2f, h / 2f + baseline, paint)
    }
}

/** 极简矩形（避免为一张示意图引入额外的几何类型）。 */
private data class Rect2(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** 绝对落点：标准盘 + 全部镖（含出框外推点）。 */
@Composable
private fun AbsoluteScatter(points: List<Point2>) {
    val Accent = com.dartvio.app.ui.theme.Accent
    val Divider = com.dartvio.app.ui.theme.Divider
    val Error = com.dartvio.app.ui.theme.Error
    val Primary = com.dartvio.app.ui.theme.Primary
    val Secondary = com.dartvio.app.ui.theme.Secondary
    val Success = com.dartvio.app.ui.theme.Success
    val SurfaceVariantDark = com.dartvio.app.ui.theme.SurfaceVariantDark
    val TextDisabledDark = com.dartvio.app.ui.theme.TextDisabledDark
    val TextPrimaryDark = com.dartvio.app.ui.theme.TextPrimaryDark
    val TextSecondaryDark = com.dartvio.app.ui.theme.TextSecondaryDark
    val Warning = com.dartvio.app.ui.theme.Warning

    val colors = drawingColors()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
    ) {
        drawBoardViewportIn(colors, 
            viewport = BoardViewport.Full,
            canvasSize = size,
            markers = emptyList(),
            showNumbers = true
        )
        points.forEach { p ->
            val dart = BoardGeometry.dartAt(p.x, p.y)
            val at = BoardViewport.Full.toCanvasPx(size.width, size.height, p.x, p.y)
            val center = Offset(at.x.toFloat(), at.y.toFloat())
            val color = if (dart.isMiss) Error else Success
            drawCircle(com.dartvio.app.ui.theme.BoardMarker, radius = 4.dp.toPx(), center = center)
            drawCircle(color, radius = 3.dp.toPx(), center = center)
        }
    }
}

private fun ImpactWindowLabel(spanMm: Double): String = ImpactWindow.labelOf(spanMm)

private fun mm(value: Double): String = "${oneDecimal(value)} mm"

private fun oneDecimal(value: Double): String = ((value * 10).toInt() / 10.0).toString()
