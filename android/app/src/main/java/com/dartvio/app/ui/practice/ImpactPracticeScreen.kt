package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.domain.impact.ImpactFrames
import com.dartvio.app.domain.impact.ImpactMissBand
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.ui.components.BoardTap
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning
import kotlin.math.abs

/**
 * 精准工坊 · 逐镖录制页（设计 §5.3）。
 *
 * 这一页**故意没有**报分、没有键盘行、没有二次确认：一次点击 = 一镖。
 * 理由很实在 —— 局部放大窗只为了看清「偏了哪一点」，任何报分都会把注意力从落点拉回分数，
 * 而分数在诊断训练里没有信息量。误点由「撤销上一镖」兜底（一次点击的成本必须远低于一次迟疑）。
 */
@Composable
fun ImpactPracticeScreen(
    onExit: () -> Unit,
    onFinish: () -> Unit,
    viewModel: ImpactPracticeViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewport = remember(state.target, state.spanMm) {
        ImpactWindow.viewportOf(state.target, state.spanMm)
    }
    val markers = remember(state.recorded) {
        state.recorded.map { BoardTap(it.xMm, it.yMm, Dart(it.number, it.multiplier)) }
    }
    var showRules by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            // 自绘头部必须自己吃状态栏内边距（`enableEdgeToEdge`），否则「退出 / 第几镖」会压到状态栏。
            .statusBarsPadding()
    ) {
        /*
         * 头部只剩三件事：退出、目标、规则。
         *
         * 「第几镖 / 命中 / 上一镖 / 本轮汇总」全部下沉到靶面下方**同一张实时卡**
         * （见 [SessionCard]）：它们每一镖都在变，散在头部与中部两处时，
         * 眼睛要在靶面上下各停一次才能读完一轮（2026-09-26 反馈）。
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onExit) {
                Text("退出", color = Primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "目标 ${state.target.label}",
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            // 规则入口钉在**最右**：它是这一页唯一一个「不在打镖」的出口，
            // 放角落才不会在低头点靶时误触。条带含义、出框怎么算全在它后面 ——
            // 常驻在页面上会盖住靶面附近的视野。
            IconButton(onClick = { showRules = true }) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = "规则与说明",
                    tint = TextSecondaryDark
                )
            }
        }

        // 「点击 = 记录这一点」那一行**已删除**：一次点击 = 一镖是这个页面的
        // 全部交互，说一遍就够了，而它正好压在靶面上沿 —— 最该看清的地方
        // （2026-09-26 反馈）。现已并入右上角的规则页。

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 图框**固定为 宽 : 高 = 1 : 1.4**，且始终紧贴头部下方：
                // ① 竖向比 1:1 多出 40% —— 局部放大窗里，沿半径方向的偏差（偏内 / 偏外）
                //    才是要读的量，多给竖向后这一维终于够看；
                // ② 比例写死在容器上而不是随内容伸缩：下方文字再长也只进滚动区，
                //    不回头挤压靶面 —— 一张会变形的示意框，比一张放不下的卡更伤「精准」二字。
                .aspectRatio(1f / 1.4f)
                .padding(12.dp)
        ) {
            ImpactBoardPreview(
                target = state.target,
                spanMm = state.spanMm,
                markers = markers,
                lastTap = markers.lastOrNull(),
                modifier = Modifier.fillMaxSize(),
                onPoint = { xMm, yMm, outBand, outLevel ->
                    viewModel.record(xMm, yMm, outBand, outLevel)
                }
            )
        }

        // 实时卡独占这一区：长度不封顶时可滚动，但**不与别的说明混排** ——
        // 一屏里只有「一块在读的东西」，剩下的是靶（2026-09-26 反馈）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            SessionCard(state)
        }

        // 两个操作键：宽度各半、**高度与字号写死**。
        // 早先两者都只给 weight(1f) 而不给高度，「结束本轮并看报告」八个字在半屏宽里
        // 折成两行，于是两个键一高一矮 —— 看起来像两个不同层级的操作（2026-09-26 反馈）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .height(ACTION_BUTTON_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.undo() },
                enabled = state.dartCount > 0,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Text("撤销上一镖", fontSize = 14.sp, maxLines = 1)
            }
            Button(
                onClick = onFinish,
                enabled = state.dartCount > 0,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                // 八个字在半屏宽里必换行，改成「结束 & 显示报告」—— 短到一行放得下，
                // 且与左边的「撤销上一镖」同为五六个字，两个键看起来才是同一级操作。
                Text(
                    if (state.isFull) "显示报告" else "结束 & 显示报告",
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }

    if (showRules) {
        ImpactRulesDialog(
            target = state.target,
            viewport = viewport,
            onDismiss = { showRules = false }
        )
    }
}

/** 底部两个操作键的固定高度（见调用处注释）。 */
private val ACTION_BUTTON_HEIGHT = 52.dp

/**
 * 实时卡：**这一页所有会随每一镖变化的数字都在这一张卡里**。
 *
 * 早先它们是三块散落的东西（头部两个计数 + 中部两个各自带背景的文字行），
 * 于是一轮打完要在靶面上下各停一次才读得全。合成一张卡之后：
 * 眼动只在「靶 → 卡」之间来回一次，而卡的位置固定，不用找（2026-09-26 反馈）。
 */
@Composable
private fun SessionCard(state: ImpactPracticeUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 第一行：进度与结果。这两个是「还要打多久 / 打得怎么样」，必须同排。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${state.dartCount + 1} / ${state.sessionSize} 镖",
                color = TextPrimaryDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "命中 ${state.hitCount}",
                color = Secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (state.last != null || state.recorded.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
        }
        if (state.last != null) {
            Text(lastDartText(state), color = TextPrimaryDark, fontSize = 13.sp)
            Text(
                "还差 ${(state.sessionSize - state.dartCount).coerceAtLeast(0)} 镖完成这一轮",
                color = TextSecondaryDark,
                fontSize = 11.sp
            )
        }
        if (state.recorded.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                tallyText(state),
                color = TextSecondaryDark,
                fontSize = 12.sp
            )
        }
        if (state.suggestWide) {
            Spacer(Modifier.height(6.dp))
            Text(
                "出框偏多 —— 这一轮结束后建议切到「宽松」档，先把落点看全再收窗口。",
                color = Warning,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 规则与说明（右上角入口）。
 *
 * 这一页的**说明性文字全部收在这里**：条带含义、出框怎么算、目标是什么。
 * 它们只在第一次用时被读一遍，之后每一镖都会重新读的是落点 ——
 * 常驻在页面上等于让一次性信息长期占据最靠近靶面的位置（2026-09-26 反馈）。
 */
@Composable
private fun ImpactRulesDialog(
    target: IntentTarget,
    viewport: BoardViewport,
    onDismiss: () -> Unit
) {
    // 条带含义：四条边「窗外是什么」全部由几何推出（换靶面常量，文案自动跟着变）。
    val outward = ImpactMissBand.outsideLabel(target, viewport, 1)
    val inward = ImpactMissBand.outsideLabel(target, viewport, 3)
    val tangentPlus = ImpactMissBand.outsideLabel(target, viewport, 2)
    val tangentMinus = ImpactMissBand.outsideLabel(target, viewport, 4)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("这一页怎么玩", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                RulesPara("目标：${target.label}。窗里那一块被点亮的区域就是它，打中就记一次命中。")
                RulesPara("一次点击 = 一镖。点歪了用「撤销上一镖」，不弹确认。")
                RulesPara(
                    "窗外的四条边记出框：" +
                        "上 = $outward，下 = $inward，左 = $tangentMinus，右 = $tangentPlus。" +
                        "每条边的内半是轻微出框、外半是远出框。"
                )
                RulesPara("这一页不给分数：诊断训练看的是落点偏了多少，不是这一镖值几分。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun RulesPara(text: String) {
    Text(
        text,
        color = TextSecondaryDark,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** 「上一镖」一行：窗内镖给偏差分解，出框镖给出框语义（都不给分数）。 */
private fun lastDartText(state: ImpactPracticeUiState): String {
    val last = state.last ?: return ""
    return if (last.outBand == 0) {
        val frame = ImpactFrames.of(state.target, last.xMm.toDouble(), last.yMm.toDouble())
        val dart = Dart(last.number, last.multiplier)
        val (clockwise, counterClockwise) = state.target.neighbors()
        val side = if (frame.eTan >= 0.0) clockwise else counterClockwise
        "上一镖：${dart.label()} · 偏差 ${if (frame.eRad >= 0) "偏外" else "偏内"} " +
            "${mm(abs(frame.eRad))} / 偏 $side 侧 ${mm(abs(frame.eTan))}"
    } else {
        val semantic = ImpactMissBand.tangentLabel(state.target, last.outBand)
            .ifBlank { ImpactFrames.semanticOf(state.target, last.outBand).label }
        "上一镖：$semantic（${if (last.outLevel == 2) "远出框 / 靶外" else "轻微出框"}）"
    }
}

/**
 * 即时层汇总（设计 §5.3-11，V1.4）—— **常驻、非打断**，每镖刷新，不弹窗、不带动画卡片。
 *
 * 三条规格：
 * 1. 数字部分只在 `n ≥ [MIN_TALLY_N]` 后出现（低于则只显示「已录 n 镖」），避免用 2 镖的平均值吓人；
 * 2. 「偏外均值」必须**带方向语义**，方向由 `mean(eRad)` 的符号 + 几何决定（不得写「偏高 / 偏低」）；
 * 3. 设了本轮目标时，这一行**只显示目标值**，**不得**显示「已达 / 未达」—— 达成判定只在轮末给，
 *    否则用户会在回合中途自我暗示（§5.3-11）。
 */
private fun tallyText(state: ImpactPracticeUiState): String {
    val recorded = state.recorded
    val frames = recorded.filter { it.outBand == 0 }.map {
        ImpactFrames.of(state.target, it.xMm.toDouble(), it.yMm.toDouble())
    }
    val outCount = recorded.count { it.outBand != 0 }
    val n = recorded.size

    val body = if (n < MIN_TALLY_N) {
        "已录 $n 镖"
    } else {
        val meanRad = frames.sumOf { it.eRad } / frames.size
        val direction = if (meanRad >= 0) "偏外" else "偏内"
        "本轮 $n 镖 · ${direction}均值 ${mm(abs(meanRad))} mm · 出框 $outCount"
    }
    val targetText = state.prescription?.let {
        " · 目标 ${it.metric.label} ${if (it.metric.lowerIsBetter) "≤" else "≥"} ${it.targetText()}"
    }.orEmpty()
    return body + targetText
}

/** 即时层出数字的最小镖数（§5.3-11）。 */
private const val MIN_TALLY_N = 4

private fun mm(value: Double): String = ((value * 10).toInt() / 10.0).toString()
