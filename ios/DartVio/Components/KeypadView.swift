import SwiftUI
import shared

/**
 * 数字键盘（对齐 Android `components/Keypad.kt` → `DartKeypad`，X01 与练习共用）。
 *
 * 录入语义（MVP 版，比 Android 更直白，避免"输到一半自动提交"的歧义）：
 * - 数字键累积成 buffer（最多两位、且不得大于 20），配 S/D/T 倍率，按「确认」投出
 * - BULL25 / BULL50 / MISS 是**立即投出**，不进 buffer
 * - buffer 为空时按 ⌫ = 撤销上一镖；buffer 非空时 = 退格
 * - buffer 为空时按「确认」= 交给宿主决定（X01：结束回合；Count Up：本轮 BUST）
 */
struct KeypadView: View {
    let confirmTitle: String
    let onDart: (Dart) -> Void
    let onUndo: () -> Void
    let onConfirm: () -> Void

    @State private var buffer = ""
    @State private var multiplier: Int32 = 1

    private enum PadKey: Hashable {
        case digit(Int)
        case multiplier(Int32)
        case bull25, bull50, miss, backspace
    }

    private let rows: [[PadKey]] = [
        [.multiplier(1), .multiplier(2), .multiplier(3), .backspace],
        [.digit(7), .digit(8), .digit(9), .bull25],
        [.digit(4), .digit(5), .digit(6), .bull50],
        [.digit(1), .digit(2), .digit(3), .miss],
    ]

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                Text(buffer.isEmpty ? "—" : "\(prefix)\(buffer)")
                    .font(.system(size: 22, weight: .semibold, design: .monospaced))
                    .foregroundStyle(Palette.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("\(legHint)")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
            }
            .frame(height: 28)

            ForEach(rows.indices, id: \.self) { rowIndex in
                HStack(spacing: 8) {
                    ForEach(rows[rowIndex].indices, id: \.self) { columnIndex in
                        padButton(rows[rowIndex][columnIndex])
                    }
                }
            }

            HStack(spacing: 8) {
                padButton(.digit(0))
                Button(action: confirm) {
                    Text(confirmTitle)
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Palette.primary)
                        .foregroundStyle(Palette.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
    }

    private var prefix: String {
        switch multiplier {
        case 2: return "D"
        case 3: return "T"
        default: return "S"
        }
    }

    private var legHint: String { "" }

    @ViewBuilder
    private func padButton(_ key: PadKey) -> some View {
        Button(action: { tap(key) }) {
            Text(label(for: key))
                .font(.system(size: 18, weight: .medium))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(background(for: key))
                .foregroundStyle(Palette.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    private func label(for key: PadKey) -> String {
        switch key {
        case .digit(let d): return "\(d)"
        case .multiplier(let m): return m == 1 ? "S" : (m == 2 ? "D" : "T")
        case .bull25: return "25"
        case .bull50: return "BULL"
        case .miss: return "MISS"
        case .backspace: return "⌫"
        }
    }

    private func background(for key: PadKey) -> Color {
        if case .multiplier(let m) = key { return m == multiplier ? Palette.secondaryDark : Palette.surfaceVariant }
        if case .digit = key { return Palette.surfaceElevated }
        return Palette.surfaceVariant
    }

    private func tap(_ key: PadKey) {
        switch key {
        case .digit(let d):
            let next = buffer + "\(d)"
            if let value = Int(next), value <= 20, next.count <= 2 { buffer = next }
        case .multiplier(let m):
            multiplier = m
        case .bull25:
            onDart(SharedFactory.bull25); clear()
        case .bull50:
            onDart(SharedFactory.bull50); clear()
        case .miss:
            onDart(SharedFactory.miss); clear()
        case .backspace:
            if buffer.isEmpty { onUndo() } else { buffer.removeLast() }
        }
    }

    private func confirm() {
        if let value = Int(buffer), value >= 1, value <= 20 {
            onDart(SharedFactory.dart(number: Int32(value), multiplier: multiplier))
            clear()
        } else if buffer.isEmpty {
            onConfirm()
        }
    }

    private func clear() {
        buffer = ""
        multiplier = 1
    }
}
