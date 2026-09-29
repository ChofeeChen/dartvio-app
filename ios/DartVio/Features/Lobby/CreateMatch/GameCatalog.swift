import Foundation

/**
 * 「创建比赛」的**游戏目录**（UI 层）。
 *
 * ## 这一份是产品目录，不是引擎能力清单
 *
 * 需求给的 19 个玩法里，引擎目前只做得出 **X01** 的权威联机对局
 * （`RoomMatchRules.supportsLiveMatch` 只认 `MatchType.X01`），其余是 P1 / 待开放。
 * 所以每一项除需求给的 P0 / P1 档位之外，还要带 `onlineReady`：
 *
 * - **STANDARD CRICKET 是 P0 但 `onlineReady = false`** —— 单机能打，联机对局还没接；
 * - 目录**可以先列全**，但**不能假装能开**：不可开的玩法在弹窗里给出具体原因，创建按钮同时禁用。
 *
 * ⚠️ 顺序即网格顺序（3 列一行，从上到下、从左到右），调整顺序前先对照需求文案。
 */
enum GameCatalog {

    enum Tier: String {
        case p0 = "P0"
        case p1 = "P1"
    }

    struct Game: Identifiable, Hashable {
        /// 稳定键：展示名改了也不会丢失已选中的玩法。
        let id: String
        /// 网格与弹窗上的展示名，与需求文案一致（全大写英文）。
        let title: String
        let tier: Tier
        /// 是否已有对应的【游戏规则配置面板】（目前只有 X01 一套 4 行分段控件）。
        let hasRulesPanel: Bool
        /// 引擎是否能给出权威联机对局（目前只有 X01）。
        let onlineReady: Bool
    }

    static let x01 = Game(id: "x01", title: "X01", tier: .p0, hasRulesPanel: true, onlineReady: true)
    static let standardCricket = Game(
        id: "standard_cricket", title: "STANDARD CRICKET", tier: .p0, hasRulesPanel: false, onlineReady: false
    )

    static let all: [Game] = [
        x01,
        standardCricket,
        Game(id: "no_score_cricket", title: "NO SCORE CRICKET", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "tactics", title: "TACTICS", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "random_cricket", title: "RANDOM CRICKET", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "cut_throat", title: "CUT THROAT", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "around_the_clock", title: "AROUND THE CLOCK", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "jdc_challenge", title: "JDC CHALLENGE", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "99_darts", title: "99 DARTS AT XX", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "round_the_world", title: "ROUND THE WORLD", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "170", title: "170", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "cricket_count_up", title: "CRICKET COUNT UP", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "count_up", title: "COUNT UP", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "hammer_cricket", title: "HAMMER CRICKET", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "half_it", title: "HALF IT", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "killer", title: "KILLER", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "shanghai", title: "SHANGHAI", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "bermuda", title: "BERMUDA", tier: .p1, hasRulesPanel: false, onlineReady: false),
        Game(id: "gotcha", title: "GOTCHA", tier: .p1, hasRulesPanel: false, onlineReady: false),
    ]
}
