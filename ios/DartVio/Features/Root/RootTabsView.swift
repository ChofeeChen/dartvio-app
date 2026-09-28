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

/// 练习中心：卡面与 Android `ui/practice/PracticeSoloScreen.kt` 一一对应（含结镖训练的两个子模式）。
///
/// **已接通**的直接进；**未接通**的行整体置灰并标「待接入」——
/// 选择置灰而不是跳空壳页，是为了让缺口一眼可见，也避免用户点了得到空白页。
/// 补齐顺序见回写清单 §12.5：练习/训练/对抗所需的引擎 commonMain 里都已具备，纯 UI 工作量。
struct PracticeEntryView: View {

    private enum PracticeMode: String, CaseIterable {
        case impact = "精准工坊"
        /// ⚠️ 别改这个 rawValue：现有 UI 测试（V2 / V4）就是靠 label 包含 "Count Up 练习" 定位入口的。
        case countUp = "Count Up 练习"
        case randomCheckout = "随机结镖（路线学习）"
        case checkoutRush = "极速挑战"
        case ninetyNine = "99 Darts"
        case cricketMpr = "Cricket MPR 挑战"
        case aiPractice = "AI 对战练习"
        case versus = "双人对抗训练"

        var subtitle: String {
            switch self {
            case .impact: return "落点偏差 · 稳定性 · 专项处方"
            case .countUp: return "8 轮 × 3 镖，累计总分"
            case .randomCheckout: return "随机目标分，最多 3 镖双结"
            case .checkoutRush: return "限时连续结镖，含战报"
            case .ninetyNine: return "分区推进，先到 99 分"
            case .cricketMpr: return "限时刷 MPR"
            case .aiPractice: return "与 AI 打一局 X01"
            case .versus: return "Bull 之争等 6 个双人模式"
            }
        }

        /// true = iOS 已实现。改这里就是在改「双端对齐进度」，别在别处再维护一份清单。
        var isReady: Bool {
            switch self {
            case .countUp, .randomCheckout, .checkoutRush, .ninetyNine,
                 .cricketMpr, .impact, .aiPractice, .versus: return true
            }
        }
    }

    var body: some View {
        List {
            ForEach(PracticeMode.allCases, id: \.rawValue) { mode in
                if mode.isReady {
                    NavigationLink(value: mode) { practiceCard(mode) }
                } else {
                    practiceCard(mode)
                        .opacity(0.45)
                        .allowsHitTesting(false)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Palette.background)
        .navigationTitle("练习")
        .navigationDestination(for: PracticeMode.self) { mode in
            switch mode {
            case .countUp: CountUpPracticeView()
            case .randomCheckout: RandomCheckoutPracticeView()
            case .checkoutRush: CheckoutRushPracticeView()
            case .ninetyNine: NinetyNineSetupView()
            case .cricketMpr: CricketMprPracticeView()
            case .impact: ImpactSetupView()
            // AI 对战练习 = 直接开一局 vs AI 的 X01（Android 侧是 versusLocked 的对局设置），
            // 所以复用现有对局页，只是把参数写死成练习口径：301 / 直入 / 双倍出 / 高级 AI。
            case .aiPractice: X01GameView(launch: X01Launch(
                targetScore: 301,
                modeRaw: "casual",
                legsToWin: 1,
                outModeRaw: "doubleOut",
                inModeRaw: "straightIn",
                smartAi: true,
                difficultyRaw: "advanced"
            ))
            case .versus: VersusListView()
            }
        }
    }

    private func practiceCard(_ mode: PracticeMode) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(mode.rawValue)
                    .font(.headline)
                    .foregroundStyle(Palette.textPrimary)
                Text(mode.subtitle)
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
            }
            Spacer()
            Text(mode.isReady ? "" : "待接入")
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
                .accessibilityLabel(mode.isReady ? "已接入" : "待接入")
        }
        .padding(.vertical, 4)
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
