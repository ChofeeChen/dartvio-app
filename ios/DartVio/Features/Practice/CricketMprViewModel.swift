import Foundation
import shared

/**
 * Cricket MPR 挑战的状态容器，对齐 Android `ui/practice/CricketMprViewModel.kt`。
 *
 * MPR（Marks Per Round）的**算法在 shared 里**（`CricketMprState.mpr`），
 * rating 文案同样由 `mprRating` 给出 —— iOS 不重新推导评级区间。
 *
 * ⚠️ Android 版结束时接了 `AchievementProgress`（未下沉），iOS 侧跳过该回调，原因见 NinetyNineViewModel 注释。
 */
@MainActor
@Observable
final class CricketMprViewModel {

    private(set) var state: CricketMprState

    init() {
        state = SharedAccess.cricketMprRules.doNewSession()
    }

    var totalRounds: Int32 { CricketMprEngineKt.CRICKET_MPR_ROUNDS }
    var maxMarksPerRound: Int32 { CricketMprEngineKt.CRICKET_MPR_MARKS_PER_ROUND_MAX }

    /// Kotlin 的 `mpr` 是 Float，导出为 float，别写成 Double。
    var mprText: String { CricketMprEngineKt.formatMpr(mpr: state.mpr) }
    var ratingText: String { CricketMprEngineKt.mprRating(mpr: state.mpr) }

    func record(_ dart: Dart) {
        guard !state.finished else { return }
        state = SharedAccess.cricketMprRules.record(state: state, dart: dart)
    }

    func undo() {
        guard !state.darts.isEmpty else { return }
        state = SharedAccess.cricketMprRules.undo(state: state)
    }

    func finishEarly() {
        guard !state.finished else { return }
        state = SharedAccess.cricketMprRules.finishEarly(state: state)
    }

    func restart() {
        state = SharedAccess.cricketMprRules.doNewSession()
    }
}
