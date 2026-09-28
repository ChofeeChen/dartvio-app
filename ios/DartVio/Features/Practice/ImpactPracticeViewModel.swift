import Foundation
import shared

/** `HeatmapGrid.render` 的结果连同它的换算参数（行 `eRad` / 列 `eTan` 由调用方解释）。 */
struct ImpactHeat {
    /// 引擎返回的原始数组：`indexOfMax` 要吃这个而不是 Swift 数组，留着避免再跨一次桥。
    let raw: KotlinFloatArray
    let grid: HeatGrid
    let halfSpanMm: Double
    let cellMm: Double
}

/** 一条训练建议。标题区分来源，便于报告页把「引擎结论」与「热点图反算」并列展示。 */
struct ImpactAdvice {
    let source: String
    let text: String
}

/** 一镖的完整记录：除了引擎的误差分解，还要留**绝对毫米坐标**——
 * 报告页的全靶热点图必须知道「落在靶面哪里」，而 `ImpactFrame` 只带 `eRad / eTan`。 */
struct ImpactPoint {
    let xMm: Double
    let yMm: Double
    let isMiss: Bool
}

/**
 * 精准工坊的状态容器，对齐 Android `ui/practice/ImpactPracticeViewModel.kt` 的核心口径。
 *
 * ## 数据从哪来
 *
 * Android 版的点坐标来自靶盘点选或视觉识别（`domain.vision`），两者都给出**毫米坐标**。
 * iOS 侧是一块自绘的真实比例点选靶（见 `ImpactPracticeView`），
 * 把 tap 的像素坐标经 `BoardProjection` 反算成毫米 —— 换算只在这一处，图标毫秒由 BoardRender 统一。
 *
 * ## 刻意简化的地方
 *
 * Android 的工坊还有 CEV 最优目标推荐（`domain.impact/ImpactValue.kt`）与多次会话的处方卡，
 * 依赖历史样本；iOS 侧暂无会话持久化（同 §13.2 的取舍），所以这两块不做。
 */
@MainActor
@Observable
final class ImpactPracticeViewModel {

    var target: IntentTarget
    let spanMm: Double

    private(set) var frames: [ImpactFrame] = []
    private(set) var points: [ImpactPoint] = []
    private(set) var hitCount = 0
    private(set) var outCount = 0

    init(kind: IntentKind = .triple, sector: Int32 = 20, spanMm: Double = 60) {
        self.target = IntentTarget(kind: kind, sector: sector)
        self.spanMm = spanMm
    }

    var viewport: BoardViewport { ImpactWindow.shared.viewportOf(target: target, spanMm: spanMm) }

    /**
     * 统计结论所需的**最少镖数**，直接问引擎要（`ImpactCalculator.MIN_FULL_N`）。
     *
     * 不在 iOS 侧写死 30：这个门槛是「σ 的相对标准误 ≈ 1/√(2(n−1))」推出来的，
     * 引擎改了它，UI 的提示必须跟着改，否则会出现「UI 说够了、引擎说不够」。
     */
    var minSampleDarts: Int { Int(ImpactCalculator.shared.MIN_FULL_N) }

    /// 还差几镖才能出稳定结论；0 = 已达标。
    var dartsToGo: Int { max(0, minSampleDarts - throwCount) }

    /**
     * 投出次数 = 样本数。
     *
     * ⚠️ 不能写成 `frames.count + outCount`：`record` 对**每一镖**都会留下一个 frame（含脱靶），
     * 再加 outCount 就等于把脱靶那一镖算了两遍（实测一点 = 显示两次，UI 测试 V9 就是这么发现的）。
     */
    var throwCount: Int { frames.count }

    var stats: ImpactStats? { ImpactCalculator.shared.of(frames: frames) }

    var headline: String {
        guard let stats else { return "还没有样本" }
        return ImpactCalculator.shared.headline(stats: stats)
    }

    /// 命中率按「投出次数」算分母（脱靶也计入分母）。
    var hitRatePercent: Int { throwCount == 0 ? 0 : hitCount * 100 / throwCount }

    /**
     * 练习页那条系统提示。
     *
     * 「最少投多少镖」这件事必须**在练的时候就讲清楚**：玩家练到 12 镖打开报告，
     * 看到一堆空白图表只会以为 App 坏了。所以把门槛、差值、以及「不足时会少给什么」一次说清。
     */
    var sampleHintText: String {
        let need = minSampleDarts
        guard dartsToGo == 0 else {
            return "统计报告最少需要 \(need) 镖，当前 \(throwCount) 镖，还差 \(dartsToGo) 镖。"
                + "随时可以看报告，样本不足时只显示能信的部分。"
        }
        return "已投 \(throwCount) 镖，过了稳定结论门槛（最少 \(need) 镖）；继续投结论会更稳。"
    }

    var fingerprintText: String? {
        ImpactFingerprintCalculator.shared
            .fingerprint(frames: frames, outCount: Int32(outCount), total: Int32(throwCount))?
            .portrait()
    }

    // MARK: - 动作

    func record(xMm: Double, yMm: Double, isMiss: Bool) {
        if isMiss {
            outCount += 1
        } else {
            hitCount += 1
        }
        frames.append(ImpactFrames.shared.of(target: target, xMm: xMm, yMm: yMm))
        points.append(ImpactPoint(xMm: xMm, yMm: yMm, isMiss: isMiss))
    }

    func undoLast() {
        guard !frames.isEmpty else { return }
        frames.removeLast()
        // `points` 与 `frames` 一一对应，所以「撤回的这一镖是命中还是脱靶」直接问最后一次记录，
        // 不必像早先那样靠 `frames.count` 反推（那会把撤命中镖算成减 outCount）。
        if points.removeLast().isMiss {
            outCount = max(0, outCount - 1)
        } else {
            hitCount = max(0, hitCount - 1)
        }
    }

    func reset() {
        frames = []
        points = []
        hitCount = 0
        outCount = 0
    }

    func changeTarget(kind: IntentKind, sector: Int32) {
        target = IntentTarget(kind: kind, sector: sector)
        reset()
    }

    // MARK: - 训练建议

    /**
     * 报告页的建议区。
     *
     * 分两路、各自署名：
     * - **引擎结论**（`headline` / `scatterAdvice` / 画像）：阈值、措辞、取舍都在 commonMain，
     *   iOS 只是搬运工 —— 自己重写一遍就会漏掉「样本不足时不给结论」这条硬约束；
     * - **热点图反算**：来自本轮 KDE（`heatPeakText`），回答「密集中心到底偏到哪去了」。
     *
     * 样本不足时只留一条「先攒样本」，不给任何数字 —— 这是引擎的态度（见 `ImpactCalculator.headline`），
     * UI 不该比它更乐观。
     */
    var advices: [ImpactAdvice] {
        guard let stats else {
            return [ImpactAdvice(source: "样本", text: "还没有样本，回到练习页点几镖再来。")]
        }
        let confidence = ImpactCalculator.shared.confidenceOf(n: Int32(stats.n))
        if confidence.isEqual(ImpactCalculator.Confidence.tooFew) {
            return [
                ImpactAdvice(
                    source: "样本不足",
                    text: "有效 \(stats.n) 镖，还没到出结论的门槛（最少 \(minSampleDarts) 镖）。"
                        + "先攒样本：低于这个数，任何偏差数字都只是噪声。"
                )
            ]
        }
        var result = [ImpactAdvice(source: "瞄点结论", text: ImpactCalculator.shared.headline(stats: stats))]
        if confidence.isEqual(ImpactCalculator.Confidence.full) {
            result.append(ImpactAdvice(source: "散布结论", text: ImpactCalculator.shared.scatterAdvice(stats: stats)))
        }
        if let peak = heatPeakText {
            result.append(ImpactAdvice(source: "热点图", text: peak))
        }
        if let portrait = fingerprintText {
            result.append(ImpactAdvice(source: "手感画像", text: portrait))
        }
        return result
    }

    // MARK: - 热点图（KDE）

    /**
     * 缓存说明：KDE 是 O(可达格数 × 点数) 的活儿，而 SwiftUI 的 body 会反复求值。
     * 所以按**样本数**做 key 缓存 —— 样本没变就复用上一次的结果。
     */
    private var boardHeatCache: (n: Int, heat: ImpactHeat)?
    private var errorHeatCache: (n: Int, heat: ImpactHeat)?

    /**
     * 整块靶的落点热力图（绝对坐标帧）。
     *
     * ⚠️ 只喂**窗内点**：`HeatmapGrid` 的注释明确要求 miss 不得折算成坐标喂进来 ——
     * 它们在 DEBUG 会沿窗口边界堆出一条假高峰，把「偏差方向」这个结论直接带偏。
     */
    func boardHeatmap() -> ImpactHeat {
        let key = points.count
        if let cached = boardHeatCache, cached.n == key { return cached.heat }
        let halfSpan = BoardMetrics.boardRadius + 10.0
        let cell = 4.0
        let samples = points.filter { !$0.isMiss }.map { Point2(x: $0.xMm, y: $0.yMm) }
        let raw = HeatmapGrid.shared.render(
            points: samples,
            halfSpanMm: halfSpan,
            cellMm: cell,
            maxPoints: HeatmapGrid.shared.MAX_POINTS
        )
        let heat = ImpactHeat(
            raw: raw,
            grid: HeatGrid.from(raw, halfSpanMm: halfSpan, cellMm: cell),
            halfSpanMm: halfSpan,
            cellMm: cell
        )
        boardHeatCache = (key, heat)
        return heat
    }

    /**
     * 局部误差帧的热点图：**原点 = 瞄点**，横轴切向、纵轴径向（正值 = 偏外）。
     *
     * 这才是「散布形状」该看的那张图 —— 全盘图回答「打在哪」，这张回答「相对瞄点歪多少、歪成什么形状」。
     */
    func errorHeatmap() -> ImpactHeat {
        let key = frames.count
        if let cached = errorHeatCache, cached.n == key { return cached.heat }
        let halfSpan = 60.0
        let cell = 2.0
        let samples = frames.map { Point2(x: $0.eTan, y: $0.eRad) }
        let raw = HeatmapGrid.shared.render(
            points: samples,
            halfSpanMm: halfSpan,
            cellMm: cell,
            maxPoints: HeatmapGrid.shared.MAX_POINTS
        )
        let heat = ImpactHeat(
            raw: raw,
            grid: HeatGrid.from(raw, halfSpanMm: halfSpan, cellMm: cell),
            halfSpanMm: halfSpan,
            cellMm: cell
        )
        errorHeatCache = (key, heat)
        return heat
    }

    /**
     * 由热点图反算出的**密集中心偏移**——一句话建议的事实来源。
     *
     * 刻意走 `HeatmapGrid.indexOfMax + mmAt`：这个「中心」是 KDE 的峰值，
     * 比直接取 `(mean eRad, mean eTan)` 更贴近「玩家实际手感的中心」，且完全复用引擎的坐标换算。
     */
    var heatPeakText: String? {
        let heat = errorHeatmap()
        guard !heat.grid.isEmpty else { return nil }
        let index = Int(HeatmapGrid.shared.indexOfMax(grid: heat.raw))
        let peak = HeatmapGrid.shared.mmAt(
            index: Int32(index),
            halfSpanMm: heat.halfSpanMm,
            cellMm: heat.cellMm
        )
        if abs(peak.x) < 1 && abs(peak.y) < 1 { return "落点密集区几乎正对瞄点。" }
        let direction = ImpactCalculator.shared.biasDirectionWord(biasRad: peak.y)
        let tangent = peak.x >= 0 ? "顺时针侧" : "逆时针侧"
        return String(
            format: "落点密集中心偏离瞄点：%@ %.1f mm，并偏向相邻分区的%@ %.1f mm。",
            direction, abs(peak.y), tangent, abs(peak.x)
        )
    }
}
