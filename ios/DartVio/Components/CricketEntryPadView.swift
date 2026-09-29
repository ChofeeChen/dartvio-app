import SwiftUI
import shared

/**
 * Cricket 对局页底部固定录入区（移动端规范）。
 *
 * ```
 * 当前：张三 出手 · 第 2/3 镖
 * [  T20  ][  19  ][  —  ]        ← 3 个镖位，点已录的格子撤回那一镖
 * [20][19][18][17]
 * [16][15][BULL][MISS]
 * [T][D][撤销]
 * [      提交本轮      ]
 * ```
 *
 * ## 为什么不再复用 `CricketKeypadView`
 *
 * 那一套是「S/D/T 倍率行 + BULL25 / BULL50 两个键 + 数字行 + MISS + ⌫ + 整行确认」，
 * 而规范要的是**单个 BULL 键配倍率**、以及「撤销上一**轮**」而不是退一镖。
 * 硬套会把两种撤销挤在同一个位置：玩家点"撤销"时不知道自己撤的是一镖还是一整轮。
 *
 * ## Bull 只有一个键，但口径有两个
 *
 * 外牛眼 = 1 个标记、内牛眼 = 2 个标记（引擎 `CricketRules` 按 `dart.multiplier` 记）。
 * 所以 BULL 键与倍率联动：**S+BULL = BULL 25（1 标记）、D+BULL = BULL 50（2 标记）**；
 * Bull 没有"三倍"，T 状态下按 BULL 一律按内牛眼录入 —— 不吞这一镖，也不假装存在 T-Bull。
 */
struct CricketEntryPadView: View {

    /// 本局的数字目标（Bull 单独成键，不在里面）。
    let numbers: [Int32]
    let shooterName: String
    /// 已录入的镖（最多 3 个），下标即镖序。
    let dartTexts: [String]
    let canInput: Bool
    let canUndoRound: Bool

    let onDart: (Dart) -> Void
    let onRemoveDart: (Int) -> Void
    let onUndoRound: () -> Void
    let onSubmit: () -> Void

    @State private var multiplier: Int32 = 1

    var body: some View {
        VStack(spacing: 8) {
            hintRow
            slotsRow
            ForEach(keyRows.indices, id: \.self) { index in
                HStack(spacing: 8) {
                    ForEach(keyRows[index], id: \.self) { label in
                        key(label)
                    }
                }
            }
            modifierRow
            submitRow
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(CricketMatchStyle.surface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cricketEntryPad")
    }

    // MARK: - 行

    private var hintRow: some View {
        HStack {
            Text("当前：\(shooterName) 出手")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(CricketMatchStyle.active)
                .lineLimit(1)
            Spacer()
            Text("第 \(min(dartTexts.count + 1, 3))/3 镖")
                .font(.caption)
                .foregroundStyle(CricketMatchStyle.textMuted)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cricketTurnHint")
    }

    private var slotsRow: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { index in
                slot(index)
            }
        }
    }

    private var modifierRow: some View {
        HStack(spacing: 8) {
            modifierKey("T", value: 3)
            modifierKey("D", value: 2)
            Button(action: onUndoRound) {
                Text("撤销")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: CricketMatchStyle.minTapHeight)
                    .background(CricketMatchStyle.surfaceAlt)
                    .foregroundStyle(canUndoRound ? CricketMatchStyle.textPrimary : CricketMatchStyle.textMuted)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .disabled(!canUndoRound)
            .accessibilityIdentifier("cricketUndoRound")
        }
    }

    private var submitRow: some View {
        Button(action: onSubmit) {
            Text("提交本轮")
                .font(.system(size: 17, weight: .bold))
                .frame(maxWidth: .infinity)
                .frame(height: CricketMatchStyle.minTapHeight)
                .background(CricketMatchStyle.closed)
                .foregroundStyle(CricketMatchStyle.closedText)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .disabled(!canInput)
        .opacity(canInput ? 1 : 0.45)
        .accessibilityIdentifier("cricketSubmitTurn")
    }

    // MARK: - 键

    private func slot(_ index: Int) -> some View {
        let text = index < dartTexts.count ? dartTexts[index] : "—"
        return Button {
            guard index < dartTexts.count else { return }
            onRemoveDart(index)
        } label: {
            Text(text)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(index < dartTexts.count ? CricketMatchStyle.textPrimary : CricketMatchStyle.textMuted)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(CricketMatchStyle.surfaceAlt)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(CricketMatchStyle.divider, lineWidth: 1)
                )
        }
        .disabled(index >= dartTexts.count)
        .accessibilityIdentifier("cricketDartSlot\(index)")
    }

    private func modifierKey(_ label: String, value: Int32) -> some View {
        let selected = multiplier == value
        return Button {
            multiplier = (multiplier == value) ? 1 : value
        } label: {
            Text(label)
                .font(.system(size: 17, weight: .bold))
                .frame(maxWidth: .infinity)
                .frame(height: CricketMatchStyle.minTapHeight)
                .background(selected ? CricketMatchStyle.closed : CricketMatchStyle.surfaceAlt)
                .foregroundStyle(selected ? CricketMatchStyle.closedText : CricketMatchStyle.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .disabled(!canInput)
        .opacity(canInput ? 1 : 0.45)
        .accessibilityIdentifier("cricketKey\(label)")
    }

    private func key(_ label: String) -> some View {
        Button {
            switch label {
            case "BULL": onDart(dart(number: 25))
            case "MISS": onDart(SharedFactory.miss)
            default: onDart(dart(number: Int32(label) ?? 0))
            }
        } label: {
            Text(label)
                .font(.system(size: label.count > 2 ? 15 : 18, weight: .medium))
                .frame(maxWidth: .infinity)
                .frame(height: CricketMatchStyle.minTapHeight)
                .background(CricketMatchStyle.surfaceAlt)
                .foregroundStyle(CricketMatchStyle.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .disabled(!canInput)
        .opacity(canInput ? 1 : 0.45)
        .accessibilityIdentifier("cricketKey\(label)")
    }

    private func dart(number: Int32) -> Dart {
        // Bull 没有三倍：T 状态下按 BULL 落在内牛眼（2 标记），而不是造出一支不存在的镖。
        let effective = (number == 25 && multiplier == 3) ? 2 : multiplier
        return SharedFactory.dart(number: number, multiplier: effective)
    }

    // MARK: - 排布

    /// 数字键 + BULL + MISS 按 4 个一行切分（标准局正好是 20 19 18 17 / 16 15 BULL MISS）。
    private var keyRows: [[String]] {
        var labels = numbers.map(String.init)
        labels.append("BULL")
        labels.append("MISS")
        var rows: [[String]] = []
        for index in stride(from: 0, to: labels.count, by: 4) {
            rows.append(Array(labels[index..<min(index + 4, labels.count)]))
        }
        return rows
    }
}
