package com.dartvio.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.rules.X01Rules
import com.dartvio.app.ui.components.BackspaceButton
import com.dartvio.app.ui.components.BoardTap
import com.dartvio.app.ui.components.BoardTapPad
import com.dartvio.app.ui.components.DartKeypad
import com.dartvio.app.ui.components.InputMode
import com.dartvio.app.ui.components.InputModeSwitch
import com.dartvio.app.ui.components.Multiplier
import com.dartvio.app.ui.components.PadButton
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * X01 对局界面。PRD M3。
 *
 * 布局（自上而下，间隙全部为 [GameBlockGap]，与页边距一致）：
 *  1. 顶部栏：标题 + 局数 + 退出
 *  2. 玩家卡片：左右横向排列（2-4 人，等宽等高，高度 [PlayerCardHeight]）；
 *     当前玩家的三角指示器画在卡片上方的间隙里，不额外占用高度
 *  3. 三镖显示区：一行 3 个槽位 + 本回合累计分
 *  4. 状态条：仅在有回合事件（爆分 / 本回合未得分 / 本局结束）时出现，无事件时不占高度
 *  5. X01 数字键盘：占据剩余空间，按键等高、间隙均匀，左右边缘与玩家卡片、三镖卡片对齐
 *
 * 回合结束规则：满 3 镖后**不自动换人**，需点击「确认」结束回合。
 */
@Composable
fun X01GameScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.newlyUnlocked.collectAsStateWithLifecycle()
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var buffer by remember { mutableStateOf("") }
    var inputLocked by remember { mutableStateOf(false) }
    // 输入方式：默认键盘（不打扰既有用户），选择在进程内保留。
    var inputMode by rememberSaveable { mutableStateOf(InputMode.KEYPAD) }
    // 本回合每一镖的点位，与 currentTurnDarts **逐镖对齐**；键盘录入的镖记为 null（无点位）。
    var turnTapSlots by remember { mutableStateOf(listOf<BoardTap?>()) }

    val inputEnabled = !state.isMatchFinished &&
        state.currentPlayer?.isAi != true &&
        !inputLocked

    // 回合切换 / 比赛结束时清空缓冲与倍率。
    LaunchedEffect(state.currentPlayerIndex, state.isMatchFinished) {
        buffer = ""
        multiplier = Multiplier.SINGLE
        turnTapSlots = emptyList()
    }

    // 每镖录入后锁定 0.5s，让玩家看清三镖显示区。
    LaunchedEffect(inputLocked) {
        if (inputLocked) {
            kotlinx.coroutines.delay(500)
            inputLocked = false
        }
    }

    /** 录入一镖。返回是否真正记入（已满 3 镖时拒绝，与 ViewModel 的口径一致）。 */
    fun recordDart(dart: Dart): Boolean {
        if (state.currentTurnDarts.size >= 3) return false
        viewModel.throwDart(dart)
        inputLocked = true
        // 先占一个空槽：靶盘录入的镖会在随后把槽换成真实点位。
        turnTapSlots = turnTapSlots + null
        return true
    }

    /** 撤销上一镖：点位槽与镖同步出栈。 */
    fun undoDart() {
        turnTapSlots = turnTapSlots.dropLast(1)
        viewModel.undoLastDart()
    }

    // ===== 胜利页：最后一镖 checkout 成功后跳转（含本局胜利 / 决胜胜利） =====
    if (state.isMatchFinished || state.showLegSummary) {
        X01VictoryScreen(
            state = state,
            onPlayAgain = onPlayAgain,
            onExit = onExit,
            onContinueNextLeg = { viewModel.continueToNextLeg() },
            unlockedAchievements = unlockedAchievements
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(GameBlockGap),
        verticalArrangement = Arrangement.spacedBy(GameBlockGap)
    ) {
        GameTopBar(
            title = state.config.displayName,
            subtitle = "第 ${state.x01Leg?.legNumber ?: 1} 局" +
                (state.legsWonText()?.let { " · $it" } ?: ""),
            onExit = onExit
        )

        PlayerCardsRow(state)

        TurnDartsRow(darts = state.currentTurnDarts)

        StatusStrip(message = state.message)

        if (inputMode == InputMode.BOARD) {
            // 靶盘输入：点击即记镖，点位随回合收集；MISS / 撤销 / 确认三键等高排在盘下方。
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BoardTapPad(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = inputEnabled,
                    markers = turnTapSlots.filterNotNull(),
                    onTap = { tap ->
                        if (recordDart(tap.dart)) {
                            // 把刚占位的 null 换成真实点位，与 currentTurnDarts 逐镖对齐。
                            turnTapSlots = turnTapSlots.dropLast(1) + tap
                        }
                    }
                )
                // 底部一行四键：输入方式 / MISS / 撤销 / 确认 ——
                // 「输入方式」在最左，与键盘模式底行同一个入口概念，切回键盘时位置也不跳。
                Row(
                    modifier = Modifier.fillMaxWidth().height(BoardKeyRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
                ) {
                    InputModeSwitch(
                        current = inputMode,
                        onSelect = { inputMode = it },
                        modifier = Modifier.weight(1.2f).fillMaxHeight()
                    )
                    PadButton(
                        label = "MISS",
                        enabled = inputEnabled,
                        accent = SurfaceElevated,
                        textColor = TextSecondaryDark,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { recordDart(Dart.MISS); buffer = "" }
                    )
                    BackspaceButton(
                        enabled = inputEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { undoDart() }
                    )
                    PadButton(
                        label = if (state.currentTurnDarts.size >= 3) "结束回合" else "确认",
                        enabled = inputEnabled,
                        accent = Primary,
                        textColor = OnPrimary,
                        modifier = Modifier.weight(2f).fillMaxHeight(),
                        onClick = {
                            viewModel.commitTurn(turnTapSlots.filterNotNull())
                            inputLocked = true
                        }
                    )
                }
            }
        } else {
            DartKeypad(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // 键盘自身不再留边：左右边缘与玩家卡片、三镖卡片严格对齐，
                // 上下间隙由外层 spacedBy(GameBlockGap) 统一提供
                contentPadding = PaddingValues(0.dp),
                enabled = inputEnabled,
                multiplier = multiplier,
                onMultiplierChange = { multiplier = it },
                buffer = buffer,
                currentRemaining = state.x01Leg?.let { X01Rules.previewRemaining(it, it.currentTurnDarts) },
                turnDartsCount = state.currentTurnDarts.size,
                onDigit = { d ->
                    buffer = appendDigit(buffer, d)
                    val v = buffer.toIntOrNull()
                    if (buffer.length == 2 && v != null) {
                        if (isValidDartValue(v)) {
                            recordDart(dartOf(v, multiplier))
                            buffer = ""
                        } else {
                            val first = buffer[0].digitToInt()
                            if (first in 1..9) recordDart(dartOf(first, multiplier))
                            buffer = "$d"
                        }
                    }
                },
                onBull25 = { recordDart(Dart.OUTER_BULL); buffer = "" },
                onBull50 = { recordDart(Dart.INNER_BULL); buffer = "" },
                onMiss = { recordDart(Dart.MISS); buffer = "" },
                onConfirm = {
                    val v = buffer.toIntOrNull()
                    if (v != null && isValidDartValue(v)) {
                        recordDart(dartOf(v, multiplier))
                    } else {
                        // 缓冲为空 → 结束本回合（满 3 镖或主动认输剩余镖）。
                        viewModel.commitTurn(turnTapSlots.filterNotNull())
                        inputLocked = true
                    }
                    buffer = ""
                },
                onBackspace = {
                    if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
                    else undoDart()
                },
                // 输入方式切换键：由 X01 传入槽位；练习 / 房间对局页不传 ⇒ 末行不留死键。
                modeSwitch = { slotModifier ->
                    InputModeSwitch(
                        current = inputMode,
                        onSelect = { inputMode = it },
                        modifier = slotModifier
                    )
                }
            )
        }
    }
}

/**
 * X01 玩家卡片行：左右横向排列、等宽、等高（[PlayerCardHeight]）。
 *
 * 卡片数量 = 参战人数（本人 + 选中的对手），宽度用 `weight(1f)` 自适应 ——
 * 2 人时两张大卡、4 人时四张窄卡，不需要为每种人数各写一套布局。
 * 第 1 席是本人（名字与头像来自登录账号），与右侧对手之间加一根短竖条：
 * 卡片本身长得一模一样，没有这根竖条时「哪个是我」要靠名字去认。
 *
 * **主得分字号在这里算了统一下发**，而不是交给每张卡片按自己的剩余分去算：
 * 卡片等宽，但 441 是 3 位、95 是 2 位，各算各的会让同一行出现两种字号，看起来像两种规格。
 */
@Composable
private fun PlayerCardsRow(state: GameUiState) {
    val leg = state.x01Leg

    // 每张卡片要显示的剩余分：当前玩家本回合已录入镖时实时预览（每投完一镖立即刷新）。
    val remainings: List<Int> = state.players.indices.map { index ->
        val playerState = leg?.players?.getOrNull(index)
        val isActive = index == state.currentPlayerIndex
        if (isActive && leg != null && leg.currentTurnDarts.isNotEmpty()) {
            X01Rules.previewRemaining(leg, leg.currentTurnDarts)
        } else {
            playerState?.remaining ?: state.config.targetScore
        }
    }
    // 目标分最高到 1101（4 位），位数取所有玩家中**最长**的那个。
    val scoreDigits = remainings.maxOfOrNull { it.toString().length } ?: 3
    // 本回合已得的实时累计（只对正在录入的这一席有意义）。
    val liveTurnScore = leg?.currentTurnDarts?.sumOf { it.score } ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardCount = state.players.size.coerceAtLeast(1)
        val dividerWidth = if (cardCount > 1) X01SeatDividerWidth else 0.dp
        // 短竖条也占横向宽度：漏掉它会让 4 人局的卡片宽度被高估、主得分顶出卡片。
        val cardWidthDp = (maxWidth - GameBlockGap * (cardCount - 1) - dividerWidth)
            .div(cardCount)
            .value
        val scoreSize = x01ScoreSize(cardWidthDp, scoreDigits)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.players.forEachIndexed { index, player ->
                // 短竖条把「本人」和「对手」分成两组。
                // 高度写固定值而不是 `fillMaxHeight(fraction)`：本行的高度由卡片决定，
                // 但 Row 拿到的约束来自外层 Column（剩余空间远大于卡片），按比例取会得到一根比卡片还高的竖条。
                if (index == 1) SeatDivider(Modifier.height(PlayerCardHeight * 0.5f))
                val playerState = leg?.players?.getOrNull(index)
                val isActive = index == state.currentPlayerIndex
                X01PlayerCard(
                    name = player.name,
                    avatarEmoji = playerAvatarEmoji(player),
                    remaining = remainings[index],
                    lastTurnScore = playerState?.lastTurnScore,
                    liveTurnScore = liveTurnScore.takeIf { isActive && it > 0 },
                    isActive = isActive,
                    legsWon = state.legsWonOf(player.id),
                    scoreSize = scoreSize,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单张 X01 玩家卡片，自上而下 4 行、主得分独占中间一行：
 *  1. 头像 + 昵称（小字，居中，窄卡片走省略号）
 *  2. **主得分**（[scoreSize] 号，整行归它）
 *  3. 上一回合得分（灰色删除线）+ 本回合已得（小方框）
 *  4. 胜 N 局（小字）
 *
 * 除主得分外一律 9~12sp。主得分必须**唯一醒目**：一局里要反复扫的就是这个数，
 * 若旁边还有一个同量级的大字，玩家每次都得先分辨「哪个才是剩余分」。
 *
 * 上一回合得分从主得分旁边挪到独立小字行，是这次字号能放大的前提 ——
 * 挤在同一行时主得分只能按「剩余空间」算字号，4 人局会被压到 20sp 上下；
 * 挪开之后同一张卡片 4 人局约 34sp、2 人局约 82sp。
 */
@Composable
private fun X01PlayerCard(
    name: String,
    avatarEmoji: String,
    remaining: Int,
    lastTurnScore: Int?,
    liveTurnScore: Int?,
    isActive: Boolean,
    legsWon: Int,
    scoreSize: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerCardHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isActive) SurfaceVariantDark else SurfaceDark)
                .border(
                    if (isActive) 2.dp else 1.dp,
                    if (isActive) Primary else Divider,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // SpaceEvenly：行数固定为 4，但第 3 行会随回合增删内容，
            // 均分间距能让主得分始终落在卡片中线上，不随那两行出现与否上下跳动。
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. 头像 + 昵称
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = avatarEmoji, fontSize = 11.sp)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = name,
                    color = if (isActive) Primary else TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    // 4 人局卡片只有 ~78dp 宽，名字 + 头像必然放不下：
                    // 省略号收尾至少能看出「名字被截断了」，硬裁会让人以为那就是全名。
                    overflow = TextOverflow.Ellipsis,
                    // weight(fill = false)：放得下就保持自然宽度（整组居中），放不下才压缩。
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            // 2. 主得分（整行归它）
            Text(
                text = "$remaining",
                color = TextPrimaryDark,
                fontSize = scoreSize.sp,
                lineHeight = (scoreSize * 1.05f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            // 3. 上一回合得分 + 本回合已得
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LastScoreText(score = lastTurnScore, fontSize = X01SecondaryScoreSize.sp)
                if (liveTurnScore != null) {
                    Spacer(Modifier.width(4.dp))
                    TurnScoreChip(score = liveTurnScore)
                }
            }

            // 4. 胜局
            Text(
                text = "胜 $legsWon 局",
                color = TextSecondaryDark,
                fontSize = 10.sp
            )
        }

        // 当前玩家三角指示器：画在卡片上方的区块间隙里，不占用布局高度，
        // 使卡片上下间隙与其它区块间隙一致。
        ActiveTriangle(
            visible = isActive,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-7).dp)
        )
    }
}

/** 「本人 | 对手」短竖条的宽度（dp），需与 [SeatDivider] 的实现保持一致。 */
private val X01SeatDividerWidth = 2.dp

/** 靶盘模式下底部四键行（输入方式 / MISS / 撤销 / 确认）的高度：与键盘按键同高。 */
private val BoardKeyRowHeight = 56.dp

/** 数字宽度按 em 估算：Roboto Bold 实际约 0.56 em，取 0.62 作安全余量。 */
private const val X01DigitWidthEm = 0.62f

/** 主得分左右各 8dp 内边距（与卡片 padding 一致），算可用宽度时扣掉。 */
private const val X01ScoreSidePaddingDp = 16f

/** 主得分最高不超过卡片高度的这个比例：再高就会顶掉上下的小字行。 */
private const val X01ScoreHeightRatio = 0.5f

/** 卡片内非主得分文字的字号（sp）：上一回合得分、本回合已得方框。 */
private const val X01SecondaryScoreSize = 9f

/**
 * X01 卡片主得分字号：在「宽度放得下 [scoreDigits] 位数字」与「不超过卡片半高」之间取小。
 *
 * 主得分独占一行，横向没有别的元素跟它抢宽度；唯一要对齐的是**所有卡片同号**，
 * 所以 [scoreDigits] 必须由调用方按所有玩家中最长的剩余分传入，而不是各卡各算。
 */
private fun x01ScoreSize(cardWidthDp: Float, scoreDigits: Int): Float {
    val digits = scoreDigits.coerceAtLeast(1)
    val byWidth = (cardWidthDp - X01ScoreSidePaddingDp) / (digits * X01DigitWidthEm)
    val byHeight = PlayerCardHeight.value * X01ScoreHeightRatio
    return minOf(byWidth, byHeight).coerceAtLeast(20f)
}

/**
 * 「本回合已得」小方框。
 *
 * 与上一回合得分的删除线**样式必须能区分**：删除线是「已经发生的历史」，
 * 方框是「这一回合正在累计的数」，两者都用小字，只靠方框把它们分开。
 */
@Composable
private fun TurnScoreChip(score: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Secondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$score",
            color = Secondary,
            fontSize = X01SecondaryScoreSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
