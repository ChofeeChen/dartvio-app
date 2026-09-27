package com.dartvio.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dartvio.app.domain.model.CricketHitResult
import com.dartvio.app.domain.model.CricketLegState
import com.dartvio.app.domain.model.CricketMarks
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartClaim
import com.dartvio.app.domain.model.ScoreSink
import com.dartvio.app.domain.rules.CricketRules
import com.dartvio.app.ui.components.CricketKeypad
import com.dartvio.app.ui.components.Multiplier
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * Cricket 对局界面。PRD M2。
 *
 * 与 X01 完全独立的 UI 与键盘：
 *  - 玩家卡片展示**标记网格**（20-15 + Bull 的标记数）与**累计分数**，而非剩余分。
 *  - 键盘使用 [CricketKeypad]（数字 + S/D/T + Bull + MISS），不使用 X01 的 BULL 25/50 体系。
 *  - 回合结算引用 [com.dartvio.app.domain.rules.CricketRules]。
 *
 * 回合结束规则：满 3 镖后需点击「确认」结束回合。
 */
@Composable
fun CricketGameScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.newlyUnlocked.collectAsStateWithLifecycle()
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var inputLocked by remember { mutableStateOf(false) }

    val inputEnabled = !state.isMatchFinished &&
        state.currentPlayer?.isAi != true &&
        !inputLocked

    LaunchedEffect(state.currentPlayerIndex, state.isMatchFinished) {
        multiplier = Multiplier.SINGLE
    }

    LaunchedEffect(inputLocked) {
        if (inputLocked) {
            kotlinx.coroutines.delay(500)
            inputLocked = false
        }
    }

    fun recordDart(dart: Dart, claim: DartClaim = DartClaim.NUMBER) {
        viewModel.throwDart(dart, claim = claim)
        inputLocked = true
    }

    // ===== 胜利页：本局胜利 / 决胜胜利 =====
    if (state.isMatchFinished || state.showLegSummary) {
        CricketVictoryScreen(
            state = state,
            onPlayAgain = onPlayAgain,
            onExit = onExit,
            onContinueNextLeg = { viewModel.continueToNextLeg() },
            unlockedAchievements = unlockedAchievements
        )
        return
    }

    val leg = state.cricketLeg
    val targets = state.config.cricketTargets
    val variant = state.config.cricketVariant

    // 目标集含类别档 ⇒ 本局是 Tactics 玩法（Q7：玩法由目标集承载，不是变体名）。
    // 归属二选一控件只在这种情况下出现，其余玩法（标准 / 不计分 / 生死局 / 随机）零视觉变化。
    val isTactics = targets.any { it is CricketTarget.Category }

    // 轮数上限进度（§4.9.8）：只在有上限时显示，无上限局不占位。
    // 字段与 X01「最多轮数」已合并为同一个 `maxRounds`（2026-09-12）。
    val roundLimit = state.config.maxRounds
    val roundText = if (roundLimit > 0) {
        " · 第 ${(leg?.currentPlayer?.turnsPlayed ?: 0) + 1}/$roundLimit 轮"
    } else {
        ""
    }

    // P7.3 按变体渲染：
    // - 不计分：分数没有意义，卡片用「已关闭 X/7」替代得分区（标记网格照常保留）；
    // - 生死局：标注当前低分领先者，并让键盘把得分写成「→ 对手」。
    val showScore = variant.scoreSink != ScoreSink.NONE
    val lowScoreLeadIds = if (variant == CricketVariant.CUT_THROAT) lowestScoreLeaderIds(leg) else emptySet()
    val turnScore = state.cricketTurnEvents.sumOf { it.hit?.scoreGained ?: 0 }

    // 已被「所有玩家」都打到 3 标（Closed）的目标位：整行置灰 + 数字加删除线
    val closedTargets: Set<CricketTarget> = leg?.players
        ?.takeIf { it.isNotEmpty() }
        ?.let { ps -> targets.filter { t -> ps.all { it.marks.isClosed(t) } }.toSet() }
        .orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(GameBlockGap),
        verticalArrangement = Arrangement.spacedBy(GameBlockGap)
    ) {
        GameTopBar(
            title = state.config.displayName,
            subtitle = "第 ${leg?.legNumber ?: 1} 局" +
                (state.legsWonText()?.let { " · $it" } ?: "") + roundText,
            onExit = onExit
        )

        // 玩家标记网格：左右排列、等宽，占据顶栏与键盘之间的全部空间。
        // 两张卡片的宽高完全一致，因此在这里统一算出字号 / 行高 / 符号尺寸再下发，
        // 避免各卡按自身分数的位数分别计算，导致两张卡的对应区域文字大小、行高不一致。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
        ) {
            val cardCount = state.players.size.coerceAtLeast(1)
            // 「本人 | 对手」之间的短竖条也占横向宽度。漏掉它会让卡片可用宽度被高估，
            // 4 人局里这点误差足以把分数字号推大一档、让数字溢出卡片。
            val seatDividerWidth = if (cardCount > 1) CricketSeatDividerWidth.value else 0f
            val cardInnerWidth = (maxWidth.value - GameBlockGap.value * (cardCount - 1) -
                seatDividerWidth) / cardCount - CricketCardPadding.value * 2f
            val cardInnerHeight = maxHeight.value - CricketTriangleHeight.value -
                CricketCardPadding.value * 2f
            val scoreDigits = state.players.indices
                .maxOfOrNull { (leg?.players?.getOrNull(it)?.score ?: 0).toString().length }
                ?.coerceAtLeast(1)
                ?: 1

            val scoreSize = cricketScoreSize(
                cardHeightDp = cardInnerHeight,
                cardWidthDp = cardInnerWidth,
                scoreDigits = scoreDigits,
                rowCount = targets.size
            )
            val labelSize = (scoreSize * 0.2f).coerceIn(9f, 13f)
            val rowHeight = cricketGridRowHeight(cardInnerHeight, scoreSize, targets.size)
            val markSizeDp = (rowHeight * 0.55f).coerceIn(20f, 30f)
            // 分区文字（BULL / 15-20）字号在基准上再放大 20%
            val numberSizeSp = (markSizeDp * 0.82f * 1.2f).coerceIn(19.8f, 28.8f)
            // 分区文字与得分标记水平方向各向中心靠拢，使两者间距缩短 30%
            val markGapShiftDp = cardInnerWidth * 0.075f

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(GameBlockGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.players.forEachIndexed { index, player ->
                    // 短竖条把「本人」（第 1 席，名字与头像来自登录账号）与对手分成两组。
                    // 这里用 `fillMaxHeight(0.5f)` 而不是固定高度：本行是撑满剩余空间的，
                    // 卡片高度由约束决定，固定值会在不同屏幕上或过长或过短。
                    if (index == 1) SeatDivider(Modifier.fillMaxHeight(0.5f))
                    val ps = leg?.players?.getOrNull(index)
                    CricketPlayerCard(
                        name = player.name,
                        avatarEmoji = playerAvatarEmoji(player),
                        score = ps?.score ?: 0,
                        showScore = showScore,
                        lowScoreLead = player.id in lowScoreLeadIds,
                        marks = ps?.marks ?: CricketMarks(),
                        targets = targets,
                        closedTargets = closedTargets,
                        isActive = index == state.currentPlayerIndex,
                        legsWon = state.legsWonOf(player.id),
                        scoreSize = scoreSize,
                        labelSize = labelSize,
                        markSizeDp = markSizeDp,
                        numberSizeSp = numberSizeSp,
                        markGapShiftDp = markGapShiftDp,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        // Tactics 归属二选一（M2 §4.9.2③④⑤）。其余玩法的目标集没有类别档，
        // `isTactics` 为 false ⇒ 这一整块不出现，键盘位置上移，视觉与一期完全一致。
        if (isTactics && inputEnabled && leg != null) {
            CricketClaimBar(
                leg = leg,
                events = state.cricketTurnEvents,
                targets = targets,
                onRedeclare = { index, claim -> viewModel.redeclareCricketDart(index, claim) }
            )
        }

        CricketKeypad(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // 键盘左右不再额外留边，与外层 padding(GameBlockGap) 合并后正好与玩家卡片左右边界对齐；
            // 上下留边交给外层统一间距，保证各处间隙均匀
            contentPadding = PaddingValues(0.dp),
            enabled = inputEnabled,
            multiplier = multiplier,
            onMultiplierChange = { multiplier = it },
            // Cricket 的回合镖数是「X01 之外」的那一半，所以走 turnDartsCount —— 
            // currentTurnDarts 只剩 X01，用它会让键盘永远显示 0 镖。
            turnDartsCount = state.turnDartsCount,
            // 已得标记取**实际结算**值（裁决会改变它），而不是按倍率估算。
            turnMarks = state.cricketTurnEvents.sumOf { it.hit?.marksGained ?: 0 },
            turnScore = turnScore,
            scoreToOpponents = variant.scoreSink == ScoreSink.OPPONENTS,
            onNumber = { number, factor -> recordDart(Dart(number, factor)) },
            onBull25 = { recordDart(Dart.OUTER_BULL) },
            onBull50 = { recordDart(Dart.INNER_BULL) },
            onMiss = { recordDart(Dart.MISS) },
            onConfirm = {
                viewModel.commitTurn()
                inputLocked = true
            },
            onBackspace = { viewModel.undoLastDart() }
        )
    }
}

/**
 * 生死局「低分领先」玩家集合（M7 P7.3）。
 *
 * 全员同分时返回空集：开局人手 0 分，此刻标在谁头上都是噪声。
 * 所以这个标注只在「已经有人因为吃分而落后」之后才有意义。
 */
private fun lowestScoreLeaderIds(leg: CricketLegState?): Set<String> {
    val players = leg?.players ?: return emptySet()
    if (players.size < 2) return emptySet()
    val scores = players.map { it.score }
    if (scores.distinct().size <= 1) return emptySet()
    val lowest = scores.min()
    return players.filter { it.score == lowest }.map { it.playerId }.toSet()
}

/**
 * Cricket 玩家卡片：顶部两行（第 1 行「头像 + 姓名」、第 2 行「分数 · 分」），
 * 下方是 20-15 + Bull 的标记网格。
 * 标记采用国际通用记法：1 标="/"，2 标="X"（/ 与 \ 交叉），3 标=带圈的 X（⊗）。
 * 若某分区双方都已打到 3 标（Closed），该行整体置灰、数字加删除线。
 *
 * 变体（M7 P7.3）：
 *  - [showScore] 为 false（不计分）时，得分区换成「已关闭 X/7」——分数恒为 0，显示它只会误导；
 *  - [lowScoreLead] 为 true（生死局低分领先者）时，底部标注「低分领先」。
 */
@Composable
private fun CricketPlayerCard(
    name: String,
    avatarEmoji: String,
    score: Int,
    showScore: Boolean,
    lowScoreLead: Boolean,
    marks: CricketMarks,
    targets: List<CricketTarget>,
    closedTargets: Set<CricketTarget>,
    isActive: Boolean,
    legsWon: Int,
    scoreSize: Float,
    labelSize: Float,
    markSizeDp: Float,
    numberSizeSp: Float,
    markGapShiftDp: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ActiveTriangle(visible = isActive, color = Secondary)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isActive) SurfaceVariantDark else SurfaceDark)
                .border(
                    if (isActive) 2.dp else 1.dp,
                    if (isActive) Secondary else Divider,
                    RoundedCornerShape(14.dp)
                )
                .padding(CricketCardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 头部第 1 行：头像 + 姓名（独占整行）。
            //
            // 2026-09-12 布局修复：这两行原本挤在同一个横排里（头像 · 姓名 · 分数 · 分），
            // 3~4 人局卡内只剩 60dp 上下，四项相加必然超宽，右侧的「分」被裁到卡片外 ——
            // 截图里的头部溢出就是这个原因。拆成两行后，姓名拿到整行宽度（窄卡片靠 ellipsis
            // 收敛，不会硬溢出），主得分也拿回整行宽度，两件事同时解决。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = avatarEmoji, fontSize = 11.sp)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = name,
                    color = if (isActive) Secondary else TextSecondaryDark,
                    fontSize = CricketPlayerNameSizeSp.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // weight(fill = false)：宽度够就保持自然宽度（保持整组居中），
                    // 真的放不下时才被压缩并省略 —— 这是「不溢出」的唯一保证。
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            // 头部第 2 行：主得分 · 分（整行归它）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                if (showScore) {
                    Text(
                        text = "$score",
                        color = TextPrimaryDark,
                        fontSize = scoreSize.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "分",
                        color = TextSecondaryDark,
                        fontSize = labelSize.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    // 不计分：分数恒为 0，改报「已关闭 X/7」——这才是该变体唯一有意义的进度。
                    val closedSections = targets.count { marks.isClosed(it) }
                    Text(
                        text = "已关闭 $closedSections/${targets.size}",
                        color = TextPrimaryDark,
                        fontSize = (labelSize * 1.5f).coerceIn(13f, 20f).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // 标记网格：按目标集行数等分剩余高度（标准 / 随机 7 行，Tactics 9 档）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                targets.forEach { target ->
                    CricketMarkRow(
                        target = target,
                        count = marks.marksOf(target),
                        closed = target in closedTargets,
                        markSizeDp = markSizeDp,
                        numberSizeSp = numberSizeSp,
                        markGapShiftDp = markGapShiftDp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                // 低分领先标注放在这里而不是头部横排：底部这行是定高单行，
                // 换行风险为零，不会挤压上方按卡片高度精确算出的标记网格。
                text = "胜 $legsWon 局" + if (lowScoreLead) " · 低分领先" else "",
                color = if (lowScoreLead) Secondary else TextSecondaryDark,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/** 玩家卡片内边距（需与卡片 modifier 中的 padding 保持一致）。 */
private val CricketCardPadding = 8.dp

/** 卡片顶部「当前回合」指示三角的高度（需与 ActiveTriangle 保持一致）。 */
private val CricketTriangleHeight = 7.dp

/** 玩家姓名字号（在 12sp 基础上放大 30%）。 */
private const val CricketPlayerNameSizeSp = 15.6f

/** 「分」字号的保守上界（sp），与 [CricketScoreLabelWidthDp] 一起用于给主得分让出宽度。 */
private const val CricketLabelMaxSizeSp = 13f

/** 数字宽度按 em 估算：Roboto Bold 实际约 0.56 em，取 0.62 作安全余量。 */
private const val CricketDigitWidthEm = 0.62f

/** 主得分右侧「分」字占用的横向宽度（dp）：字号上界 + 3dp 固定间距。 */
private const val CricketScoreLabelWidthDp = CricketLabelMaxSizeSp + 3f

/** 姓名行高度（dp）：15.6sp 的行盒 + 余量。 */
private const val CricketNameLineHeightDp = 19f

/** 底部「胜 N 局」行高度（dp）：10sp 的行盒 + 余量。 */
private const val CricketBottomLineHeightDp = 13f

/** 卡片内 4 个纵向子项之间的 3 处 4dp 间距合计（dp）。 */
private const val CricketColumnSpacingDp = 12f

/** 标记网格的单行最小高度（dp）：低于这个值，1 标 / 2 标 / 3 标的区别就看不出来了。 */
private const val CricketMinMarkRowDp = 18f

/** 行盒高度与字号之比（Compose 默认行盒）。 */
private const val CricketScoreLineRatio = 1.2f

/** 主得分最多占卡片内高的比例：数字再醒目，也不该把标记网格挤到失真。 */
private const val CricketScoreHeightRatio = 0.22f

/** 「本人 | 对手」短竖条的宽度（dp），需与 `SeatDivider` 的实现保持一致。 */
private val CricketSeatDividerWidth = 2.dp

/**
 * Cricket 卡片主得分自适应字号。
 *
 * 主得分现在**独占一行**，横向只受「数字 + 分」约束，不再与姓名抢宽度；
 * 纵向扣掉姓名行、「胜 N 局」行与固定间距后，还要保证标记网格每行不少于
 * [CricketMinMarkRowDp]，最后再套一层 [CricketScoreHeightRatio] 的上限。
 *
 * [scoreDigits] 取所有玩家中**最长**的分数位数，保证同一行卡片的字号完全一致 ——
 * 否则 100 分和 95 分两张卡会得到两个字号，看起来像两种规格。
 *
 * ⚠️ 三条约束缺一不可：只按高度算，4 人局的窄卡片会把数字顶出卡片；只按宽度算，
 * Tactics 的 9 行网格会被压到看不清；不设上限，2 人局会出现一个占满半张卡的巨号数字。
 */
private fun cricketScoreSize(
    cardHeightDp: Float,
    cardWidthDp: Float,
    scoreDigits: Int,
    rowCount: Int
): Float {
    val digits = scoreDigits.coerceAtLeast(1)
    val rows = rowCount.coerceAtLeast(1)

    val byWidth = (cardWidthDp - CricketScoreLabelWidthDp) / (digits * CricketDigitWidthEm)

    val gridReserve = CricketMinMarkRowDp * rows + 3f * (rows - 1)
    val byGrid = (cardHeightDp - CricketNameLineHeightDp - CricketBottomLineHeightDp -
        CricketColumnSpacingDp - gridReserve) / CricketScoreLineRatio

    val byCard = cardHeightDp * CricketScoreHeightRatio / CricketScoreLineRatio

    return minOf(byWidth, byGrid, byCard).coerceAtLeast(20f)
}

/**
 * 由卡片内高反推标记网格的单行行高（dp）：
 * 扣掉姓名行、主得分行、「胜 N 局」行与三处 4dp 间距后，均分 [rowCount] 行。
 *
 * ⚠️ 行数必须由**目标集大小**传入，不能写死 7：Tactics 是 9 档，写死会让网格溢出卡片
 * （最后两行被裁掉，而「三倍档 / Bull」恰好就在最后）。随机局是 5 档，写死则会留下大片空白。
 */
private fun cricketGridRowHeight(cardHeightDp: Float, scoreSize: Float, rowCount: Int): Float {
    val rows = rowCount.coerceAtLeast(1)
    val headerHeight = CricketNameLineHeightDp + scoreSize * CricketScoreLineRatio
    val gridHeight = cardHeightDp - headerHeight - CricketBottomLineHeightDp -
        CricketColumnSpacingDp - 3f * (rows - 1)
    return (gridHeight / rows).coerceAtLeast(CricketMinMarkRowDp)
}

/**
 * 标记行的板面标签。
 *
 * 数字分区照号位显示；Bull 沿用一期写法「BULL」而**不是** `target.display`（"25"）。
 * `display` 是数据层的默认展示名，板面标签是界面口径，两者本来就不是一回事 ——
 * 把 Bull 从「BULL」改成「25」是一次没有依据的界面变更，2A 的验收标准是零行为变更。
 * 类别档（2C tactics）走 `display`，其板面写法到那时再定。
 */
private fun boardLabel(target: CricketTarget): String =
    if (target is CricketTarget.Number && target.value == 25) "BULL" else target.display

// ===== Cricket 标记符号参数 =====

/**
 * 单个分区的标记行：左侧数字、右侧标记符号，各占半行宽度并居中。
 * 字号与符号尺寸由卡片高度自适应推算：19.8sp / 20dp 为下限。
 *
 * 数字侧与标记侧分别向中心位移 [markGapShiftDp]，使两者间距比原始布局缩短 30%。
 *
 * @param target 该行对应的目标位（二期 2A：不再假设它是数字）。
 * @param closed 该目标位已被双方关闭：标签置灰 + 删除线，标记符号一并置灰。
 */
@Composable
private fun CricketMarkRow(
    target: CricketTarget,
    count: Int,
    closed: Boolean,
    markSizeDp: Float,
    numberSizeSp: Float,
    markGapShiftDp: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = boardLabel(target),
            color = if (closed) TextDisabledDark else TextPrimaryDark,
            fontSize = numberSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            textDecoration = if (closed) TextDecoration.LineThrough else null,
            // 使用 offset 而非收窄宽度：字号放大 20% 后仍不会被裁切，
            // 同时向内位移，使分区文字与右侧标记符号的间距缩短 30%
            modifier = Modifier
                .weight(1f)
                .offset(x = markGapShiftDp.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .offset(x = -markGapShiftDp.dp),
            contentAlignment = Alignment.Center
        ) {
            CricketMarkSymbol(count = count, sizeDp = markSizeDp, muted = closed)
        }
    }
}

/**
 * Cricket 国际通用标记符号（Canvas 绘制，1 镖 1 笔）：
 *  - 0 标：留空
 *  - 1 标："/"
 *  - 2 标："/" + "\" 交叉成 "X"
 *  - 3 标（Closed）：X 外再套一个圆圈，即 "⊗"
 *
 * 未闭合用主色、闭合用辅助色；分区被双方关闭（[muted]）时整体置灰。
 */
@Composable
private fun CricketMarkSymbol(
    count: Int,
    sizeDp: Float,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    val Divider = com.dartvio.app.ui.theme.Divider
    val Primary = com.dartvio.app.ui.theme.Primary
    val Secondary = com.dartvio.app.ui.theme.Secondary
    val SurfaceVariantDark = com.dartvio.app.ui.theme.SurfaceVariantDark
    val TextDisabledDark = com.dartvio.app.ui.theme.TextDisabledDark
    val TextPrimaryDark = com.dartvio.app.ui.theme.TextPrimaryDark
    val TextSecondaryDark = com.dartvio.app.ui.theme.TextSecondaryDark

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        if (count <= 0) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val side = minOf(size.width, size.height)
        val arm = side * 0.28f                       // 斜线半臂长
        val stroke = (side * 0.11f).coerceAtLeast(1.5f)
        val ink = when {
            muted -> TextDisabledDark
            count >= 3 -> Secondary
            else -> Primary
        }

        // 第 1 笔："/"（左下 → 右上）
        drawLine(
            color = ink,
            start = Offset(center.x - arm, center.y + arm),
            end = Offset(center.x + arm, center.y - arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // 第 2 笔："\"（左上 → 右下），与 "/" 交叉成 "X"
        if (count >= 2) {
            drawLine(
                color = ink,
                start = Offset(center.x - arm, center.y - arm),
                end = Offset(center.x + arm, center.y + arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        // 第 3 笔：闭合圆圈，得到 "⊗"
        if (count >= 3) {
            drawCircle(
                color = ink,
                radius = side * 0.47f,
                center = center,
                style = Stroke(width = stroke)
            )
        }
    }
}

/**
 * Tactics 归属裁决条（M2 §4.9.2③④，二期 2C）。
 *
 * 只在本局目标集含类别档时出现 —— 标准 / 不计分 / 生死局 / 随机局没有类别档，
 * 这一整块不存在，画面与一期逐像素相同（红线 §10.6）。
 *
 * 结构：
 * - 本回合**已固定**的镖：只读展示「T19·记19」—— 改判窗口到「投下一镖」为止，
 *   所以前面的镖不再可点（冻结口径，不是遗漏）。
 * - 本回合**最后一镖**：两个选项各带**自己的即时结算**。这是硬要求不是锦上添花：
 *   死区豁免会让「看起来更划算」的那个选项反而拿 0 分（记「三倍档」= 0 分，
 *   记「19」= 57 分），只放两个光秃秃的按钮就是把玩家往坑里带。
 */
@Composable
private fun CricketClaimBar(
    leg: CricketLegState,
    events: List<CricketTurnEvent>,
    targets: List<CricketTarget>,
    onRedeclare: (Int, DartClaim) -> Unit,
) {
    val lastIndex = leg.currentTurnDarts.lastIndex
    if (lastIndex < 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = GameBlockGap),
        horizontalArrangement = Arrangement.spacedBy(GameBlockGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        events.dropLast(1).forEach { event ->
            Text(
                text = "${dartLabel(event.dart)}·记${claimTargetLabel(event.dart, event.claim, targets)}",
                color = TextSecondaryDark,
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }

        val claimed = leg.currentTurnDarts[lastIndex]
        val actualHit = events.getOrNull(lastIndex)?.hit
        CLAIM_CANDIDATES.forEach { claim ->
            val selected = claim == claimed.claim
            // 已选中的一侧直接用它自己的结算结果；另一侧用「重放到这一镖」的推演值。
            val hit = if (selected) actualHit else previewHit(leg, lastIndex, claim)
            ClaimChip(
                label = "记「${claimTargetLabel(claimed.dart, claim, targets)}」",
                detail = claimDetail(claimed.dart, hit),
                selected = selected,
                // 已选中的一侧点了也是原样，禁用掉避免无意义的重复结算。
                enabled = !selected,
                onClick = { onRedeclare(lastIndex, claim) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 归属的两个候选；数字在前 —— 它就是默认选中的那一侧（§8.7）。 */
private val CLAIM_CANDIDATES = listOf(DartClaim.NUMBER, DartClaim.CATEGORY)

/**
 * 推演「把第 [index] 支镖改判成 [claim]」的结算结果。
 *
 * 走规则层的 [CricketRules.redeclare]（重放本回合序列）而不是在这里另算一遍：
 * 死区豁免 / Overkill 的条件都不轻，复制一份必然漂移。重放成本 ≤3 镖，可忽略。
 */
private fun previewHit(
    leg: CricketLegState,
    index: Int,
    claim: DartClaim,
): CricketHitResult? = CricketRules.redeclare(leg, index, claim).second.getOrNull(index)

/** 裁决选项上显示的「记给谁」：类别裁决落到类别档，其余（含回落）落到号位。 */
private fun claimTargetLabel(
    dart: Dart,
    claim: DartClaim,
    targets: List<CricketTarget>,
): String {
    val category = CricketRules.categoryFor(dart)
    if (claim == DartClaim.CATEGORY && category != null && category in targets) {
        return category.display
    }
    return dartLabel(dart)
}

private fun dartLabel(dart: Dart): String = when {
    dart.isMiss -> "脱靶"
    dart.isInnerBull || dart.isOuterBull -> "BULL"
    else -> dart.number.toString()
}

/**
 * 单个裁决选项的即时结算文案。
 *
 * 标记与分**分开**写，不合并成一句「+57 分」—— 0 标记 57 分（死区）与
 * 3 标记 0 分（关门）是完全不同的战术含义，合并后就分不出来了。
 * [CricketHitResult.deadZoneExemptionApplied] / [CricketHitResult.scoreSuppressedByOverkill]
 * 必须显式标出来源，否则这两种「反直觉」的结果会被当成算错。
 */
private fun claimDetail(dart: Dart, hit: CricketHitResult?): String {
    if (hit == null) {
        // 非脱靶却未命中 ⇒ 只可能是落点不在本局目标集内，把原因写出来。
        return if (dart.isMiss) "未投中" else "不在本局目标内"
    }
    val parts = mutableListOf("+${hit.marksGained} 标记")
    if (hit.scoreGained > 0) parts += "+${hit.scoreGained} 分"
    if (hit.deadZoneExemptionApplied) parts += "死区豁免"
    if (hit.scoreSuppressedByOverkill) parts += "超杀·得分不计"
    return parts.joinToString("、")
}

/** 裁决选项胶囊。选中态高亮、另一侧可点（点它 = 改判）。 */
@Composable
private fun ClaimChip(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Primary.copy(alpha = 0.18f) else SurfaceVariantDark)
            .border(
                width = 1.dp,
                color = if (selected) Primary else Divider,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) Primary else TextPrimaryDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = detail,
            color = if (enabled) TextSecondaryDark else TextDisabledDark,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}
