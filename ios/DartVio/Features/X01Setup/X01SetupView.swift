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

    static func players(from launch: X01Launch) -> [Player] {
        [
            SharedFactory.player(id: "p_human", name: "我", type: PlayerType.human),
            SharedFactory.player(
                id: "p_ai",
                name: "电脑",
                type: PlayerType.ai,
                aiDifficulty: difficulty(launch.difficultyRaw)
            ),
        ]
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

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
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

                SetupRow(title: "对手难度") {
                    Picker("", selection: $difficultyRaw) {
                        Text("入门").tag("beginner")
                        Text("进阶").tag("intermediate")
                        Text("高手").tag("advanced")
                        Text("专业").tag("pro")
                    }
                    .pickerStyle(.menu)
                }

                SetupRow(title: "智能难度") {
                    Toggle("", isOn: $smartAi)
                }

                NavigationLink(value: currentLaunch) {
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
        .navigationDestination(for: X01Launch.self) { launch in
            X01GameView(launch: launch)
        }
    }

    private var currentLaunch: X01Launch {
        X01Launch(
            targetScore: targetScore,
            modeRaw: modeRaw,
            legsToWin: legsToWin,
            outModeRaw: outModeRaw,
            inModeRaw: inModeRaw,
            smartAi: smartAi,
            difficultyRaw: difficultyRaw
        )
    }
}
