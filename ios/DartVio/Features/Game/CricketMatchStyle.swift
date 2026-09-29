import SwiftUI

/**
 * Cricket 对局页的配色（移动端规范指定值）。
 *
 * ⚠️ 不并进 `Palette`：规范给的是 `#1A1A1A / #38C172 / #F24747`，
 * 而 `Palette` 是照搬 Android `Color.kt` 的那一套（底 `#121212`、绿 `#5BC236`、红 `#E5484D`）。
 * 两边不同值、不同用途，先各自成立；等设计系统统一，再把这套收进 `Palette` 一次改完。
 *
 * 移动端规范要的是：深色底 + 白字 + **绿=已关闭 / 红=当前出手**这套语义色。
 */
enum CricketMatchStyle {
    static let background = Color(hex: 0x1A1A1A)
    static let surface = Color(hex: 0x242424)
    static let surfaceAlt = Color(hex: 0x2E2E2E)
    static let divider = Color(hex: 0x3A3A3A)

    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.62)
    static let textMuted = Color.white.opacity(0.42)

    /// 已关闭（该靶号累计 3 次命中）。
    static let closed = Color(hex: 0x38C172)
    static let closedText = Color(hex: 0x08240F)
    /// 当前出手选手。
    static let active = Color(hex: 0xF24747)

    /// 移动端最小点击尺寸：键盘键高统一不低于这个数。
    static let minTapHeight: CGFloat = 52
}
