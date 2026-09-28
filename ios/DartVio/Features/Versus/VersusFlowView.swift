import SwiftUI
import shared

/**
 * 双人对抗训练：模式列表 → 配置 → 对局 → 战报。
 *
 * 对齐 Android `practice/versus/{VersusListScreen, VersusSetupScreen, VersusBattleScreen, VersusReportScreen}`：
 * 四条链路一条不少，但**六个模式共用这一套页面**（引擎侧 `VersusRule` 接口统一，
 * 见 `VersusViewModel` 的注释）。
 */

// MARK: - 模式列表

struct VersusListView: View {

    /// 直接来自 shared 的注册表，不在 iOS 侧再抄一份清单 —— 加第 7 个模式时两端都不会漏。
    private var modes: [VersusModeInfo] { VersusModes.shared.ALL }

    var body: some View {
        List {
            ForEach(modes.indices, id: \.self) { index in
                let mode = modes[index]
                if mode.available {
                    NavigationLink(value: mode.modeKey) {
                        card(mode)
                    }
                } else {
                    card(mode).opacity(0.45).allowsHitTesting(false)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Palette.background)
        .navigationTitle("双人对抗训练")
        .navigationDestination(for: String.self) { modeKey in
            VersusSetupView(modeKey: modeKey)
        }
    }

    private func card(_ mode: VersusModeInfo) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(mode.title)
                    .font(.headline)
                    .foregroundStyle(Palette.textPrimary)
                Text(mode.desc)
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
            }
            Spacer()
            Text(mode.available ? "" : "不可用")
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
        }
        .padding(.vertical, 4)
        .accessibilityIdentifier("versusItem\(mode.modeKey)")
    }
}

// MARK: - 配置

struct VersusSetupView: View {

    let modeKey: String

    @State private var playerA = "选手 1"
    @State private var playerB = "选手 2"
    @State private var targetScore: Int32 = 20

    private var info: VersusModeInfo? { VersusModes.shared.infoOf(modeKey: modeKey) }
    private var rule: VersusRule? { VersusModes.shared.ruleOf(modeKey: modeKey) }

    /// 只有「先达 N 分」类模式才需要选目标分；环游类由步数决定胜负。
    private var isScoredMode: Bool {
        modeKey == VersusModes.shared.BULL_BATTLE || modeKey == VersusModes.shared.RING_RACE
    }

    var body: some View {
        Form {
            Section("选手") {
                TextField("选手 1", text: $playerA).accessibilityIdentifier("versusPlayerA")
                TextField("选手 2", text: $playerB).accessibilityIdentifier("versusPlayerB")
            }
            if isScoredMode {
                Section("目标分") {
                    Picker("目标分", selection: $targetScore) {
                        ForEach([Int32(10), 20, 30, 50], id: \.self) { score in
                            Text("\(score) 分").tag(score)
                        }
                    }
                }
            }
            Section("规则") {
                ForEach(info?.rulesSummary ?? [], id: \.self) { line in
                    Text(line).font(.caption).foregroundStyle(Palette.textSecondary)
                }
            }
            Section {
                NavigationLink(value: true) {
                    Text("开局")
                        .font(.headline)
                        .foregroundStyle(Palette.primary)
                }
                .accessibilityIdentifier("versusStart")
            }
        }
        .scrollContentBackground(.hidden)
        .background(Palette.background)
        .navigationTitle(info?.title ?? "双人对抗")
        .navigationDestination(for: Bool.self) { _ in
            if let rule {
                let config = SharedFactory.versusConfig(
                    modeKey: modeKey,
                    base: rule.defaultConfig(),
                    targetScore: isScoredMode ? targetScore : 0
                )
                VersusBattleView(
                    viewModel: VersusViewModel(
                        modeKey: modeKey,
                        playerNames: [playerA, playerB],
                        config: config
                    )
                )
            }
        }
    }
}

// MARK: - 对局

struct VersusBattleView: View {

    let viewModel: VersusViewModel

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 12) {
                    Text(viewModel.caption)
                        .font(.headline)
                        .foregroundStyle(Palette.primary)
                        .accessibilityIdentifier("versusCaption")
                    Text("第 \(viewModel.state.roundNo) 轮 · 轮到 \(viewModel.currentPlayerName)")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)

                    ForEach(viewModel.state.players.indices, id: \.self) { index in
                        let player = viewModel.state.players[index]
                        HStack {
                            Text(player.name).foregroundStyle(Palette.textPrimary)
                            Spacer()
                            Text(viewModel.progressText(index))
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundStyle(
                                    index == Int(viewModel.state.currentPlayerIndex) ? Palette.primary : Palette.textSecondary
                                )
                        }
                        .font(.subheadline)
                        .padding()
                        .background(Palette.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    HStack(spacing: 8) {
                        ForEach(0..<3, id: \.self) { index in
                            let hits = viewModel.state.dartsInRound
                            DartSlot(text: index < hits.count ? hits[index].label() : "")
                        }
                    }

                    if let message = viewModel.lastEventText {
                        Text(message)
                            .font(.headline)
                            .foregroundStyle(Palette.accent)
                            .accessibilityIdentifier("versusEvent")
                    }

                    if viewModel.isRoundComplete {
                        Button {
                            viewModel.endRound()
                        } label: {
                            Text("结算本轮")
                                .font(.headline)
                                .foregroundStyle(Palette.onPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(Palette.primary)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .accessibilityIdentifier("versusEndRound")
                    }

                    if viewModel.state.finished {
                        NavigationLink(value: true) {
                            Text("查看战报")
                                .font(.headline)
                                .foregroundStyle(Palette.primary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.primary, lineWidth: 1))
                        }
                        .accessibilityIdentifier("versusReport")
                    }
                }
                .padding()
            }
            KeypadView(
                confirmTitle: "记一镖",
                onDart: { viewModel.record(BoardHit.companion.of(dart: $0)) },
                onUndo: { },
                onConfirm: { }
            )
        }
        .background(Palette.background)
        .navigationTitle(viewModel.info.title)
        .navigationDestination(for: Bool.self) { _ in
            VersusReportView(viewModel: viewModel)
        }
    }
}

// MARK: - 战报

struct VersusReportView: View {

    let viewModel: VersusViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(spacing: 6) {
                    Text(viewModel.winnerText.map { "\($0) 获胜" } ?? "本局中止")
                        .font(.headline)
                        .foregroundStyle(Palette.primary)
                        .accessibilityIdentifier("versusWinner")
                    Text("\(viewModel.endReasonText) · 第 \(viewModel.state.roundNo) 轮 · 共 \(viewModel.state.totalDarts) 镖")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                ForEach(viewModel.state.players.indices, id: \.self) { index in
                    let player = viewModel.state.players[index]
                    VStack(alignment: .leading, spacing: 6) {
                        Text(player.name).font(.headline).foregroundStyle(Palette.textPrimary)
                        Text("最终进度：\(viewModel.progressText(index))")
                            .font(.subheadline)
                            .foregroundStyle(Palette.textSecondary)
                        Text("回合：\(player.history.count)")
                            .font(.caption)
                            .foregroundStyle(Palette.textMuted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(Palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("\(viewModel.info.title) · 战报")
    }
}
