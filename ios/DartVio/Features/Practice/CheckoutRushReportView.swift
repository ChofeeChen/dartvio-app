import shared

import SwiftUI

/**
 * 极速挑战战报，对齐 Android `ui/practice/CheckoutRushReportScreen.kt`。
 *
 * 所有数字都由 shared 的 `CheckoutRushStatistics` 算好送过来，本页不做任何统计口径的推导
 * —— 尤其**不能再自己除一遍成功率**（跳过/中断不计入分母，重算必错，见 `RushResult.countsTowardsSuccessRate`）。
 */
struct CheckoutRushReportView: View {

    let viewModel: CheckoutRushViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(spacing: 6) {
                    Text("\(viewModel.statistics.successRatePct)%")
                        .font(.system(size: 46, weight: .bold, design: .rounded))
                        .foregroundStyle(Palette.primary)
                        .accessibilityIdentifier("rushReportRate")
                    Text("成功率（共 \(viewModel.statistics.scoredAttempts) 题计入）")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(spacing: 8) {
                    statRow("结镖成功", "\(viewModel.statistics.successCount) 题")
                    statRow("爆分", "\(viewModel.statistics.bustCount) 次")
                    statRow("最长连续成功", "\(viewModel.statistics.longestSuccessStreak) 题")
                    statRow("平均用时", secondsText(viewModel.statistics.avgThrowMs))
                    statRow("最快成功", secondsText(viewModel.statistics.fastestUnhintedSuccessMs))
                    statRow("最常失手的目标", viewModel.statistics.mostFailedTarget.map { "\($0.intValue) 分" } ?? "—")
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("极速挑战战报")
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

    /**
     * Kotlin 侧是 `Long?`：`null` 表示本次会话没有可用样本（全跳过 / 没有成功）。
     *
     * `SharedLong` 是 `NSNumber` 子类（`SharedNumber : NSNumber`），所以直接按 NSNumber 取值
     * 比去猜它的 Swift 名字稳妥。
     */
    private func secondsText(_ millis: KotlinLong?) -> String {
        guard let number = millis as? NSNumber else { return "—" }
        return String(format: "%.1fs", number.doubleValue / 1000)
    }
}
