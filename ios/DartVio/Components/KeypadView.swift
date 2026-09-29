import SwiftUI
import shared

/**
 * 数字键盘（对齐 Android `components/Keypad.kt` → `DartKeypad`，X01 与练习共用）。
 *
 * 录入语义（MVP 版，比 Android 更直白，避免"输到一半自动提交"的歧义）：
 * - 数字键累积成 buffer（最多两位、且必须是 1...20 的扇区），配 S/D/T 倍率，按「确认」投出
 * - BULL25 / BULL50 / MISS 是**立即投出**，不进 buffer
 * - buffer 为空时按 ⌫ = 撤销上一镖；buffer 非空时 = 退格
 * - buffer 为空时按「确认」= 交给宿主决定（X01：结束回合）；宿主不需要该语义时传 `nil`，按钮自动禁用
 *
 * ## `layout` 是什么
 *
 * 双人对抗的六个模式各自允许点的键**不一样**（`VersusRule.inputFilter` 按当前状态给出）：
 * Bull 之争只有牛眼、环游三镖只有当前目标分区、减半挑战的「任意双倍」轮只认双倍环。
 * 通用键盘若不加约束，就会留下一排点了必然记 0 分的死键 —— 引擎注释里把这件事
 * 称作「这套 UI 最不能有的东西」。所以约束**由引擎给、键盘只照着画**。
 *
 * `layout == nil` = 不限制（X01 / 随意记分的练习）。
 */
struct KeypadView: View {
    let confirmTitle: String
    let onDart: (Dart) -> Void
    let onUndo: () -> Void
    let onConfirm: (() -> Void)?
    let layout: KeyboardLayout?

    init(
        confirmTitle: String,
        onDart: @escaping (Dart) -> Void,
        onUndo: @escaping () -> Void = {},
        onConfirm: (() -> Void)? = nil,
        layout: KeyboardLayout? = nil
    ) {
        self.confirmTitle = confirmTitle
        self.onDart = onDart
        self.onUndo = onUndo
        self.onConfirm = onConfirm
        self.layout = layout
    }

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
            /**
             * 输入行**只在有内容时占位**：buffer 为空时这一行只有一个"——"，
             * 是一整条 28pt 的空白。有内容才渲染，键盘区就不再有这块空地。
             */
            if !buffer.isEmpty || !legHint.isEmpty {
                HStack(spacing: 12) {
                    Text(buffer.isEmpty ? "—" : "\(prefix)\(buffer)")
                        .font(.system(size: 22, weight: .semibold, design: .monospaced))
                        .foregroundStyle(Palette.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(legHint)
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .frame(height: 28)
            }

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
                        .background(confirmEnabled ? Palette.primary : Palette.surfaceVariant)
                        .foregroundStyle(confirmEnabled ? Palette.onPrimary : Palette.textMuted)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .disabled(!confirmEnabled)
            }
        }
        // 模式收紧了可用环带时（例如「任意双倍」轮只认 D），把当前倍率落到一个还能用的值上，
        // 否则键盘会停在 T 上、玩家投出去的每一镖都被记成 0 分。
        .onChange(of: layoutIdentity) { _, _ in
            if !ringAllowed(multiplier), let fallback = firstAllowedRing {
                multiplier = fallback
            }
            buffer = ""
        }
    }

    private var prefix: String {
        switch multiplier {
        case 2: return "D"
        case 3: return "T"
        default: return "S"
        }
    }

    /// 约束发生变化时要重置输入，所以需要一个可比较的身份值（`KeyboardLayout` 是 Kotlin data class，
    /// 每次 `inputFilter` 返回新实例，不能直接拿来当 onChange 的值）。
    private var layoutIdentity: String {
        guard let layout else { return "any" }
        return "\(layout.sectors.map { $0.intValue })-\(layout.rings.map { $0.name })-\(layout.bull)-\(layout.miss)"
    }

    private var legHint: String { "" }

    /// 「确认」是否可点：数字没输完时不让点；数字为空时只有宿主确实接了 `onConfirm` 才可点。
    private var confirmEnabled: Bool {
        if let value = Int(buffer), value >= 1, value <= 20 {
            return sectorAllowed(Int32(value)) && ringAllowed(multiplier)
        }
        return buffer.isEmpty && onConfirm != nil
    }

    // MARK: - 引擎约束

    private func sectorAllowed(_ sector: Int32) -> Bool {
        guard let layout else { return true }
        let allowed = layout.sectors
        if allowed.isEmpty { return false }
        return allowed.contains { $0.intValue == sector }
    }

    private func ringAllowed(_ raw: Int32) -> Bool {
        guard let layout else { return true }
        let rings = layout.rings
        if rings.isEmpty { return false }
        return rings.contains { ring in
            switch raw {
            case 2: return ring.isEqual(Ring.double_)
            case 3: return ring.isEqual(Ring.triple)
            default: return ring.isEqual(Ring.single)
            }
        }
    }

    /// 当前约束下第一个可用的倍率（`onChange` 的回落值用）。
    private var firstAllowedRing: Int32? {
        for candidate: Int32 in [1, 2, 3] where ringAllowed(candidate) { return candidate }
        return nil
    }

    private func digitEnabled(_ digit: Int) -> Bool {
        guard let layout else { return true }
        // 逐位判断：允许的扇区是 18 时，1 与 8 都得能按，但 3 不能按。
        // 最终值是否合法仍在 `confirm()` 里再判一次（"19" 这种组合在这里拦不住）。
        return layout.sectors.contains { sector in
            String(sector.intValue).contains("\(digit)")
        }
    }

    // MARK: - 键面

    @ViewBuilder
    private func padButton(_ key: PadKey) -> some View {
        Button(action: { tap(key) }) {
            Text(label(for: key))
                .font(.system(size: 18, weight: .medium))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(background(for: key))
                .foregroundStyle(foreground(for: key))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                // 死键直接置灰不可点，而不是点了记 0 分让玩家去猜为什么没分。
                .opacity(enabled(key) ? 1 : 0.35)
        }
        .disabled(!enabled(key))
    }

    private func enabled(_ key: PadKey) -> Bool {
        switch key {
        case .digit(let d): return digitEnabled(d)
        case .multiplier(let m): return ringAllowed(m)
        case .bull25, .bull50: return layout?.bull ?? true
        case .miss: return layout?.miss ?? true
        case .backspace: return true
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

    private func foreground(for key: PadKey) -> Color {
        enabled(key) ? Palette.textPrimary : Palette.textMuted
    }

    // MARK: - 交互

    private func tap(_ key: PadKey) {
        switch key {
        case .digit(let d):
            // 单独一个 0 不是合法扇区（扇区是 1...20），放它进 buffer 会让「确认」变成死区。
            let next = buffer + "\(d)"
            guard let value = Int(next), next.count <= 2, value >= 1, value <= 20 else { return }
            buffer = next
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
        if let value = Int(buffer), value >= 1, value <= 20,
           sectorAllowed(Int32(value)), ringAllowed(multiplier) {
            onDart(SharedFactory.dart(number: Int32(value), multiplier: multiplier))
            clear()
        } else if buffer.isEmpty {
            onConfirm?()
        }
    }

    private func clear() {
        buffer = ""
        multiplier = 1
    }
}
