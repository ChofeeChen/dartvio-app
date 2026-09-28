import SwiftUI

/**
 * 色板：从 Android `ui/theme/Color.kt` 原样搬过来，两端不另起一套颜色。
 * MVP 只做深色主题（与 Android 现状一致），浅色三色先留在文件里备用。
 */
enum Palette {
    // 主色（品牌橙）
    static let primary = Color(hex: 0xEE9756)
    static let primaryDark = Color(hex: 0xC97840)
    static let primaryLight = Color(hex: 0xF6C29A)
    static let primaryContainer = Color(hex: 0x4A2E17)
    static let onPrimary = Color(hex: 0x1A1108)

    // 副色（青）与强调（金）
    static let secondary = Color(hex: 0x4EC9C4)
    static let secondaryDark = Color(hex: 0x2E9A96)
    static let accent = Color(hex: 0xE8C468)
    static let onAccent = Color(hex: 0x241B04)

    // 深色底（MVP 用的主题）
    static let background = Color(hex: 0x121212)
    static let surface = Color(hex: 0x1E1E1E)
    static let surfaceVariant = Color(hex: 0x2A2A2A)
    static let surfaceElevated = Color(hex: 0x333333)
    static let divider = Color(hex: 0x3D3D3D)

    // 浅色底（备用）
    static let backgroundLight = Color(hex: 0xF7F5F2)
    static let surfaceLight = Color(hex: 0xFFFFFF)
    static let surfaceVariantLight = Color(hex: 0xEFEBE6)

    // 语义色
    static let success = Color(hex: 0x5BC236)
    static let warning = Color(hex: 0xF5A623)
    static let error = Color(hex: 0xE5484D)
    static let info = Color(hex: 0x5B9BD5)

    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.65)
    static let textMuted = Color.white.opacity(0.45)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

/**
 * 主得分字号：同一排所有席位取最长位数统一，避免同排出现两种字号（对齐 Android `x01ScoreSize`）。
 */
enum ScoreTypography {
    static func size(cardWidth: CGFloat, digits: Int) -> CGFloat {
        let perDigit = cardWidth / CGFloat(max(digits, 2))
        return min(max(perDigit * 0.62, 28), 56)
    }
}
