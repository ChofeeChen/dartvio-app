import SwiftUI

/**
 * 【游戏规则配置面板】：由创建比赛弹窗的「游戏设置」唤起，**按当前选中的游戏渲染**。
 *
 * 目前只有 X01 有一套真实面板（4 行横向分段单选）：
 *
 * | 行 | 选项 | 默认 |
 * | --- | --- | --- |
 * | 1 | STRAIGHT IN / DOUBLE IN / MASTER IN | STRAIGHT IN |
 * | 2 | STRAIGHT OUT / DOUBLE OUT / MASTER OUT | DOUBLE OUT |
 * | 3 | 15 / 20 / 50 / 80 轮 | 50 轮 |
 * | 4 | 输入每镖分值 / 输入每轮总分 | 输入每轮总分 |
 *
 * 其余游戏（含 P0 的 STANDARD CRICKET）没有面板，如实显示"尚未开放"，
 * **不复用 X01 的那 4 行** —— 把 X01 的进出镖规则套到 Cricket 上是规则错配，比没有面板更糟。
 *
 * ## 改的是副本，保存才写回
 *
 * 面板在**本地副本**上编辑（`@State` 初值取自草稿），点「保存」才 `onSave` 回草稿 ——
 * 直接改草稿的话，右上角 X 关闭就成了"改了一半还留着"，与"修改后保存"的语义不符。
 */
struct GameRulesPanelView: View {

    let game: GameCatalog.Game

    @State private var inModeRaw: String
    @State private var outModeRaw: String
    @State private var maxRounds: Int
    @State private var scoringInputRaw: String

    private let onSave: (X01RulesPatch) -> Void
    private let onClose: () -> Void

    init(draft: CreateMatchDraft, onSave: @escaping (X01RulesPatch) -> Void, onClose: @escaping () -> Void) {
        self.game = draft.game
        _inModeRaw = State(initialValue: draft.inModeRaw)
        _outModeRaw = State(initialValue: draft.outModeRaw)
        _maxRounds = State(initialValue: draft.maxRounds)
        _scoringInputRaw = State(initialValue: draft.scoringInputRaw)
        self.onSave = onSave
        self.onClose = onClose
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider().background(Palette.divider)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if game.hasRulesPanel {
                        rulesBody
                    } else {
                        notReady
                    }
                }
                .padding(16)
            }
        }
        .background(Palette.background)
        // 「保存」固定底部：设置项再多也不会随内容滑走。
        .safeAreaInset(edge: .bottom) {
            if game.hasRulesPanel {
                Button { save() } label: {
                    Text("保存")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(CreateMatchStyle.selectedText)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(CreateMatchStyle.selected)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Palette.background)
                .accessibilityIdentifier("rulesSave")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("gameRulesPanel")
    }

    private var header: some View {
        ZStack {
            VStack(spacing: 2) {
                Text("游戏设置")
                    .font(.headline)
                    .foregroundStyle(Palette.textPrimary)
                Text(game.title)
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
            }
            HStack {
                Button { onClose() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .frame(width: 30, height: 30)
                        .background(Palette.surfaceVariant)
                        .clipShape(Circle())
                }
                .accessibilityIdentifier("rulesClose")
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - X01 面板

    private var rulesBody: some View {
        VStack(alignment: .leading, spacing: 14) {
            optionRow(
                title: "开镖",
                options: X01RulesOptions.inModes,
                selected: $inModeRaw,
                idPrefix: "rulesIn"
            )
            optionRow(
                title: "结镖",
                options: X01RulesOptions.outModes,
                selected: $outModeRaw,
                idPrefix: "rulesOut"
            )
            VStack(alignment: .leading, spacing: 8) {
                Text("轮数上限")
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
                HStack(spacing: 8) {
                    ForEach(X01RulesOptions.rounds, id: \.self) { rounds in
                        chip(title: "\(rounds) 轮", selected: maxRounds == rounds) {
                            maxRounds = rounds
                        }
                        .accessibilityIdentifier("rulesRounds\(rounds)")
                    }
                }
            }
            optionRow(
                title: "录入口径",
                options: X01RulesOptions.inputs,
                selected: $scoringInputRaw,
                idPrefix: "rulesInput"
            )
        }
    }

    private var notReady: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(game.title) 的规则配置面板尚未开放")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.textPrimary)
            Text("目前只有 X01 有一套规则面板（开镖 / 结镖 / 轮数 / 录入口径）。"
                 + "其他玩法各自的规则项还没定，先把入口留在这里 —— 用 X01 的面板去套别的玩法是规则错配。")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("rulesNotReady")
    }

    // MARK: - 行与键

    private func optionRow(
        title: String,
        options: [X01RulesOptions.Option],
        selected: Binding<String>,
        idPrefix: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
            HStack(spacing: 8) {
                ForEach(options) { option in
                    chip(title: option.title, selected: selected.wrappedValue == option.raw) {
                        selected.wrappedValue = option.raw
                    }
                    .accessibilityIdentifier("\(idPrefix)_\(option.raw)")
                }
            }
        }
    }

    /// 选中绿色、未选中深灰（需求指定）；横向等分，一行放得下就不断行。
    private func chip(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(selected ? CreateMatchStyle.selectedText : CreateMatchStyle.unselectedText)
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(selected ? CreateMatchStyle.selected : CreateMatchStyle.unselected)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private func save() {
        onSave(
            X01RulesPatch(
                inModeRaw: inModeRaw,
                outModeRaw: outModeRaw,
                maxRounds: maxRounds,
                scoringInputRaw: scoringInputRaw
            )
        )
    }
}
