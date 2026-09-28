import SwiftUI
import shared

/**
 * 精准工坊报告页，对齐 Android `ui/practice/ImpactReportScreen.kt` 的数据部分。
 *
 * 所有数值口径（偏差 / σ / RMSE / R95 / 画像）都来自 shared，
 * Android 侧那些统计图表（热力图、趋势线、处方卡）依赖多会话历史，**iOS 暂无持久化**，故不实现。
 */
struct ImpactReportView: View {

    let viewModel: ImpactPracticeViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(spacing: 6) {
                    Text(viewModel.headline)
                        .font(.headline)
                        .foregroundStyle(Palette.primary)
                        .multilineTextAlignment(.center)
                        .accessibilityIdentifier("impactHeadline")
                    Text("共 \(viewModel.throwCount) 镖 · 命中 \(viewModel.hitRatePercent)%")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                if let stats = viewModel.stats {
                    VStack(spacing: 8) {
                        statRow("样本数", "\(stats.n)")
                        statRow("平均偏差", String(format: "%.1f mm", stats.bias))
                        statRow("径向偏差", String(format: "%.1f mm", stats.biasRad))
                        statRow("切向偏差", String(format: "%.1f mm", stats.biasTan))
                        statRow("离散 σ", String(format: "%.1f mm", max(stats.sigmaRad, stats.sigmaTan)))
                        statRow("R95", String(format: "%.1f mm", stats.r95))
                        statRow("RMSE", String(format: "%.1f mm", stats.rmse))
                    }
                    .accessibilityIdentifier("impactStats")
                } else {
                    Text("样本不足，先投几镖再来")
                        .font(.subheadline)
                        .foregroundStyle(Palette.textMuted)
                }

                if let portrait = viewModel.fingerprintText {
                    Text(portrait)
                        .font(.subheadline)
                        .foregroundStyle(Palette.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Palette.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .accessibilityIdentifier("impactPortrait")
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("精准工坊报告")
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
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
