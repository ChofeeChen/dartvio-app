import SwiftUI
import shared

/**
 * P0 首页：**只做入口，不做设置**。
 *
 * ## 为什么不能再把设置页当首页
 *
 * 之前「对局」tab 直接就是 X01 设置页，Cricket 只能作为一张卡片**嵌在** X01 的设置里 ——
 * 这在语义上是错的：设置页是「某一局怎么打」，首页是「今天玩什么」。
 * 混在一起的后果是第二个玩法没有自己的门（Cricket 得从 X01 的设置页里钻进去），
 * 第三个玩法（联机/比赛大厅）就更无处可放。
 *
 * 所以首页只列玩法与功能区，点进去才是各自的设置页。
 *
 * ## 为什么不用底部 Tab
 *
 * Tab 天然要求「几个入口平级且常驻切换」，而这六个入口里真正高频的只有前两个游戏；
 * 训练中心 / 统计数据本身还是**两级**（模块 → 项目），塞进 Tab 会让第二级无处安放。
 * 统一用一条 `NavigationStack` + 首页导航，层级才和真实信息结构一致。
 */
struct HomeView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("今天玩什么")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Palette.textPrimary)
                    .padding(.horizontal, 4)

                LazyVGrid(columns: columns, spacing: 12) {
                    // 本地两大玩法：一等公民，各自有自己的设置页
                    NavigationLink { X01SetupView() } label: { entryCard(entry: .x01) }
                        .accessibilityIdentifier("entryX01")
                    NavigationLink { CricketSetupView() } label: { entryCard(entry: .cricket) }
                        .accessibilityIdentifier("entryCricket")

                    // 联机 / 训练 / 数据 / 设置
                    NavigationLink { LobbyView() } label: { entryCard(entry: .lobby) }
                        .accessibilityIdentifier("entryLobby")
                    NavigationLink { TrainingCenterView() } label: { entryCard(entry: .training) }
                        .accessibilityIdentifier("entryTraining")
                    NavigationLink {
                        PlaceholderView(
                            title: "统计数据",
                            subtitle: "统计 / 成就 待接入（等 D5 把 domain/{stats,achievement,leaderboard} 下沉 shared 后直接复用，不在 iOS 侧另做一套本地实现）"
                        )
                    } label: { entryCard(entry: .stats) }
                    .accessibilityIdentifier("entryStats")
                    NavigationLink {
                        PlaceholderView(
                            title: "设置",
                            subtitle: "外观 / 单位 / 通知 等通用设置待接入；各玩法的对局参数在**各自的设置页**里，不在这里"
                        )
                    } label: { entryCard(entry: .settings) }
                    .accessibilityIdentifier("entrySettings")
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("DartVio")
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 2)

    private enum Entry {
        case x01, cricket, lobby, training, stats, settings

        var title: String {
            switch self {
            case .x01: return "X01"
            case .cricket: return "Cricket"
            case .lobby: return "比赛大厅"
            case .training: return "训练中心"
            case .stats: return "统计数据"
            case .settings: return "设置"
            }
        }

        var subtitle: String {
            switch self {
            case .x01: return "本地 · 301/501/701"
            case .cricket: return "本地 · 15-20 + Bull"
            case .lobby: return "远程联机 · P0 仅 X01"
            case .training: return "精准 / 单人 / 双人"
            case .stats: return "历史与能力画像"
            case .settings: return "通用偏好"
            }
        }

        var symbol: String {
            switch self {
            case .x01: return "target"
            case .cricket: return "circle.hexagongrid"
            case .lobby: return "person.3"
            case .training: return "figure.strengthtraining.traditional"
            case .stats: return "chart.bar"
            case .settings: return "gearshape"
            }
        }

        /** 两个本地玩法是主推，用主色描边区分「能直接开打」与「功能区」。 */
        var isPrimary: Bool {
            switch self {
            case .x01, .cricket: return true
            default: return false
            }
        }
    }

    /**
     * 卡片内容**相对卡片居中**（图标 / 标题 / 副标题整体居中）。
     *
     * 之前是 `.leading`：六张卡宽度一致而文字长短不一，左对齐会让每张卡的"视觉重心"
     * 落在不同位置，扫视时要逐张重新定位；居中后卡片本身成为一个稳定的视觉单元。
     */
    private func entryCard(entry: Entry) -> some View {
        VStack(spacing: 6) {
            Image(systemName: entry.symbol)
                .font(.title3)
                .foregroundStyle(entry.isPrimary ? Palette.primary : Palette.textSecondary)
            Text(entry.title)
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text(entry.subtitle)
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 104, alignment: .center)
        .padding(12)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(entry.isPrimary ? Palette.primary.opacity(0.7) : Palette.divider, lineWidth: 1)
        )
        // 让整张卡都是点击热区（卡片内部是 VStack，不声明的话只有文字可点）。
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }
}
