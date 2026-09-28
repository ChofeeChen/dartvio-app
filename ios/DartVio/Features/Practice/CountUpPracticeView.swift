import SwiftUI
import shared

/// P4：Count Up 练习页。与对局页共用 `KeypadView`，只是没有「剩余分」概念（currentRemaining = nil）。
struct CountUpPracticeView: View {
    @State private var viewModel = CountUpViewModel()

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("第 \(viewModel.state.currentRoundNumber) / 8 轮")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textSecondary)
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("累计总分")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                    Text("\(viewModel.state.totalScore)")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(Palette.primary)
                }
            }
            .padding(.horizontal)

            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { index in
                    let darts = viewModel.state.currentDarts
                    DartSlot(text: index < darts.count ? DartText.label(darts[index]) : "")
                }
            }
            .padding(.horizontal)

            Text("本轮 \(viewModel.state.currentRoundScore) 分")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)

            if viewModel.state.bustFlash {
                Text("BUST · 本轮 0 分")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 34)
                    .background(Palette.error)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .padding(.horizontal)
            }

            Spacer(minLength: 0)

            KeypadView(
                confirmTitle: "确认",
                onDart: { viewModel.throwDart($0) },
                onUndo: { viewModel.undoLastDart() },
                onConfirm: { viewModel.bust() }
            )
            .padding(.horizontal)

            Text("提示：未投镖时按「确认」= Bust（本轮 0 分）；每轮 3 镖后自动进入下一轮。")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
                .padding(.bottom, 8)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Count Up 练习")
        .navigationDestination(isPresented: finishedBinding) {
            CountUpResultView(viewModel: viewModel)
        }
    }

    /// 结算页由「finished」驱动；返回时顺手重置，否则 finished 仍为 true 会被立刻再次推出来。
    private var finishedBinding: Binding<Bool> {
        Binding(
            get: { viewModel.state.finished },
            set: { presented in
                if !presented { viewModel.restart() }
            }
        )
    }
}
