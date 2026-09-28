import SwiftUI
import shared

/**
 * 精准工坊：设置页 + 练习页（点选靶面），对齐 Android `ImpactSetupScreen` / `ImpactPracticeScreen`。
 *
 * ## iOS 为什么要自己画这块靶
 *
 * Android 有 `BoardTapPad`（回写清单 §3 列为 iOS 缺失的 5 个通用组件之一）。
 * 这里不像 Count Up 那样复用现有 `KeypadView`，是因为**本练习要的是落点坐标而不是得分**：
 * 键盘只能给出「打到哪一格」，而 Impact 分析需要「偏离目标多少毫米」。
 *
 * 坐标换算走 `BoardProjection`（见 `BoardRender.swift`）：视图产出**毫米**交给 VM，
 * 所有半径比例来自 `BoardGeometry`，iOS 侧不抄任何尺寸常量。
 */
struct ImpactSetupView: View {

    @State private var kind: IntentKind = .triple
    @State private var sector: Int32 = 20
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 6)

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("想练哪一格")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)

            Picker("意图", selection: $kind) {
                Text(IntentKind.triple.label).tag(IntentKind.triple)
                Text(IntentKind.double_.label).tag(IntentKind.double_)
                Text(IntentKind.singleOuter.label).tag(IntentKind.singleOuter)
                Text(IntentKind.bull.label).tag(IntentKind.bull)
            }
            .pickerStyle(.segmented)

            if kind != .bull {
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(1...20, id: \.self) { number in
                        Button {
                            sector = Int32(number)
                        } label: {
                            Text("\(number)")
                                .frame(maxWidth: .infinity)
                                .frame(height: 40)
                                .background(sector == Int32(number) ? Palette.primary : Palette.surface)
                                .foregroundStyle(sector == Int32(number) ? Palette.onPrimary : Palette.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .accessibilityIdentifier("impactSector\(number)")
                    }
                }
            }

            NavigationLink {
                ImpactPracticeView(kind: kind, sector: sector)
            } label: {
                Text("开练（当前：\(IntentTarget(kind: kind, sector: sector).label)）")
                    .font(.headline)
                    .foregroundStyle(Palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .accessibilityIdentifier("impactStart")
            Spacer()
        }
        .padding()
        .background(Palette.background)
        .navigationTitle("精准工坊")
    }
}

/// 练习页：点靶记样本 → 随时可以开报告。
struct ImpactPracticeView: View {

    @State private var viewModel: ImpactPracticeViewModel
    /// 靶面上的落点标记（存**毫米**，不存像素）：存在对_pad 内部的话撤销时点不掉，
    /// 而存像素则在画面尺寸变化时全部错位。
    @State private var marks: [ImpactPoint] = []

    init(kind: IntentKind, sector: Int32) {
        _viewModel = State(initialValue: ImpactPracticeViewModel(kind: kind, sector: sector))
    }

    var body: some View {
        VStack(spacing: 12) {
            VStack(spacing: 2) {
                Text(viewModel.target.label)
                    .font(.headline)
                    .foregroundStyle(Palette.textPrimary)
                Text("投 \(viewModel.throwCount) 镖 · 命中 \(viewModel.hitRatePercent)%")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
                    .accessibilityIdentifier("impactCounter")
            }

            ImpactBoardTapPad(target: viewModel.target, marks: $marks) { xMm, yMm, isMiss in
                viewModel.record(xMm: xMm, yMm: yMm, isMiss: isMiss)
            }

            Text(viewModel.sampleHintText)
                .font(.caption)
                .foregroundStyle(viewModel.dartsToGo == 0 ? Palette.primary : Palette.textMuted)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("impactSampleHint")

            HStack(spacing: 12) {
                outlinedButton("撤销") {
                    viewModel.undoLast()
                    if marks.count > viewModel.throwCount { marks.removeLast() }
                }
                NavigationLink {
                    ImpactReportView(viewModel: viewModel)
                } label: {
                    Text("查看报告")
                        .font(.headline)
                        .foregroundStyle(Palette.onPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Palette.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .accessibilityIdentifier("impactReport")
            }
        }
        .padding()
        .background(Palette.background)
        .navigationTitle("精准工坊")
    }

    private func outlinedButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .foregroundStyle(Palette.textSecondary)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.divider, lineWidth: 1))
        }
    }
}

/**
 * 点选靶：**真实比例**的标准硬式靶局部放大图。
 *
 * 三条硬要求，逐条对应：
 * 1. **目标格居中** —— 视窗中心直接取 `IntentTarget.anchorMm()`，所以切到 T20 时 T20 格正好在框中心；
 * 2. **切目标不变倍率** —— `pxPerMm` 只由「框高 ÷ [verticalSpanMm]」决定，与半径无关。
 *    ⚠️ 不能取 `ImpactWindow.spanX`：它按目标半径算弧宽，换目标就会变焦，
 *    那样「T20 与 D11 的散布」根本没法横向比较；
 * 3. **比例照真实靶** —— 全部半径 / 角度来自 `BoardGeometry`（见 `BoardRender.swift`）。
 */
struct ImpactBoardTapPad: View {

    let target: IntentTarget
    @Binding var marks: [ImpactPoint]
    let onTap: (Double, Double, Bool) -> Void

    /** 框的下边沿到上边沿（含边框）覆盖的毫米跨度。**常数** ⇒ 放大倍率与目标无关。 */
    static let verticalSpanMm = 60.0
    /** 框高：原 300 pt 抬到 420 pt（+40%）。倍率随之从 5 pt/mm 升到 7 pt/mm，8 mm 环宽 ⇒ 56 pt，看得清。 */
    static let height: CGFloat = 420

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let pxPerMm = max(1, size.height / CGFloat(Self.verticalSpanMm))
            let anchor = target.anchorMm()
            let centerMm = CGPoint(x: anchor.x, y: anchor.y)
            let projection = BoardProjection(size: size, centerMm: centerMm, pxPerMm: pxPerMm)

            ZStack {
                DartBoardCanvas(
                    centerMm: centerMm,
                    pxPerMm: pxPerMm,
                    highlightSector: target.kind == IntentKind.bull ? nil : Int(target.sector)
                )

                aimCrosshair(center: projection.point(xMm: anchor.x, yMm: anchor.y))

                ForEach(marks.indices, id: \.self) { index in
                    let point = marks[index]
                    Circle()
                        .fill(point.isMiss ? Palette.error : Palette.accent)
                        .frame(width: 8, height: 8)
                        .position(projection.point(xMm: point.xMm, yMm: point.yMm))
                }

                Text("点一下 = 记一次落点 · 四周条带 = 出框")
                    .font(.caption)
                    .foregroundStyle(Palette.textPrimary)
                    .padding(6)
                    .background(.black.opacity(0.45))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, 8)
            }
            // ⚠️ accessibilityElement() 不能少：纯 Shape 组合的容器不会被 SwiftUI 暴露成可命中的元素，
            // UI 测试会找不到 impactPad，tap 也就落在空处（V9 最初的失败根因）。
            .contentShape(Rectangle())
            .accessibilityElement()
            .accessibilityIdentifier("impactPad")
            .accessibilityLabel("记镖靶面")
            .onTapGesture { location in
                record(location: location, size: size, projection: projection)
            }
        }
        .frame(height: Self.height)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func aimCrosshair(center: CGPoint) -> some View {
        ZStack {
            Circle()
                .stroke(Palette.primary, lineWidth: 2)
                .frame(width: 26, height: 26)
            Path { path in
                path.move(to: CGPoint(x: center.x - 18, y: center.y))
                path.addLine(to: CGPoint(x: center.x + 18, y: center.y))
                path.move(to: CGPoint(x: center.x, y: center.y - 18))
                path.addLine(to: CGPoint(x: center.x, y: center.y + 18))
            }
            .stroke(Palette.primary.opacity(0.8), lineWidth: 1)
        }
        .position(center)
    }

    private func record(location: CGPoint, size: CGSize, projection: BoardProjection) {
        let tapped = projection.mm(at: location)

        // ⚠️ density 传 1.0：SwiftUI 的布局单位就是 pt，与 Android 的 dp 同量纲，
        // 所以 `BAND_MIN_DP × 1` 直接就是想要的条带宽度。早先传的是 px/mm（≈7），
        // 条带被放大到 280 pt —— 大半块靶都成了「出框区」。
        let band = ImpactMissBand.shared.bandAt(
            xPx: Float(location.x),
            yPx: Float(location.y),
            widthPx: Float(size.width),
            heightPx: Float(size.height),
            density: 1.0
        )

        if let band {
            // 出框点仍要落到 mm 上才能进数据库与热点图：clamp + 外推的口径由引擎给。
            let viewport = BoardViewport(
                centerXMm: projection.centerMm.x,
                centerYMm: projection.centerMm.y,
                spanXMm: Double(size.width) / Double(projection.pxPerMm),
                spanYMm: Double(size.height) / Double(projection.pxPerMm)
            )
            let point = ImpactMissBand.shared.missPointMm(
                viewport: viewport,
                xMm: tapped.x,
                yMm: tapped.y,
                hit: band
            )
            let sample = ImpactPoint(xMm: point.x, yMm: point.y, isMiss: true)
            marks.append(sample)
            onTap(point.x, point.y, true)
        } else {
            let sample = ImpactPoint(xMm: tapped.x, yMm: tapped.y, isMiss: false)
            marks.append(sample)
            onTap(tapped.x, tapped.y, false)
        }
    }
}
