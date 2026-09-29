import SwiftUI
import shared

/**
 * Cricket 专属键盘，对齐 Android `ui/components/Keypad.kt` 的 `CricketKeypad`。
 *
 * ## 为什么不能复用 X01 的 `KeypadView`
 *
 * 两件事不一样，混用必然出错：
 *
 * 1. **可用扇区不同**：Cricket 只打 `config.cricketTargets`（标准局是 15–20 + Bull），
 *    而 X01 键盘是 0–9 数字 buffer + 20 扇区任意组合 —— 用它录 Cricket，
 *    会留下一排"点了也记不到凭证"的键（例如 1 / 5 / 13），这正是测试里发现的引用错误；
 * 2. **牛眼口径不同**：Cricket 用 **BULL 25 / BULL 50**（`CricketTargetNumber.value == 25`
 *    配倍率 1 / 2），不是 X01 键盘那种"数字 + S/D/T 拼出来"的录入方式。
 *
 * ## 排布（与 Android 一致）
 *
 * - R1：S / D / T 倍率 + BULL25 + BULL50
 * - R2：高分数字（按目标集降序，标准局是 20 19 18 17）
 * - R3：剩余数字 + MISS + ⌫
 * - R4：确认（整行）—— 满 3 镖后唯一还能交出回合的键
 *
 * ⚠️ 与 X01 键盘同一条口径：**满 3 镖后不再接受加镖**，但退格与确认仍然可点 ——
 * 第 3 镖录错要能退回来改，录满后也必须有键能把回合交出去。
 */
struct CricketKeypadView: View {

    /// 本局的目标分区（Bull 由固定的 BULL 键承担，不进数字键）。
    let targets: [any CricketTarget]
    let canAddDart: Bool
    let confirmTitle: String
    let turnDartsCount: Int
    let onDart: (Dart) -> Void
    let onUndo: () -> Void
    let onConfirm: () -> Void

    @State private var multiplier: Int32 = 1

    var body: some View {
        VStack(spacing: 8) {
            multiplierRow
            ForEach(numberRows.indices, id: \.self) { index in
                HStack(spacing: 8) {
                    ForEach(numberRows[index], id: \.self) { number in
                        key("\(number)") { onDart(SharedFactory.dart(number: number, multiplier: multiplier)) }
                    }
                }
            }
            lastRow
            confirmRow
        }
        .accessibilityIdentifier("cricketKeypad")
    }

    // MARK: - 行

    private var multiplierRow: some View {
        HStack(spacing: 8) {
            multiplierKey("S", value: 1)
            multiplierKey("D", value: 2)
            multiplierKey("T", value: 3)
            key("BULL 25", identifier: "cricketKeyBull25") { onDart(SharedFactory.bull25) }
            key("BULL 50", identifier: "cricketKeyBull50") { onDart(SharedFactory.bull50) }
        }
    }

    private var lastRow: some View {
        // 剩余数字不足 2 个时（例如随机目标变体只给 3 个分区），MISS 与退格自动补位，
        // 这样每一行都是 4 键，不会突然空出半行。
        HStack(spacing: 8) {
            ForEach(tailNumbers, id: \.self) { number in
                key("\(number)") { onDart(SharedFactory.dart(number: number, multiplier: multiplier)) }
            }
            key("MISS", identifier: "cricketKeyMiss") { onDart(SharedFactory.miss) }
            key("⌫", identifier: "cricketKeyBackspace", disabled: !canAddDart && turnDartsCount == 0) { onUndo() }
        }
    }

    private var confirmRow: some View {
        Button(action: onConfirm) {
            Text(confirmTitle)
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Palette.primary)
                .foregroundStyle(Palette.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .accessibilityIdentifier("cricketConfirm")
    }

    // MARK: - 键

    private func multiplierKey(_ label: String, value: Int32) -> some View {
        let selected = multiplier == value
        return Button {
            multiplier = value
        } label: {
            Text(label)
                .font(.system(size: 18, weight: .medium))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(selected ? Palette.primary : Palette.surfaceVariant)
                .foregroundStyle(selected ? Palette.onPrimary : Palette.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .disabled(!canAddDart)
        .opacity(canAddDart ? 1 : 0.35)
        .accessibilityIdentifier("cricketKey\(label)")
    }

    private func key(
        _ label: String,
        identifier: String? = nil,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: label.count > 2 ? 14 : 18, weight: .medium))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Palette.surfaceVariant)
                .foregroundStyle(Palette.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .disabled(disabled || !canAddDart)
        .opacity(disabled || !canAddDart ? 0.35 : 1)
        .accessibilityIdentifier(identifier ?? "cricketKey\(label)")
    }

    // MARK: - 数字键

    /**
     * 数字键**由目标集推导**，不写死 15–20：
     * 二期 tactics 的 9/12 分区、random 的随机目标集，只要 `config.cricketTargets` 变了，
     * 键盘就跟着变 —— 写死一份会让新变体的分区在键盘上找不到键。
     */
    private var numbers: [Int32] {
        targets.compactMap { ($0 as? CricketTargetNumber)?.value }
            .filter { $0 != 25 }
            .sorted(by: >)
    }

    private var numberRows: [[Int32]] {
        let head = Array(numbers.prefix(4))
        return head.isEmpty ? [] : [head]
    }

    private var tailNumbers: [Int32] { Array(numbers.dropFirst(4)) }
}
