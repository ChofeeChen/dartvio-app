import SwiftUI
import shared

/**
 * 对局页通用视觉件，对应 Android `ui/game/GameCommon.kt` 里的一批 Composable。
 * 文案也从 Android 侧原样搬过来（Android 的 strings.xml 只有 app_name，其余全硬编码在 Composable 里）。
 */

// MARK: - 镖的文本与得分

enum DartText {
    static func label(_ dart: Dart) -> String {
        if dart.number == 0 { return "MISS" }
        if dart.number == 25 { return dart.multiplier == 2 ? "BULL" : "25" }
        switch dart.multiplier {
        case 2: return "D\(dart.number)"
        case 3: return "T\(dart.number)"
        default: return "\(dart.number)"
        }
    }

    static func score(_ dart: Dart) -> Int32 { dart.number * dart.multiplier }

    static func total(_ darts: [Dart]) -> Int32 { darts.reduce(0) { $0 + score($1) } }
}

// MARK: - 顶部栏

struct GameTopBar: View {
    let title: String
    let legText: String
    let onExit: () -> Void

    var body: some View {
        HStack {
            Button(action: onExit) { Image(systemName: "xmark").foregroundStyle(Palette.textSecondary) }
                .accessibilityLabel("退出")
            Spacer()
            VStack(spacing: 2) {
                Text(title).font(.headline).foregroundStyle(Palette.textPrimary)
                Text(legText).font(.caption).foregroundStyle(Palette.textMuted)
            }
            Spacer()
            Color.clear.frame(width: 24, height: 1) // 与左侧按钮对称
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
}

// MARK: - 当前席三角指示器

struct ActiveTriangle: View {
    var body: some View {
        Triangle()
            .fill(Palette.primary)
            .frame(width: 16, height: 10)
    }
}

struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

// MARK: - 玩家卡片

struct X01PlayerCard: View {
    let name: String
    let remaining: Int32
    let isActive: Bool
    let previousRemaining: Int32
    let lastTurnScore: Int32
    let turnScore: Int32
    let legsWon: Int32
    let isAi: Bool

    var body: some View {
        VStack(spacing: 0) {
            ActiveTriangle()
                .opacity(isActive ? 1 : 0)
                .frame(height: 10)
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 4) {
                    Text(name)
                        .font(.subheadline)
                        .foregroundStyle(isActive ? Palette.primary : Palette.textSecondary)
                        .lineLimit(1)
                    if isAi {
                        Text("AI")
                            .font(.caption2)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Palette.secondaryDark)
                            .clipShape(RoundedRectangle(cornerRadius: 3))
                    }
                }
                Text("\(remaining)")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(isActive ? Palette.primary : Palette.textPrimary)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    if lastTurnScore > 0 || previousRemaining > 0 {
                        Text("\(previousRemaining)")
                            .strikethrough()
                            .font(.caption)
                            .foregroundStyle(Palette.textMuted)
                    }
                    if turnScore > 0 {
                        Text("+\(turnScore)")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .overlay(RoundedRectangle(cornerRadius: 3).stroke(Palette.accent, lineWidth: 1))
                            .foregroundStyle(Palette.accent)
                    }
                }
                Text("胜 \(legsWon) 局")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isActive ? Palette.primaryContainer : Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isActive ? Palette.primary : Palette.divider, lineWidth: isActive ? 1.5 : 1)
            )
        }
    }
}

// MARK: - 本回合三镖

struct TurnDartsRow: View {
    let darts: [Dart]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { index in
                DartSlot(text: index < darts.count ? DartText.label(darts[index]) : "")
            }
            Spacer()
            Text("本回合 \(DartText.total(darts))")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)
        }
        .padding(.horizontal)
    }
}

struct DartSlot: View {
    let text: String

    var body: some View {
        Text(text.isEmpty ? "—" : text)
            .font(.system(size: 15, weight: .medium, design: .monospaced))
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .background(Palette.surfaceVariant)
            .foregroundStyle(text.isEmpty ? Palette.textMuted : Palette.textPrimary)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - 状态提示条

struct StatusStrip: View {
    let message: String?

    var body: some View {
        Text(message ?? "")
            .font(.headline.weight(.bold))
            .foregroundStyle(Palette.error)
            .frame(maxWidth: .infinity)
            .frame(height: message == nil ? 0 : 34)
            .background(Palette.error.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .padding(.horizontal)
            .opacity(message == nil ? 0 : 1)
    }
}

// MARK: - 通用设置行

struct SetupRow<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        HStack {
            Text(title).foregroundStyle(Palette.textSecondary)
            Spacer()
            content()
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

struct PrimaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Palette.primary)
                .foregroundStyle(Palette.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}
