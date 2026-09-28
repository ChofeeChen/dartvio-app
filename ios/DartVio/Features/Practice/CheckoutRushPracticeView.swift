import shared

import SwiftUI

/**
 * 极速挑战做题页，对齐 Android `ui/practice/CheckoutRushScreen.kt` 的核心部分
 * （Android 另有 rubric：输入模式切换、切后台中断弹窗，iOS MVP 暂不做，见 VM 注释）。
 */
struct CheckoutRushPracticeView: View {

    @State private var viewModel: CheckoutRushViewModel

    init(difficulty: RushDifficulty = .mixed) {
        _viewModel = State(initialValue: CheckoutRushViewModel(difficulty: difficulty))
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 12) {
                    targetBoard
                    dartsRow
                    if viewModel.showRouteHint { hintRow }
                    if case .revealed(let result) = viewModel.phase {
                        resultBanner(result)
                        nextButton
                    } else {
                        primaryRow
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 10)
            }
            KeypadView(
                confirmTitle: "投镖",
                onDart: { viewModel.addDart($0) },
                onUndo: { viewModel.undoLastDart() },
                onConfirm: { }
            )
        }
        .background(Palette.background)
        .navigationTitle("极速挑战")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Text("第 \(viewModel.questionIndex) / \(viewModel.totalQuestions) 题 · \(viewModel.elapsedSecondsText)")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
                    .accessibilityIdentifier("rushProgress")
            }
        }
        .navigationDestination(isPresented: Binding(
            get: { viewModel.phase == .finished },
            set: { _ in }
        )) {
            CheckoutRushReportView(viewModel: viewModel)
        }
    }

    // MARK: - 子视图

    private var targetBoard: some View {
        HStack(spacing: 12) {
            valueCard(title: "目标", value: "\(viewModel.currentTarget.score)")
            valueCard(title: "剩余", value: "\(viewModel.remaining)")
        }
    }

    private func valueCard(title: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
            Text(value)
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(Palette.textPrimary)
                .minimumScaleFactor(0.6)
                .accessibilityIdentifier("rush\(title)")
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var dartsRow: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { index in
                DartSlot(text: index < viewModel.darts.count ? DartText.label(viewModel.darts[index]) : "")
            }
        }
    }

    private var hintRow: some View {
        HStack(spacing: 6) {
            Text("推荐路线")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
            Text(viewModel.preferredRouteText)
                .font(.system(size: 15, weight: .medium, design: .monospaced))
                .foregroundStyle(Palette.primary)
            Spacer()
        }
        .padding(10)
        .background(Palette.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityIdentifier("rushHint")
    }

    private func resultBanner(_ result: RushResult) -> some View {
        // Kotlin enum 导出为 class 实例，判定同 §13.4：用 isEqual，不用 ==。
        let ok = RushResult.checkout.isEqual(result)
        return Text(result.label)
            .font(.headline.weight(.bold))
            .foregroundStyle(ok ? Palette.primary : Palette.error)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background((ok ? Palette.primary : Palette.error).opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .accessibilityIdentifier("rushResult")
    }

    private var primaryRow: some View {
        HStack(spacing: 12) {
            outlinedButton(viewModel.showRouteHint ? "隐藏路线" : "看路线") { viewModel.toggleRouteHint() }
            outlinedButton("跳过") { viewModel.skip() }
            outlinedButton("重做") { viewModel.retry() }
        }
    }

    private var nextButton: some View {
        Button {
            viewModel.next()
        } label: {
            Text(viewModel.questionIndex >= viewModel.totalQuestions ? "查看战报" : "下一题")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Palette.primary)
                .foregroundStyle(Palette.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .accessibilityIdentifier("rushNext")
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
