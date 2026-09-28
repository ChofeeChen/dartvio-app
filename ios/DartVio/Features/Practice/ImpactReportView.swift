import SwiftUI
import shared

/**
 * 精准工坊报告页，对齐 Android `ui/practice/ImpactReportScreen.kt`。
 *
 * ## 图表从哪来
 *
 * 三张图的**原始格子都是 shared 算的**（`HeatmapGrid.render`，Silverman 分轴带宽的高斯核密度），
 * iOS 只负责把 `[0,1]` 的格子画成色块：
 *
 * - **全靶热点图**（必含）：绝对 mm 帧，回答「落点整体压在哪一块」；
 * - **误差热点图**：原点 = 瞄点，纵轴径向（上=偏外）/ 横轴切向，回答「相对瞄点歪成什么形状」；
 * - **方向对比条**：σ 的径向 / 切向分量对比，回答「散布是沿半径拉长还是左右飘」。
 *
 * 之所以坚持用引擎的 KDE 而不是自己在 Swift 里再写一遍：带宽、归一化、取点上限
 * （`MAX_POINTS` 只取最近 500 镖）这些细节一旦不一致，两端会给出两张不同的图，
 * 而「哪张图是对的」没法用测试判 —— 所以只留一个实现。
 */
struct ImpactReportView: View {

    let viewModel: ImpactPracticeViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                headlineCard
                boardHeatmapCard
                fingerprintCard
                errorHeatmapCard
                if let stats = viewModel.stats { scatterShapeCard(stats) }
                metricsCard
                adviceCard
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("精准工坊报告")
    }

    // MARK: - 头部

    private var headlineCard: some View {
        VStack(spacing: 6) {
            Text(viewModel.target.label)
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
            Text(viewModel.headline)
                .font(.headline)
                .foregroundStyle(Palette.primary)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("impactHeadline")
            Text("共 \(viewModel.throwCount) 镖 · 命中 \(viewModel.hitRatePercent)% · 最少 \(viewModel.minSampleDarts) 镖出结论")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - 全靶热点图

    private var boardHeatmapCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            cardTitle(
                "全靶热点图",
                subtitle: "Kernel Density · 覆盖半径 \(Int(BoardMetrics.boardRadius.rounded())) mm 的整块靶"
            )
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                let heat = viewModel.boardHeatmap()
                let pxPerMm = side / CGFloat(heat.halfSpanMm * 2)
                ZStack {
                    DartBoardCanvas(
                        centerMm: .zero,
                        pxPerMm: pxPerMm,
                        highlightSector: viewModel.target.kind == IntentKind.bull ? nil : Int(viewModel.target.sector)
                    )
                    HeatmapCanvas(grid: heat.grid, centerMm: .zero, pxPerMm: pxPerMm)
                    impactDots(pxPerMm: pxPerMm)
                    targetRing(pxPerMm: pxPerMm)
                    centroidMark(pxPerMm: pxPerMm)
                    aimMarker(pxPerMm: pxPerMm)
                }
                .frame(width: side, height: side)
                .frame(maxWidth: .infinity)
            }
            .frame(height: 300)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.divider, lineWidth: 1))
            .accessibilityIdentifier("impactHeatBoard")
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    /// 瞄点标记：让你一眼看出「练的是哪一格」压在了热点图的什么位置。
    private func aimMarker(pxPerMm: CGFloat) -> some View {
        let anchor = viewModel.target.anchorMm()
        return Circle()
            .strokeBorder(Palette.primary, lineWidth: 2)
            .frame(width: 14, height: 14)
            .offset(
                x: CGFloat(anchor.x) * pxPerMm,
                // mm 的 +y 向上、画布 +y 向下 ⇒ 纵向取反。
                y: -CGFloat(anchor.y) * pxPerMm
            )
    }

    // MARK: - 全靶热点图的叠加层（md 方案 §2.3：落点 + 目标区高亮 + 质心）

    /** 落点层：命中绿 / 脱靶红，白描边（md §2.3 叠加层 1 的配色规范）。 */
    private func impactDots(pxPerMm: CGFloat) -> some View {
        ForEach(viewModel.points.indices, id: \.self) { index in
            let point = viewModel.points[index]
            Circle()
                .fill(point.isMiss ? Color(red: 0.91, green: 0.30, blue: 0.24)
                                   : Color(red: 0.18, green: 0.80, blue: 0.44))
                .frame(width: 7, height: 7)
                .overlay(Circle().strokeBorder(.white, lineWidth: 1))
                .offset(
                    x: CGFloat(point.xMm) * pxPerMm,
                    y: -CGFloat(point.yMm) * pxPerMm
                )
        }
    }

    /**
     * 目标区高亮：橙色虚线圈（md §2.3 叠加层 4）。
     *
     * 圈半径取**所在环带的半宽**（环中线到边正好是它），所以 T20 的圈就贴着三倍环的宽窄，
     * 一眼能看出「圈外 = 打到隔壁」。
     */
    private func targetRing(pxPerMm: CGFloat) -> some View {
        let anchor = viewModel.target.anchorMm()
        let kind = viewModel.target.kind
        let halfWidth: Double
        if kind == IntentKind.bull {
            halfWidth = BoardMetrics.innerBull
        } else if kind == IntentKind.double_ {
            halfWidth = (BoardMetrics.boardRadius - BoardMetrics.doubleInner) / 2
        } else if kind == IntentKind.singleOuter {
            halfWidth = (BoardMetrics.doubleInner - BoardMetrics.tripleOuter) / 2
        } else {
            halfWidth = (BoardMetrics.tripleOuter - BoardMetrics.tripleInner) / 2
        }
        let radius = max(6, halfWidth)
        return Circle()
            .stroke(Color(red: 1.0, green: 0.42, blue: 0.21),
                    style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
            .frame(width: CGFloat(radius * 2) * pxPerMm, height: CGFloat(radius * 2) * pxPerMm)
            .offset(x: CGFloat(anchor.x) * pxPerMm, y: -CGFloat(anchor.y) * pxPerMm)
    }

    /** 质心十字（md §2.3 叠加层 3）：命中样本的平均位置，白色大号。 */
    @ViewBuilder
    private func centroidMark(pxPerMm: CGFloat) -> some View {
        let hits = viewModel.points.filter { !$0.isMiss }
        if !hits.isEmpty {
            let x = hits.map(\.xMm).reduce(0, +) / Double(hits.count)
            let y = hits.map(\.yMm).reduce(0, +) / Double(hits.count)
            let center = CGPoint(x: CGFloat(x) * pxPerMm, y: -CGFloat(y) * pxPerMm)
            ZStack {
                Circle()
                    .strokeBorder(.white, lineWidth: 2)
                    .frame(width: 16, height: 16)
                Path { path in
                    path.move(to: CGPoint(x: center.x - 11, y: center.y))
                    path.addLine(to: CGPoint(x: center.x + 11, y: center.y))
                    path.move(to: CGPoint(x: center.x, y: center.y - 11))
                    path.addLine(to: CGPoint(x: center.x, y: center.y + 11))
                }
                .stroke(.white, lineWidth: 1.5)
            }
            .offset(x: center.x, y: center.y)
        }
    }

    // MARK: - 失误指纹（md 方案 核心图②：一张条形图讲清「偏哪」）

    /** 四向计数：每镖归入占比最大的一向，四向之和 = 总镖数 ⇒ 百分比直接可加可比。 */
    private var fingerprint: (left: Int, right: Int, far: Int, near: Int, total: Int)? {
        let samples = viewModel.points
        guard !samples.isEmpty else { return nil }
        let anchor = viewModel.target.anchorMm()
        let length = hypot(anchor.x, anchor.y)
        // BULL 的锚点在靶心，「径向」没有定义方向 ⇒ 退化为以 +y（打上方）为远端。
        let ux = length > 0.5 ? anchor.x / length : 0.0
        let uy = length > 0.5 ? anchor.y / length : 1.0
        var left = 0, right = 0, far = 0, near = 0
        for point in samples {
            let dx = point.xMm - anchor.x
            let dy = point.yMm - anchor.y
            let radial = dx * ux + dy * uy
            // 哪个分量大归哪向：横向位移大 = 左右偏，径向位移大 = 远近偏。
            if abs(dx) >= abs(radial) {
                if dx < 0 { left += 1 } else { right += 1 }
            } else {
                if radial > 0 { far += 1 } else { near += 1 }
            }
        }
        return (left, right, far, near, samples.count)
    }

    private var fingerprintCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            cardTitle("失误指纹", subtitle: "相对瞄点的四向占比 · 偏左/偏右 = 角度，偏远/偏近 = 力度")
            if let tally = fingerprint, tally.total > 0 {
                let percent = { (count: Int) in Int((Double(count) / Double(tally.total) * 100).rounded()) }
                fingerprintBar("偏左", count: tally.left, percent: percent(tally.left),
                               color: Color(red: 0.20, green: 0.60, blue: 0.86))
                fingerprintBar("偏右", count: tally.right, percent: percent(tally.right),
                               color: Color(red: 0.16, green: 0.50, blue: 0.72))
                fingerprintBar("偏远", count: tally.far, percent: percent(tally.far),
                               color: Color(red: 0.90, green: 0.49, blue: 0.13))
                fingerprintBar("偏近", count: tally.near, percent: percent(tally.near),
                               color: Color(red: 0.83, green: 0.33, blue: 0.00))
                Text(fingerprintSentence(tally))
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
            } else {
                Text("还没有样本，回到练习页投几镖。")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
            }
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("impactFingerprint")
    }

    private func fingerprintBar(_ label: String, count: Int, percent: Int, color: Color) -> some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
                .frame(width: 32, alignment: .leading)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.surfaceVariant)
                    Capsule()
                        .fill(color)
                        .frame(width: max(2, geo.size.width * CGFloat(percent) / 100))
                }
            }
            .frame(height: 10)
            Text("\(percent)%")
                .font(.caption.monospacedDigit())
                .foregroundStyle(Palette.textPrimary)
                .frame(width: 36, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(percent)%（\(count) 镖）")
    }

    /**
     * 指纹结论（md §2.4 的规则）：先看主因，再看次因；四向都不占优时直说「稳定性问题」。
     *
     * 阈值来自 md 方案：35% 定主因、25% 定次因、30% 是「没有单一方向问题」的分界。
     */
    private func fingerprintSentence(_ print: (left: Int, right: Int, far: Int, near: Int, total: Int)) -> String {
        let percent = { (count: Int) in Double(count) / Double(print.total) * 100 }
        let left = percent(print.left), right = percent(print.right)
        let far = percent(print.far), near = percent(print.near)
        let directions: [(String, Double)] = [("偏左", left), ("偏右", right), ("偏远", far), ("偏近", near)]
        if left > 35 && far > 25 { return "主要问题是偏左，其次打远 —— 先修瞄线，再压低出手。" }
        if left > 35 { return "主要问题是偏左，出手前检查站位与瞄线。" }
        if right > 35 { return "主要问题是偏右，出手前检查站位与瞄线。" }
        if directions.allSatisfy({ $0.1 < 30 }) {
            return "四向都不占优：不是方向问题，是稳定性问题 —— 练固定节奏的 grouping。"
        }
        if let main = directions.max(by: { $0.1 < $1.1 }) {
            return "落点整体偏\(main.0)（\(Int(main.1.rounded()))%），练的时候盯住这一向。"
        }
        return "样本还看不出明显方向。"
    }

    // MARK: - 误差热点图

    private var errorHeatmapCard: some View {
        let heat = viewModel.errorHeatmap()
        return VStack(alignment: .leading, spacing: 8) {
            cardTitle("误差分布热点图", subtitle: "原点 = 瞄点 · 上=偏外 / 右=切向顺时针")
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                let pxPerMm = side / CGFloat(heat.halfSpanMm * 2)
                ZStack {
                    RoundedRectangle(cornerRadius: 8).fill(Palette.surfaceVariant)
                    errorAxes(side: side, pxPerMm: pxPerMm)
                    HeatmapCanvas(grid: heat.grid, centerMm: .zero, pxPerMm: pxPerMm)
                    if let stats = viewModel.stats {
                        errorOverlay(side: side, pxPerMm: pxPerMm, stats: stats)
                    }
                }
                .frame(width: side, height: side)
                .frame(maxWidth: .infinity)
            }
            .frame(height: 240)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.divider, lineWidth: 1))
            .accessibilityIdentifier("impactHeatError")
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    /// 十字轴 + 每 8 mm 一圈的环宽参考。
    private func errorAxes(side: CGFloat, pxPerMm: CGFloat) -> some View {
        ZStack {
            ForEach([8.0, 16.0, 24.0, 32.0], id: \.self) { radius in
                Circle()
                    .stroke(Palette.divider.opacity(0.9), lineWidth: 1)
                    .frame(width: CGFloat(radius * 2) * pxPerMm, height: CGFloat(radius * 2) * pxPerMm)
            }
            Path { path in
                path.move(to: CGPoint(x: side / 2, y: 0))
                path.addLine(to: CGPoint(x: side / 2, y: side))
                path.move(to: CGPoint(x: 0, y: side / 2))
                path.addLine(to: CGPoint(x: side, y: side / 2))
            }
            .stroke(Palette.divider, lineWidth: 1)
        }
    }

    /** 质心（平均 systemic offset）与 R95 圈：**overlay 在热点图上的两个统计量**。 */
    private func errorOverlay(side: CGFloat, pxPerMm: CGFloat, stats: ImpactStats) -> some View {
        let cx = CGFloat(stats.biasTan) * pxPerMm
        let cy = -CGFloat(stats.biasRad) * pxPerMm
        return ZStack {
            Circle()
                .stroke(Palette.primary.opacity(0.8), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                .frame(width: CGFloat(stats.r95 * 2) * pxPerMm, height: CGFloat(stats.r95 * 2) * pxPerMm)
                .offset(x: cx, y: cy)
            Circle()
                .fill(Palette.primary)
                .frame(width: 8, height: 8)
                .offset(x: cx, y: cy)
        }
        .frame(width: side, height: side)
    }

    // MARK: - 散布方向条

    private func scatterShapeCard(_ stats: ImpactStats) -> some View {
        let maximum = max(stats.sigmaRad, stats.sigmaTan, 0.01)
        return VStack(alignment: .leading, spacing: 8) {
            cardTitle("散布方向", subtitle: "σ 的径向 / 切向拆分（越接近 = 越圆）")
            scatterBar("径向（偏内 ↔ 偏外）", value: stats.sigmaRad, maximum: maximum)
            scatterBar("切向（左邻 ↔ 右邻）", value: stats.sigmaTan, maximum: maximum)
            Text(shapeSentence(stats))
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func scatterBar(_ title: String, value: Double, maximum: Double) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title).font(.caption).foregroundStyle(Palette.textSecondary)
                Spacer()
                Text(String(format: "σ = %.1f mm", value))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(Palette.textPrimary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.surfaceVariant)
                    Capsule()
                        .fill(Palette.primary)
                        .frame(width: max(2, geo.size.width * CGFloat(value / maximum)))
                }
            }
            .frame(height: 8)
        }
    }

    /**
     * 形状判读。
     *
     * 阈值（1.4）是「长轴比短轴」的经验分界：**径向往返 = 力度 / 释放早晚**，
     * **切向左右 = 瞄点横向漂移**。两者练法不同，所以值得分开说 ——
     * 只给一个 R95 数字，玩家无从下手。
     */
    private func shapeSentence(_ stats: ImpactStats) -> String {
        guard stats.sigmaTan > 0 && stats.sigmaRad > 0 else { return "样本还太少，看不出散布形状。" }
        if stats.sigmaRad > stats.sigmaTan * 1.4 {
            return "散布沿**半径**明显拉长：多是力度忽大忽小或释放早晚不稳，练固定的随挥节奏。"
        }
        if stats.sigmaTan > stats.sigmaRad * 1.4 {
            return "散布沿**切线**拉长：左右飘得比远近多，先固定站位与瞄线，别急着改力度。"
        }
        return "两个方向散布接近，是圆形的随机散布 —— 靠增加样本量提高一致性。"
    }

    // MARK: - 数字

    private var metricsCard: some View {
        VStack(spacing: 8) {
            cardTitle("统计量", subtitle: "口径全部由 shared 给出")
            if let stats = viewModel.stats {
                statRow("样本数", "\(stats.n)")
                statRow("平均偏差", String(format: "%.1f mm", stats.bias))
                statRow("径向偏差", String(format: "%.1f mm（%@）", abs(stats.biasRad),
                                          ImpactCalculator.shared.biasDirectionWord(biasRad: stats.biasRad)))
                statRow("切向偏差", String(format: "%.1f mm", stats.biasTan))
                statRow("离散 σ", String(format: "径向 %.1f / 切向 %.1f mm", stats.sigmaRad, stats.sigmaTan))
                statRow("R95", String(format: "%.1f mm", stats.r95))
                statRow("RMSE", String(format: "%.1f mm", stats.rmse))
            } else {
                Text("样本不足，先投几镖再来")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("impactStats")
    }

    // MARK: - 建议

    private var adviceCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            cardTitle("训练建议", subtitle: "低于 \(viewModel.minSampleDarts) 镖时引擎不给结论")
            ForEach(viewModel.advices.indices, id: \.self) { index in
                let advice = viewModel.advices[index]
                VStack(alignment: .leading, spacing: 4) {
                    Text(advice.source)
                        .font(.caption2)
                        .foregroundStyle(Palette.primary)
                    Text(advice.text)
                        .font(.subheadline)
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Palette.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            Text("σ / R95 是**上限估计**：观测噪声 = 真实散布 + 手点误差（加性）。"
                 + "均值类结论对此稳健，散布类会被系统性高估 —— 横向比较同一台设备上的成绩即可。")
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
        }
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("impactAdvice")
    }

    // MARK: - 小块

    private func cardTitle(_ title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.headline).foregroundStyle(Palette.textPrimary)
            Text(subtitle).font(.caption).foregroundStyle(Palette.textMuted)
        }
    }

    private func statRow(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title).foregroundStyle(Palette.textSecondary)
            Spacer()
            Text(value).foregroundStyle(Palette.textPrimary)
        }
        .font(.subheadline)
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(Palette.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
