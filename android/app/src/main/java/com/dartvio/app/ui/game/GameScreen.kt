package com.dartvio.app.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.Player
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 对局路由入口。
 *
 * 按玩法分发到各自独立的对局页：
 *  - X01    → [X01GameScreen]（剩余分、倍率、Double-Out 等 X01 专属 UI / 键盘）
 *  - CRICKET → [CricketGameScreen]（标记网格、分数、Cricket 专属键盘）
 *
 * 修复：Cricket 此前复用了 X01 的布局与键盘（倍率 S/D/T、Bull 50、剩余分等），
 * 属于错误的玩法耦合。现改为各自独立实现，仅在通用视觉组件（顶栏/提示条/三镖区）上复用。
 */
@Composable
fun GameScreen(
    config: MatchConfig,
    players: List<Player>,
    onExit: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    // 进入本页时启动一场新比赛（仅在首次组合 / 参数变化时执行）。
    LaunchedEffect(config, players) {
        viewModel.startMatch(config, players)
    }

    // 「再来一局」＝ 用相同配置原地重开，而不是退回首页
    // （修复：此前 onPlayAgain 与 onExit 实现完全相同，语义错误）。
    val playAgain: () -> Unit = { viewModel.startMatch(config, players) }

    when (config.matchType) {
        MatchType.CRICKET -> CricketGameScreen(
            viewModel = viewModel,
            onExit = onExit,
            onPlayAgain = playAgain
        )
        else -> X01GameScreen(
            viewModel = viewModel,
            onExit = onExit,
            onPlayAgain = playAgain
        )
    }
}
