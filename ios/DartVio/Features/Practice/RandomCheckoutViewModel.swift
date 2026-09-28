import Foundation
import shared

/**
 * 随机结镖练习（路线学习）的状态容器，对齐 Android `ui/practice/RandomCheckoutViewModel.kt`。
 *
 * 玩法：引擎随机给一个目标分，玩家最多 3 镖把它双结掉；
 * 爆分 / 剩 1 / 三镖没结掉 → 本题结束，可以看标准答案。
 *
 * ⚠️ 这里刻意**没有**照搬 Android 的成就回调：
 * Android 版 `throwDart` 结束时会调 `PracticeSessionTrigger` 重算成就，
 * 而 `domain/achievement` 还留在 `:app`、没有下沉到 commonMain（见回写清单 §6 的 D4）。
 * iOS 侧只统计 attempts / successes，**不另做一套本地成就**，等下沉后再补触发器。
 */
@MainActor
@Observable
final class RandomCheckoutViewModel {

    private(set) var state: RandomCheckoutState
    private(set) var attempts: Int
    private(set) var successes: Int

    /// 与 Android 的 `SharedPreferences("dartvio_practice")` 同名 key 对齐，
    /// 将来真要做双端数据互通时不用再改key名（目前 iOS 写入的是 UserDefaults，两边仍互不相通）。
    private static let attemptsKey = "random_checkout_attempts"
    private static let successesKey = "random_checkout_successes"

    init() {
        attempts = UserDefaults.standard.integer(forKey: Self.attemptsKey)
        successes = UserDefaults.standard.integer(forKey: Self.successesKey)
        state = SharedFactory.randomCheckoutTarget()
    }

    /// Android 侧算的是 `successes * 100 / attempts`，同样是整除，别改浮点。
    var successRate: Int { attempts == 0 ? 0 : successes * 100 / attempts }

    /// 已投镖数（引擎侧上限就是 3，超出再投会被 `throwDart` 原样返回）。
    var dartsThrown: Int { state.darts.count }

    /// 标准答案文案复用 shared 的实现（含送镖顺序），不在这里另写一套格式化。
    var answerText: String {
        state.bestRoute.isEmpty ? "" : SharedAccess.checkoutSolver.formatRoute(route: state.bestRoute)
    }

    var hasAnswer: Bool { !state.bestRoute.isEmpty }

    // MARK: - 动作

    func throwDart(_ dart: Dart) {
        guard !state.isFinished, state.darts.count < 3 else { return }
        let wasFinished = state.isFinished
        state = SharedAccess.randomCheckoutRules.throwDart(state: state, dart: dart)
        if !wasFinished, state.isFinished { record(success: isSuccess) }
    }

    /// 重做本题（同一目标分）：对齐 Android 的 `retry()`。
    func retry() {
        state = SharedAccess.randomCheckoutRules.retry(state: state)
    }

    /// 换下一题：目标分与路线都重新生成。
    func nextTarget() {
        state = SharedFactory.randomCheckoutTarget()
    }

    func toggleAnswer() {
        state = SharedAccess.randomCheckoutRules.toggleAnswer(state: state)
    }

    func resetStats() {
        attempts = 0
        successes = 0
        UserDefaults.standard.removeObject(forKey: Self.attemptsKey)
        UserDefaults.standard.removeObject(forKey: Self.successesKey)
    }

    // MARK: - 结果解读

    /// Kotlin enum 导出成 ObjC 后是 class 实例，**Swift 侧不会自动合成 Equatable**，
    /// 所以用 `isEqual`（Kotlin 按 name/ordinal 实现）而不是 `==`。
    private func matches(_ candidate: CheckoutResult) -> Bool { candidate.isEqual(state.result) }

    var isSuccess: Bool { matches(CheckoutResult.success) }
    var isFailBust: Bool { matches(CheckoutResult.failBust) }
    var isFailNoCheckout: Bool { matches(CheckoutResult.failNoCheckout) }

    /// 对齐 Android `when (state.result)` 的结果文案。
    var resultTitle: String {
        if isSuccess { return "结镖成功" }
        if isFailBust { return "爆分" }
        if isFailNoCheckout { return "三镖未结" }
        return ""
    }

    // MARK: - 内部

    private func record(success: Bool) {
        attempts += 1
        if success { successes += 1 }
        UserDefaults.standard.set(attempts, forKey: Self.attemptsKey)
        UserDefaults.standard.set(successes, forKey: Self.successesKey)
    }
}
