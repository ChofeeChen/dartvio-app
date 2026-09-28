import SwiftUI

/**
 * Cricket MPR 挑战页，对齐 Android `ui/practice/CricketMprScreen.kt`。
 *
 * 输入复用通用 `KeypadView`：它能给出任意 (number, multiplier) 组合，正好覆盖 Cricket 的靶目
 * （15–20 与 BULL）；标记数由 shared 的 `cricketMarks` 判定，本页不做第二套换算。
 */
struct CricketMprPracticeView: View {

    @State private var viewModel = CricketMprViewModel()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 12) {
                    header
                    statsGrid
                    currentRoundRow
                    if viewModel.state.finished {
                        finishedBanner
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 10)
            }
            KeypadView(
                confirmTitle: "投镖",
                onDart: { viewModel.record($0) },
                onUndo: { viewModel.undo() },
                onConfirm: { }
            )
        }
        .background(Palette.background)
        .navigationTitle("Cricket MPR 挑战")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(viewModel.state.finished ? "再来一次" : "提前结束") {
                    viewModel.state.finished ? viewModel.restart() : viewModel.finishEarly()
                }
                .font(.caption)
                .foregroundStyle(Palette.primary)
            }
        }
    }

    private var header: some View {
        VStack(spacing: 4) {
            Text(viewModel.mprText)
                .font(.system(size: 46, weight: .bold, design: .rounded))
                .foregroundStyle(Palette.primary)
                .accessibilityIdentifier("mprValue")
            Text("\(viewModel.ratingText) · 第 \(viewModel.state.currentRoundNumber) / \(viewModel.totalRounds) 轮")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
                .accessibilityIdentifier("mprMeta")
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var statsGrid: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                statCell("总标记", "\(viewModel.state.totalMarks)")
                statCell("命中率", "\(viewModel.state.hitRate)%")
            }
            HStack(spacing: 8) {
                statCell("最佳单轮", "\(viewModel.state.bestRoundMarks)")
                statCell("已投镖", "\(viewModel.state.dartsThrown)")
            }
        }
    }

    private func statCell(_ title: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(title).font(.caption2).foregroundStyle(Palette.textMuted)
            Text(value).font(.headline).foregroundStyle(Palette.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private var currentRoundRow: some View {
        VStack(spacing: 6) {
            Text("本轮 \(viewModel.state.currentRoundMarks) 标记")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { index in
                    let darts = viewModel.state.currentRoundDarts
                    DartSlot(text: index < darts.count ? DartText.label(darts[index].dart) : "")
                }
            }
        }
    }

    private var finishedBanner: some View {
        VStack(spacing: 8) {
            Text("本场结束 · MPR \(viewModel.mprText)")
                .font(.headline.weight(.bold))
                .foregroundStyle(Palette.primary)
                .accessibilityIdentifier("mprFinished")
            Button("再来一次") { viewModel.restart() }
                .font(.headline)
                .foregroundStyle(Palette.onPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Palette.primary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}
