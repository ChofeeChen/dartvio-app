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
 * 坐标换算交给 shared：视图只产出 **归一化坐标**，毫米值由 `ImpactWindow.viewportOf` 给出的视窗换算。
 */
/**
 * 精准工坊的路由（设置 → 练习 → 报告）。
 *
 * 同 `VersusRoute`：两级都用 `Bool` 会让两个 `navigationDestination(for: Bool.self)`
 * 落在同一栈里，「下一级是谁」变成注册顺序的问题。
 */
private enum ImpactRoute: Hashable {
    case practice
    case report
}

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

            NavigationLink(value: ImpactRoute.practice) {
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
        .navigationDestination(for: ImpactRoute.self) { route in
            if case .practice = route { ImpactPracticeView(kind: kind, sector: sector) }
        }
    }
}

/// 练习页：点靶记样本 → 够了就可以开报告。
struct ImpactPracticeView: View {

    @State private var viewModel: ImpactPracticeViewModel
    /// 靶面上的落点标记。**放在这里而不是 pad 内部**：撤销时要点掉最后一个点，
    /// 否则「撤销」只减计数、画面上的点还在，两边立刻对不上。
    @State private var marks: [CGPoint] = []

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

            ImpactBoardTapPad(viewport: viewModel.viewport, marks: $marks) { xMm, yMm, isMiss in
                viewModel.record(xMm: xMm, yMm: yMm, isMiss: isMiss)
            }

            HStack(spacing: 12) {
                outlinedButton("撤销") {
                    viewModel.undoLast()
                    if marks.count > viewModel.throwCount { marks.removeLast() }
                }
                NavigationLink(value: ImpactRoute.report) {
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
        .navigationDestination(for: ImpactRoute.self) { route in
            if case .report = route { ImpactReportView(viewModel: viewModel) }
        }
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
 * 自绘的点选靶：把 tap 的归一化坐标换算成毫米，命中判定交给 `ImpactMissBand`。
 *
 * 视窗是 **center ± span/2** 的矩形；坐标系 y 轴向上，所以纵向要做一次翻转。
 */
struct ImpactBoardTapPad: View {

    let viewport: BoardViewport
    /// 落点标记由宿主持有（撤销时要能点掉最后一个点），见 `ImpactPracticeView.marks`。
    @Binding var marks: [CGPoint]
    let onTap: (Double, Double, Bool) -> Void

    var body: some View {
        GeometryReader { geo in
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Palette.surfaceVariant)
                targetRings(size: geo.size)
                ForEach(Array(marks.enumerated()), id: \.offset) { _, point in
                    Circle()
                        .fill(Palette.accent)
                        .frame(width: 8, height: 8)
                        .position(point)
                }
                Text("点一下=记一次落点")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
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
                marks.append(location)
                let width = Double(geo.size.width)
                let height = Double(geo.size.height)
                let u = Double(location.x) / width
                let v = Double(location.y) / height

                // 视窗是 center ± span/2 的矩形，y 轴向上 → 纵向翻转一次。
                let xMm = viewport.centerXMm + (u - 0.5) * viewport.spanXMm
                let yMm = viewport.centerYMm + (0.5 - v) * viewport.spanYMm

                // 命中判定交给 shared：`bandAt` 返回 nil = 落在目标扇区内，否则是某一档脱靶。
                let density = Float(width / viewport.spanXMm)
                let band = ImpactMissBand.shared.bandAt(
                    xPx: Float(location.x),
                    yPx: Float(location.y),
                    widthPx: Float(width),
                    heightPx: Float(height),
                    density: density
                )
                onTap(xMm, yMm, band != nil)
            }
        }
        .frame(height: 300)
    }

    private func targetRings(size: CGSize) -> some View {
        let side = min(size.width, size.height)
        return ZStack {
            ForEach([0.30, 0.55, 0.80], id: \.self) { ratio in
                Circle()
                    .stroke(Palette.divider, lineWidth: 1)
                    .frame(width: side * ratio, height: side * ratio)
            }
            Circle()
                .fill(Palette.primary.opacity(0.35))
                .frame(width: side * 0.12, height: side * 0.12)
        }
    }

}
