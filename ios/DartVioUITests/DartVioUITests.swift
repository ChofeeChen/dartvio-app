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

        // 我的 tab → 占位页
        app.tabBars.buttons["我的"].tap()
        XCTAssertTrue(app.staticTexts["我的"].waitForExistence(timeout: 5))

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

    // MARK: - 辅助

    /// 录一镖 20：数字 2、0 进 buffer，确认投出（KeypadView 的录入语义）。
    private func throwDart20(on app: XCUIApplication) {
        app.buttons["2"].tap()
        app.buttons["0"].tap()
        app.buttons["确认"].tap()
    }

    /// 断言失败时转储真实 UI 文案：用于区分「App 行为不对」与「测试脚本写错」。
    /// ⚠️ 不能只用 print —— UITest 的 stdout 不会进 xcodebuild 日志，只能写文件取证。
    private func dumpUI(on app: XCUIApplication, tag: String) {
        var lines: [String] = ["===DIAG \(tag) START==="]
        for button in app.buttons.allElementsBoundByIndex { lines.append("BTN: [\(button.label)]") }
        for text in app.staticTexts.allElementsBoundByIndex { lines.append("TXT: [\(text.label)]") }
        lines.append("===DIAG \(tag) END===")
        let dump = lines.joined(separator: "\n")
        try? dump.write(toFile: "/tmp/ui_dump_\(tag).txt", atomically: true, encoding: .utf8)
    }
}
