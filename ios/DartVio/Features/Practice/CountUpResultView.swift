import SwiftUI
import shared

/// P5：Count Up 结算页。平均分/单轮最高等派生值全部来自 `CountUpState` 自带的计算属性，iOS 侧不重算。
struct CountUpResultView: View {
    let viewModel: CountUpViewModel

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 4) {
                    Text("总分")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                    Text("\(viewModel.state.totalScore)")
                        .font(.system(size: 52, weight: .bold, design: .rounded))
                        .foregroundStyle(Palette.primary)
                    if viewModel.state.totalScore >= viewModel.best && viewModel.state.totalScore > 0 {
                        Text("NEW RECORD")
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Palette.accent)
                            .foregroundStyle(Palette.onAccent)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                    Text("历史最佳 \(viewModel.best)")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .padding(.top, 12)

                VStack(spacing: 8) {
                    // Kotlin 的 averagePerRound 是 Int（整除），不能按浮点格式化：
                    // %.1f 会从错误的寄存器宽度读值，显示成无意义数字
                    statRow("平均每轮", "\(viewModel.state.averagePerRound)")
                    statRow("单轮最高", "\(viewModel.state.maxRoundScore)")
                    statRow("完成镖数", "\(viewModel.state.dartsThrown)")
                    statRow("完成轮次", "\(viewModel.state.roundsPlayed)")
                }
                .padding()
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 8) {
                    Text("各轮得分")
                        .font(.subheadline)
                        .foregroundStyle(Palette.textSecondary)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(viewModel.roundScores.indices, id: \.self) { index in
                            Text(
                                viewModel.roundScores[index]
                                    .map { "\($0)" } ?? "—"
                            )
                                .font(.system(size: 15, weight: .medium, design: .monospaced))
                                .frame(maxWidth: .infinity)
                                .frame(height: 38)
                                .background(Palette.surfaceVariant)
                                .foregroundStyle(Palette.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                }
                .padding()
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                PrimaryButton(title: "再来一次") {
                    viewModel.restart()
                    dismiss()
                }
                Button("返回") {
                    viewModel.restart()
                    dismiss()
                }
                .foregroundStyle(Palette.textSecondary)
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("练习完成")
        .navigationBarBackButtonHidden(true)
    }

    private func statRow(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title).foregroundStyle(Palette.textSecondary)
            Spacer()
            Text(value).foregroundStyle(Palette.textPrimary)
        }
        .font(.subheadline)
    }
}
