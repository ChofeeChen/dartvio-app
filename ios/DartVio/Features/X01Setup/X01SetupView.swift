import SwiftUI
import shared

/**
 * 对局启动参数。
 *
 * Kotlin 的枚举导出到 Swift 后是**类**（`MatchMode` / `OutMode` / `InMode` / `AiDifficulty`），
 * 不是 Swift enum，既不 Hashable 也不能直接放进 navigationDestination 的值里。
 * 所以跨页面传原始字符串，落到对局页再用 `X01SetupMapping` 还原成 Kotlin 枚举。
 */
struct X01Launch: Hashable {
    var targetScore: Int32
    var modeRaw: String
    var legsToWin: Int32
    var outModeRaw: String
    var inModeRaw: String
    var smartAi: Bool
    var difficultyRaw: String
    /// 对手席位（每个席位自带「是否 AI」与 AI 的 PPR 档）；空 = 老口径（1 个进阶机器人）。
    var opponents: [OpponentSeat] = [.ai("intermediate")]
}

enum X01SetupMapping {
    static func matchMode(_ raw: String) -> MatchMode {
        raw == "multiLeg" ? MatchMode.multiLeg : MatchMode.casual
    }

    static func outMode(_ raw: String) -> OutMode {
        switch raw {
        case "straightOut": return OutMode.straightOut
        case "masterOut": return OutMode.masterOut
        default: return OutMode.doubleOut
        }
    }

    static func inMode(_ raw: String) -> InMode {
        switch raw {
        case "doubleIn": return InMode.doubleIn
        case "masterIn": return InMode.masterIn
        default: return InMode.straightIn
        }
    }

    static func difficulty(_ raw: String) -> AiDifficulty {
        switch raw {
        case "beginner": return AiDifficulty.beginner
        case "advanced": return AiDifficulty.advanced
        case "pro": return AiDifficulty.pro
        default: return AiDifficulty.intermediate
        }
    }

    static func config(from launch: X01Launch) -> MatchConfig {
        SharedFactory.x01Config(
            targetScore: launch.targetScore,
            mode: matchMode(launch.modeRaw),
            legsToWin: launch.modeRaw == "multiLeg" ? launch.legsToWin : 1,
            outMode: outMode(launch.outModeRaw),
            inMode: inMode(launch.inModeRaw),
            smartAi: launch.smartAi
        )
    }

    /**
     * 名单：**第 1 席恒为本人**，其余按 `launch.opponents` 展开。
     *
     * `difficultyRaw` 只作为「没有席位信息时的兜底」（外部构造 launch 不传 opponents 的老调用方），
     * 正式路径上每个 AI 用它自己的档位 —— 这样才做得到「两个机器人一强一弱」。
     */
    static func players(from launch: X01Launch) -> [Player] {
        OpponentPlayers.build(seats: launch.opponents)
    }

    /// AI 档位名（"入门"…），取自引擎 `AiDifficulty` 的展示口径。
    static func aiLevelTitle(_ raw: String) -> String {
        switch raw {
        case "beginner": return "入门"
        case "advanced": return "高手"
        case "pro": return "专业"
        default: return "进阶"
        }
    }
}

/// P2：X01 设置页。
struct X01SetupView: View {
    @State private var targetScore: Int32 = 501
    @State private var modeRaw = "casual"
    @State private var legsToWin: Int32 = 3
    @State private var outModeRaw = "doubleOut"
    @State private var inModeRaw = "straightIn"
    @State private var smartAi = true
    @State private var difficultyRaw = "intermediate"
    @State private var opponents: [OpponentSeat] = [.ai("intermediate")]

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // ⚠️ Cricket 的入口卡已从本页移除：它是**另一个游戏**，不该藏在 X01 的设置里。
                // 现在两个玩法在首页平级各自入口（见 HomeView）。
                SetupRow(title: "目标分") {
                    Picker("", selection: $targetScore) {
                        Text("301").tag(Int32(301))
                        Text("501").tag(Int32(501))
                        Text("701").tag(Int32(701))
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 190)
                }

                SetupRow(title: "赛制") {
                    Picker("", selection: $modeRaw) {
                        Text("休闲单局").tag("casual")
                        Text("多局决胜").tag("multiLeg")
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 190)
                }

                if modeRaw == "multiLeg" {
                    SetupRow(title: "先胜局数") {
                        Stepper(value: $legsToWin, in: 1...9) {
                            Text("\(legsToWin) 局")
                        }
                    }
                }

                SetupRow(title: "结束规则") {
                    Picker("", selection: $outModeRaw) {
                        Text("双倍出").tag("doubleOut")
                        Text("直出").tag("straightOut")
                        Text("大师出").tag("masterOut")
                    }
                    .pickerStyle(.menu)
                }

                SetupRow(title: "开局规则") {
                    Picker("", selection: $inModeRaw) {
                        Text("直入").tag("straightIn")
                        Text("双倍入").tag("doubleIn")
                        Text("大师入").tag("masterIn")
                    }
                    .pickerStyle(.menu)
                }

                OpponentSetupSection(
                    seats: $opponents,
                    hint: "最多 \(OpponentSeat.maxOpponents) 个对手；真人席位在同一台设备上轮流投镖，远程对战请走首页的比赛大厅。"
                )
                .padding()
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                SetupRow(title: "智能难度") {
                    Toggle("", isOn: $smartAi)
                }
                Text("开启后机器人会按你最近几轮的表现微调自己的 PPR（不会跨出所选档位的区间）。")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // ⚠️ 同样的坑（见 CricketSetupView）：本页面现在是从首页 push 进来的，
                // 「被 push 的页」里的 `NavigationLink(value:)` + `navigationDestination` 不生效，
                // 点下去原地不动。改用视图型链接。
                NavigationLink {
                    X01GameView(launch: currentLaunch)
                } label: {
                    Text("开始对局")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Palette.primary)
                        .foregroundStyle(Palette.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.top, 8)
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("本地对局")
        // ⚠️ 不再注册 `navigationDestination(for: X01Launch.self)`：开始对局已改为视图型链接；
        // 保留注册会出现「注册了但没人用」，将来若有人再加同类型注册，命中谁取决于注册顺序。
    }

    private var currentLaunch: X01Launch {
        X01Launch(
            targetScore: targetScore,
            modeRaw: modeRaw,
            legsToWin: legsToWin,
            outModeRaw: outModeRaw,
            inModeRaw: inModeRaw,
            smartAi: smartAi,
            difficultyRaw: difficultyRaw,
            opponents: opponents
        )
    }
}
