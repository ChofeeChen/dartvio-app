import SwiftUI

/**
 * 随机结镖练习页，对齐 Android `ui/practice/RandomCheckoutScreen.kt`。
 *
 * 结构照搬 Android：目标 / 剩余两块大数字 → 已投三镖 → 结果条 → 答案开关 → 键盘。
 * 键盘直接复用 X01 与 Count Up 那个 `KeypadView`，本页不另做输入控件。
 */
struct RandomCheckoutPracticeView: View {

    @State private var viewModel = RandomCheckoutViewModel()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 12) {
                    scoreBoard
                    dartsRow
                    if viewModel.state.isFinished { resultBanner }
                    answerSection
                    actionRow
                }
                .padding(.horizontal)
                .padding(.vertical, 10)
            }
            KeypadView(
                confirmTitle: "投镖",
                onDart: { viewModel.throwDart($0) },
                onUndo: { viewModel.retry() },
                onConfirm: { }
            )
        }
        .background(Palette.background)
        .navigationTitle("随机结镖练习")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Text("今日 \(viewModel.attempts) 次 · \(viewModel.successRate)%")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
                    .accessibilityIdentifier("rcStats")
            }
        }
    }

    // MARK: - 子视图

    private var scoreBoard: some View {
        HStack(spacing: 12) {
            valueCard(title: "目标", value: "\(viewModel.state.target)")
            valueCard(title: "剩余", value: "\(viewModel.state.remaining)")
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
                .accessibilityIdentifier("rc\(title)")
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var dartsRow: some View {
        let darts = viewModel.state.darts
        return HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { index in
                DartSlot(text: index < darts.count ? DartText.label(darts[index]) : "")
            }
        }
        .accessibilityIdentifier("rcDarts")
    }

    private var resultBanner: some View {
        let ok = viewModel.isSuccess
        return Text(viewModel.resultTitle)
            .font(.headline.weight(.bold))
            .foregroundStyle(ok ? Palette.primary : Palette.error)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background((ok ? Palette.primary : Palette.error).opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .accessibilityIdentifier("rcResult")
    }

    private var answerSection: some View {
        VStack(spacing: 8) {
            Button {
                viewModel.toggleAnswer()
            } label: {
                Text(viewModel.state.showAnswer ? "隐藏答案" : "查看答案")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .foregroundStyle(Palette.primary)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.primary, lineWidth: 1))
            }
            .disabled(!viewModel.hasAnswer)
            .opacity(viewModel.hasAnswer ? 1 : 0.4)

            if viewModel.state.showAnswer {
                Text(viewModel.answerText.isEmpty ? "无可用路线" : viewModel.answerText)
                    .font(.system(size: 15, weight: .medium, design: .monospaced))
                    .foregroundStyle(Palette.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(Palette.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .accessibilityIdentifier("rcAnswer")
            }
        }
    }

    private var actionRow: some View {
        HStack(spacing: 12) {
            outlinedButton("重试本题") { viewModel.retry() }
            outlinedButton("跳过此题") { viewModel.nextTarget() }
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
