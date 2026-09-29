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
                    NavigationLink { LobbyEntryView() } label: { entryCard(entry: .lobby) }
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

    private func entryCard(entry: Entry) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: entry.symbol)
                .font(.title3)
                .foregroundStyle(entry.isPrimary ? Palette.primary : Palette.textSecondary)
            Text(entry.title)
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text(entry.subtitle)
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 104, alignment: .leading)
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

/**
 * 训练中心：三大本地模块（精准工坊 / 单人训练 / 双人对抗训练）。
 *
 * 分组不是为了好看：这三类**练的东西不同** —— 精准工坊练落点与稳定性、
 * 单人训练练结镖与分数推进、双人对抗练压力下的节奏。混在一张平铺列表里，
 * 玩家每次都要重新判断「这个模式练什么」。
 */
struct TrainingCenterView: View {

    /**
     * ⚠️ 用 `ScrollView` 而不是 `List`：List 的屏幕外行**不会渲染**，
     * XCUITest 就查不到（实测「双人对抗训练」在最后一屏外，V10 直接找不到按钮）。
     * 内容一共 8 项，全部一次渲染没有性能代价。
     */
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
            moduleSection("精准工坊", subtitle: "落点偏差 · 稳定性 · 专项处方") {
                NavigationLink { ImpactSetupView() } label: { row("精准工坊", "手动点出落点，出偏差诊断与训练建议") }
                    .accessibilityIdentifier("trainImpact")
            }

            moduleSection("单人训练", subtitle: "结镖与分数推进，一个人也能刷") {
                NavigationLink { CountUpPracticeView() } label: { row("Count Up 练习", "8 轮 × 3 镖，累计总分") }
                NavigationLink { RandomCheckoutPracticeView() } label: { row("随机结镖（路线学习）", "随机目标分，最多 3 镖双结") }
                NavigationLink { CheckoutRushPracticeView() } label: { row("极速挑战", "限时连续结镖，含战报") }
                NavigationLink { NinetyNineSetupView() } label: { row("99 Darts", "分区推进，先到 99 分") }
                NavigationLink { CricketMprPracticeView() } label: { row("Cricket MPR 挑战", "限时刷 MPR") }
                NavigationLink {
                    X01GameView(launch: X01Launch(
                        targetScore: 301,
                        modeRaw: "casual",
                        legsToWin: 1,
                        outModeRaw: "doubleOut",
                        inModeRaw: "straightIn",
                        smartAi: true,
                        difficultyRaw: "advanced",
                        opponents: [OpponentSeat(isAi: true, difficultyRaw: "advanced")]
                    ))
                } label: { row("AI 对战练习", "与 AI 打一局 X01") }
            }

            moduleSection("双人对抗训练", subtitle: "同一台设备轮流投，或对抗小模式") {
                NavigationLink { VersusListView() } label: { row("双人对抗训练", "Bull 之争等 6 个双人模式") }
            }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("训练中心")
    }

    private func moduleSection<Content: View>(
        _ title: String,
        subtitle: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(subtitle).font(.caption2)
            }
            .foregroundStyle(Palette.textMuted)
            VStack(spacing: 2) { content() }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func row(_ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.headline).foregroundStyle(Palette.textPrimary)
            Text(subtitle).font(.caption).foregroundStyle(Palette.textMuted)
        }
        .padding(.vertical, 4)
    }
}

/**
 * 比赛大厅（P0：只列产品定义与当前可达范围）。
 *
 * ## 边界写死在这里是有原因的
 *
 * shared 的 `domain/room`（`RoomRules` / `RoomMatchRules` / `RoomEventReplay`）**已经在 commonMain**，
 * 且 `RoomMatchRules.supportsLiveMatch` 明说了：**权威联机对局只支持 X01**，房间是 1v1。
 * 这条不是 iOS 的取舍，是引擎边界 —— 所以 UI 必须按它写，不能给 Cricket 也挂个「联机」按钮。
 *
 * 还没到位的是 `net/online` 那一层（PostgREST + Realtime WebSocket），它只在 `android/app`，
 * 不在 shared（commonMain 要求零依赖）。iOS 要真联机得新写这一层，故此处如实标注。
 */
struct LobbyEntryView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                infoCard(
                    title: "P0 范围",
                    lines: [
                        "玩法：仅 X01（引擎的权威联机对局目前只支持 X01）",
                        "人数：1v1 房间",
                        "流程：建房 / 加入 → 等候 → 对局 → 结算 → 再来一局"
                    ]
                )
                infoCard(
                    title: "当前状态",
                    lines: [
                        "房间内核（事件流重放）已在 shared：RoomRules / RoomMatchRules / RoomEventReplay",
                        "联机网络层（PostgREST + Realtime WebSocket）尚未下沉 shared，iOS 侧待接入",
                        "在此之前可先玩本地对局：首页 → X01 / Cricket"
                    ]
                )
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("比赛大厅")
    }

    private func infoCard(title: String, lines: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline).foregroundStyle(Palette.textPrimary)
            ForEach(lines, id: \.self) { line in
                Text("· \(line)")
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
