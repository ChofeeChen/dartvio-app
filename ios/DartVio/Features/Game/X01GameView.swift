import SwiftUI
import shared

/// P3：X01 对局页。布局对齐 Android `X01GameScreen`：顶部栏 → 玩家卡 → 三镖行 → 状态条 → 键盘。
struct X01GameView: View {
    let launch: X01Launch

    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: X01GameViewModel

    @MainActor
    init(launch: X01Launch) {
        self.launch = launch
        let config = X01SetupMapping.config(from: launch)
        let players = X01SetupMapping.players(from: launch)
        _viewModel = State(initialValue: X01GameViewModel(config: config, players: players))
    }

    var body: some View {
        VStack(spacing: 10) {
            GameTopBar(
                title: "X01 · \(viewModel.config.targetScore)",
                legText: "第 \(viewModel.legNumber) 局",
                onExit: { dismiss() }
            )

            HStack(spacing: 10) {
                ForEach(viewModel.leg.players.indices, id: \.self) { index in
                    playerCard(at: index)
                }
            }
            .padding(.horizontal)

            TurnDartsRow(darts: viewModel.turnDarts)
            StatusStrip(message: viewModel.message)

            Spacer(minLength: 0)

            KeypadView(
                confirmTitle: viewModel.confirmTitle,
                onDart: { viewModel.throwDart($0) },
                onUndo: { viewModel.undoLastDart() },
                onConfirm: { viewModel.commitTurn() }
            )
            .padding(.horizontal)
            .opacity(viewModel.canInput ? 1 : 0.5)
            .disabled(!viewModel.canInput)

            Text(viewModel.isCurrentPlayerAi ? "电脑思考中…" : "提示：投满 3 镖后按「结束回合」")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
                .padding(.bottom, 8)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationBarHidden(true)
        .sheet(isPresented: resultPresented) { resultSheet }
    }

    // MARK: - 玩家卡

    private func playerCard(at index: Int) -> some View {
        let seat = viewModel.leg.players[index]
        let isActive = index == viewModel.leg.currentPlayerIndex
        let player = viewModel.players.first { $0.id == seat.playerId }
        let remaining = (isActive && !viewModel.turnDarts.isEmpty)
            ? viewModel.previewedRemaining
            : seat.remaining

        return X01PlayerCard(
            name: player?.name ?? seat.playerId,
            remaining: remaining,
            isActive: isActive,
            // 「上一回合剩分」与「上一回合得分」在 Kotlin 侧是 Int?（导出为装箱的 KotlinInt），需要拆箱
            previousRemaining: unwrap(seat.previousRemaining),
            lastTurnScore: unwrap(seat.lastTurnScore),
            turnScore: isActive ? viewModel.turnScore : 0,
            legsWon: seat.legsWon,
            isAi: player?.type == PlayerType.ai
        )
    }

    private func unwrap(_ value: KotlinInt?) -> Int32 {
        Int32(value?.intValue ?? 0)
    }

    // MARK: - 局间总结 / 比赛结果

    private var resultPresented: Binding<Bool> {
        Binding(
            get: { viewModel.showLegSummary || viewModel.matchWinnerId != nil },
            set: { presented in
                if !presented { viewModel.dismissLegSummary() }
            }
        )
    }

    private var resultSheet: some View {
        let isMatchOver = viewModel.matchWinnerId != nil
        let winnerId = viewModel.matchWinnerId ?? latestLegWinnerId
        let winnerName = viewModel.players.first { $0.id == winnerId }?.name ?? "玩家"

        return VStack(spacing: 16) {
            Text(isMatchOver ? "比赛结束" : "本局结束")
                .font(.title2.weight(.bold))
                .foregroundStyle(Palette.textPrimary)
            Text("\(winnerName) 拿下本局")
                .foregroundStyle(Palette.accent)
            Text("比分 " + viewModel.legsWon
                .map { key, value in "\(name(of: key)) \(value)" }
                .joined(separator: " : "))
                .font(.caption)
                .foregroundStyle(Palette.textMuted)

            if isMatchOver {
                PrimaryButton(title: "再来一次") { viewModel.restartMatch() }
            } else {
                PrimaryButton(title: "继续下一局") { viewModel.continueToNextLeg() }
            }
            Button("返回") { dismiss() }
                .foregroundStyle(Palette.textSecondary)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Palette.surface)
    }

    /// 局间总结时 `matchWinnerId` 还是 nil，用当前局的赢家（引擎已把 currentPlayerIndex 切走之前落账）。
    private var latestLegWinnerId: String {
        viewModel.legsWon.max(by: { $0.value < $1.value })?.key ?? viewModel.currentPlayerId
    }

    private func name(of playerId: String) -> String {
        viewModel.players.first { $0.id == playerId }?.name ?? playerId
    }
}
