import SwiftUI
import shared

/**
 * Cricket 正式对局页（移动端规范），对齐 Android `ui/game/CricketGameScreen.kt`。
 *
 * ## 板面为什么是「矩阵」
 *
 * Cricket 的进度不是一个数，而是 **目标位 × 玩家** 的一张凭证表：
 * 每个目标要打中 3 次才算「关闭」，关闭之后别人在这个目标上还能得分、自己则不再累加。
 * 所以 X01 那种「一个剩余分」的展示格局在这里不成立。
 *
 * ## 这一版按移动端规范重排了三处
 *
 * 1. **板面竖排**：每行一靶号，三栏「本人标记 | 靶号 | 对手标记」（规范的三栏是 1v1 口径；
 *    多人局塞不进三栏，仍走下面的矩阵，不假装能塞）；
 * 2. **顶栏**：玩法名 + Leg 信息 + 倒计时/暂停；
 * 3. **底部录入区**：3 个镖位 + 快捷号位键 + T/D + 撤销上一轮 + 提交本轮（见 `CricketEntryPadView`）。
 *
 * ## 规则一律不在这一页
 *
 * 标记、得分、胜负、轮次切换全部由引擎 `CricketRules` 给出（`CricketGameViewModel` 只是状态容器）：
 * 「关满 + 分不落后才算赢」这类判定在 iOS 侧重写一遍，两端迟早跑偏
 * —— 更何况 `isRoundLimitWin` 上还有一条已申报的平分口径缺口，那个行为必须两端一致。
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
            topBar
            ScrollView {
                VStack(spacing: 10) {
                    board
                    scoreCards
                    if let status = viewModel.statusMessage {
                        Text(status)
                            .font(.caption)
                            .foregroundStyle(CricketMatchStyle.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("cricketStatus")
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        // 录入区固定在底部：板面再长，键位也不会跟着滚走。
        .safeAreaInset(edge: .bottom) { entryPad }
        .background(CricketMatchStyle.background.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .task { viewModel.startTimer() }
        .onDisappear { viewModel.stopTimer() }
        .overlay { resultOverlay }
    }

    // MARK: - 顶栏

    private var topBar: some View {
        HStack(spacing: 10) {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CricketMatchStyle.textSecondary)
                    .frame(width: 32, height: 32)
                    .background(CricketMatchStyle.surfaceAlt)
                    .clipShape(Circle())
            }
            .accessibilityIdentifier("cricketClose")

            Spacer(minLength: 2)

            VStack(spacing: 2) {
                Text(viewModel.titleText)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(CricketMatchStyle.textPrimary)
                    .lineLimit(1)
                Text("LEG \(viewModel.legNumber) · 先胜 \(viewModel.configLegsToWin) 局 · \(viewModel.legsScoreText)")
                    .font(.caption2)
                    .foregroundStyle(CricketMatchStyle.textMuted)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("cricketTopBar")

            Spacer(minLength: 2)

            HStack(spacing: 6) {
                Text("\(viewModel.remainingSeconds)s")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(viewModel.isPaused ? CricketMatchStyle.textMuted : CricketMatchStyle.active)
                    .accessibilityIdentifier("cricketCountdown")
                Button(viewModel.isPaused ? "继续" : "暂停") { viewModel.togglePause() }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(CricketMatchStyle.textSecondary)
                    .accessibilityIdentifier("cricketPause")
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(CricketMatchStyle.surface)
    }

    // MARK: - 计分板

    /**
     * 规范的三栏板面（1v1）：「本人标记 | 靶号 | 对手标记」逐行竖排。
     *
     * 多人局（对手席位最多 3 个）**不硬塞进三栏**：三栏的语义就是"我 / 对手"两个人，
     * 四个人挤进去只会让每一行都读不出是谁的标记，那种情况下退回下面的矩阵。
     */
    private var board: some View {
        Group {
            if viewModel.players.count == 2 {
                twoColumnBoard
            } else {
                matrixBoard
            }
        }
    }

    private var twoColumnBoard: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text(viewModel.playerName(0))
                    .font(.caption2)
                    .foregroundStyle(CricketMatchStyle.textSecondary)
                    .frame(maxWidth: .infinity)
                Text("靶号")
                    .font(.caption2)
                    .foregroundStyle(CricketMatchStyle.textMuted)
                    .frame(width: 56)
                Text(viewModel.playerName(1))
                    .font(.caption2)
                    .foregroundStyle(CricketMatchStyle.textSecondary)
                    .frame(maxWidth: .infinity)
            }
            .padding(.bottom, 4)

            ForEach(orderedTargets, id: \.token) { target in
                HStack(spacing: 8) {
                    markCell(target: target, playerIndex: 0)
                    Text(viewModel.targetDisplay(target))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(isDead(target) ? CricketMatchStyle.closed : CricketMatchStyle.textPrimary)
                        .frame(width: 56)
                    markCell(target: target, playerIndex: 1)
                }
                .padding(.vertical, 3)
            }
        }
        .padding(10)
        .background(CricketMatchStyle.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        // ⚠️ 容器必须声明成 `contain`：否则 identifier 落到子按钮/文本上，
        // `cricketBoard` 自己查不到（V14 这么挂过一次）。
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cricketBoard")
    }

    /// 双方都关了这个靶号 ⇒ 该号位此后再无人能得分（"死号"）。
    private func isDead(_ target: any CricketTarget) -> Bool {
        viewModel.players.indices.allSatisfy { viewModel.isClosed(target: target, playerIndex: $0) }
    }

    private func markCell(target: any CricketTarget, playerIndex: Int) -> some View {
        let isClosed = viewModel.isClosed(target: target, playerIndex: playerIndex)
        let isActive = playerIndex == viewModel.currentIndex
        let text = viewModel.marksDisplay(target: target, playerIndex: playerIndex)
        return Text(text)
            .font(.system(size: 17, weight: .bold))
            .foregroundStyle(isClosed ? CricketMatchStyle.closedText : CricketMatchStyle.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(isClosed ? CricketMatchStyle.closed : CricketMatchStyle.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isActive ? CricketMatchStyle.active : Color.clear, lineWidth: isActive ? 2 : 0)
            )
            .accessibilityIdentifier("cricketMark\(playerIndex)_\(viewModel.targetDisplay(target))")
    }

    /// 多人局回退：目标 × 玩家的矩阵（与 Android 同一格局）。
    private var matrixBoard: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: viewModel.players.count + 1)
        return VStack(spacing: 6) {
            LazyVGrid(columns: columns, spacing: 4) {
                Text("目标")
                    .font(.caption2)
                    .foregroundStyle(CricketMatchStyle.textMuted)
                ForEach(0..<viewModel.players.count, id: \.self) { index in
                    Text(viewModel.playerName(index))
                        .font(.caption2)
                        .foregroundStyle(index == viewModel.currentIndex ? CricketMatchStyle.active : CricketMatchStyle.textMuted)
                        .lineLimit(1)
                }
                ForEach(orderedTargets, id: \.token) { target in
                    Text(viewModel.targetDisplay(target))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(CricketMatchStyle.textPrimary)
                        .frame(height: 34)
                    ForEach(0..<viewModel.players.count, id: \.self) { index in
                        markCell(target: target, playerIndex: index)
                    }
                }
            }
        }
        .padding()
        .background(CricketMatchStyle.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cricketBoard")
    }

    /// 靶号自上而下：20 → 15，Bull 垫底（目标集由引擎给出，这里只定顺序）。
    private var orderedTargets: [any CricketTarget] {
        viewModel.targets.sorted { lhs, rhs in
            let left = (lhs as? CricketTargetNumber)?.value ?? 0
            let right = (rhs as? CricketTargetNumber)?.value ?? 0
            if left == 25 { return false }
            if right == 25 { return true }
            return left > right
        }
    }

    // MARK: - 选手比分卡

    private var scoreCards: some View {
        HStack(spacing: 10) {
            ForEach(0..<viewModel.players.count, id: \.self) { index in
                VStack(spacing: 2) {
                    Text(viewModel.playerName(index))
                        .font(.caption)
                        .foregroundStyle(CricketMatchStyle.textSecondary)
                        .lineLimit(1)
                    Text("\(viewModel.players[index].score)")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundStyle(CricketMatchStyle.textPrimary)
                    Text("胜 \(viewModel.legsWon[index])")
                        .font(.caption2)
                        .foregroundStyle(CricketMatchStyle.textMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(CricketMatchStyle.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                // 当前出手选手：红框（规范指定 #F24747）。
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            index == viewModel.currentIndex ? CricketMatchStyle.active : Color.clear,
                            lineWidth: 2
                        )
                )
            }
        }
        // ⚠️ 同上：不声明成容器，`cricketScores` 查不到（V14 挂过）。
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cricketScores")
    }

    // MARK: - 底部录入区

    private var entryPad: some View {
        VStack(spacing: 0) {
            Divider().background(CricketMatchStyle.divider)
            CricketEntryPadView(
                numbers: viewModel.targetNumbers,
                shooterName: viewModel.playerName(viewModel.currentIndex),
                dartTexts: viewModel.turnDarts.map { viewModel.dartText($0) },
                canInput: !viewModel.isInputLocked && viewModel.turnDarts.count < 3,
                canUndoRound: viewModel.canUndoRound,
                onDart: { viewModel.throwDart($0) },
                onRemoveDart: { viewModel.removeDart(at: $0) },
                onUndoRound: { viewModel.undoLastRound() },
                onSubmit: { viewModel.commitTurn(allowEmpty: true) }
            )
        }
        .background(CricketMatchStyle.surface)
    }

    // MARK: - Leg 胜利弹窗

    @ViewBuilder
    private var resultOverlay: some View {
        if let result = viewModel.legResult {
            resultCard(
                title: "\(viewModel.playerName(result.winnerIndex)) 拿下第 \(result.legNumber) 局",
                subtitle: result.isMatchWin ? "已赢下整场比赛" : "当前比分 \(viewModel.legsScoreText)",
                primaryTitle: result.isMatchWin ? "查看结果" : "继续下一局",
                primaryId: "cricketNextLeg"
            ) {
                if result.isMatchWin {
                    viewModel.continueMatch()
                } else {
                    viewModel.continueMatch()
                    viewModel.startTimer()
                }
            }
        } else if let winner = viewModel.matchWinnerIndex {
            resultCard(
                title: "\(viewModel.playerName(winner)) 赢得比赛",
                subtitle: "比分 \(viewModel.legsScoreText)",
                primaryTitle: "返回",
                primaryId: "cricketResultBack"
            ) { dismiss() }
        }
    }

    private func resultCard(
        title: String,
        subtitle: String,
        primaryTitle: String,
        primaryId: String,
        onPrimary: @escaping () -> Void
    ) -> some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()
            VStack(spacing: 14) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(CricketMatchStyle.textPrimary)
                    .accessibilityIdentifier("cricketLegResult")
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(CricketMatchStyle.textSecondary)
                Button(action: onPrimary) {
                    Text(primaryTitle)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(CricketMatchStyle.closedText)
                        .frame(maxWidth: .infinity)
                        .frame(height: CricketMatchStyle.minTapHeight)
                        .background(CricketMatchStyle.closed)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .accessibilityIdentifier(primaryId)
                Button { dismiss() } label: {
                    Text("退出对局")
                        .font(.caption)
                        .foregroundStyle(CricketMatchStyle.textSecondary)
                }
                .accessibilityIdentifier("cricketResultExit")
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(CricketMatchStyle.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 32)
        }
    }
}
