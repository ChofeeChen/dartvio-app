package com.dartvio.app.ui.game

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.CricketPlayerState
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.ScoreSink
import com.dartvio.app.domain.model.X01PlayerState
import com.dartvio.app.ui.achievement.AchievementUnlockCard
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.OnSecondary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import kotlin.math.abs

// =====================================================================================
// 胜利页通用外壳
// =====================================================================================

/**
 * 胜利祝贺页（通用外壳）。
 *
 * - 顶部：胜利横幅（缩放脉冲动效）
 * - 中部：第几局 + 双方统计数据对比表
 * - 底部：再来一局 / 返回首页
 * - 音效：使用 [ToneGenerator] 播放由 P1 触发的胜利音序列（无需音频资源）
 *
 * @param winnerLabel 获胜者显示名
 * @param legNumber 第几局
 * @param accent 主题强调色（X01 用金色 Primary，Cricket 用青色 Secondary）
 * @param stats 双方统计行：[列标题, P1 值, P2 值]
 * @param playSound 是否播放音效（默认仅 P1 的胜利触发，由调用方决定）
 * @param modeLabel 本局玩法名（M7 P7.4：三种变体都要标注，为空则不追加）
 * @param note 变体判定说明（如生死局的「低分领先判定」），为空时不占任何高度
 */
@Composable
fun VictoryScreen(
    winnerLabel: String,
    legNumber: Int,
    accent: Color,
    stats: List<Triple<String, String, String>>,
    playerNames: List<String>,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    playSound: Boolean = true,
    /** 比赛是否已结束（决胜局）。false 表示本局胜利，仍需继续下一局。 */
    isMatchOver: Boolean = true,
    /** 点击"继续下一局"（仅 isMatchOver=false 时展示）。 */
    onContinueNextLeg: (() -> Unit)? = null,
    /** 本场结算后新解锁的成就（决策④：结算页解锁卡片）。为空时不占任何高度。 */
    unlockedAchievements: List<AchievementProgress> = emptyList(),
    modeLabel: String? = null,
    note: String? = null,
    /**
     * **还没打完整场**时的一句进度说明（如「整场先赢 3 局 · 打完才会计入统计」）。
     *
     * 这句必须说：统计与历史以**整场**为一条记录（不是一局），
     * 而界面上「本局获胜」已经长得像「赢了」—— 不说这一句，
     * 在这里退出的人回头会发现数据页一片空白，且无从得知是为什么
     * （2026-09-27 真机反馈：打完一局本地 X01，数据页没有数据）。
     */
    progressNote: String? = null,
) {
    // ===== 动效：胜利徽章脉冲缩放 =====
    val infinite = rememberInfiniteTransition(label = "victory")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // ===== 声效：P1 胜利时播放（无音频资源，使用系统提示音） =====
    if (playSound) {
        VictorySound()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.16f),
                        BackgroundDark,
                        BackgroundDark
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            // 解锁卡片会让页面内容变高，加滚动保证小屏也不会把底部按钮挤出屏幕
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== 胜利徽章 =====
            Box(
                modifier = Modifier
                    .scale(pulse)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(3.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\uD83C\uDFC6", fontSize = 44.sp)
            }

            Text(
                text = if (isMatchOver) "恭喜获胜！" else "本局获胜！",
                color = accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = winnerLabel,
                color = TextPrimaryDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                // 玩法名与局数同行：本局是按哪套规则判的，是回看结果时最先要回答的问题。
                text = "第 $legNumber 局" + (modeLabel?.let { " · $it" } ?: ""),
                color = TextSecondaryDark,
                fontSize = 13.sp
            )

            // ===== 成就解锁卡片（决策④ 主路径：先于统计表出现，保证第一眼可见）=====
            AchievementUnlockCard(
                unlocked = unlockedAchievements,
                accent = accent
            )

            Spacer(Modifier.height(4.dp))

            // ===== 双方统计对比表 =====
            StatsTable(
                playerNames = playerNames,
                stats = stats,
                accent = accent
            )

            // ===== 整场进度（多局未打完时）=====
            if (progressNote != null) {
                Text(
                    text = progressNote,
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ===== 变体判定说明（如生死局「低分领先判定」）=====
            if (note != null) {
                Text(
                    text = note,
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "返回首页", color = TextPrimaryDark)
                }
                Button(
                    onClick = {
                        if (isMatchOver) onPlayAgain() else onContinueNextLeg?.invoke()
                    },
                    modifier = Modifier.weight(2f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        text = if (isMatchOver) "再来一局" else "继续下一局",
                        color = if (accent == Primary) OnPrimary else OnSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** 双方统计对比表。 */
@Composable
private fun StatsTable(
    playerNames: List<String>,
    stats: List<Triple<String, String, String>>,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // 表头
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "统计", color = TextSecondaryDark, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                text = playerNames.getOrNull(0) ?: "P1",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = playerNames.getOrNull(1) ?: "P2",
                color = TextSecondaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        stats.forEach { (label, v1, v2) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark.copy(alpha = 0.4f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, color = TextSecondaryDark, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    text = v1,
                    color = TextPrimaryDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = v2,
                    color = TextPrimaryDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 胜利音效：使用系统 [ToneGenerator] 播放上行音阶，
 * 避免引入音频资源文件。播放结束后释放资源。
 */
@Composable
private fun VictorySound() {
    val tone = remember {
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        }.getOrNull()
    }

    LaunchedEffect(tone) {
        val t = tone ?: return@LaunchedEffect
        // 上行小音阶，营造胜利感
        val sequence = listOf(
            ToneGenerator.TONE_DTMF_1,
            ToneGenerator.TONE_DTMF_3,
            ToneGenerator.TONE_DTMF_5,
            ToneGenerator.TONE_DTMF_9
        )
        for (s in sequence) {
            t.startTone(s, 160)
            delay(190)
        }
        // 长音收尾
        t.startTone(ToneGenerator.TONE_DTMF_0, 420)
    }

    DisposableEffect(tone) {
        onDispose { runCatching { tone?.release() } }
    }
}

// =====================================================================================
// X01 胜利页
// =====================================================================================

@Composable
fun X01VictoryScreen(
    state: GameUiState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    onContinueNextLeg: (() -> Unit)? = null,
    unlockedAchievements: List<AchievementProgress> = emptyList()
) {
    val leg = state.x01Leg
    val winnerId = state.winnerPlayerId ?: state.lastLegWinnerId
    val winner = state.playerById(winnerId ?: "") ?: state.players.firstOrNull()
    val p1 = leg?.players?.getOrNull(0)
    val p2 = leg?.players?.getOrNull(1)

    val stats = listOf(
        Triple("胜局", "${state.legsWonOf(state.players.getOrNull(0)?.id ?: "")}", "${state.legsWonOf(state.players.getOrNull(1)?.id ?: "")}"),
        Triple("剩余分", "${p1?.remaining ?: 0}", "${p2?.remaining ?: 0}"),
        Triple("PPR", formatPpr(p1), formatPpr(p2)),
        Triple("投镖数", "${p1?.dartsThrown ?: 0}", "${p2?.dartsThrown ?: 0}"),
        Triple("累计得分", "${p1?.totalScored ?: 0}", "${p2?.totalScored ?: 0}")
    )

    // P1（索引 0 的玩家）获胜时播放音效
    val p1IsWinner = winnerId != null && winnerId == state.players.getOrNull(0)?.id

    VictoryScreen(
        winnerLabel = winner?.name ?: "玩家",
        legNumber = leg?.legNumber ?: 1,
        accent = Primary,
        stats = stats,
        playerNames = listOf(
            state.players.getOrNull(0)?.name ?: "P1",
            state.players.getOrNull(1)?.name ?: "P2"
        ),
        onPlayAgain = onPlayAgain,
        onExit = onExit,
        playSound = p1IsWinner,
        isMatchOver = state.isMatchFinished,
        onContinueNextLeg = onContinueNextLeg,
        unlockedAchievements = unlockedAchievements,
        progressNote = legProgressNote(state),
    )
}

/**
 * 多局赛制、且这一局不是决胜局时的一句进度说明。
 *
 * 只在这时说：休闲单局 / 先赢 1 局打完就是整场结束，那句「打完才计入统计」是废话，
 * 而一句永远正确的废话会把真正该看的那句（什么时候真的要提醒）一起淹掉。
 */
private fun legProgressNote(state: GameUiState): String? {
    if (state.isMatchFinished) return null
    val need = state.config.legsToWin
    if (need <= 1) return null
    return "整场先赢 $need 局 · 全部打完才会计入统计"
}

private fun formatPpr(p: X01PlayerState?): String {
    val v = p?.ppr ?: 0.0
    return String.format("%.1f", abs(v))
}

// =====================================================================================
// Cricket 胜利页
// =====================================================================================

@Composable
fun CricketVictoryScreen(
    state: GameUiState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    onContinueNextLeg: (() -> Unit)? = null,
    unlockedAchievements: List<AchievementProgress> = emptyList()
) {
    val leg = state.cricketLeg
    val winnerId = state.winnerPlayerId ?: state.lastLegWinnerId
    val winner = state.playerById(winnerId ?: "") ?: state.players.firstOrNull()
    val p1 = leg?.players?.getOrNull(0)
    val p2 = leg?.players?.getOrNull(1)
    val targets = state.config.cricketTargets
    val variant = state.config.cricketVariant

    val stats = buildList {
        add(
            Triple(
                "胜局",
                "${state.legsWonOf(state.players.getOrNull(0)?.id ?: "")}",
                "${state.legsWonOf(state.players.getOrNull(1)?.id ?: "")}"
            )
        )
        // 不计分变体的得分恒为 0，留在表里只会让玩家怀疑是不是哪里没算对。
        if (variant.scoreSink != ScoreSink.NONE) {
            add(Triple("得分", "${p1?.score ?: 0}", "${p2?.score ?: 0}"))
        }
        add(Triple("已关闭", "${closedCount(p1, targets)}", "${closedCount(p2, targets)}"))
        add(Triple("总标记", "${markCount(p1, targets)}", "${markCount(p2, targets)}"))
    }

    val p1IsWinner = winnerId != null && winnerId == state.players.getOrNull(0)?.id

    VictoryScreen(
        winnerLabel = winner?.name ?: "玩家",
        legNumber = leg?.legNumber ?: 1,
        accent = Secondary,
        stats = stats,
        playerNames = listOf(
            state.players.getOrNull(0)?.name ?: "P1",
            state.players.getOrNull(1)?.name ?: "P2"
        ),
        onPlayAgain = onPlayAgain,
        onExit = onExit,
        playSound = p1IsWinner,
        isMatchOver = state.isMatchFinished,
        onContinueNextLeg = onContinueNextLeg,
        unlockedAchievements = unlockedAchievements,
        progressNote = legProgressNote(state),
        // P7.4：三种变体都要标注本局玩法名。
        modeLabel = variant.label,
        // P7.4：生死局必须补上「低分领先判定」说明，否则赢家自己都会以为判定反了。
        note = when (variant) {
            CricketVariant.CUT_THROAT ->
                "生死局按「低分领先」判定：关满 7 分区后，分数较低的一方胜出（同分时先关满者胜）。"
            CricketVariant.NO_SCORE ->
                "不计分：先关闭全部 7 分区的一方直接胜出，双方分数不参与判定。"
            CricketVariant.STANDARD -> null
        }
    )
}

private fun closedCount(p: CricketPlayerState?, targets: List<CricketTarget>): Int =
    targets.count { (p?.marks?.marksOf(it) ?: 0) >= 3 }

private fun markCount(p: CricketPlayerState?, targets: List<CricketTarget>): Int =
    targets.sumOf { (p?.marks?.marksOf(it) ?: 0).coerceAtMost(3) }
