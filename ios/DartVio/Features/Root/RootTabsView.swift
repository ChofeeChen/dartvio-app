import SwiftUI

/**
 * P0：导航壳。
 *
 * ⚠️ 底部 Tab 已移除：Tab 要求入口平级且常驻切换，而「训练中心 / 统计数据」本身是**两级**结构，
 * 塞进 Tab 会让第二级无处安放；而且把 X01 设置页当首页会让 Cricket、联机这些新玩法
 * 没有自己的门（只能作为卡片嵌进 X01 的设置里）。现在统一：首页列入口 → 各自设置页 → 对局页。
 */
struct RootView: View {
    var body: some View {
        NavigationStack {
            HomeView()
        }
        .tint(Palette.primary)
        .background(Palette.background)
    }
}

struct PlaceholderView: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "hammer")
                .font(.largeTitle)
                .foregroundStyle(Palette.textMuted)
            Text(title).font(.headline).foregroundStyle(Palette.textPrimary)
            Text(subtitle)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(Palette.textMuted)
                .padding(.horizontal)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.background)
        .navigationTitle(title)
    }
}
