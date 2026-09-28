import Foundation
import shared

/**
 * Count Up 练习的状态容器（对齐 Android `ui/practice/CountUpViewModel.kt`）。
 *
 * ⚠️ 结算节奏与 X01 是**两套**，不要共用触发器：
 * - Count Up：满 3 镖后延迟 480ms **自动**进下一轮（X01 是满 3 镖仍需手动确认）
 * - Count Up：未投镖按「确认」= BUST，红条闪 900ms 后由 VM 自己关掉（不是 UI 层 delay）
 */
@MainActor
@Observable
final class CountUpViewModel {

    private(set) var state: CountUpState
    private(set) var best: Int32

    /// 与 Android 的 SharedPreferences `dartvio_practice/countup_best_score` 对齐，key 用同名。
    private static let bestScoreKey = "countup_best_score"

    init() {
        state = SharedFactory.initialCountUpState()
        best = Int32(UserDefaults.standard.integer(forKey: Self.bestScoreKey))
    }

    /**
     * **长度固定为 8** 且与轮次一一对应：`nil` = 该轮未进行，`0` = 该轮 BUST。
     *
     * 所以这里不能用 `compactMap` —— 它会把 nil 压掉，导致下标错位
     *（第 5 轮还没打时，index 4 会取到第 6 轮的分）。
     */
    var roundScores: [Int32?] {
        state.roundScores.map { ($0 as? NSNumber)?.intValue }.map { $0.map(Int32.init) }
    }

    func throwDart(_ dart: Dart) {
        guard !state.finished, !state.roundLocked, !state.isRoundFull else { return }
        state = SharedAccess.countUpRules.throwDart(state: state, dart: dart)
        if state.isRoundFull {
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 480_000_000)
                self?.finalizeRound()
            }
        }
    }

    /// 未投镖时按「确认」：本轮 0 分，红条闪 900ms。
    func bust() {
        guard !state.finished, !state.roundLocked else { return }
        state = SharedAccess.countUpRules.bust(state: state)
        Task { [weak self] in
            // 对齐 Android 的 BUST_FLASH_MS = 900ms（别为了测试调长，
            // UI 测试采不到这么短的元素是测试的问题，不是这里的问题，见 UI 测试注释）
            try? await Task.sleep(nanoseconds: 900_000_000)
            self?.clearBustFlash()
        }
    }

    func undoLastDart() {
        guard !state.finished, !state.currentDarts.isEmpty else { return }
        state = SharedAccess.countUpRules.undoLastDart(state: state)
    }

    func finalizeRound() {
        guard !state.finished, state.isRoundFull || state.roundLocked else { return }
        state = SharedAccess.countUpRules.finalizeRound(state: state)
        persistBestIfNeeded()
    }

    func restart() {
        state = SharedFactory.initialCountUpState()
    }

    // MARK: - 内部

    /**
     * 清掉 BUST 闪红：这里用了 `doCopy`，因为 `CountUpState` 只有 7 个字段，
     * 全参尚可接受；`X01LegState` 那种 9 字段的就不走这条路（见 X01GameViewModel 的说明）。
     */
    private func clearBustFlash() {
        state = state.doCopy(
            roundScores: state.roundScores,
            currentRoundIndex: state.currentRoundIndex,
            currentDarts: state.currentDarts,
            dartsThrown: state.dartsThrown,
            roundLocked: state.roundLocked,
            bustFlash: false,
            finished: state.finished
        )
    }

    private func persistBestIfNeeded() {
        guard state.finished, state.totalScore > best else { return }
        best = state.totalScore
        UserDefaults.standard.set(Int(best), forKey: Self.bestScoreKey)
    }
}
