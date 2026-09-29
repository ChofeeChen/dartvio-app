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

    /**
     * 一局结束的结果（**不是**"赢下整场比赛"）。
     *
     * 有值 = 弹出 Leg 胜利弹窗，并**停在这里等玩家确认**（不再自动开下一局）——
     * 自动开下一局会把"你赢了这一局"这个事实从屏幕上抹掉，玩家回头看到的是新一局的空板。
     */
    private(set) var legResult: CricketLegResult?

    // MARK: 倒计时（本回合）

    /// 一回合的思考/投掷时限（秒）。需求只说"倒计时"，没给数值，取 30（一轮 3 镖的常规限时）。
    static let turnSeconds = 30
    private(set) var remainingSeconds = turnSeconds
    private(set) var isPaused = false
    private var timerTask: Task<Void, Never>?

    /// 回合级撤销用的快照栈：每次提交前压一份（**提交前**，不是提交后）。
    private var history: [CricketSnapshot] = []
    /// 只留最近 20 步：一局最多几十个回合，全留着没必要，且撤销不能无限退。
    private static let historyLimit = 20

    /// AI 每镖之间的间隔：太快看不清它打了哪里，太慢玩家会以为卡住。
    private let aiStepNanoseconds: UInt64 = 700_000_000
    /// ⚠️ `deinit` 访问 MainActor 隔离的属性会被 Swift 并发检查拒绝（"nonisolated context"），
    /// 所以这里没有 `deinit { aiTask?.cancel() }` —— Task 内部已只用 `weak self`，
    /// 页面销毁后它自然停在下一个 `await` 上。
    private var aiTask: Task<Void, Never>?
    /**
     * AI 任务的**世代令牌**。
     *
     * ⚠️ 光有 `Task.cancel()` 不够：取消是协作式的，而 AI 任务尾部的 `commitTurn`
     * 与已排队的 `applyClaimed` 都不会再查取消标记 —— 撤销后它们照样把镖写进**本人**回合
     * （V20 抓到的正是这个：撤销完镖位里出现对手的 D16）。
     * 所以撤销 / 换局时递增这个令牌，任务在每次落地前比对，过期即退出。
     */
    private var aiGeneration = 0
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

    /// 键盘的数字键（Bull 单独成键，不在这里）：由引擎的目标集推导，不写死 15–20。
    var targetNumbers: [Int32] {
        targets.compactMap { ($0 as? CricketTargetNumber)?.value }
            .filter { $0 != 25 }
            .sorted(by: >)
    }

    /// 顶栏的先胜局数。
    var configLegsToWin: Int32 { config.legsToWin }

    /// 顶栏 / 弹窗上的比分，如 "1 : 0"。
    var legsScoreText: String { legsWon.map(String.init).joined(separator: " : ") }

    var players: [CricketPlayerState] { displayLeg.players }

    var currentIndex: Int { Int(displayLeg.currentPlayerIndex) }

    var isCurrentHuman: Bool { currentIndex == 0 }

    var isInputLocked: Bool { leg.isFinished || aiThinking || !isCurrentHuman || matchWinnerIndex != nil }

    var variantLabel: String { CricketSetupMapping.variantLabel(launch.variantRaw) }

    var statusMessage: String? { statusText }

    /**
     * 记分矩阵里一格的内容：**按移动端规范**用「/ → X → ⊠」表示已打 1 / 2 / 3 次。
     *
     * ⊠ 表示**该玩家已经关闭这个目标**：此后别人在上面还能得分，自己得分则不再累加。
     *
     * ⚠️ 与 Android 的「/ // X」是两套写法（Android 用两条斜杠表示 2 次）：
     * 移动端规范指定 2 次画 X、3 次画 ⊠，这里按规范来，Android 侧待同步。
     */
    func marksDisplay(target: any CricketTarget, playerIndex: Int) -> String {
        guard players.indices.contains(playerIndex) else { return "" }
        let count = players[playerIndex].marks.marksOf(target: target)
        switch count {
        case 0: return ""
        case 1: return "/"
        case 2: return "X"
        default: return "⊠"
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

    /// 撤掉本回合第 `index` 镖（点镖位格子撤回），未提交的镖走重放，与撤销上一**轮**是两件事。
    func removeDart(at index: Int) {
        guard canUndoLastDart, turnDarts.indices.contains(index) else { return }
        turnDarts.remove(at: index)
        turnHits.remove(at: index)
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

    /**
     * 提交本回合。
     *
     * - Parameter allowEmpty: 是否允许"空轮提交"（一镖没录）。倒计时归零时用 `true`
     *   —— 超时且没投镖也是一个回合（引擎的 `endTurn` 照样给该玩家计一轮）。
     */
    func commitTurn(allowEmpty: Bool = false) {
        guard !isInputLocked else { return }
        guard allowEmpty || !turnDarts.isEmpty else { return }

        // 快照压在**提交前**：撤销要回到"投这一轮之前"的状态，
        // 压在提交后就会退到"投完这一轮"，等于没退。
        history.append(
            CricketSnapshot(leg: leg, legNumber: legNumber, legsWon: legsWon, matchWinnerIndex: matchWinnerIndex)
        )
        if history.count > Self.historyLimit { history.removeFirst() }

        let triple = CricketRules.shared.applyTurn(state: leg, darts: turnDarts)
        guard let settled = triple.first else { return }
        turnDarts = []
        turnHits = []
        preview = nil
        leg = settled
        stopTimer()

        if settled.isFinished {
            registerLegWin(settled)
        } else if isCurrentHuman {
            startTimer()
        } else {
            startAiTurnIfNeeded()
        }
    }

    /// 键盘传递过来的「确认」= 完成本回合。
    func finishTurn() {
        commitTurn()
    }

    // MARK: - 撤销上一轮

    var canUndoRound: Bool { !history.isEmpty }

    /**
     * 撤销上一轮（回合级），不是撤一镖。
     *
     * 为什么要连 AI 的那一轮一起退：撤销的语义是"回到我上一次投掷之前"。
     * 只退一步会停在"轮到 AI"的状态 —— 板面变了、但键盘锁着、且 AI 不会自己动，
     * 玩家看到的是一个没人能继续的死局。所以一直退到**轮到本人**为止。
     */
    func undoLastRound() {
        guard !history.isEmpty else { return }
        // 先作废在飞的 AI 回合：否则它会在撤销之后接着落镖、接着提交。
        aiGeneration += 1
        aiTask?.cancel()
        aiThinking = false
        stopTimer()
        repeat {
            let snapshot = history.removeLast()
            leg = snapshot.leg
            legNumber = snapshot.legNumber
            legsWon = snapshot.legsWon
            matchWinnerIndex = snapshot.matchWinnerIndex
        } while !history.isEmpty && !isCurrentHuman
        legResult = nil
        turnDarts = []
        turnHits = []
        preview = nil
        statusText = "已撤销上一轮"
        if isCurrentHuman { startTimer() }
    }

    // MARK: - 倒计时

    func startTimer() {
        isPaused = false
        remainingSeconds = Self.turnSeconds
        tick()
    }

    func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
    }

    func togglePause() {
        isPaused.toggle()
        if isPaused {
            stopTimer()
        } else {
            tick()
        }
    }

    /**
     * 倒计时只在**本人回合**里走：AI 思考与弹窗期间 `isInputLocked` 为真，
     * 此时循环空转（`continue`）而不是停表 —— 停表后要重新唤起，反而多一处状态。
     */
    private func tick() {
        timerTask?.cancel()
        timerTask = Task { [weak self] in
            while true {
                if Task.isCancelled { return }
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }
                if Task.isCancelled { return }
                guard !self.isPaused, !self.isInputLocked, self.legResult == nil else { continue }
                self.remainingSeconds -= 1
                if self.remainingSeconds <= 0 {
                    self.statusText = "本回合超时，按空轮提交"
                    self.commitTurn(allowEmpty: true)
                    return
                }
            }
        }
    }

    // MARK: - AI

    private func startAiTurnIfNeeded() {
        guard config.matchType.isEqual(MatchType.cricket), !leg.isFinished, !isCurrentHuman else { return }
        guard matchWinnerIndex == nil else { return }
        aiThinking = true
        aiTask?.cancel()
        let generation = aiGeneration
        aiTask = Task { [weak self] in
            guard let self else { return }
            let difficulty = X01SetupMapping.difficulty(launch.difficultyRaw)
            let darts = CricketAi.shared.generateTurn(leg: leg, difficulty: difficulty, random: random)
            for claimed in darts {
                if Task.isCancelled { return }
                try? await Task.sleep(nanoseconds: aiStepNanoseconds)
                await MainActor.run {
                    // 撤销 / 换局之后这一轮已作废：再落镖就是把对手的镖塞进本人回合（V20 抓到的）。
                    guard generation == self.aiGeneration, !Task.isCancelled else { return }
                    self.applyClaimed(claimed)
                }
            }
            try? await Task.sleep(nanoseconds: aiStepNanoseconds)
            await MainActor.run {
                // 同上：过期任务的这一次提交必须丢掉，否则撤销完回合又会自己往前走一格。
                guard generation == self.aiGeneration, !Task.isCancelled else { return }
                self.aiThinking = false
                self.commitTurn(allowEmpty: true)
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

    /**
     * 一局结束：**只登记结果，不开下一局**。
     *
     * 下一局由玩家在 Leg 胜利弹窗上点「继续下一局」触发（[continueMatch]）——
     * 胜负的这一刻必须停在屏幕上让玩家看见。
     */
    private func registerLegWin(_ settled: CricketLegState) {
        guard let winner = settled.winnerIndex else { return }
        let index = Int(winner.intValue)
        guard legsWon.indices.contains(index) else { return }
        let willWinMatch = (legsWon[index] + 1) >= config.legsToWin
        legResult = CricketLegResult(
            winnerIndex: index, legNumber: legNumber, isMatchWin: willWinMatch
        )
    }

    /// 弹窗上的「继续下一局」：先记胜场，再决定是开新一局还是整场结束。
    func continueMatch() {
        guard let result = legResult else { return }
        let index = result.winnerIndex
        guard legsWon.indices.contains(index) else { return }
        legsWon[index] += 1
        legResult = nil
        // 新一局：作废上一局遗留的 AI 回合，否则它会把镖落进新板面。
        aiGeneration += 1
        aiTask?.cancel()
        aiThinking = false
        if legsWon[index] >= config.legsToWin {
            matchWinnerIndex = index
            return
        }
        legNumber += 1
        // 新一局的板面从零开始，只把**已赢局数**带到玩家状态上（引擎的 `legsWon` 字段）。
        leg = CricketLegState(
            config: config,
            players: leg.players.enumerated().map { offset, player in
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
        statusText = "第 \(legNumber) 局开始"
        if isCurrentHuman {
            startTimer()
        } else {
            startAiTurnIfNeeded()
        }
    }

    func playerName(_ index: Int) -> String {
        CricketSetupMapping.players(from: launch)[safe: index]?.name ?? "选手 \(index + 1)"
    }

    // MARK: - 展示文案

    /**
     * 顶栏标题：移动端规范要的是玩法名 + Leg 信息。
     *
     * 变体名跟着 `launch.variantRaw` 走（引擎的 `CricketVariant` 是唯一来源），
     * 不写死 STANDARD —— 选了「不计分 / 生死局」却顶着 STANDARD CRICKET 是撒谎。
     */
    var titleText: String {
        switch CricketSetupMapping.variant(launch.variantRaw).name {
        case "NO_SCORE": return "NO SCORE CRICKET"
        case "CUT_THROAT": return "CUT THROAT"
        default: return "STANDARD CRICKET"
        }
    }

    /// 一镖的录入文案（填进镖位格子）：T20 / D16 / BULL 50 / MISS。
    func dartText(_ claimed: ClaimedDart) -> String {
        let dart = claimed.dart
        let number = Int(dart.number)
        if number == 0 { return "MISS" }
        if number == 25 {
            // Bull 没有"三倍"：T 键打到 Bull 上一律按内牛眼（2 标记）录入，不吞这一镖。
            return dart.multiplier >= 2 ? "BULL 50" : "BULL 25"
        }
        switch dart.multiplier {
        case 3: return "T\(number)"
        case 2: return "D\(number)"
        default: return "\(number)"
        }
    }
}

/** 一局结束的结果：谁赢的、第几局、是不是顺带赢下整场。 */
struct CricketLegResult {
    let winnerIndex: Int
    let legNumber: Int32
    let isMatchWin: Bool
}

/** 回合级撤销的快照：撤销要还原的不止板面，还有局数与胜场。 */
private struct CricketSnapshot {
    let leg: CricketLegState
    let legNumber: Int32
    let legsWon: [Int32]
    let matchWinnerIndex: Int?
}

private extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
