import Foundation
import shared

/**
 * 精准工坊的状态容器，对齐 Android `ui/practice/ImpactPracticeViewModel.kt` 的核心口径。
 *
 * ## 数据从哪来
 *
 * Android 版的点坐标来自靶盘点选或视觉识别（`domain.vision`），两者都给出**毫米坐标**。
 * iOS 侧目前只有一块自绘的点选靶（见 `ImpactPracticeView`），把 tap 的归一化坐标按
 * `ImpactWindow.viewportOf` 给出的视窗反算成毫米 —— **换算口径统一交给 shared**，iOS 不自己推导。
 *
 * ## 刻意简化的地方
 *
 * Android 的工坊还有 CEV 最优目标推荐（`domain.impact/ImpactValue.kt`）与处方卡（`ImpactPrescription`），
 * 依赖多次会话的历史样本；iOS 侧暂无会话持久化（同 §13.2 的取舍），所以这两块不做。
 */
@MainActor
@Observable
final class ImpactPracticeViewModel {

    static let standardSpanMm: Double = ImpactWindow.shared.spanY(spanMm: 60)

    var target: IntentTarget
    let spanMm: Double

    private(set) var frames: [ImpactFrame] = []
    private(set) var hitCount = 0
    private(set) var outCount = 0

    /**
     * 与 `frames` **一一对应**的「这一镖是不是脱靶」。
     *
     * `ImpactFrame` 生成后不再携带该标记，所以撤销时没处问「撤回的是命中还是脱靶」——
     * 早前靠 `frames.count` 反推，结果是撤了命中镖却减了 `outCount`，命中率越撤越离谱。
     * 与其在帧里找，不如另存一列：撤销时 pop 出来即可，代价只有一个 Bool。
     */
    private var missFlags: [Bool] = []

    init(kind: IntentKind = .triple, sector: Int32 = 20, spanMm: Double = 60) {
        self.target = IntentTarget(kind: kind, sector: sector)
        self.spanMm = spanMm
    }

    var viewport: BoardViewport { ImpactWindow.shared.viewportOf(target: target, spanMm: spanMm) }

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

    var fingerprintText: String? {
        ImpactFingerprintCalculator.shared
            .fingerprint(frames: frames, outCount: Int32(outCount), total: Int32(throwCount))?
            .portrait()
    }

    // MARK: - 动作

    /**
     * 记录一次投镖。
     *
     * @param xMm yMm 落点的**毫米**坐标（由视图层从视窗反算）
     * @param isMiss `ImpactMissBand.bandAt` 判定为落在目标之外；nil 表示命中目标扇区。
     */
    func record(xMm: Double, yMm: Double, isMiss: Bool) {
        if isMiss {
            outCount += 1
        } else {
            hitCount += 1
        }
        frames.append(ImpactFrames.shared.of(target: target, xMm: xMm, yMm: yMm))
        missFlags.append(isMiss)
    }

    func undoLast() {
        guard !frames.isEmpty else { return }
        frames.removeLast()
        if missFlags.removeLast() {
            outCount = max(0, outCount - 1)
        } else {
            hitCount = max(0, hitCount - 1)
        }
    }

    func reset() {
        frames = []
        missFlags = []
        hitCount = 0
        outCount = 0
    }

    func changeTarget(kind: IntentKind, sector: Int32) {
        target = IntentTarget(kind: kind, sector: sector)
        reset()
    }
}
