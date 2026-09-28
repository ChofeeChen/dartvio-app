import Foundation
import Observation
import UIKit
import shared

/**
 * Cricket 正式对局的状态容器，对齐 Android `CricketGameViewModel` 的双层结构。
 *
 * ## 内层 state / 外层 state 在这里对应什么
 *
 *  cricket 不存在 bust，UI 需要**每一镖落地后立刻显示**凭证箱里的标记数变化。
 *  所以这里维护两个 state：
 *
 * - `leg`：**回合开始时的基线**。注意引擎每镖返回的是「已把这一镖写进 currentTurnDarts」的状态，
 *   不能直接拿来回喂下一次预览，否则同一镖会被叠加两次；
 * - `preview`：逐镖预览出的实时状态（`applySingleDart` 的返回值）。
 *
 *  撤销 = 把剩余镖**从基线重放一遍**（`replay()`）—— 而不是倒着调用引擎；
 *  cricket 的撤销要这么正是因为没有 bust 之类的单向可以直接回滚。
 *
 * ## 谁说了算
 *
 * **胜负条件不由 UI 判断**：回合交给 `applyTurn` 一次性提交，
 * 赢没赢、轮到谁、`endedByRoundLimit` 全在引擎里 ——
 * `CricketRules.isRoundLimitWin` 上还有一条**已申报未解决的平分口径缺口**（并列时由轮到的那一方获胜），
 * 这个行为必须两端一致；在 iOS 侧重写一遍等于把这个缺陷同时固化到两份代码里。
 */
@MainActor
@Observable
final class CricketGameViewModel {

    let launch: CricketLaunch

    private(set) var leg: CricketLegState
    private(set) var preview: CricketLegState?
    private(set) var turnDarts: [ClaimedDart] = []
    private(set) var turnHits: [CricketHitResult?] = []
    private(set) var statusText: String?
    private(set) var aiThinking = false
    private(set) var legNumber: Int32 = 1
    private(set) var legsWon: [Int32]
    private(set) var matchWinnerIndex: Int?

    /// AI 每镖之间的间隔：太快看不清它打了哪里，太慢玩家会以为卡住。
    private let aiStepNanoseconds: UInt64 = 700_000_000
    /// ⚠️ `deinit` 访问 MainActor 隔离的属性会被 Swift 并发检查拒绝（"nonisolated context"），
    /// 所以这里没有 `deinit { aiTask?.cancel() }` —— Task 内部已只用 `weak self`，
    /// 页面销毁后它自然停在下一个 `await` 上。
    private var aiTask: Task<Void, Never>?
    // ⚠️ 随机源必须走 `SharedAccess.newRandom()`（= `KotlinRandom.Default.shared`）。
    // 直接 `KotlinRandom()` 是在调**抽象类**的构造器：一初始化就 `AbstractClassConstructorCalled`
    // 崩溃（V14 的崩溃根因）—— 头文件里有 init 符号，编译能过，运行必炸。
    private let random = SharedAccess.newRandom()
    private var config: MatchConfig { leg.config }

    init(launch: CricketLaunch) {
        self.launch = launch
        let count = CricketSetupMapping.players(from: launch).count
        self.legsWon = [Int32](repeating: 0, count: count)
        self.leg = CricketRules.shared.doNewLeg(
            config: CricketSetupMapping.config(from: launch),
            players: CricketSetupMapping.players(from: launch),
            legNumber: 1
        )
    }


    // MARK: - 派生读法

    var displayLeg: CricketLegState { preview ?? leg }

    var targets: [any CricketTarget] { config.cricketTargets }

    var players: [CricketPlayerState] { displayLeg.players }

    var currentIndex: Int { Int(displayLeg.currentPlayerIndex) }

    var isCurrentHuman: Bool { currentIndex == 0 }

    var isInputLocked: Bool { leg.isFinished || aiThinking || !isCurrentHuman || matchWinnerIndex != nil }

    var variantLabel: String { CricketSetupMapping.variantLabel(launch.variantRaw) }

    var statusMessage: String? { statusText }

    /**
     * 记分矩阵里一格的内容：按 Cricket 的惯例用「/ // ✕」表示已打 1 / 2 / 3 次。
     *
     * ✕ 表示**该玩家已经关闭这个目标**：此后别人在上面还能得分，自己得分则不再累加。
     */
    func marksDisplay(target: any CricketTarget, playerIndex: Int) -> String {
        guard players.indices.contains(playerIndex) else { return "" }
        let count = players[playerIndex].marks.marksOf(target: target)
        switch count {
        case 0: return ""
        case 1: return "/"
        case 2: return "//"
        default: return "X"
        }
    }

    func isClosed(target: any CricketTarget, playerIndex: Int) -> Bool {
        guard players.indices.contains(playerIndex) else { return false }
        return players[playerIndex].marks.isClosed(target: target)
    }

    /// 目标行的展示名（Bull 的 token 是 "25"，但板面口径写 BULL —— 与 Android 一致）。
    func targetDisplay(_ target: any CricketTarget) -> String {
        if let number = target as? CricketTargetNumber, number.value == 25 { return "BULL" }
        return target.display
    }

    func hitText(_ hit: CricketHitResult?) -> String? {
        guard let hit else { return "无关镖" }
        if hit.marksGained > 0 && hit.scoreGained > 0 {
            return "\(targetDisplay(hit.target)) +\(hit.scoreGained)"
        }
        if hit.marksGained > 0 {
            return targetDisplay(hit.target)
        }
        if hit.scoreSuppressedByOverkill { return "\(targetDisplay(hit.target)) 已关闭" }
        guard isAnyTarget(hit.target) else { return "无关镖" }
        return "\(targetDisplay(hit.target)) 无收益"
    }

    /**
     * 这一镖的目标是目标集里的吗。
     *
     * ⚠️ 不能用 `isEqual`：`any CricketTarget` 是 ObjC protocol 的存在体，
     * 只暴露 `display` / `token`，没有 NSObjectProtocol 的方法。
     * 比 `token` 反而更合适 —— 注释里写明 token 是**冻结的持久化契约**，唯一且稳定。
     */
    private func isAnyTarget(_ target: any CricketTarget) -> Bool {
        targets.contains { $0.token == target.token }
    }

    // MARK: - 动作

    func throwDart(_ dart: Dart) {
        guard !isInputLocked, turnDarts.count < 3 else { return }
        statusText = nil
        let claimed = SharedFactory.claimed(dart: dart, claim: DartClaim.number)
        let pair = CricketRules.shared.applySingleDart(state: displayLeg, claimed: claimed)
        guard let next = pair.first else { return }
        preview = next
        turnDarts.append(claimed)
        turnHits.append(pair.second)
    }

    func undoLastDart() {
        guard canUndoLastDart else { return }
        turnDarts.removeLast()
        turnHits.removeLast()
        replay()
    }

    /// 只能撤**还没提交**的镖：一旦 `applyTurn` 提交过，回合数已经算进 players，
    /// 再回放就不是同一个回合了。
    var canUndoLastDart: Bool { !isInputLocked && !turnDarts.isEmpty }

    /** 未提交前重放：cricket 的 preview 是幂等的（同一批镖从同一个基線結果相同），所以重放安全。 */
    private func replay() {
        var state = leg
        var hits: [CricketHitResult?] = []
        for claimed in turnDarts {
            let pair = CricketRules.shared.applySingleDart(state: state, claimed: claimed)
            guard let next = pair.first else { continue }
            state = next
            hits.append(pair.second)
        }
        preview = turnDarts.isEmpty ? nil : state
        turnHits = hits
    }

    func commitTurn() {
        guard !isInputLocked, !turnDarts.isEmpty else { return }
        let triple = CricketRules.shared.applyTurn(state: leg, darts: turnDarts)
        guard let settled = triple.first else { return }
        turnDarts = []
        turnHits = []
        preview = nil
        leg = settled
        if settled.isFinished {
            registerLegWin(settled)
        } else {
            startAiTurnIfNeeded()
        }
    }

    /// 键盘传递过来的「确认」= 完成本回合。
    func finishTurn() {
        commitTurn()
    }

    // MARK: - AI

    private func startAiTurnIfNeeded() {
        guard config.matchType.isEqual(MatchType.cricket), !leg.isFinished, !isCurrentHuman else { return }
        guard matchWinnerIndex == nil else { return }
        aiThinking = true
        aiTask?.cancel()
        aiTask = Task { [weak self] in
            guard let self else { return }
            let difficulty = X01SetupMapping.difficulty(launch.difficultyRaw)
            let darts = CricketAi.shared.generateTurn(leg: leg, difficulty: difficulty, random: random)
            for claimed in darts {
                if Task.isCancelled { return }
                try? await Task.sleep(nanoseconds: aiStepNanoseconds)
                await MainActor.run { self.applyClaimed(claimed) }
            }
            try? await Task.sleep(nanoseconds: aiStepNanoseconds)
            await MainActor.run {
                self.aiThinking = false
                self.commitTurn()
            }
        }
    }

    /// AI 的镖也走同一条 preview 路径，只是 AI 自己已经把 claim 判好了（直接透传，不再重判）。
    private func applyClaimed(_ claimed: ClaimedDart) {
        guard turnDarts.count < 3 else { return }
        let pair = CricketRules.shared.applySingleDart(state: displayLeg, claimed: claimed)
        guard let next = pair.first else { return }
        preview = next
        turnDarts.append(claimed)
        turnHits.append(pair.second)
    }

    // MARK: - 多局

    private func registerLegWin(_ settled: CricketLegState) {
        guard let winner = settled.winnerIndex else { return }
        let index = Int(winner.intValue)
        guard legsWon.indices.contains(index) else { return }
        legsWon[index] += 1
        if legsWon[index] >= config.legsToWin {
            matchWinnerIndex = index
            return
        }
        legNumber += 1
        leg = CricketLegState(
            config: config,
            players: settled.players.enumerated().map { offset, player in
                CricketPlayerState(
                    playerId: player.playerId,
                    marks: CricketMarks(marks: [:]),
                    score: 0,
                    legsWon: legsWon[offset],
                    turnsPlayed: 0
                )
            },
            currentPlayerIndex: 0,
            currentTurnDarts: [],
            legNumber: legNumber,
            isFinished: false,
            winnerIndex: nil,
            turnStartPlayers: nil,
            endedByRoundLimit: false
        )
        statusText = "\(playerName(index)) 拿下一局，开始第 \(legNumber) 局"
    }

    func playerName(_ index: Int) -> String {
        CricketSetupMapping.players(from: launch)[safe: index]?.name ?? "选手 \(index + 1)"
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
