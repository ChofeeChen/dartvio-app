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

    var winnerText: String? {
        guard state.finished, let winner = state.winnerIndex else { return nil }
        return state.players[Int(winner)].name
    }

    var endReasonText: String { state.endReason?.label ?? "" }

    // MARK: - 动作

    func record(_ hit: BoardHit) {
        guard !state.finished, !state.isRoundComplete else { return }
        let pair = rule.onDart(state: state, hit: hit)
        state = pair.first as! BattleState
        lastEventText = eventText(pair.second)
    }

    /// 本轮三镖（加赛则一镖）录完，让引擎结算并换人。
    func endRound() {
        guard !state.finished, state.isRoundComplete else { return }
        let pair = rule.onRoundEnd(state: state)
        state = pair.first as! BattleState
        lastEventText = eventText(pair.second)
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
        case let win as DartEvent.Win: return "\(state.players[Int(win.playerIndex)].name) 获胜"
        default: return nil
        }
    }
}
