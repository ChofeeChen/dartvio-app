import Foundation
import shared

/**
 * 双人对抗中某一局的 ViewModel —— **六个模式共用同一个 VM**，对齐 Android `ui/practice/versus` 的组织方式。
 *
 * 之所以能共用：commonMain 的 `VersusRule` 是统一接口（`defaultConfig` / `doNewState` / `onDart` /
 * `onRoundEnd` / `targetCaption` / `progressText`），而 `VersusModes.ruleOf(modeKey:)` 能按 modeKey
 * 取出各自的引擎实例。所以 **iOS 这边不需要为每个模式写一个页面**。
 *
 * ⚠️ 刻意简化：Android 会逐轮落 `versus_round_records` 并在结束时出 `VersusBattleReport`；
 * iOS 暂无会话持久化，战报直接由 `BattleState` 派生（一样准确，只是退出即丢）。
 */
@MainActor
@Observable
final class VersusViewModel {

    private(set) var state: BattleState
    private(set) var lastEventText: String?

    let info: VersusModeInfo
    let rule: VersusRule

    init(modeKey: String, playerNames: [String], config: BattleConfig) {
        guard let info = VersusModes.shared.infoOf(modeKey: modeKey),
              let rule = VersusModes.shared.ruleOf(modeKey: modeKey) else {
            // modeKey 来自 VersusModes.ALL，取不到属于内部错误，直接崩在启动处比带着空无法玩的状态往下走好定位。
            fatalError("未知的对抗模式：\(modeKey)")
        }
        self.info = info
        self.rule = rule
        self.state = rule.doNewState(config: config, playerNames: playerNames)
    }

    // MARK: - 展示口径（全部来自引擎，不自己拼）

    var caption: String { rule.targetCaption(state: state) }

    func progressText(_ index: Int) -> String { rule.progressText(state: state, playerIndex: Int32(index)) }

    var currentPlayerName: String { state.current.name }

    var isRoundComplete: Bool { state.isRoundComplete }

    /// 本轮该记几镖（加赛 Bull 时是 1），别在对局页写死 3。
    var dartsPerRound: Int { Int(state.dartsPerRound) }

    /**
     * 键盘允许点什么 —— **口径来自引擎**，不由页面决定。
     *
     * 引擎注释里点名了这件事：留着一排点了必然记 0 分的死键是这套 UI 最不能有的东西。
     * 参数收的是 state 而不是 config，所以环游类的目标分区随回合推进时，键盘会跟着走。
     */
    var inputFilter: KeyboardLayout { rule.inputFilter(state: state) }

    var winnerText: String? {
        // `winnerIndex` 是 Kotlin 的 `Int?` → 导出成 `KotlinInt?`（NSNumber 子类），取 `intValue`。
        guard state.finished, let winner = state.winnerIndex else { return nil }
        return name(at: Int(winner.intValue))
    }

    var endReasonText: String { state.endReason?.label ?? "" }

    // MARK: - 动作

    func record(_ hit: BoardHit) {
        guard !state.finished, !state.isRoundComplete else { return }
        let pair = rule.onDart(state: state, hit: hit)
        // 早先是 `as!`：`Pair.first` 一旦为 nil 就是崩溃。拿不到新状态就保持原状态（比整页崩掉好）。
        guard let next = pair.first else { return }
        state = next
        lastEventText = eventText(pair.second)
    }

    /// 本轮三镖（加赛则一镖）录完，让引擎结算并换人。
    func endRound() {
        guard !state.finished, state.isRoundComplete else { return }
        let pair = rule.onRoundEnd(state: state)
        guard let next = pair.first else { return }
        state = next
        lastEventText = eventText(pair.second)
    }

    /// 中途退出：`abort` 保留已录数据、只标 `ABORT`，比直接 pop 丢掉整局好。
    func abort() {
        guard !state.finished else { return }
        state = rule.abort(state: state)
    }

    /// 下标安全版取名字：`DartEvent.Win.playerIndex` 与 `winnerIndex` 都来自引擎，
    /// 越界（例如老存档的席位更多）不该让整页崩掉。
    private func name(at index: Int) -> String? {
        guard state.players.indices.contains(index) else { return nil }
        return state.players[index].name
    }

    func resign(playerIndex: Int) {
        guard !state.finished else { return }
        state = rule.resign(state: state, loserIndex: Int32(playerIndex))
    }

    /// 把事件翻译成一句提示。文案照搬 Android 的反馈口径，口径本身由引擎给出。
    private func eventText(_ event: Any?) -> String? {
        switch event {
        case let scored as DartEvent.Scored: return "得 \(scored.points) 分"
        case let advance as DartEvent.Advance: return "推进到 \(advance.sector)"
        case let halve as DartEvent.Halve: return "总分减半：\(halve.from) → \(halve.to)"
        case let shanghai as DartEvent.Shanghai: return "上海秒杀！\(shanghai.sector) 分区"
        case let win as DartEvent.Win:
            return name(at: Int(win.playerIndex)).map { "\($0) 获胜" } ?? "本局结束"
        default: return nil
        }
    }
}
