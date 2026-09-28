import SwiftUI
import shared

/**
 * 真实比例的**标准硬式靶**渲染 + 毫米 ↔ 像素投影。
 *
 * 为什么单独抽这一层：练习页（局部放大）与报告页（全盘 + 热点图）要的是同一块靶的
 * **同一个几何**，差别只在中心与放大倍数。各画一套必然出现「两边比例不一致」，
 * 而这里所有半径一律取自 `BoardGeometry` —— 改引擎常量，两处同时跟着变。
 */
enum BoardMetrics {
    static let boardRadius = BoardGeometry.shared.DOUBLE_OUTER_RADIUS_MM
    static let doubleInner = BoardGeometry.shared.DOUBLE_INNER_RADIUS_MM
    static let tripleOuter = BoardGeometry.shared.TRIPLE_OUTER_RADIUS_MM
    static let tripleInner = BoardGeometry.shared.TRIPLE_INNER_RADIUS_MM
    static let outerBull = BoardGeometry.shared.OUTER_BULL_RADIUS_MM
    static let innerBull = BoardGeometry.shared.INNER_BULL_RADIUS_MM
    static let sectorAngleDeg = BoardGeometry.shared.SECTOR_ANGLE_DEG

    /** `SECTOR_ORDER` 导出为 `KotlinIntArray`（不是 Swift 数组），只能逐个取。 */
    static let sectorOrder: [Int] = {
        let array = BoardGeometry.shared.SECTOR_ORDER
        return (0..<Int(array.size)).map { Int(array.get(index: Int32($0))) }
    }()
}

/**
 * 硬式靶的标准配色。
 *
 * 取 WDF / BDO 通用 scheme：单倍区奶白 / 炭黑交替，双倍与三倍环红 / 绿交替，
 * 外牛绿（25）、内牛红（50）。顺序由 `SECTOR_ORDER` 的**奇偶**决定，
 * 所以 20 分区（索引 0）是黑单区配红倍环 —— 与真实靶一致。
 */
enum BoardPalette {
    static let singleCream = Color(red: 0.93, green: 0.90, blue: 0.80)
    static let singleBlack = Color(red: 0.14, green: 0.13, blue: 0.12)
    static let ringRed = Color(red: 0.76, green: 0.16, blue: 0.14)
    static let ringGreen = Color(red: 0.10, green: 0.52, blue: 0.28)
    static let wire = Color(red: 0.62, green: 0.60, blue: 0.55)
}

// MARK: - 投影

struct BoardProjection {
    let size: CGSize
    /** 视窗中心（靶面 mm）。练习页把它设成意图锚点 ⇒ 目标格必定落在框中央。 */
    let centerMm: CGPoint
    /**
     * 每毫米多少像素。
     *
     * 练习页要求「切换目标分区时放大比例不变」，所以这个值由**框高 ÷ 固定的毫米跨度**算出，
     * 与目标无关（不能取 `ImpactWindow.spanX` —— 它按目标半径变化，会让 20 与 11 的图差一倍）。
     */
    let pxPerMm: CGFloat

    func point(xMm: Double, yMm: Double) -> CGPoint {
        CGPoint(
            x: size.width / 2 + CGFloat(xMm - centerMm.x) * pxPerMm,
            // mm 的 y 向上、画布 y 向下 ⇒ 纵向翻转一次。
            y: size.height / 2 - CGFloat(yMm - centerMm.y) * pxPerMm
        )
    }

    /** 落点像素 → 靶面 mm（点选记镖用）。 */
    func mm(at point: CGPoint) -> CGPoint {
        CGPoint(
            x: centerMm.x + Double(point.x - size.width / 2) / pxPerMm,
            y: centerMm.y - Double(point.y - size.height / 2) / pxPerMm
        )
    }

    func radius(_ rMm: Double) -> CGFloat { CGFloat(rMm) * pxPerMm }
}

// MARK: - 靶盘绘制

enum BoardPainter {

    static func paint(
        _ ctx: inout GraphicsContext,
        projection: BoardProjection,
        highlightSector: Int? = nil
    ) {
        let center = CGPoint(x: projection.size.width / 2, y: projection.size.height / 2)
        let angleStep = BoardMetrics.sectorAngleDeg

        for (index, sector) in BoardMetrics.sectorOrder.enumerated() {
            let midDeg = Double(index) * angleStep
            let a0 = Angle.degrees(-90 + midDeg - angleStep / 2)
            let a1 = Angle.degrees(-90 + midDeg + angleStep / 2)
            let isEven = index % 2 == 0
            let base = isEven ? BoardPalette.singleBlack : BoardPalette.singleCream
            let ring = isEven ? BoardPalette.ringRed : BoardPalette.ringGreen

            // 先铺满整段单倍区（含内外两段），再用三倍 / 双倍环覆盖：
            // 两环之间那一段自动成为「外单倍」，不需要单独画。
            fillSector(
                &ctx, center: center, projection: projection,
                inner: BoardMetrics.outerBull, outer: BoardMetrics.boardRadius,
                a0: a0, a1: a1, color: base
            )
            fillSector(
                &ctx, center: center, projection: projection,
                inner: BoardMetrics.tripleInner, outer: BoardMetrics.tripleOuter,
                a0: a0, a1: a1, color: ring
            )
            fillSector(
                &ctx, center: center, projection: projection,
                inner: BoardMetrics.doubleInner, outer: BoardMetrics.boardRadius,
                a0: a0, a1: a1, color: ring
            )

            if highlightSector == sector {
                fillSector(
                    &ctx, center: center, projection: projection,
                    inner: BoardMetrics.outerBull, outer: BoardMetrics.boardRadius,
                    a0: a0, a1: a1, color: .yellow.opacity(0.12)
                )
            }
        }

        // 牛眼压在最后：它不属于任何扇区。
        fillCircle(&ctx, center: center, projection: projection, radius: BoardMetrics.outerBull, color: BoardPalette.ringGreen)
        fillCircle(&ctx, center: center, projection: projection, radius: BoardMetrics.innerBull, color: BoardPalette.ringRed)

        paintWires(&ctx, center: center, projection: projection)
    }

    private static func fillSector(
        _ ctx: inout GraphicsContext,
        center: CGPoint,
        projection: BoardProjection,
        inner: Double,
        outer: Double,
        a0: Angle,
        a1: Angle,
        color: Color
    ) {
        let r0 = projection.radius(inner)
        let r1 = projection.radius(outer)
        var path = Path()
        path.addArc(center: center, radius: r1, startAngle: a0, endAngle: a1, clockwise: false)
        path.addArc(center: center, radius: r0, startAngle: a1, endAngle: a0, clockwise: true)
        path.closeSubpath()
        ctx.fill(path, with: .color(color))
    }

    private static func fillCircle(
        _ ctx: inout GraphicsContext,
        center: CGPoint,
        projection: BoardProjection,
        radius: Double,
        color: Color
    ) {
        let r = projection.radius(radius)
        let path = Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2))
        ctx.fill(path, with: .color(color))
    }

    /** 分区铁丝：真实靶的分区是靠铁丝隔开的，画上去才知道「打偏到隔壁」意味着什么。 */
    private static func paintWires(_ ctx: inout GraphicsContext, center: CGPoint, projection: BoardProjection) {
        let lineWidth = max(0.5, projection.pxPerMm * 0.7)
        let angleStep = BoardMetrics.sectorAngleDeg
        for index in 0..<BoardMetrics.sectorOrder.count {
            let deg = Double(index) * angleStep - angleStep / 2
            let rad = (-90 + deg) * .pi / 180
            let r0 = projection.radius(BoardMetrics.outerBull)
            let r1 = projection.radius(BoardMetrics.boardRadius)
            var path = Path()
            path.move(to: CGPoint(x: center.x + cos(rad) * r0, y: center.y + sin(rad) * r0))
            path.addLine(to: CGPoint(x: center.x + cos(rad) * r1, y: center.y + sin(rad) * r1))
            ctx.stroke(path, with: .color(BoardPalette.wire), lineWidth: lineWidth)
        }
    }
}

/// 一块真实比例的靶（画到多大只看该(coordinate)投影：中心 + pxPerMm）。
struct DartBoardCanvas: View {
    let centerMm: CGPoint
    let pxPerMm: CGFloat
    var highlightSector: Int? = nil

    var body: some View {
        Canvas { ctx, size in
            let projection = BoardProjection(size: size, centerMm: centerMm, pxPerMm: pxPerMm)
            BoardPainter.paint(&ctx, projection: projection, highlightSector: highlightSector)
        }
    }
}

// MARK: - 热点栅格

/**
 * `HeatmapGrid.render` 的结果快照。
 *
 * Kotlin 侧返回 `FloatArray` → ObjC 侧是 `KotlinFloatArray`（只能 `get(index:)`），
 * 这里趁早转成 Swift 数组：SwiftUI 的 Canvas 要按行列随机访问，逐个跨桥太慢。
 *
 * 行序：第 0 行对应 `-halfSpanMm`（即 **靶面 y 最小**），与 `HeatmapGrid.mmAt` 一致。
 */
struct HeatGrid {
    let cells: Int
    let values: [Float]
    let halfSpanMm: Double
    let cellMm: Double

    func value(col: Int, row: Int) -> Float {
        guard col >= 0, row >= 0, col < cells, row < cells else { return 0 }
        return values[row * cells + col]
    }

    var isEmpty: Bool { values.allSatisfy { $0 <= 0 } }

    /** 从 shared 的原始结果转换。 */
    static func from(_ array: KotlinFloatArray, halfSpanMm: Double, cellMm: Double) -> HeatGrid {
        let count = Int(array.size)
        let cells = Int((2 * halfSpanMm / cellMm).rounded()) + 1
        var values = [Float](repeating: 0, count: count)
        for i in 0..<count { values[i] = array.get(index: Int32(i)) }
        return HeatGrid(cells: cells, values: values, halfSpanMm: halfSpanMm, cellMm: cellMm)
    }

    /**
     * 热度配色：低蓝 → 中黄 → 高红（和常见的 KDE 图一致）。
     *
     * 透明度用 `value^0.65` 而不是线性：KDE 归一化后大部分格子集中在低值区，
     * 线性映射会让整张图几乎看不见，只剩一个红点。
     */
    func color(at row: Int, col: Int) -> Color {
        let v = value(col: col, row: row)
        guard v > 0.01 else { return .clear }
        let hue = max(0, 0.62 - 0.62 * Double(v))
        return Color(hue: hue, saturation: 0.85, brightness: 0.95).opacity(Double(pow(v, 0.65)) * 0.8)
    }
}

/**
 * 把热点栅格叠在给定投影上。
 *
 * `halfSpanMm` 的意义由调用方决定：全盘热力图传 170（覆盖整块靶），
 * 局部误差图传 60（此时坐标是 `eTan / eRad`，**原点 = 瞄点**而不是靶心）。
 */
struct HeatmapCanvas: View {
    let grid: HeatGrid
    let centerMm: CGPoint
    let pxPerMm: CGFloat

    var body: some View {
        Canvas { ctx, size in
            let projection = BoardProjection(size: size, centerMm: centerMm, pxPerMm: pxPerMm)
            let halfCellPx = projection.radius(grid.cellMm / 2)
            for row in 0..<grid.cells {
                for col in 0..<grid.cells {
                    let color = grid.color(at: row, col: col)
                    if color == .clear { continue }
                    // 栅格坐标 → mm（左下角 + (col,row)·cell，注意 y 由 mmAt 定义为向上）
                    let xMm = -grid.halfSpanMm + Double(col) * grid.cellMm
                    let yMm = -grid.halfSpanMm + Double(row) * grid.cellMm
                    let p = projection.point(xMm: xMm, yMm: yMm)
                    let rect = CGRect(
                        x: p.x - halfCellPx, y: p.y - halfCellPx,
                        width: halfCellPx * 2, height: halfCellPx * 2
                    )
                    ctx.fill(Path(rect), with: .color(color))
                }
            }
        }
    }
}
