import SwiftUI
import shared

/**
 * 训练中心：**一页直达**所有子入口。
 *
 * ## 为什么改成「两张父卡 + 卡内子入口」
 *
 * 之前是三个平铺 section，进双人训练还要再点一次列表才看得到六个模式 ——
 * 每次都得先判断「这个模式练什么、它藏在第几层」。现在父标题与子入口**同屏同卡**：
 *
 * - 单人训练（左）／双人对抗训练（右）：父卡里直接列出各自的子入口，一跳进入；
 * - 精准工坊（下方通栏）：它既是一个模块、又只有一个入口，所以整卡即按钮。
 *
 * ## 归属关系怎么体现
 *
 * 子入口放在父卡**内部**、带 `›` 前导标记，父标题是一条独立色带 ——
 * 用「包含在卡片里」表达归属，而不是靠缩进深浅让人去猜。
 */
struct TrainingCenterView: View {

    /**
     * ⚠️ 用 `ScrollView` 而不是 `List`：List 的屏幕外行**不会渲染**，
     * XCUITest 就查不到（实测「双人对抗训练」在最后一屏外，V10 直接找不到按钮）。
     * 内容一共 13 项，全部一次渲染没有性能代价。
     */
    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack(alignment: .top, spacing: 12) {
                    soloCard
                    versusCard
                }
                impactCard
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("训练中心")
        .accessibilityIdentifier("trainingCenter")
    }

    // MARK: - 单人训练（左）

    private var soloCard: some View {
        moduleCard(title: "单人训练", subtitle: "结镖与分数推进", identifier: "trainSolo") {
            subEntry("Count Up 练习", "8 轮 × 3 镖，累计总分", identifier: "trainCountUp") { CountUpPracticeView() }
            subEntry("随机结镖", "路线学习：随机目标分，最多 3 镖双结", identifier: "trainRandomCheckout") { RandomCheckoutPracticeView() }
            subEntry("极速挑战", "限时连续结镖，含战报", identifier: "trainRush") { CheckoutRushPracticeView() }
            subEntry("99 Darts", "分区推进，先到 99 分", identifier: "train99") { NinetyNineSetupView() }
            subEntry("Cricket MPR 挑战", "限时刷 MPR", identifier: "trainCricketMpr") { CricketMprPracticeView() }
            subEntry("AI 对战练习", "与 AI 打一局 X01", identifier: "trainAiMatch") {
                X01GameView(launch: X01Launch(
                    targetScore: 301,
                    modeRaw: "casual",
                    legsToWin: 1,
                    outModeRaw: "doubleOut",
                    inModeRaw: "straightIn",
                    smartAi: true,
                    difficultyRaw: "advanced",
                    opponents: [OpponentSeat(isAi: true, difficultyRaw: "advanced")]
                ))
            }
        }
    }

    // MARK: - 双人对抗训练（右）

    /// 六个模式**直接来自引擎注册表**，不在 iOS 侧抄一份清单 —— 加第 7 个模式时两端都不会漏。
    private var versusCard: some View {
        moduleCard(title: "双人对抗训练", subtitle: "同一台设备轮流投", identifier: "trainVersus") {
            ForEach(versusModes.indices, id: \.self) { index in
                let mode = versusModes[index]
                if mode.available {
                    subEntry(mode.title, mode.desc, identifier: "trainVersus\(mode.modeKey)") {
                        VersusSetupView(modeKey: mode.modeKey)
                    }
                } else {
                    // 不可用模式照样列出（让人知道有这个模式），但不给点。
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("›").font(.caption).foregroundStyle(Palette.textMuted)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(mode.title).font(.subheadline).foregroundStyle(Palette.textMuted)
                            Text(mode.desc).font(.caption2).foregroundStyle(Palette.textMuted)
                        }
                        Spacer(minLength: 0)
                        Text("不可用").font(.caption2).foregroundStyle(Palette.textMuted)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .opacity(0.5)
                }
            }
        }
    }

    private var versusModes: [VersusModeInfo] { VersusModes.shared.ALL }

    // MARK: - 精准工坊（下方通栏，整卡即按钮）

    /**
     * 精准工坊**不做成父卡**：它只有一个入口，再包一层子入口列表纯属浪费点击。
     *
     * 位置放在两张父卡**下方**：先选对抗形态（单人 / 双人），再进工坊做落点诊断，
     * 与真实的训练顺序一致；同时它通栏，视觉上不会被误读成某一张父卡的子项。
     */
    private var impactCard: some View {
        NavigationLink { ImpactSetupView() } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text("精准工坊")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.textPrimary)
                Text("落点偏差 · 稳定性 · 专项处方")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Palette.primary.opacity(0.5), lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .accessibilityIdentifier("trainImpact")
    }

    // MARK: - 父卡

    private func moduleCard<Content: View>(
        title: String,
        subtitle: String,
        identifier: String,
        @ViewBuilder entries: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // 父标题是一条色带：与下面的子入口区分开，归属一眼可见。
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.textPrimary)
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(Palette.surfaceVariant)

            VStack(alignment: .leading, spacing: 0) { entries() }
                .padding(.vertical, 4)
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier(identifier)
    }

    // MARK: - 子入口

    private func subEntry<Destination: View>(
        _ title: String,
        _ subtitle: String,
        identifier: String,
        @ViewBuilder destination: () -> Destination
    ) -> some View {
        NavigationLink { destination() } label: {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                // `›` 是归属标记：没有它，子入口与父标题只是「上下相邻」。
                Text("›")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline)
                        .foregroundStyle(Palette.textPrimary)
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            // 整行可点（HStack 不声明的话只有文字是热区）。
            .contentShape(Rectangle())
        }
        .accessibilityIdentifier(identifier)
    }
}
