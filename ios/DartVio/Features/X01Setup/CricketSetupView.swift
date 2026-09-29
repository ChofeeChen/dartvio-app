import SwiftUI
import shared

/**
 * Cricket 对局的启动参数。
 *
 * 与 `X01Launch` 同样的取舍：Kotlin 的 `CricketVariant` 导出到 Swift 是**类**（`SharedKotlinEnum` 子类），
 * 不满足 `Hashable`，没法直接当 `navigationDestination` 的导航值，所以跨页面传**字符串**，
 * 落到对局页再还原成 Kotlin 对象（M2 §4.8 的 key 本身就是 stable protocol value，正好合适）。
 */
struct CricketLaunch: Hashable {
    var variantRaw: String
    var legsToWin: Int32
    var difficultyRaw: String
    /// 对手席位（逐席位可选是否 AI、AI 各自的 PPR 档）。
    var opponents: [OpponentSeat] = [.ai("intermediate")]
}

enum CricketSetupMapping {

    /**
     * 变体解析交给引擎的 `fromKey` —— **不要**在 iOS 侧自己 switch key。
     *
     * `fromKey` 的兜底是长期契约：二期加入 tactics / random 取值后，
     * 老客户端读到新键必须回落到 STANDARD 而不是崩掉；自己写 switch 就会丢掉这层保护。
     */
    static func variant(_ raw: String) -> CricketVariant { CricketVariant.companion.fromKey(raw: raw) }

    static func difficulty(_ raw: String) -> AiDifficulty { X01SetupMapping.difficulty(raw) }

    /**
     * 目标集。
     *
     * 一期固定「15-20 + Bull」（`DEFAULT_TARGETS`），所以这里不做分区选择器 ——
     * 玩法差异（二期 tactics 的 9/12 分区、random 的随机集）由**目标集**承载，
     * 届时这里改成传 config.cricketTargets 即可，Go 层的 `MeetTargetSetCsv` 已经留好了口子。
     */
    // `CricketTarget` 是 Kotlin sealed interface，导出成 **protocol**，拿不到 `companion`；
    // 静态成员要走 companion 的单例类 `CricketTargetCompanion.shared`。
    static func targets() -> [any CricketTarget] { CricketTargetCompanion.shared.DEFAULT_TARGETS }

    static func config(from launch: CricketLaunch) -> MatchConfig {
        MatchConfig(
            matchType: MatchType.cricket,
            // Cricket 不看目标分：targetScore 保留 X01 的字段语义，这里必须是 0，
            // 否则 X01 侧那条「剩余分 = targetScore」的读法会把它当成 Cricket 的分数口径。
            targetScore: 0,
            mode: MatchMode.multiLeg,
            legsToWin: launch.legsToWin,
            outMode: OutMode.doubleOut,
            inMode: InMode.straightIn,
            bullMode: BullMode.standard2550,
            // 0 = 无轮数上限（`CricketRules.isRoundLimitWin` 的口径：`maxRounds <= 0` 恒 false）。
            maxRounds: 0,
            smartAi: false,
            cricketVariant: variant(launch.variantRaw),
            cricketTargets: targets(),
            overkillEnabled: true
        )
    }

    static func players(from launch: CricketLaunch) -> [Player] {
        OpponentPlayers.build(seats: launch.opponents)
    }

    /**
     * 变体的展示名与人话解释。
     *
     * ⚠️ 这两个文案在 `CricketVariant` 里本来就有（`label` / `hint`），
     * 但它们是**构造参数**而不是 companion 成员，KMP 只导出了 `standard / noScore / cutThroat` 三个实例，
     * 取不到 `label`；所以这里按 key 写一份，并把它和 `Hint` 放在一起，方便将来对齐。
     * `key` 取值来自 shared 的 `CricketVariant.name.lowercase()`。
     */
    static func variantLabel(_ raw: String) -> String {
        switch variant(raw).name {
        case "NO_SCORE": return "不计分"
        case "CUT_THROAT": return "生死局"
        default: return "标准"
        }
    }

    static func variantHint(_ raw: String) -> String {
        switch variant(raw).name {
        case "NO_SCORE": return "只比谁先关闭全部分区，不看分数"
        case "CUT_THROAT": return "打中得分算给对手，分数低者领先"
        default: return "关闭 15-20 与 Bull，分数高者胜"
        }
    }
}

/**
 * Cricket 设置页，对齐 Android `ui/setup/CricketSettingsScreen.kt`。
 *
 * 三个变体的差别只有两处（得分归属 + 胜负比较），所以设置项也很少 ——
 * 分区集一期不做选择（见 `targets()`）。
 */
struct CricketSetupView: View {

    @State private var variantRaw = "standard"
    @State private var legsToWin: Int32 = 3
    @State private var difficultyRaw = "intermediate"
    @State private var opponents: [OpponentSeat] = [.ai("intermediate")]

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("关闭 15 / 16 / 17 / 18 / 19 / 20 与 Bull（各 3 次命中即关闭）；"
                        + "先关满全部目标且分数不落后的一方获胜。")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 10))

                SetupRow(title: "玩法") {
                    Picker("", selection: $variantRaw) {
                        Text("标准").tag("standard")
                        Text("不计分").tag("no_score")
                        Text("生死局").tag("cut_throat")
                    }
                    .pickerStyle(.menu)
                }
                Text(CricketSetupMapping.variantHint(variantRaw))
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)

                SetupRow(title: "先胜局数") {
                    Stepper(value: $legsToWin, in: 1...9) { Text("\(legsToWin) 局") }
                }

                OpponentSetupSection(
                    seats: $opponents,
                    hint: "最多 \(OpponentSeat.maxOpponents) 个对手；真人席位在同一台设备上轮流投镖，远程对战请走首页的比赛大厅。"
                )
                .padding()
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

            }
            .padding()
        }
        // 「开始对局」固定在屏幕底部（见 `startBar`）：设置项有多少，按钮都在同一个位置。
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 0) {
                Divider()
                startBar
            }
            .background(Palette.surface)
        }
        .background(Palette.background)
        .navigationTitle("Cricket")
        // ⚠️ 这里**不要**再注册 `navigationDestination(for:)`：开始对局已改为视图型链接，
        // 注册了也用不上；而且同类型重复注册会让「点下去去哪」依赖注册顺序（见回写清单 §16.4）。
    }

    /**
     * 底部固定条里的「开始对局」。
     *
     * ⚠️ 用**视图型** `NavigationLink`，不是 `NavigationLink(value:)`：
     * 这种「被 push 的页」里注册 `navigationDestination(for:)` 实测不生效
     * （点下去原地不动 —— V14 就是这么挂的），而视图型链接始终可用。
     */
    private var startBar: some View {
        NavigationLink {
            CricketGameView(launch: currentLaunch)
        } label: {
            Text("开始对局")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Palette.primary)
                .foregroundStyle(Palette.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .accessibilityIdentifier("cricketStart")
    }

    private var currentLaunch: CricketLaunch {
        CricketLaunch(
            variantRaw: variantRaw,
            legsToWin: legsToWin,
            difficultyRaw: difficultyRaw,
            opponents: opponents
        )
    }
}
