import Foundation
import shared

/**
 * 99 Darts 练习的状态容器，对齐 Android `ui/practice/NinetyNineViewModel.kt`。
 *
 * 玩法：选定一个扇区（1–20），99 镖（33 轮 × 3 镖）打满，按 S/D/T/MISS 记点数，累计总分。
 *
 * ⚠️ Android 版在结束时用 `AchievementProgress` 触发成就重算；那个包还在 `:app` 未下沉
 * （回写清单 §6 / D4），所以 iOS 侧**不做第二套本地成就**，只统计本次会话。
 */
@MainActor
@Observable
final class NinetyNineViewModel {

    private(set) var state: NinetyNineState

    init(sector: Int32 = 20) {
        state = SharedAccess.ninetyNineRules.doNewSession(sector: sector)
    }

    var totalDarts: Int32 { NinetyNineEngineKt.NINETY_NINE_TOTAL_DARTS }
    var totalRounds: Int32 { NinetyNineEngineKt.NINETY_NINE_ROUNDS }

    /// 当前轮已投几镖，用于显示「本轮 x/3」。
    var dartsInRound: Int32 { state.dartsInCurrentRound }

    func record(_ hit: SectorHit) {
        guard !state.finished else { return }
        state = SharedAccess.ninetyNineRules.record(state: state, hit: hit)
    }

    func undo() {
        guard !state.throws.isEmpty else { return }
        state = SharedAccess.ninetyNineRules.undo(state: state)
    }

    func restart(sector: Int32? = nil) {
        state = SharedAccess.ninetyNineRules.doNewSession(sector: sector ?? state.sector)
    }
}
