import Foundation
import shared

/**
 * 本地 X01 对局的状态容器（对齐 Android `ui/game/GameViewModel.kt` 的编排语义）。
 *
 * ⚠️ 与 Android 版最大的一处不同：**Swift 侧不照抄 `leg.copy(currentTurnDarts = …)`**。
 * Kotlin data class 的 copy 导出到 ObjC 后变成必须写满全部字段的 `doCopy`，
 * `X01LegState` 有 9 个字段，为了改一个字段要重复传 8 个自身值，不可维护。
 *
 * 因此这里采用「**不可变基准局 + Swift 侧独占回合数组**」：
 * - `leg` 只能被 `applyTurn` 的返回值整体替换，Kotlin 侧当纯函数引擎用
 * - `turnDarts` 是本回合已录的镖，完全由 Swift 持有
 * - 实时预览走 `previewRemaining(state:darts:)`，不需要把镖写回 Kotlin state
 *
 * 副产品：AI 停手判据 `turnDarts.isEmpty`（同 Android 的 `turnDartsCount == 0`）
 * 必须依赖「applyTurn 返回后才清空」这一点，二者是配套的，不要拆开改。
 */
@MainActor
@Observable
final class X01GameViewModel {

    // MARK: - 对外状态

    private(set) var leg: X01LegState
    private(set) var turnDarts: [Dart] = []
    private(set) var message: String?
    private(set) var inputLocked = false
    private(set) var legsWon: [String: Int] = [:]
    private(set) var matchWinnerId: String?
    private(set) var showLegSummary = false
    private(set) var legNumber: Int32 = 1

    let config: MatchConfig
    let players: [Player]

    private let random = SharedAccess.newRandom()
    private var aiTask: Task<Void, Never>?
    private var aiDifficultyById: [String: AiDifficulty] = [:]
    /**
     * 每个**机器人席位**一个自适应控制器（key = playerId）。
     *
     * ⚠️ 之前这里没有控制器，画像直接由 `AiProfile(ppr: seat.ppr)` 现算 ——
     * 而 `seat.ppr` 是**机器人自己**的历史 PPR，首回合 `dartsThrown == 0` ⇒ `ppr == 0` ⇒
     * `hitChanceFor(0) = 0` ⇒ 机器人第一回合必偏，之后也在「自己的实际表现」和档位中值之间乱跳，
     * 完全没有自适应。现在改成引擎的 `AdaptiveAiController`：
     * 起算点是档位中值，真人每回合结算后喂 `recordHumanTurn`，机器人跟着真人的手感微调
     * （且始终 clamp 在档位区间内，不会跨档）。
     */
    private var aiControllers: [String: AdaptiveAiController] = [:]

    init(config: MatchConfig, players: [Player]) {
        self.config = config
        self.players = players
        self.leg = SharedFactory.newX01Leg(config: config, players: players, legNumber: 1)
        // @Observable 展开后，通过下标修改 self 上的属性算「使用 self」，
        // 必须等所有存储属性（含 leg）初始化完再做，否则报 used before being initialized
        for player in players {
            guard let difficulty = player.aiDifficulty else { continue }
            aiDifficultyById[player.id] = difficulty
            aiControllers[player.id] = AdaptiveAiController.companion.forMatch(
                config: config,
                difficulty: difficulty,
                smartEnabled: config.smartAi
            )
        }
        scheduleAiTurnIfNeeded()
    }

    // MARK: - 派生

    var currentPlayerId: String { leg.currentPlayer.playerId }

    var currentPlayerName: String {
        players.first { $0.id == currentPlayerId }?.name ?? "玩家"
    }

    var isCurrentPlayerAi: Bool {
        players.first { $0.id == currentPlayerId }?.type == PlayerType.ai
    }

    var previewedRemaining: Int32 {
        turnDarts.isEmpty
            ? leg.currentRemaining
            : SharedAccess.x01Rules.previewRemaining(state: leg, darts: turnDarts)
    }

    var turnScore: Int32 { DartText.total(turnDarts) }

    var confirmTitle: String { turnDarts.count >= 3 ? "结束回合" : "确认" }

    var canInput: Bool { !leg.isFinished && !inputLocked && !showLegSummary && !isCurrentPlayerAi }

    // MARK: - 人类输入

    func throwDart(_ dart: Dart) {
        guard canInput, turnDarts.count < 3 else { return }
        recordDart(dart, fromAI: false)
    }

    func undoLastDart() {
        guard canInput, !turnDarts.isEmpty else { return }
        turnDarts.removeLast()
        message = nil
    }

    func commitTurn() {
        guard canInput, !turnDarts.isEmpty else { return }
        commit(darts: turnDarts)
    }

    // MARK: - 局间流转

    /** 供 View 层的 sheet binding 关闭用（`showLegSummary` 是 private(set)，不对外可写）。 */
    func dismissLegSummary() {
        showLegSummary = false
    }

    func continueToNextLeg() {
        showLegSummary = false
        message = nil
        turnDarts = []
        legNumber += 1
        leg = SharedFactory.newX01Leg(config: config, players: players, legNumber: legNumber)
        scheduleAiTurnIfNeeded()
    }

    func restartMatch() {
        aiTask?.cancel()
        aiTask = nil
        showLegSummary = false
        matchWinnerId = nil
        message = nil
        turnDarts = []
        legsWon = [:]
        legNumber = 1
        leg = SharedFactory.newX01Leg(config: config, players: players, legNumber: 1)
        scheduleAiTurnIfNeeded()
    }

    // MARK: - 核心：录镖与结算

    private func recordDart(_ dart: Dart, fromAI: Bool) {
        guard !leg.isFinished, turnDarts.count < 3 else { return }
        let darts = turnDarts + [dart]
        // 试算：只有「结镖 / BUST」立即落账，否则继续等玩家投满三镖或手动确认
        let pair = SharedAccess.x01Rules.applyTurn(state: leg, darts: darts)
        guard let probeLeg = pair.first, let outcome = pair.second else { return }

        if outcome.won || outcome.result == TurnResult.bust {
            leg = probeLeg          // 直接采用试算结果，避免重复计算
            turnDarts = []
            handleOutcome(outcome, fromAI: fromAI)
        } else {
            turnDarts = darts
        }
    }

    private func commit(darts: [Dart]) {
        let pair = SharedAccess.x01Rules.applyTurn(state: leg, darts: darts)
        guard let newLeg = pair.first, let outcome = pair.second else { return }
        leg = newLeg
        turnDarts = []
        handleOutcome(outcome, fromAI: false)
    }

    private func handleOutcome(_ outcome: TurnOutcome, fromAI: Bool) {
        message = outcome.message
        // 自适应只学**真人**：把机器人自己的回合喂回去会变成正反馈（它越准就越准）。
        if !fromAI, players.first(where: { $0.id == outcome.playerId })?.type == PlayerType.human {
            let thrown = Int32(outcome.darts.count)
            for controller in aiControllers.values {
                controller.recordHumanTurn(scored: outcome.scored, darts: thrown)
            }
        }
        if outcome.won {
            handleLegWin(outcome)
            return
        }
        if !fromAI { lockInputBriefly() }
        scheduleAiTurnIfNeeded()
    }

    private func handleLegWin(_ outcome: TurnOutcome) {
        let playerId = outcome.playerId
        legsWon[playerId, default: 0] += 1
        if legsWon[playerId, default: 0] >= config.legsToWin {
            matchWinnerId = playerId
        } else {
            showLegSummary = true
        }
    }

    /** 对齐 Android：每镖录入后锁输入 0.5s，让玩家看清三镖区。 */
    private func lockInputBriefly() {
        inputLocked = true
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)
            self?.inputLocked = false
        }
    }

    // MARK: - AI 编排

    private func scheduleAiTurnIfNeeded() {
        guard aiTask == nil, !leg.isFinished, isCurrentPlayerAi else { return }
        aiTask = Task { [weak self] in
            await self?.runAiLoop()
            self?.aiTask = nil
        }
    }

    private func runAiLoop() async {
        while !Task.isCancelled {
            guard !leg.isFinished, isCurrentPlayerAi else { break }

            let seat = leg.currentPlayer
            let difficulty = aiDifficultyById[seat.playerId] ?? .intermediate
            // 画像取自控制器：它给出的 PPR 是「档位中值 + 跟着真人微调」，而不是机器人自己的历史值。
            let profile = aiControllers[seat.playerId]?.profile()
                ?? SharedFactory.aiProfile(difficulty: difficulty, ppr: Double(difficulty.ppr))
            let remaining = leg.currentRemaining
            let darts = SharedAccess.x01Ai.generateTurn(
                remaining: remaining,
                profile: profile,
                config: leg.config,
                hasOpened: seat.hasOpened,
                random: random
            )

            for dart in darts {
                if Task.isCancelled { return }
                recordDart(dart, fromAI: true)
                // 本镖已被自动结算（BUST / 结镖）→ turnDarts 被清空，停止续投
                if turnDarts.isEmpty { break }
                let delay = UInt64(max(profile.nextDelayMs(random: random), 0)) * 1_000_000
                try? await Task.sleep(nanoseconds: delay)
            }

            if Task.isCancelled { return }
            if turnDarts.isEmpty {
                let half = UInt64(max(profile.nextDelayMs(random: random) / 2, 0)) * 1_000_000
                try? await Task.sleep(nanoseconds: half)
            }
            commit(darts: turnDarts)
            try? await Task.sleep(nanoseconds: 300_000_000)
        }
    }
}
