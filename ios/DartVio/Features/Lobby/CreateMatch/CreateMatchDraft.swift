import Foundation
import shared

/**
 * 参赛条件（本期不开发）。
 *
 * 「预留」指的是**数据结构与落点先定好**，不是先画几个能点的开关然后点了没反应 ——
 * 所以这里按最终形态留字段，UI 整组禁用且全部默认关闭（需求③）。
 */
struct EntryConditions {
    var friendsOnly = false
    var passwordRequired = false
    var minPprRequired = false
    /// PPR 门槛值；`minPprRequired == false` 时不参与校验。
    var minPpr: Double = 40
}

/// X01 规则面板的选项表。放在这里是为了让「面板」与「弹窗上的摘要文字」读同一份表，
/// 不会出现面板改了措辞、摘要还写着旧词。
enum X01RulesOptions {
    struct Option: Identifiable, Hashable {
        let raw: String
        let title: String
        var id: String { raw }
    }

    static let inModes: [Option] = [
        Option(raw: "straightIn", title: "STRAIGHT IN"),
        Option(raw: "doubleIn", title: "DOUBLE IN"),
        Option(raw: "masterIn", title: "MASTER IN"),
    ]

    static let outModes: [Option] = [
        Option(raw: "straightOut", title: "STRAIGHT OUT"),
        Option(raw: "doubleOut", title: "DOUBLE OUT"),
        Option(raw: "masterOut", title: "MASTER OUT"),
    ]

    /// 轮数上限：与引擎 `MatchConfig.maxRounds` 同一口径（`<= 0` 表示不限）。
    static let rounds = [15, 20, 50, 80]

    /**
     * 录入口径：每镖分值 / 每轮总分。
     *
     * ⚠️ 这是 **UI 侧口径**，不进 `MatchConfig`（引擎按镖结算，不认"整轮总分"）——
     * 它决定的是记分键盘让用户一次录一镖还是一次录一轮，落点在对局页的录入方式上。
     */
    static let inputs: [Option] = [
        Option(raw: "perDart", title: "输入每镖分值"),
        Option(raw: "perRound", title: "输入每轮总分"),
    ]

    static func inTitle(_ raw: String) -> String { inModes.first { $0.raw == raw }?.title ?? "STRAIGHT IN" }
    static func outTitle(_ raw: String) -> String { outModes.first { $0.raw == raw }?.title ?? "DOUBLE OUT" }
    static func inputTitle(_ raw: String) -> String { inputs.first { $0.raw == raw }?.title ?? "输入每轮总分" }
}

/**
 * 创建比赛的表单草稿。
 *
 * ## 草稿活在大厅里，不在弹窗里
 *
 * 需求：「子页面操作不重置弹窗原有表单」。所以草稿由 `LobbyView` 持有，
 * 弹窗 / 游戏网格 / 规则面板三页只是读写同一份 —— 关闭弹窗去选游戏再回来，分数与人数都还在。
 *
 * 弹窗内部若各自持有 `@State`，一次 dismiss 就全没了。
 */
@Observable
final class CreateMatchDraft {

    /// 与引擎 `Room.MAX_MEMBERS` 对齐（1v1）；引擎改上限时这里要跟着改。
    static let maxOnlineSeats = 2
    static let scoreRange = 101...1001
    static let seatsRange = 2...8
    static let legOptions = [1, 2, 3, 5, 7]

    var game: GameCatalog.Game
    var targetScore: Int = 501
    var playerCount: Int = 2
    /// 「FIRST TO 2 LEGS」的 2；1 = 单局定胜负。
    var legsToWin: Int = 2

    var inModeRaw = "straightIn"
    var outModeRaw = "doubleOut"
    var maxRounds = 50
    var scoringInputRaw = "perRound"

    var conditions = EntryConditions()

    init(game: GameCatalog.Game = GameCatalog.x01) {
        self.game = game
    }

    // MARK: - 文案

    var formatTitle: String { "FIRST TO \(legsToWin) LEG\(legsToWin > 1 ? "S" : "")" }

    /// 弹窗上的规则摘要：规则面板保存后**这一行**就是"回到弹窗更新文字"的落点。
    var rulesSummary: String {
        guard game.hasRulesPanel else { return "\(game.title) 的规则配置面板尚未开放" }
        return [
            X01RulesOptions.inTitle(inModeRaw),
            X01RulesOptions.outTitle(outModeRaw),
            "\(maxRounds) 轮",
            X01RulesOptions.inputTitle(scoringInputRaw),
        ].joined(separator: " · ")
    }

    /**
     * 为什么现在**不能**创建（nil = 可以创建）。
     *
     * 单一来源：玩法未开放 / 人数超限都在这里判定，弹窗只负责显示并禁用按钮 ——
     * 若把判定散在按钮的 `disabled` 里，用户会看到一个灰着的按钮却不知道为什么。
     */
    var createBlocker: String? {
        if !game.onlineReady {
            return game.tier == .p0
                ? "\(game.title) 的联机对局尚未开放：引擎目前只为 X01 维护权威联机对局"
                : "\(game.title)（\(game.tier.rawValue)）尚未开放，敬请期待"
        }
        if playerCount > Self.maxOnlineSeats {
            return "P0 联机房间是 1v1（引擎 `Room.MAX_MEMBERS = \(Self.maxOnlineSeats)`），\(playerCount) 人房间尚未开放"
        }
        return nil
    }

    /// 交给引擎的 `MatchConfig`（X01 才有权威联机对局，这里也只构造 X01）。
    var matchConfig: MatchConfig {
        SharedFactory.x01Config(
            targetScore: Int32(targetScore),
            mode: legsToWin > 1 ? MatchMode.multiLeg : MatchMode.casual,
            legsToWin: Int32(max(legsToWin, 1)),
            outMode: X01SetupMapping.outMode(outModeRaw),
            inMode: X01SetupMapping.inMode(inModeRaw),
            smartAi: false,
            maxRounds: Int32(maxRounds)
        )
    }

    func apply(_ patch: X01RulesPatch) {
        inModeRaw = patch.inModeRaw
        outModeRaw = patch.outModeRaw
        maxRounds = patch.maxRounds
        scoringInputRaw = patch.scoringInputRaw
    }
}

/**
 * 规则面板的保存结果。
 *
 * 面板在**本地副本**上改，点「保存」才写回草稿 —— 直接改草稿的话，
 * 右上角 X 关闭就成了"半改半留"，与"修改后保存"的语义不符。
 */
struct X01RulesPatch {
    var inModeRaw: String
    var outModeRaw: String
    var maxRounds: Int
    var scoringInputRaw: String
}
