import SwiftUI

/**
 * 「创建比赛」弹窗的**浅色卡片**配色。
 *
 * ⚠️ 不并进 `Palette`：那一套是照搬 Android `Color.kt` 的深色主题（两端共用），
 * 而本弹窗按需求是**白底深字 + 绿色选中**，两端还没有对应的浅色板。
 * 先局部定义，等 Android 也做同一套弹窗再一起收进 `Palette`，避免现在就在共用色板里塞一套只有一端在用。
 */
enum CreateMatchStyle {
    static let card = Color(hex: 0xFFFFFF)
    static let title = Color(hex: 0x1B1B1B)
    static let body = Color(hex: 0x4A4A4A)
    static let hint = Color(hex: 0x8A8A8A)
    static let line = Color(hex: 0xE4E4E4)
    static let field = Color(hex: 0xF3F3F3)

    /// 选中 = 绿，未选中 = 深灰（需求指定）。
    static let selected = Palette.success
    static let selectedText = Color(hex: 0x0F2410)
    static let unselected = Color(hex: 0x2A2A2A)
    static let unselectedText = Color.white.opacity(0.72)

    /// 底部深色栏。
    static let bar = Color(hex: 0x1E1E1E)
    static let barText = Color.white
}
