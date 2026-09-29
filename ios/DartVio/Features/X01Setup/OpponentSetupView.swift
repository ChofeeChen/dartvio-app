import SwiftUI
import shared

/**
 * 一个对手席位。
 *
 * ## 为什么是「逐席位」而不是「整局二选一」
 *
 * Android 现有 `VersusMode` 是**整局**在「真人对真人 / 真人对 AI」里二选一，
 * AI 难度也是**整局一档**对全部机器人生效 —— 那样做不出「1 个真人 + 2 个机器人，
 * 而且两个机器人一强一弱」。所以这里按席位存：`isAi` 与 `difficultyRaw` 都落在每个 seat 上。
 *
 * 上限 3 个对手（共 4 人）与 Android 的 `MAX_OPPONENTS = MAX_PLAYERS - 1` 一致。
 */
struct OpponentSeat: Hashable {
    var isAi: Bool
    /// AI 能力档的 key（`"beginner" | "intermediate" | "advanced" | "pro"`），真人席位不用。
    var difficultyRaw: String

    static let maxOpponents = 3

    static func ai(_ difficultyRaw: String = "intermediate") -> OpponentSeat {
        OpponentSeat(isAi: true, difficultyRaw: difficultyRaw)
    }

    static func human() -> OpponentSeat { OpponentSeat(isAi: false, difficultyRaw: "intermediate") }
}

/**
 * AI 的 PPR（每轮平均分）能力档。
 *
 * ⚠️ PPR 区间与命中率的换算**只有引擎那一处**（`AiDifficulty.pprLow/pprMid/pprHigh`
 * 与 `AiDifficulty.hitChanceFor`），iOS 不在本地再抄一张表 —— 抄了就会出现
 * 「UI 说 55、引擎按 62 打」这种对不上的情况。
 *
 * 之所以把 PPR 显示出来：玩家对「进阶/高手」没有直觉，但对「这机器人一轮打 47 分」有。
 * 显示 PPR 区间也顺带说明了「自适应」的上下界（机器人不会跨出这一档）。
 */
enum AiLevel: String, CaseIterable, Identifiable {
    case beginner
    case intermediate
    case advanced
    case pro

    var id: String { rawValue }

    var difficulty: AiDifficulty { X01SetupMapping.difficulty(rawValue) }

    /** 引擎给的档位名（"入门" ...）。 */
    var title: String { X01SetupMapping.aiLevelTitle(rawValue) }

    /// 形如「进阶 · PPR 47（40-55）」—— 中值是实际起算点，区间是自适应的 clamp 边界。
    var pickerTitle: String {
        "\(title) · PPR \(difficulty.ppr)（\(difficulty.pprLow)-\(difficulty.pprHigh)）"
    }
}

/**
 * 设置页的「对手」段：人数 + 每个席位的类型 + AI 的 PPR 档。
 *
 * 两个设置页（X01 / Cricket）都用这一段 —— 对手是谁与玩法无关，
 * 各写一份就会出现「X01 能选 3 个对手、Cricket 只能选 1 个」这种不一致。
 */
struct OpponentSetupSection: View {

    @Binding var seats: [OpponentSeat]
    /// 玩法提示：真人席位在同一台设备上轮流投，多人远程要走去大厅。
    let hint: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("对手")
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text(hint)
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
                .fixedSize(horizontal: false, vertical: true)

            Stepper(value: Binding(
                get: { seats.count },
                set: { count in
                    let clamped = min(OpponentSeat.maxOpponents, max(1, count))
                    if clamped > seats.count {
                        seats.append(contentsOf: Array(repeating: .ai(), count: clamped - seats.count))
                    } else {
                        seats.removeLast(seats.count - clamped)
                    }
                }
            ), in: 1...OpponentSeat.maxOpponents) {
                Text("\(seats.count) 个对手（最多 \(OpponentSeat.maxOpponents)）")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textSecondary)
            }

            ForEach(seats.indices, id: \.self) { index in
                seatRow(index: index)
                    .padding(10)
                    .background(Palette.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private func seatRow(index: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(displayName(index))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.textPrimary)
                Spacer()
                Picker("", selection: Binding(
                    get: { seats[index].isAi },
                    set: { seats[index].isAi = $0 }
                )) {
                    Text("AI 机器人").tag(true)
                    Text("真人").tag(false)
                }
                .pickerStyle(.segmented)
                .frame(width: 160)
            }

            if seats[index].isAi {
                VStack(alignment: .leading, spacing: 4) {
                    Text("能力（每轮平均分 PPR）")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                    Picker("", selection: Binding(
                        get: { seats[index].difficultyRaw },
                        set: { seats[index].difficultyRaw = $0 }
                    )) {
                        ForEach(AiLevel.allCases) { level in
                            Text(level.pickerTitle).tag(level.rawValue)
                        }
                    }
                    .pickerStyle(.menu)
                    Text("对局中会按你的表现自适应，但不会跨出这一档的区间。")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                }
            }
        }
    }

    /// 席位名：真人按顺序编号，AI 带上档位名（同档多个 AI 也能区分）。
    private func displayName(_ index: Int) -> String {
        let seat = seats[index]
        if seat.isAi {
            return "机器人 \(index + 1)（\(AiLevel(rawValue: seat.difficultyRaw)?.title ?? "进阶")）"
        }
        return "玩家 \(index + 2)"
    }
}

/// 由席位表生成 `Player` 名单：第 1 席恒为本人。
enum OpponentPlayers {
    static func build(seats: [OpponentSeat]) -> [Player] {
        var players: [Player] = [
            SharedFactory.player(id: "p_human", name: "我", type: PlayerType.human)
        ]
        for (offset, seat) in seats.enumerated() {
            let index = offset + 1
            if seat.isAi {
                players.append(SharedFactory.player(
                    id: "p_ai_\(index)",
                    name: "机器人\(index)",
                    type: PlayerType.ai,
                    aiDifficulty: X01SetupMapping.difficulty(seat.difficultyRaw)
                ))
            } else {
                players.append(SharedFactory.player(
                    id: "p_human_\(index)",
                    name: "玩家\(index + 1)",
                    type: PlayerType.human
                ))
            }
        }
        return players
    }
}
