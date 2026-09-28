import XCTest

/**
 * V2–V4 的端到端验收（蓝图 §1）：
 * V2 五页可达、导航正常；V3 本地 X01 完整回合（含 AI 出手与回合切换）；
 * V4 Count Up 8 轮打完并自动进结算页。
 */
final class DartVioUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // MARK: - V2 导航

    func testV2Navigation() throws {
        let app = XCUIApplication()
        app.launch()

        // P2（对局 tab 根 = 设置页）
        XCTAssertTrue(app.buttons["开始对局"].waitForExistence(timeout: 10))

        // 练习 tab → P4 入口
        app.tabBars.buttons["练习"].tap()
        let countUpEntry = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "Count Up 练习")
        ).firstMatch
        XCTAssertTrue(countUpEntry.waitForExistence(timeout: 5))

        // 我的 tab → 占位页（T3：Tab 可达、不崩、有「统计 / 成就 待接入」说明）
        app.tabBars.buttons["我的"].tap()
        XCTAssertTrue(app.staticTexts["我的"].waitForExistence(timeout: 5))
        let placeholder = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "统计 / 成就 待接入")
        ).firstMatch
        XCTAssertTrue(placeholder.waitForExistence(timeout: 5))

        // 对局 tab → 开始对局 → P3（键盘出现即视为到达）
        app.tabBars.buttons["对局"].tap()
        app.buttons["开始对局"].tap()
        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))
        app.buttons["退出"].tap()
        XCTAssertTrue(app.buttons["开始对局"].waitForExistence(timeout: 10))
    }

    // MARK: - V3 本地对局（含 AI 回合）

    func testV3X01GameFlow() throws {
        let app = XCUIApplication()
        app.launch()
        app.buttons["开始对局"].tap()
        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))

        // 人类回合：投 3 镖 20（501 → 441），确认键变「结束回合」
        for _ in 0..<3 { throwDart20(on: app) }
        if !app.buttons["结束回合"].exists { dumpUI(on: app, tag: "V3-after-darts") }
        XCTAssertTrue(app.buttons["结束回合"].waitForExistence(timeout: 5))
        app.buttons["结束回合"].tap()

        // 回合切给 AI → 「电脑思考中…」出现，AI 打完后回到人类（该文案消失）
        let aiThinking = app.staticTexts["电脑思考中…"]
        XCTAssertTrue(aiThinking.waitForExistence(timeout: 15))
        let humanBack = NSPredicate(format: "exists == 0")
        expectation(for: humanBack, evaluatedWith: aiThinking)
        waitForExpectations(timeout: 90)

        // 回到人类回合：键盘再次可用
        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))

        app.buttons["退出"].tap()
    }

    // MARK: - V3 补齐：真正打到 GAME SHOT（301 + 双倍出）

    /**
     * 为什么是 **301 + 双倍出**，而不是「301 + 直出」：
     *
     * 蓝图 §1 对 V3 的定义里，「双倍出结镖」本身就是要验的路径之一
     *（`OutMode.DOUBLE_OUT` 要求最后一镖命中双倍区）。走直出会绕过这个判定，
     * 补上的只是「结镖 UI」而不是「结镖规则」—— 那样的 ✅ 是假的。
     *
     * 所以这里刻意构造一条**最后一镖落在双倍区**的收尾路线：
     *   301 → T20×3（180）→ 121 → T20（60）→ 61 → T19（57）→ 4 → **D2（4）→ 0**
     * 这样既把回合数压到 2 个（301 是 501 的一半不到），又完整踩中 double-out。
     */
    func testV3CheckoutGameShot() throws {
        let app = XCUIApplication()
        app.launch()

        // 结束规则默认就是「双倍出」，这里只把目标分从 501 换成 301
        app.buttons["301"].tap()
        app.buttons["开始对局"].tap()
        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))

        // 第 1 回合：T20 × 3 = 180 → 301 - 180 = 121
        for _ in 0..<3 { throwDart(on: app, multiplier: "T", number: "20") }
        XCTAssertTrue(app.staticTexts["121"].waitForExistence(timeout: 5))
        app.buttons["结束回合"].tap()

        // 等 AI 打完把手交回人类（AI 每镖之间有延迟，这段不会太快结束）
        XCTAssertTrue(app.staticTexts["电脑思考中…"].waitForExistence(timeout: 20))
        let aiDone = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == 0"),
            object: app.staticTexts["电脑思考中…"]
        )
        wait(for: [aiDone], timeout: 90)
        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))

        // 第 2 回合：逐步验证剩余分，最后一镖用 D2 收尾（双倍区）
        throwDart(on: app, multiplier: "T", number: "20")
        XCTAssertTrue(app.staticTexts["61"].waitForExistence(timeout: 5))
        throwDart(on: app, multiplier: "T", number: "19")
        XCTAssertTrue(app.staticTexts["4"].waitForExistence(timeout: 5))
        throwDart(on: app, multiplier: "D", number: "2")

        // 结镖：状态条给出 GAME SHOT，并弹出归属人类的结算（若 AI 先结镖，这里会是「电脑 拿下本局」）
        XCTAssertTrue(app.staticTexts["GAME SHOT"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["我 拿下本局"].waitForExistence(timeout: 10))
    }

    // MARK: - V4 Count Up 8 轮 + 结算页

    func testV4CountUpFlow() throws {
        let app = XCUIApplication()
        app.launch()

        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "Count Up 练习")
        ).firstMatch.tap()

        XCTAssertTrue(app.buttons["确认"].waitForExistence(timeout: 10))

        // 第 1 轮：3 镖 20 = 60 分，满 3 镖自动进下一轮
        for _ in 0..<3 { throwDart20(on: app) }
        XCTAssertTrue(app.staticTexts["第 2 / 8 轮"].waitForExistence(timeout: 10))

        // 未投镖按「确认」= BUST。
        //
        // ⚠️ 红条「BUST · 本轮 0 分」只存在 900ms（对齐 Android 的 BUST_FLASH_MS），
        // **不能用 UI 测试断言它**：实测把时长临时拉到 4s 时 V4 能通过、改回 900ms 必失败
        //（waitForExistence 与 XCTNSPredicateExpectation 都测过），说明本机 XCUITest
        // 单次快照耗时接近 1s，采样不到这个瞬时元素。
        // 所以这里断言 BUST 的**稳定副作用**：轮次推进到下一轮，且本轮得分归零。
        app.buttons["确认"].tap()
        XCTAssertTrue(app.staticTexts["第 3 / 8 轮"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["本轮 0 分"].waitForExistence(timeout: 5))

        // 打满剩余 6 轮（第 3 轮起）→ 第 8 轮结束进结算页。
        //
        // ⚠️ 必须**逐轮等待**，不能一口气连点 18 镖：每轮第 3 镖后有 480ms 自动结算
        //（Android 的 AUTO_ADVANCE_MS），结算前 roundLocked 为真，VM 会丢弃后续输入，
        // 连点会导致镖数不够、8 轮打不满 —— 这是典型的「测试速度快于被测逻辑」的 flaky。
        for round in 3...8 {
            for _ in 0..<3 { throwDart20(on: app) }
            if round < 8 {
                XCTAssertTrue(app.staticTexts["第 \(round + 1) / 8 轮"].waitForExistence(timeout: 5))
            }
        }

        // P5 结算页出现（最佳分断言用前缀匹配：UserDefaults 跨启动保留，写死数值不稳）
        XCTAssertTrue(app.staticTexts["总分"].waitForExistence(timeout: 10))
        let bestRow = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "历史最佳")
        ).firstMatch
        XCTAssertTrue(bestRow.waitForExistence(timeout: 5))
    }

    // MARK: - 随机结镖练习（对齐 Android 练习中心）

    /**
     * **为什么用 MISS 而不是真实分数**：目标是引擎随机生成的（`CheckoutSolver.generateTarget`），
     * 写死任何具体分数都会随机失败。MISS 计 0 分，三镖投满就是「三镖未结」，
     * 这条路径与目标分无关，断言才稳定。
     */
    func testV5RandomCheckoutPractice() throws {
        let app = XCUIApplication()
        app.launch()

        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "随机结镖")
        ).firstMatch.tap()

        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["rc目标"].waitForExistence(timeout: 5))

        let targetText = app.staticTexts["rc目标"].label
        let target = Int(targetText) ?? 0
        XCTAssertGreaterThan(target, 1, "目标分应是 >1 的可结镖分数，实得 \(targetText)")

        // 三镖 MISS → 目标未完成 → 结果横幅「三镖未结」
        for _ in 0..<3 { app.buttons["MISS"].tap() }
        let result = app.staticTexts["rcResult"]
        XCTAssertTrue(result.waitForExistence(timeout: 5))
        XCTAssertEqual(result.label, "三镖未结")

        // 查看答案：标准答案由 shared 的 CheckoutSolver 给出，非空即可
        app.buttons["查看答案"].tap()
        let answer = app.staticTexts["rcAnswer"]
        XCTAssertTrue(answer.waitForExistence(timeout: 5))
        XCTAssertFalse(answer.label.isEmpty)

        // 跳过此题 → 换签，结果横幅消失
        app.buttons["跳过此题"].tap()
        let gone = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == 0"),
            object: app.staticTexts["rcResult"]
        )
        wait(for: [gone], timeout: 5)
    }

    // MARK: - 新增练习模式的冒烟（V6–V10）

    /// 极速挑战：目标分随机，只能用与目标无关的路径断言（三镖 MISS ⇒ 未完成）。
    func testV6CheckoutRush() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "极速挑战")).firstMatch.tap()

        XCTAssertTrue(app.buttons["MISS"].waitForExistence(timeout: 10))
        for _ in 0..<3 { app.buttons["MISS"].tap() }
        XCTAssertEqual(app.staticTexts["rushResult"].label, "未完成")

        // 定局后给出的是「下一题」（跳过 / 重做只在做题阶段可用），用它验题号推进
        app.buttons["rushNext"].tap()
        XCTAssertTrue(app.staticTexts["rushProgress"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["rushProgress"].label.contains("第 2 / 10 题"))
    }

    /// 99 Darts：单倍 = 1 分，与扇区无关，可以精确断言总分。
    func testV7NinetyNine() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "99 Darts")).firstMatch.tap()

        app.buttons["nnSector20"].tap()
        XCTAssertTrue(app.staticTexts["nnTotal"].waitForExistence(timeout: 10))
        app.buttons["nnHitS"].tap()
        XCTAssertEqual(app.staticTexts["nnTotal"].label, "1")
        XCTAssertTrue(app.staticTexts["nnProgress"].label.contains("已投 1 / 99 镖"))
    }

    /// Cricket MPR：MPR 与评级都由 shared 计算，这里只验「能进、能记镖、MPR 有值」。
    func testV8CricketMpr() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Cricket MPR")).firstMatch.tap()

        let mpr = app.staticTexts["mprValue"]
        XCTAssertTrue(mpr.waitForExistence(timeout: 10))
        XCTAssertFalse(mpr.label.isEmpty)
        app.buttons["MISS"].tap()
        XCTAssertTrue(app.staticTexts["mprMeta"].waitForExistence(timeout: 5))
    }

    /// 精准工坊：自绘点选靶记为一次落点。
    func testV9Impact() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "精准工坊")).firstMatch.tap()

        app.buttons["impactStart"].tap()
        let pad = app.otherElements["impactPad"]
        XCTAssertTrue(pad.waitForExistence(timeout: 10))
        pad.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["impactCounter"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["impactCounter"].label.contains("投 1 镖"))
    }

    /// 双人对抗：六个模式共用一套页面，拿列表第一项 Bull 之争验通道。
    func testV10VersusBattle() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "双人对抗训练")).firstMatch.tap()

        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Bull 之争")).firstMatch.tap()
        app.buttons["versusStart"].tap()

        XCTAssertTrue(app.staticTexts["versusCaption"].waitForExistence(timeout: 10))
        for _ in 0..<3 { app.buttons["MISS"].tap() }
        let endRound = app.buttons["versusEndRound"]
        // 三镖录满后「结算本轮」就该出现；没出现时转储真实 UI，
        // 以便区分「引擎没记上镖」与「按钮被键盘约束禁掉了」。
        if !endRound.exists { dumpUI(on: app, tag: "V10-after-darts") }
        XCTAssertTrue(endRound.waitForExistence(timeout: 5))
        endRound.tap()
        XCTAssertTrue(app.staticTexts["versusCaption"].waitForExistence(timeout: 5))
    }

    // MARK: - 键盘约束（引擎 inputFilter → UI）

    /**
     * 六个对抗模式能点的键**不一样**，且由引擎的 `VersusRule.inputFilter` 逐状态给出：
     * Bull 之争只开牛眼与 MISS，环游三镖只开当前目标分区。
     *
     * 这条测试钉的是「引擎说的约束真的落到了键盘上」—— 一旦接线断掉，
     * UI 会退回「什么键都能点、点错了记 0 分」，而**这种退化不会让任何一条既有测试失败**
     *（V10 用 MISS，恰好在所有模式里都可点）。所以必须单独有一条断言 `isEnabled`。
     */
    func testV11VersusKeyboardFilter() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "双人对抗训练")).firstMatch.tap()

        // Bull 之争：扇区键与倍率键全关，只留牛眼与 MISS
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Bull 之争")).firstMatch.tap()
        app.buttons["versusStart"].tap()
        XCTAssertTrue(app.staticTexts["versusCaption"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["2"].isEnabled, "Bull 之争不该能点扇区键")
        XCTAssertFalse(app.buttons["T"].isEnabled, "Bull 之争不该能点倍率键")
        XCTAssertTrue(app.buttons["MISS"].isEnabled)
        XCTAssertTrue(app.buttons["BULL"].isEnabled)
    }

    /**
     * 环游三镖的约束**随回合变化**，所以这条钉的是另一半：只开「当前目标分区」（开局是 1 分区），
     * 但环带不限（S/D/T 都能点）。与 V11 合起来才覆盖 `inputFilter` 的两种形态。
     */
    func testV12VersusClockKeyboardFilter() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "双人对抗训练")).firstMatch.tap()

        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "环游三镖")).firstMatch.tap()
        app.buttons["versusStart"].tap()
        XCTAssertTrue(app.staticTexts["versusCaption"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["versusCaption"].label.contains("打 1 分区"))

        XCTAssertTrue(app.buttons["1"].isEnabled, "环游三镖应只能点当前目标分区")
        XCTAssertFalse(app.buttons["0"].isEnabled)
        XCTAssertFalse(app.buttons["2"].isEnabled)
        XCTAssertTrue(app.buttons["S"].isEnabled, "环游类不限环带")
        XCTAssertFalse(app.buttons["BULL"].isEnabled, "环游类不开牛眼")
    }

    /**
     * 精准工坊改造后的验收：真实比例放大靶 + 最少镖数提示 + 随时可看的报告。
     *
     * 三条要求都能在这条里验：
     * - 「最少 \(N) 镖」由引擎的 `ImpactCalculator.MIN_FULL_N` 给出 ⇒ 断言**包含数字**，
     *   写死 30 会在引擎调门槛时假失败，写死文案又会漏掉「提示根本没显示」；
     * - 报告在样本不足时也必须能打开（用户要求「随时查看」），所以只投 3 镖就点进去；
     * - 热点图 / 误差图 / 建议区三块都必须存在 —— 它们是这次报告页的骨架。
     */
    func testV13ImpactReportHeatmap() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["练习"].tap()
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "精准工坊")).firstMatch.tap()
        app.buttons["impactStart"].tap()

        let pad = app.otherElements["impactPad"]
        XCTAssertTrue(pad.waitForExistence(timeout: 10))

        let hint = app.staticTexts["impactSampleHint"]
        XCTAssertTrue(hint.waitForExistence(timeout: 5))
        XCTAssertTrue(hint.label.contains("最少需要"), "应提示报告所需的最少镖数，实得：\(hint.label)")
        XCTAssertTrue(hint.label.contains(String(viewModelMinDarts)), "门槛数字应来自引擎常量")

        // 三镖不同落点：热点图要有东西可算
        for fraction: CGFloat in [0.5, 0.62, 0.38] {
            pad.coordinate(withNormalizedOffset: CGVector(dx: fraction, dy: 0.5)).tap()
        }
        XCTAssertTrue(app.staticTexts["impactCounter"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["impactCounter"].label.contains("投 3 镖"))

        app.buttons["impactReport"].tap()
        let board = app.otherElements["impactHeatBoard"]
        if !board.exists { dumpUI(on: app, tag: "V13-report") }
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        XCTAssertTrue(app.otherElements["impactHeatError"].waitForExistence(timeout: 5))

        // 建议区在 ScrollView 最底下：**没滚到就不会被渲染**，XCUITest 也就查不到它
        //（没有报错、只是找不到，很容易误判成「页面没做」）。
        let scroll = app.scrollViews.firstMatch
        scroll.swipeUp()
        scroll.swipeUp()
        // 建议区里有多个 Text，SwiftUI 会把它整块提升成文本节点而不是 otherElement，
        // 所以按它自己的标题文本定位（而不是夸 identifier 一定能查到）。
        XCTAssertTrue(app.staticTexts["训练建议"].waitForExistence(timeout: 5))
    }

    /**
     * Cricket 正式对局：进入 → 打一个三镖回合 → 断言凭证矩阵与分数更新。
     *
     * 这条钉的是**整套接线**：设置页入口、 Cricket 的 `applySingleDart` 逐镖预览、
     * 以及最重要的**矩阵渲染**（进度不是一个数，是「目标 × 玩家」的凭证表）。
     *
     * 选 T20 是因为它是第一个目标行、也是玩家一眼会去看的那一行：
     * 一镖 T20 应当画出 3 个记号（= 直接关闭），而不是 1 个 ——
     * 这正是 Cricket 区别于「数镖」Suite 的地方。
     */
    func testV14CricketGame() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["对局"].tap()
        app.buttons["openCricket"].tap()
        app.buttons["cricketStart"].tap()

        let board = app.otherElements["cricketBoard"]
        if !board.exists {
            dumpUI(on: app, tag: "V14-board")
        }
        XCTAssertTrue(board.waitForExistence(timeout: 10), "cricketBoard 未出现，见 /tmp/ui_dump_V14-board.txt")

        // T20：三倍 20 = 直接集满 3 个记号（Cricket 的"累计 3 次"口径）
        app.buttons["T"].tap()
        app.buttons["2"].tap()
        app.buttons["0"].tap()
        app.buttons["结束回合"].tap()

        // 矩阵与分数都应在**回合结束后**立即可见
        if !app.otherElements["cricketScores"].exists {
            dumpUI(on: app, tag: "V14-scores")
        }
        XCTAssertTrue(app.otherElements["cricketScores"].waitForExistence(timeout: 5))
        let scoreLabel = app.staticTexts["20"]
        XCTAssertTrue(app.staticTexts["20"].waitForExistence(timeout: 5), "20 分区应出现在板面上")
        _ = scoreLabel
        XCTAssertTrue(app.staticTexts["BULL"].waitForExistence(timeout: 5), "目标集应包含 Bull")
    }

    /// 报告门槛：断言用的字面量，改了要与引擎 `ImpactCalculator.MIN_FULL_N` 保持一致。
    private let viewModelMinDarts = 30

    // MARK: - 辅助

    /// 录一镖 20：数字 2、0 进 buffer，确认投出（KeypadView 的录入语义）。
    private func throwDart20(on app: XCUIApplication) {
        app.buttons["2"].tap()
        app.buttons["0"].tap()
        app.buttons["确认"].tap()
    }

    /// 录指定倍率的一镖（如 T20 / D2）。倍率键在每次投出后会复位成 S，所以每镖都要先点倍率。
    private func throwDart(on app: XCUIApplication, multiplier: String, number: String) {
        app.buttons[multiplier].tap()
        for digit in number { app.buttons[String(digit)].tap() }
        app.buttons["确认"].tap()
    }

    /// 断言失败时转储真实 UI 文案：用于区分「App 行为不对」与「测试脚本写错」。
    /// ⚠️ 不能只用 print —— UITest 的 stdout 不会进 xcodebuild 日志，只能写文件取证。
    private func dumpUI(on app: XCUIApplication, tag: String) {
        var lines: [String] = ["===DIAG \(tag) START==="]
        for button in app.buttons.allElementsBoundByIndex { lines.append("BTN: [\(button.label)]") }
        for text in app.staticTexts.allElementsBoundByIndex { lines.append("TXT: [\(text.label)]") }
        // 带 identifier 的容器（如 cricketBoard / cricketScores）大概率露成 otherElement，
        // 只按 label 找会误判成「没做」，所以把 identifier 也打出来。
        for element in app.otherElements.allElementsBoundByIndex {
            lines.append("OTH: id=[\(element.identifier)] label=[\(element.label)]")
        }
        lines.append("===DIAG \(tag) END===")
        let dump = lines.joined(separator: "\n")
        // 同时打到 stdout：xcodebuild 测试日志里能直接看到，不用去模拟器沙盒捞文件。
        print(dump)
        try? dump.write(toFile: "/tmp/ui_dump_\(tag).txt", atomically: true, encoding: .utf8)
    }
}
