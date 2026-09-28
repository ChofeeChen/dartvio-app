import SwiftUI

/// P1：首页 / 导航壳。MVP 三个 tab，每个 tab 各自一个 NavigationStack。
struct RootTabsView: View {
    var body: some View {
        TabView {
            NavigationStack {
                X01SetupView()
            }
            .tabItem { Label("对局", systemImage: "target") }

            NavigationStack {
                PracticeEntryView()
            }
            .tabItem { Label("练习", systemImage: "figure.strengthtraining.traditional") }

            NavigationStack {
                PlaceholderView(
                    title: "我的",
                    subtitle: "统计 / 成就 待接入（等 D5 把 domain/{stats,achievement,leaderboard} 下沉 shared 后直接复用，不在 iOS 侧另做一套本地实现）"
                )
            }
            .tabItem { Label("我的", systemImage: "person") }
        }
        .tint(Palette.primary)
        .background(Palette.background)
    }
}

/// 练习入口：MVP 只放 Count Up 一项，其余练习后续按同一模式加。
struct PracticeEntryView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                NavigationLink(value: true) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Count Up 练习")
                                .font(.headline)
                                .foregroundStyle(Palette.textPrimary)
                            Text("8 轮 × 3 镖，累计总分")
                                .font(.caption)
                                .foregroundStyle(Palette.textMuted)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(Palette.textMuted)
                    }
                    .padding()
                    .background(Palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("练习")
        .navigationDestination(isPresented: .constant(false)) { EmptyView() }
        .navigationDestination(for: Bool.self) { _ in
            CountUpPracticeView()
        }
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
