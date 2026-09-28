package com.dartvio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.OnSecondary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/** 倍率选择。 */
enum class Multiplier(val factor: Int, val label: String) {
    SINGLE(1, "S"),
    DOUBLE(2, "D"),
    TRIPLE(3, "T")
}

/** 键盘统一间距：所有按键间隙一致。 */
private val GAP = 8.dp
private val KEY_RADIUS = 12.dp

/**
 * 键盘按键：统一形状、等大、居中。禁用时轻微降透明度，保持布局视觉稳定。
 * 作为公共组件，供 X01 与 Cricket 键盘复用。
 */
@Composable
fun PadButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    accent: Color = SurfaceVariantDark,
    textColor: Color = TextPrimaryDark,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    content: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KEY_RADIUS))
            .background(accent.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            Box(
                modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.5f),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        } else {
            Text(
                text = label,
                color = textColor.copy(alpha = if (enabled) 1f else 0.45f),
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    label.contains('\n') -> 14.sp
                    label.length > 3 -> 14.sp
                    label.length > 1 -> 18.sp
                    else -> fontSize
                },
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}

/** 退格键：使用通用退格字符，避免图标依赖。 */
@Composable
fun BackspaceButton(
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KEY_RADIUS))
            .background(if (enabled) SurfaceElevated else SurfaceVariantDark)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u232B",
            color = if (enabled) TextPrimaryDark else TextDisabledDark,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 所有键盘共用的按键间隙。 */
@Composable
fun KeypadContainer(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(GAP),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(GAP),
        content = content
    )
}

// =====================================================================================
// X01 计分键盘
// =====================================================================================

/**
 * X01 飞镖计分键盘。PRD M3。
 *
 * 布局（4 列 × 5 行，所有间隙一致、按键等高）：
 *   R1: [S] [1] [2] [3]
 *   R2: [D] [4] [5] [6]
 *   R3: [T] [7] [8] [9]
 *   R4: [BULL 25] [MISS] [0] [⌫]
 *   R5: [BULL 50] [输入方式(槽位)] [ 确认（占 2～3 键宽） ]
 *
 * 左列放倍率与牛眼、右侧三列放数字：一行就是一个倍率档配三个数字，
 * 与「先选倍率，再输数字」的操作顺序同向；纵向由 6 行压到 5 行 ⇒ 每个键更高、更好点。
 */
@Composable
fun DartKeypad(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(GAP),
    enabled: Boolean = true,
    multiplier: Multiplier = Multiplier.SINGLE,
    onMultiplierChange: (Multiplier) -> Unit = {},
    buffer: String = "",
    currentRemaining: Int? = null,
    /**
     * 本回合**已确认**镖的总分。预览必须先扣它再扣当前输入：
     * 逐镖录入时用户每按一次「确认」就落定一支镖，若预览不扣已录部分，
     * 确认键右边的「新剩余」会一直等于回合起点，到结镖回合就没法用它判断
     * 剩下的分能不能一镖完成。不传（本地三镖一次记分页）恒为 0，行为不变。
     */
    turnDartsScore: Int = 0,
    turnDartsCount: Int = 0,
    onDigit: (Int) -> Unit = {},
    onBull25: () -> Unit = {},
    onBull50: () -> Unit = {},
    onMiss: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onBackspace: () -> Unit = {},
    /**
     * 输入方式切换键（槽位）。只有 X01 对局页传入（可切键盘 / 靶盘）；
     * 未传的页面（练习、房间对局）**不显示这个键**，确认键自动占满底部整行 ——
     * 不留一个点了没反应的死键。
     */
    modeSwitch: (@Composable (Modifier) -> Unit)? = null
) {
    val pendingScore = buffer.toIntOrNull()?.let { it * multiplier.factor }
    val pendingLabel = if (buffer.isNotEmpty()) "${multiplier.label}$buffer" else null
    // 第 3 镖已录入时，确认键变为"结束回合"。
    val isTurnEnd = turnDartsCount >= 3 && buffer.isEmpty()
    /*
     * 本回合**镖数已满**：不再接受「再加一支镖」的输入（倍率 / 数字 / 牛眼 / MISS）。
     *
     * 2026-09-28 真机反馈：满 3 镖后数字键仍可点，用户会继续录第 4、5 镖，
     * 而落库侧只收 3 镖 —— 表现成「输入了却没反应」，接着按确认又因为
     * 缓冲里还有未落定的数字而进不了「结束回合」分支，整页卡住。
     *
     * 与 [isTurnEnd] 分开：那个还要看有没有待确认输入（决定确认键显示什么），
     * 这个只回答「还能不能再加一支镖」，两者口径不同，不能合并。
     *
     * ⚠️ 退格键与确认键**不走这个开关**，仍然是 [enabled]：
     * 第 3 镖录错要能退回来改，满 3 镖后也必须有键能把回合交出去。
     */
    val canAddDart = enabled && turnDartsCount < 3

    KeypadContainer(modifier = modifier, contentPadding = contentPadding) {
        // ===== R1-R3：左列倍率（S/D/T），右侧三列数字 1-9 =====
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9).chunked(3).forEachIndexed { rowIndex, digits ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GAP)
            ) {
                val m = Multiplier.entries[rowIndex]
                val selected = m == multiplier
                PadButton(
                    label = m.label,
                    enabled = canAddDart,
                    accent = if (selected) Primary else SurfaceVariantDark,
                    textColor = if (selected) OnPrimary else TextPrimaryDark,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    onClick = { onMultiplierChange(m) }
                )
                digits.forEach { n ->
                    PadButton(
                        label = "$n",
                        enabled = canAddDart,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onClick = { onDigit(n) }
                    )
                }
            }
        }

        // ===== R4：BULL 25 / MISS / 0 / 退格 =====
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            PadButton(
                label = "BULL\n25",
                enabled = canAddDart,
                accent = SurfaceElevated,
                textColor = TextPrimaryDark,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onBull25
            )
            PadButton(
                label = "MISS",
                enabled = canAddDart,
                accent = SurfaceElevated,
                textColor = TextSecondaryDark,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onMiss
            )
            PadButton(
                label = "0",
                enabled = canAddDart,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = { onDigit(0) }
            )
            // 退格保留 [enabled]：满 3 镖后退回一支是纠正，不是「再加一镖」。
            BackspaceButton(
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onBackspace
            )
        }

        // ===== R5：BULL 50 / [输入方式] / 确认 =====
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            PadButton(
                label = "BULL\n50",
                enabled = canAddDart,
                accent = SurfaceElevated,
                textColor = Secondary,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onBull50
            )
            if (modeSwitch == null) {
                ConfirmKey(
                    enabled = enabled,
                    pendingLabel = pendingLabel,
                    pendingScore = pendingScore,
                    currentRemaining = currentRemaining,
                    turnDartsScore = turnDartsScore,
                    isTurnEnd = isTurnEnd,
                    modifier = Modifier.weight(3f).fillMaxSize(),
                    onConfirm = onConfirm
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    modeSwitch(Modifier.fillMaxSize())
                }
                ConfirmKey(
                    enabled = enabled,
                    pendingLabel = pendingLabel,
                    pendingScore = pendingScore,
                    currentRemaining = currentRemaining,
                    turnDartsScore = turnDartsScore,
                    isTurnEnd = isTurnEnd,
                    modifier = Modifier.weight(2f).fillMaxSize(),
                    onConfirm = onConfirm
                )
            }
        }
    }
}

/** 确认键（主题色、数字居中、右侧带分数预览）。键盘与靶盘两种输入共用。 */
@Composable
private fun ConfirmKey(
    enabled: Boolean,
    pendingLabel: String?,
    pendingScore: Int?,
    currentRemaining: Int?,
    turnDartsScore: Int,
    isTurnEnd: Boolean,
    modifier: Modifier,
    onConfirm: () -> Unit
) {
    PadButton(
        label = "",
        enabled = enabled,
        accent = Primary,
        textColor = OnPrimary,
        modifier = modifier,
        onClick = onConfirm
    ) {
        ConfirmContent(
            pendingLabel = pendingLabel,
            pendingScore = pendingScore,
            currentRemaining = currentRemaining,
            turnDartsScore = turnDartsScore,
            isTurnEnd = isTurnEnd
        )
    }
}

/**
 * 确认键内容：**待提交数字在按键中居中显示**（强调色）。
 * 右侧低对比度叠放分数预览：当前剩余（删除线）→ 本镖之后的新剩余。
 * 「当前剩余」先扣掉本回合已确认的镖（[turnDartsScore]），见 [DartKeypad.turnDartsScore]。
 */
@Composable
private fun ConfirmContent(
    pendingLabel: String?,
    pendingScore: Int?,
    currentRemaining: Int?,
    turnDartsScore: Int,
    isTurnEnd: Boolean
) {
    // 回合起点之后的真实剩余：权威帧的分数还没走（回合结束才推进），先扣已确认的镖。
    val liveRemaining = currentRemaining?.let { it - turnDartsScore }

    Box(modifier = Modifier.fillMaxSize()) {
        // 居中主内容：输入的分数 / 提示文案
        Text(
            text = pendingLabel ?: if (isTurnEnd) "结束回合" else "确认",
            color = OnPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = if (pendingLabel != null) 30.sp else 20.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        // 右侧分数预览（不参与居中，保持主数字视觉居中）
        if (pendingScore != null && liveRemaining != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$liveRemaining",
                    color = TextDisabledDark,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    text = " \u2192 ",
                    color = OnPrimary.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                Text(
                    text = "${liveRemaining - pendingScore}",
                    color = OnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

// =====================================================================================
// Cricket 计分键盘（独立布局，不使用 X01 的倍率/牛眼体系）
// =====================================================================================

/**
 * Cricket 飞镖计分键盘（独立布局，不套用 X01 的倍率/牛眼体系）。PRD M2。
 *
 * Cricket 只涉及 20-15 与 Bull，标记数由倍率决定（S+1 / D+2 / T+3），
 * 因此布局为"数字区 + S/D/T 倍率 + Bull + MISS + 确认"：
 *   R1: [S] [D] [T] [BULL25] [BULL50]      —— 倍率与牛眼
 *   R2: [20] [19] [18] [17]
 *   R3: [16] [15] [MISS] [⌫]
 *   R4: [ 确认（占满整行，与数字键等高） ]
 *
 * 交互：先选倍率 → 点数字 → 立即记录该镖（标记数 = 倍率）。
 */
@Composable
fun CricketKeypad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    multiplier: Multiplier = Multiplier.SINGLE,
    onMultiplierChange: (Multiplier) -> Unit = {},
    turnDartsCount: Int = 0,
    turnMarks: Int = 0,
    /** 本回合产生的分数（no_score 恒为 0）。 */
    turnScore: Int = 0,
    /** 分数是否记在对手账上（cut_throat）—— 决定提示是否补「→ 对手」。 */
    scoreToOpponents: Boolean = false,
    onNumber: (Int, Int) -> Unit = { _, _ -> },
    onBull25: () -> Unit = {},
    onBull50: () -> Unit = {},
    onMiss: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onBackspace: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(GAP)
) {
    val isTurnEnd = turnDartsCount >= 3
    /*
     * 与 X01 键盘同一条口径（2026-09-28 真机反馈）：本回合镖数已满后，
     * 不再接受「再加一支镖」的输入；**退格与确认仍走 [enabled]** ——
     * 第 3 镖录错要能退回来改，满 3 镖后也必须有键能把回合交出去。
     */
    val canAddDart = enabled && !isTurnEnd

    KeypadContainer(modifier = modifier, contentPadding = contentPadding) {
        // ===== R1：倍率 + 牛眼 =====
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Multiplier.entries.forEach { m ->
                val selected = m == multiplier
                PadButton(
                    label = m.label,
                    enabled = canAddDart,
                    accent = if (selected) Secondary else SurfaceVariantDark,
                    textColor = if (selected) OnSecondary else TextPrimaryDark,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    onClick = { onMultiplierChange(m) }
                )
            }
            PadButton(
                label = "BULL\n25",
                enabled = canAddDart,
                accent = SurfaceElevated,
                textColor = TextPrimaryDark,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onBull25
            )
            PadButton(
                label = "BULL\n50",
                enabled = canAddDart,
                accent = SurfaceElevated,
                textColor = Secondary,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onBull50
            )
        }

        // ===== R2：高分数字 20-17 =====
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            listOf(20, 19, 18, 17).forEach { n ->
                PadButton(
                    label = "$n",
                    enabled = canAddDart,
                    fontSize = 26.sp,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    onClick = { onNumber(n, multiplier.factor) }
                )
            }
        }

        // ===== R3：低分数字 + MISS + 退格 =====
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            listOf(16, 15).forEach { n ->
                PadButton(
                    label = "$n",
                    enabled = canAddDart,
                    fontSize = 26.sp,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    onClick = { onNumber(n, multiplier.factor) }
                )
            }
            PadButton(
                label = "MISS",
                enabled = canAddDart,
                accent = SurfaceElevated,
                textColor = TextSecondaryDark,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onMiss
            )
            // 退格保留 [enabled]：满 3 镖后退回一支是纠正，不是「再加一镖」。
            BackspaceButton(
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = onBackspace
            )
        }

        // ===== R4：确认（整行，与数字键等高） =====
        PadButton(
            label = "",
            enabled = enabled,
            accent = Secondary,
            textColor = OnSecondary,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onClick = onConfirm
        ) {
            CricketConfirmContent(
                turnDartsCount = turnDartsCount,
                turnMarks = turnMarks,
                turnScore = turnScore,
                scoreToOpponents = scoreToOpponents,
                isTurnEnd = isTurnEnd
            )
        }
    }
}

/**
 * Cricket 确认键内容：
 *  - 未录入时显示"确认"
 *  - 录入中显示"本回合 +N 标"与已录镖数
 *  - 满 3 镖后显示"结束回合"
 *
 * 副行在有得分时追加「得分 +N」，cut_throat 再补「→ 对手」（M7 P7.3）：
 * 生死局的教练误解点就是「我以为我得分了」，这里必须写明分记给了谁。
 */
@Composable
private fun CricketConfirmContent(
    turnDartsCount: Int,
    turnMarks: Int,
    turnScore: Int,
    scoreToOpponents: Boolean,
    isTurnEnd: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isTurnEnd) "结束回合" else if (turnDartsCount > 0) "本回合 +$turnMarks 标" else "确认",
            color = OnSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = if (isTurnEnd) 22.sp else 18.sp,
            lineHeight = 20.sp
        )
        if (!isTurnEnd && turnDartsCount > 0) {
            Text(
                text = "已录 $turnDartsCount/3 镖" + scoreHintSuffix(turnScore, scoreToOpponents),
                color = OnSecondary.copy(alpha = 0.75f),
                fontSize = 11.sp,
                lineHeight = 12.sp,
                maxLines = 1
            )
        }
    }
}

/** 得分提示后缀：无得分时为空串，生死局补「→ 对手」。 */
private fun scoreHintSuffix(turnScore: Int, scoreToOpponents: Boolean): String {
    if (turnScore <= 0) return ""
    return if (scoreToOpponents) " · 得分 +$turnScore → 对手" else " · 得分 +$turnScore"
}
