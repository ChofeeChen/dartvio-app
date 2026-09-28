import SwiftUI
import shared

/**
 * Cricket 正式对局页，对齐 Android `ui/game/CricketGameScreen.kt`。
 *
 * ## 板面为什么是「矩阵」
 *
 * Cricket 的进度不是一个数，而是 **目标位 × 玩家** 的一张凭证表：
 * 每个目标要打中 3 次才算「关闭」，关闭之后别人在这个目标上还能得分、自己则不再累加。
 * 所以 X01 那种「一个剩余分」的展示格局在这里不成立，必须矩阵化 ——
 * 这也是这个页面与 X01 唯一的本质差异。
 *
 * ## 为什么细化到「镖」
 *
 * cricket 不存在 bust，每一镖落地后必须**立即**在矩阵上体现（靠 VM 的 preview 机制），
 * 而不是等整回合结算才更新 —— 迟到一镖的反馈会让玩家误以为自己打空了。
 */
struct CricketGameView: View {

    let launch: CricketLaunch
    @State private var viewModel: CricketGameViewModel
    @Environment(\.dismiss) private var dismiss

    // `@MainActor` 不能少：`@Environment` 的包装值初始化依赖于主线程环境，
    // 没有它编译器会判「init 未初始化全部存储属性」（与 X01GameView 一致）。
    @MainActor
    init(launch: CricketLaunch) {
        self.launch = launch
        _viewModel = State(initialValue: CricketGameViewModel(launch: launch))
    }

    var body: some View {
        VStack(spacing: 0) {
            GameTopBar(
                title: "Cricket",
                legText: "第 \(viewModel.legNumber) 局 · \(viewModel.variantLabel)",
                onExit: { dismiss() }
            )

            ScrollView {
                VStack(spacing: 12) {
                    marksBoard
                    TurnDartsRow(darts: viewModel.turnDarts.map { $0.dart })
                        .padding(.horizontal, 4)
                    hitStrip
                    scoreRow
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
            }

            KeypadView(
                confirmTitle: "结束回合",
                onDart: { viewModel.throwDart($0) },
                onUndo: { viewModel.undoLastDart() },
                onConfirm: { viewModel.finishTurn() }
            )
        }
        .background(Palette.background)
        .navigationBarBackButtonHidden(true)
        .overlay(alignment: .bottom) {
            if let message = matchResultText {
                resultBanner(message)
            }
        }
    }

    // MARK: - 记分矩阵

    private var marksBoard: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: viewModel.players.count + 1)
        return VStack(spacing: 6) {
            LazyVGrid(columns: columns, spacing: 4) {
                Text("目标")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
                ForEach(0..<viewModel.players.count, id: \.self) { index in
                    Text(viewModel.playerName(index))
                        .font(.caption2)
                        .foregroundStyle(index == viewModel.currentIndex ? Palette.primary : Palette.textMuted)
                        .lineLimit(1)
                }
                ForEach(viewModel.targets, id: \.token) { target in
                    Text(viewModel.targetDisplay(target))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Palette.textPrimary)
                        .frame(height: 34)
                    ForEach(0..<viewModel.players.count, id: \.self) { index in
                        MarkCell(
                            text: viewModel.marksDisplay(target: target, playerIndex: index),
                            isClosed: viewModel.isClosed(target: target, playerIndex: index),
                            isActiveColumn: index == viewModel.currentIndex
                        )
                    }
                }
            }
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("cricketBoard")
    }

    // MARK: - 分数行

    private var scoreRow: some View {
        HStack(spacing: 8) {
            ForEach(0..<viewModel.players.count, id: \.self) { index in
                let player = viewModel.players[index]
                VStack(spacing: 2) {
                    Text(viewModel.playerName(index))
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                    Text("\(player.score)")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(index == viewModel.currentIndex ? Palette.primary : Palette.textPrimary)
                    Text("胜 \(viewModel.legsWon[index])")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(index == viewModel.currentIndex ? Palette.primaryContainer : Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        // ⚠️ `accessibilityElement(children: .contain)` 不能少：纯 HStack 不会被 SwiftUI
        // 暴露成无障碍节点，`accessibilityIdentifier` 也就随之丢失（XCUITest 查不到
        // `cricketScores` —— V14 这么挂过一次）。声明成容器后 identifier 才挂得上，
        // 且子文本仍可单独查询。
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cricketScores")
    }

    // MARK: - 本回合命中

    private var hitStrip: some View {
        let texts = viewModel.turnHits.compactMap { viewModel.hitText($0) }
        return Group {
            if texts.isEmpty {
                EmptyView()
            } else {
                Text(texts.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(Palette.accent)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("cricketTurnHits")
            }
        }
    }

    // MARK: - 结果

    private var matchResultText: String? {
        if let winner = viewModel.matchWinnerIndex {
            return "\(viewModel.playerName(winner)) 赢得比赛（\(viewModel.legsWon[winner]) : \(viewModel.legsWon[1 - winner])）"
        }
        if viewModel.leg.isFinished {
            return "\(viewModel.playerName(Int(viewModel.leg.winnerIndex?.intValue ?? 0))) 拿下一局"
        }
        return nil
    }

    private func resultBanner(_ message: String) -> some View {
        VStack(spacing: 10) {
            Text(message)
                .font(.headline)
                .foregroundStyle(Palette.onPrimary)
                .accessibilityIdentifier("cricketResult")
            Button(action: { dismiss() }) {
                Text("返回设置")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.primary)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .background(Palette.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Palette.primary.opacity(0.96))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .padding()
    }
}

/// 矩阵里的一格：已关闭的目标用实心色标记，一眼能看出「这门谁还开着」。
private struct MarkCell: View {
    let text: String
    let isClosed: Bool
    let isActiveColumn: Bool

    var body: some View {
        Text(text)
            .font(.system(size: 15, weight: .bold, design: .monospaced))
            .frame(maxWidth: .infinity)
            .frame(height: 34)
            .background(isClosed ? Palette.primaryContainer : Palette.surfaceVariant)
            .foregroundStyle(isClosed ? Palette.primary : Palette.textPrimary)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(isActiveColumn ? Palette.primary.opacity(0.6) : Color.clear, lineWidth: 1)
            )
    }
}
