import SwiftUI

/**
 * 【创建比赛】模态弹窗：白卡 + 模糊遮罩。
 *
 * ## 结构（自上而下）
 *
 * ```
 * （遮罩之上）如果 5 分钟内无选手加入或单方退出超过 5 分钟，比赛将自动失效。
 * ┌ 白卡 ──────────────────────────────┐
 * │ ✕              创建比赛            │
 * │ [− 501 +]        [X01 ⌄] [⚙]       │   ① 分数步进器 + 游戏选择 + 游戏设置
 * │ STRAIGHT IN · DOUBLE OUT · 50 轮…  │   规则摘要（面板保存后这一行更新）
 * │ [− 2 位选手 +]                     │   ② 人数步进器
 * │ 参赛条件  [本期不开发]              │   ③ 整组禁用（预留）
 * │ ┌ 深色栏 ───────────────────────┐  │
 * │ │ FIRST TO 2 LEGS ⌃⌄   创建比赛 │  │   ④ 赛制 + 主按钮
 * │ └───────────────────────────────┘  │
 * └────────────────────────────────────┘
 * ```
 *
 * ## 不能创建的两种情况都由 `blocker` 统一给出原因
 *
 * 玩法未开放 / 人数超限来自草稿，后端未配置 / 未同意出境来自大厅，
 * 两处汇成一条文案显示在卡里 —— 灰着的按钮必须配一句"为什么"，否则用户只能猜。
 */
struct CreateMatchModalView: View {

    @Bindable var draft: CreateMatchDraft

    /// 不能创建的原因（nil = 可创建）；由大厅汇总草稿与后端状态后传入。
    let blocker: String?
    let errorMessage: String?
    let isCreating: Bool
    let onClose: () -> Void
    /// 打开【游戏模式网格页】。
    let onPickGame: () -> Void
    /// 打开选中玩法对应的【游戏规则配置面板】。
    let onOpenRules: () -> Void
    let onCreate: () -> Void

    var body: some View {
        ZStack {
            scrim
            VStack(spacing: 14) {
                expireHint
                card
            }
            .padding(.horizontal, 20)
        }
        // 弹窗背景本身透明，模糊由下面这层自己负责（否则 material 采到的是系统底色，看不出模糊）。
        .presentationBackground(.clear)
    }

    // MARK: - 遮罩与提示

    private var scrim: some View {
        Rectangle()
            .fill(.ultraThinMaterial)
            .overlay(Color.black.opacity(0.45))
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture { onClose() }
    }

    /// 弹窗**上方**的静态说明（失效规则要先说清，不能等房间真失效了才告知）。
    private var expireHint: some View {
        Text("如果5分钟内无选手加入或单方退出超过5分钟，比赛将自动失效。")
            .font(.caption)
            .foregroundStyle(.white.opacity(0.92))
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("createMatchExpireHint")
    }

    // MARK: - 卡片

    private var card: some View {
        VStack(spacing: 12) {
            header
            scoreRow
            rulesLine
            playersRow
            conditionsBlock
            if let blocker {
                Text(blocker)
                    .font(.caption2)
                    .foregroundStyle(Palette.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("createMatchBlocker")
            }
            if let errorMessage {
                Text(errorMessage)
                    .font(.caption2)
                    .foregroundStyle(Palette.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("createMatchError")
            }
            bottomBar
        }
        .padding(16)
        .background(CreateMatchStyle.card)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("createMatchCard")
    }

    private var header: some View {
        ZStack {
            Text("创建比赛")
                .font(.title3.weight(.bold))
                .foregroundStyle(CreateMatchStyle.title)
            HStack {
                Button { onClose() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(CreateMatchStyle.body)
                        .frame(width: 30, height: 30)
                        .background(CreateMatchStyle.field)
                        .clipShape(Circle())
                }
                .accessibilityIdentifier("createMatchClose")
                Spacer()
            }
        }
    }

    // MARK: - ① 分数 + 游戏

    private var scoreRow: some View {
        HStack(spacing: 8) {
            stepper(
                text: "\(draft.targetScore)",
                valueId: "createMatchScoreValue",
                minusId: "createMatchScoreMinus",
                plusId: "createMatchScorePlus",
                canMinus: draft.targetScore > CreateMatchDraft.scoreRange.lowerBound,
                canPlus: draft.targetScore < CreateMatchDraft.scoreRange.upperBound,
                onMinus: { draft.targetScore -= 1 },
                onPlus: { draft.targetScore += 1 }
            )
            Spacer(minLength: 2)
            gameChip
            Button { onOpenRules() } label: {
                Image(systemName: "gearshape")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CreateMatchStyle.title)
                    .frame(width: 34, height: 34)
                    .background(CreateMatchStyle.field)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityIdentifier("createMatchGameSettings")
        }
    }

    private var gameChip: some View {
        Button { onPickGame() } label: {
            HStack(spacing: 4) {
                Text(draft.game.title)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .bold))
            }
            .foregroundStyle(CreateMatchStyle.title)
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .background(CreateMatchStyle.field)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .accessibilityIdentifier("createMatchGameSelect")
    }

    private var rulesLine: some View {
        Text(draft.rulesSummary)
            .font(.caption2)
            .foregroundStyle(CreateMatchStyle.hint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("createMatchRulesSummary")
    }

    // MARK: - ② 人数

    private var playersRow: some View {
        HStack(spacing: 10) {
            Text("选手")
                .font(.caption)
                .foregroundStyle(CreateMatchStyle.body)
            stepper(
                text: "\(draft.playerCount) 位选手",
                valueId: "createMatchPlayersValue",
                minusId: "createMatchPlayersMinus",
                plusId: "createMatchPlayersPlus",
                canMinus: draft.playerCount > CreateMatchDraft.seatsRange.lowerBound,
                canPlus: draft.playerCount < CreateMatchDraft.seatsRange.upperBound,
                onMinus: { draft.playerCount -= 1 },
                onPlus: { draft.playerCount += 1 }
            )
            Spacer()
        }
    }

    // MARK: - ③ 参赛条件（本期不开发）

    private var conditionsBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text("参赛条件")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(CreateMatchStyle.title)
                Text("本期不开发")
                    .font(.caption2)
                    .foregroundStyle(CreateMatchStyle.hint)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(CreateMatchStyle.field)
                    .clipShape(Capsule())
            }
            conditionRow("仅限好友", id: "entryConditionFriends")
            conditionRow("需要密码", id: "entryConditionPassword")
            conditionRow("最低 PPR 门槛", id: "entryConditionPpr")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(CreateMatchStyle.field.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        // 整组禁用：字段与 UI 先留着，但不给出"能点却没反应"的假象。
        .disabled(true)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("createMatchConditions")
    }

    private func conditionRow(_ title: String, id: String) -> some View {
        Toggle(isOn: .constant(false)) {
            Text(title)
                .font(.caption)
                .foregroundStyle(CreateMatchStyle.body)
        }
        .tint(CreateMatchStyle.selected)
        .accessibilityIdentifier(id)
    }

    // MARK: - ④ 底部深色栏

    private var bottomBar: some View {
        HStack(spacing: 10) {
            Menu {
                ForEach(CreateMatchDraft.legOptions, id: \.self) { legs in
                    Button {
                        draft.legsToWin = legs
                    } label: {
                        Text("FIRST TO \(legs) LEG\(legs > 1 ? "S" : "")")
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(draft.formatTitle)
                        .font(.caption.weight(.semibold))
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 9, weight: .bold))
                }
                .foregroundStyle(CreateMatchStyle.barText)
                .padding(.horizontal, 10)
                .padding(.vertical, 9)
                .background(Color.white.opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityIdentifier("createMatchFormat")

            Spacer()

            Button { onCreate() } label: {
                Text(isCreating ? "创建中…" : "创建比赛")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(blocker == nil ? CreateMatchStyle.selectedText : Color.white.opacity(0.55))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(blocker == nil ? CreateMatchStyle.selected : Color(hex: 0x3A3A3A))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .disabled(blocker != nil || isCreating)
            .accessibilityIdentifier("createMatchSubmit")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(CreateMatchStyle.bar)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - 步进器

    private func stepper(
        text: String,
        valueId: String,
        minusId: String,
        plusId: String,
        canMinus: Bool,
        canPlus: Bool,
        onMinus: @escaping () -> Void,
        onPlus: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 2) {
            Button(action: onMinus) {
                Image(systemName: "minus")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(CreateMatchStyle.title)
                    .frame(width: 40, height: 34)
            }
            .disabled(!canMinus)
            .accessibilityIdentifier(minusId)

            Text(text)
                .font(.system(size: 16, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(CreateMatchStyle.title)
                .frame(minWidth: 76)
                .accessibilityIdentifier(valueId)

            Button(action: onPlus) {
                Image(systemName: "plus")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(CreateMatchStyle.title)
                    .frame(width: 40, height: 34)
            }
            .disabled(!canPlus)
            .accessibilityIdentifier(plusId)
        }
        .background(CreateMatchStyle.field)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
