import shared

/**
 * 构造收敛层。
 *
 * 为什么必须有这一层：Kotlin data class 的**默认参数不会导出到 ObjC**，
 * `MatchConfig` 有 12 个字段，Kotlin 里 `MatchConfig(targetScore = 501)` 一句话的事，
 * Swift 侧必须写满 12 个实参。同理 `copy` 也变成了必须全参的 `doCopy`（字段越多越不可用）。
 * 所以所有构造集中在这里，业务代码里不准出现裸的多参数 init。
 */
enum SharedFactory {

    // MARK: - X01

    static func x01Config(
        targetScore: Int32,
        mode: MatchMode,
        legsToWin: Int32,
        outMode: OutMode,
        inMode: InMode,
        smartAi: Bool
    ) -> MatchConfig {
        MatchConfig(
            matchType: MatchType.x01,
            targetScore: targetScore,
            mode: mode,
            legsToWin: legsToWin,
            outMode: outMode,
            inMode: inMode,
            bullMode: BullMode.standard2550,
            maxRounds: 0,
            smartAi: smartAi,
            cricketVariant: CricketVariant.standard,
            cricketTargets: [],
            overkillEnabled: false
        )
    }

    static func player(
        id: String,
        name: String,
        type: PlayerType,
        aiDifficulty: AiDifficulty? = nil,
        avatar: String = ""
    ) -> Player {
        Player(id: id, name: name, type: type, aiDifficulty: aiDifficulty, avatar: avatar)
    }

    /** `newLeg` 在 ObjC 里叫 `doNewLeg`（`new` 前缀与 ObjC 的 `new` 家族方法冲突）。 */
    static func newX01Leg(config: MatchConfig, players: [Player], legNumber: Int32) -> X01LegState {
        SharedAccess.x01Rules.doNewLeg(config: config, players: players, legNumber: legNumber)
    }

    static func dart(number: Int32, multiplier: Int32) -> Dart {
        Dart(number: number, multiplier: multiplier)
    }

    static let miss = Dart(number: 0, multiplier: 1)
    static let bull25 = Dart(number: 25, multiplier: 1)
    static let bull50 = Dart(number: 25, multiplier: 2)

    // MARK: - 练习

    /**
     * Kotlin 侧的默认值是 `List(COUNT_UP_ROUNDS) { null }`（Android 直接 `CountUpState()`）。
     * 默认参数不导出，所以必须手工补齐 —— **且不能用 0 占位**：
     *
     * `CountUpState` 的注释写明了 null = 该轮尚未进行、0 = 该轮 BUST，
     * `roundsPlayed` / `averagePerRound` 都靠 `filterNotNull()` 区分二者。全填 0 会让
     * 未开的 8 轮被当成已打完，平均分直接被摊薄。
     *
     * 更不能传空数组：`advance()` 里 `it[roundIndex] = score` 会 IndexOutOfBoundsException，
     * 这是 V4 崩溃的直接原因（Kotlin 未捕获异常 → `terminateWithUnhandledException` → SIGABRT）。
     * ObjC 签名是 `NSArray<id>` → Swift 用 `NSNull()` 表达 position nil。
     *
     * 轮数用 shared 导出的 `CountUpEngineKt.COUNT_UP_ROUNDS`（顶层 const val 会导出，
     * 挂在 CountUpEngine.kt 的**文件门面类**上，不是裸的全局常量），不再在 iOS 侧硬编码 8。
     */
    static func initialCountUpState() -> CountUpState {
        CountUpState(
            roundScores: Array(repeating: NSNull(), count: Int(CountUpEngineKt.COUNT_UP_ROUNDS)),
            currentRoundIndex: 0,
            currentDarts: [],
            dartsThrown: 0,
            roundLocked: false,
            bustFlash: false,
            finished: false
        )
    }



    /**
     * 随机结镖的新目标：先让 `CheckoutSolver` 生成目标分与全部可行路线，
     * 再交给状态机建初始帧。Kotlin 侧写作 `newTarget(target, routes)`，
     * 导出后因 `new` 前缀冲突自动改名为 `doNewTarget`。
     */
    static func randomCheckoutTarget() -> RandomCheckoutState {
        let target = SharedAccess.checkoutSolver.generateTarget()
        return SharedAccess.randomCheckoutRules.doNewTarget(
            target: target,
            routes: SharedAccess.checkoutSolver.routesFor(target: target)
        )
    }

    /**
     * 极速挑战的一次尝试记录。
     *
     * `RushAttemptRecord` 在 Kotlin 侧有 **17 个带默认值的字段**，ObjC 全部导出成必填，
     * 所以这里收敛成一个 Swift 侧只用得上的小参数列表，其余按训练口径写死：
     * 本题就是 Double Out + 标准靶（`rushLegState` 那一局的口径），录入方式只有逐镖键盘。
     */
    static func rushAttemptRecord(
        target: Int32,
        difficulty: RushDifficulty,
        darts: [Dart],
        throwElapsedMs: Int64,
        routeHintUsed: Bool,
        result: RushResult,
        remainingAfter: Int32,
        bustReason: BustReason?,
        createdAt: Int64
    ) -> RushAttemptRecord {
        RushAttemptRecord(
            id: 0,
            sessionId: Self.rushSessionId,
            createdAt: createdAt,
            target: target,
            outMode: OutMode.doubleOut,
            bullMode: BullMode.standard2550,
            difficulty: difficulty,
            darts: darts,
            inputMode: DartSource.dartByDart,
            throwElapsedMs: throwElapsedMs,
            inputElapsedMs: throwElapsedMs,
            routeHintUsed: routeHintUsed,
            result: result,
            remainingAfter: remainingAfter,
            bustReason: bustReason,
            retryOfAttemptId: 0,
            timingInvalidated: false
        )
    }

    /// iOS 侧暂无 Room / 云端会话表，用固定 sessionId 占位；将来接持久化时换成真 UUID。
    static let rushSessionId = "ios_local_rush"

    // MARK: - 双人对抗

    /**
     * 在引擎给的**推荐默认配置**上改一处：目标分。
     *
     * `BattleConfig` 有 11 个字段且默认值不导出，所以走 `doCopy` 而不是重新拼 init；
     * 这样「每个模式的推荐默认值」（`defaultConfig`）仍然只有 shared 一处定义，
     * iOS 只覆盖用户实际改了的那一格 —— 环游类传 0 表示「不由目标分决定胜负」。
     */
    static func versusConfig(modeKey: String, base: BattleConfig, targetScore: Int32) -> BattleConfig {
        base.doCopy(
            modeKey: modeKey,
            targetSector: base.targetSector,
            targetScore: targetScore,
            targetScores: base.targetScores,
            startSteps: base.startSteps,
            splitBull: base.splitBull,
            tripleOnlySeats: base.tripleOnlySeats,
            doubleOnly: base.doubleOnly,
            singleHitAdvance: base.singleHitAdvance,
            finishOnBull: base.finishOnBull
        )
    }

    // MARK: - AI

    /** Kotlin 侧 `ppr` 是 Double（导出为 `double`），这里别写成 Float。 */
    static func aiProfile(difficulty: AiDifficulty, ppr: Double = 45.0) -> AiProfile {
        AiProfile(difficulty: difficulty, ppr: ppr)
    }
}
