import Foundation
import shared

/**
 * 极速挑战，对齐 Android `ui/practice/CheckoutRushViewModel.kt`。
 *
 * ⚠️ 这是**刻意简化后**的实现，两处没有照搬 Android：
 * 1. **不落库**：Android 把每次尝试写 Room 并按 sessionId 聚合历史；iOS 侧暂无 shared 的
 *    会话表（`CheckoutRushSession` 的数据models仍与 Room 耦合），所以**只在内存里聚合本次会话**，
 *    退出即丢。等 D4/D5 下沉后再把 `_records` 换成持久化实现，本文件对外接口不用动。
 * 2. **不做输入模式切换**：Android 支持键盘/靶盘点选切换（`requestSwitchInput`），iOS MVP 只有键盘。
 *
 * 其余口径**全部交给 shared 判定**，不在 iOS 侧重写第二套：
 * - 是否结镖 / 是否爆分 / 用镖数：`CheckoutRushRules.evaluate`
 * - 成功率、连胜、爆分分布：`CheckoutRushStatistics.of(records:)`
 */
@MainActor
@Observable
final class CheckoutRushViewModel {

    enum Phase: Equatable {
        case solving
        case revealed(result: RushResult)
        case finished
    }

    private(set) var phase: Phase = .solving
    private(set) var questionIndex = 1
    private(set) var darts: [Dart] = []
    private(set) var currentTarget: CheckoutTarget
    private(set) var outcome: CheckoutRushOutcome?
    private(set) var showRouteHint = false
    private(set) var elapsedMs: Int64 = 0
    private(set) var statistics = CheckoutRushStatistics.companion.empty()

    /// 一场 10 题，与 Android 的会话长度口径一致。
    let totalQuestions = 10

    private let difficulty: RushDifficulty
    private let rules = SharedAccess.checkoutRushRules
    private let targetFactory = SharedAccess.checkoutTargetFactory

    private var recentTargets: Set<Int> = []
    private var records: [RushAttemptRecord] = []
    private var startedAt: Date?

    init(difficulty: RushDifficulty = .mixed) {
        self.difficulty = difficulty
        currentTarget = CheckoutTarget(score: 0, routes: [])
        drawTarget()
        startedAt = Date()
    }

    // MARK: - 派生量

    var remaining: Int32 {
        rules.evaluate(target: currentTarget.score, darts: darts).remainingAfter
    }

    /**
     * 本题是否做完。
     *
     * ⚠️ 不能只用 `isTerminal`：引擎的 `isTerminal` 只覆盖「结镖 / 爆分」两种**已分胜负**的情形，
     * 三镖投完仍未完成时它返回 false，但对玩家来说这一题已经结束了 ——
     * 对应的正是 `RushResult.NOT_FINISHED`（会计入成功率分母）。所以镖数用满同样算做完。
     */
    var isQuestionOver: Bool {
        rules.isTerminal(target: currentTarget.score, darts: darts)
            || darts.count >= Int(CheckoutRushRules.shared.MAX_DARTS)
    }

    var preferredRouteText: String {
        currentTarget.preferredRoute.map(DartText.label).joined(separator: " → ")
    }

    func secondsText(_ millis: Int64) -> String {
        String(format: "%.1fs", Double(millis) / 1000)
    }

    /**
     * **正在走**的用时。
     *
     * `elapsedMs` 只在投镖那一刻固化（`tick()`），是给 `RushAttemptRecord.throwElapsedMs` 用的；
     * 拿它去显示就变成「不投镖时间不走」—— 对一个限时模式来说等于没有计时。
     * 所以展示口径按 `startedAt` 现算，由视图层定期重绘（见 `CheckoutRushPracticeView`）。
     */
    var currentElapsedMs: Int64 {
        guard let startedAt else { return 0 }
        return Int64(Date().timeIntervalSince(startedAt) * 1000)
    }

    var elapsedSecondsText: String { secondsText(currentElapsedMs) }

    // MARK: - 动作

    func addDart(_ dart: Dart) {
        guard case .solving = phase, !isQuestionOver else { return }
        darts.append(dart)
        tick()
        if isQuestionOver {
            let evaluated = rules.evaluate(target: currentTarget.score, darts: darts)
            outcome = evaluated
            phase = .revealed(result: evaluated.result)
            record(result: evaluated.result, remainingAfter: evaluated.remainingAfter, bustReason: evaluated.bustReason)
        }
    }

    /// 撤销上一镖（仅在本题还没定局时可用）。
    func undoLastDart() {
        guard case .solving = phase, !darts.isEmpty else { return }
        darts.removeLast()
    }

    func toggleRouteHint() {
        showRouteHint.toggle()
    }

    /// 跳过：按 shared 的口径记 SKIPPED，**不进成功率分母**。
    func skip() {
        record(result: RushResult.skipped, remainingAfter: remaining, bustReason: nil)
        advance()
    }

    /// 本题重来：换一次尝试，目标分不变。
    func retry() {
        darts = []
        showRouteHint = false
        outcome = nil
        phase = .solving
        startedAt = Date()
        elapsedMs = 0
    }

    /// 定局后进入下一题；最后一题则收尾出战报。
    func next() {
        guard case .revealed = phase else { return }
        advance()
    }

    // MARK: - 内部

    private func advance() {
        if questionIndex >= totalQuestions {
            finish()
            return
        }
        questionIndex += 1
        darts = []
        outcome = nil
        showRouteHint = false
        phase = .solving
        drawTarget()
        startedAt = Date()
        elapsedMs = 0
    }

    private func finish() {
        // 收尾时也固化一次：本页之后不再计时，展示要停在最后一眼看到的值上。
        tick()
        // statistics.of 要求按 createdAt 升序传入 —— 记录本来就是顺序 append 的。
        statistics = CheckoutRushStatistics.companion.of(records: records)
        phase = .finished
    }

    /// 战报页的「再练一场」：把整场重置回第 1 题，题面也重新抽。
    func restartSession() {
        questionIndex = 1
        darts = []
        outcome = nil
        showRouteHint = false
        phase = .solving
        records = []
        recentTargets = []
        statistics = CheckoutRushStatistics.companion.empty()
        drawTarget()
        startedAt = Date()
        elapsedMs = 0
    }

    private func drawTarget() {
        // `recent` 在 ObjC 里是 `NSSet<SharedInt *> *`（Swift 侧改名为 KotlinInt）：最近出过的题，
        // 用于让 10 题里尽量不重复出现同一分数。
        var recent = Set<KotlinInt>()
        recentTargets.forEach { recent.insert(KotlinInt(int: Int32($0))) }
        currentTarget = targetFactory.nextTarget(
            difficulty: difficulty,
            recent: recent,
            random: SharedAccess.newRandom()
        )
        recentTargets.insert(Int(currentTarget.score))
    }

    private func tick() {
        guard let startedAt else { return }
        elapsedMs = Int64(Date().timeIntervalSince(startedAt) * 1000)
    }

    private func record(result: RushResult, remainingAfter: Int32, bustReason: BustReason?) {
        records.append(
            SharedFactory.rushAttemptRecord(
                target: currentTarget.score,
                difficulty: difficulty,
                darts: darts,
                throwElapsedMs: elapsedMs,
                routeHintUsed: showRouteHint,
                result: result,
                remainingAfter: remainingAfter,
                bustReason: bustReason,
                createdAt: Int64(Date().timeIntervalSince1970 * 1000)
            )
        )
    }
}
