import SwiftUI
import shared

/**
 * 99 Darts 练习页，对齐 Android `ui/practice/NinetyNineScreen.kt`。
 *
 * 输入只有四种结果（MISS / 单 / 双 / 三），所以**不用通用数字键盘**，
 * 而是四个大按钮 —— 这也是 Android 的做法（普通 Keypad 在这里会要求玩家自己按倍率键，多一步且易错）。
 */
struct NinetyNinePracticeView: View {

    let sector: Int32
    @State private var viewModel: NinetyNineViewModel

    init(sector: Int32) {
        self.sector = sector
        _viewModel = State(initialValue: NinetyNineViewModel(sector: sector))
    }

    var body: some View {
        VStack(spacing: 14) {
            header
            statsGrid
            currentRoundRow
            hitPad
            if viewModel.state.finished {
                Text("已完成 99 镖")
                    .font(.headline)
                    .foregroundStyle(Palette.primary)
                    .accessibilityIdentifier("nnFinished")
                Button("重来一次") { viewModel.restart() }
                    .font(.headline)
                    .foregroundStyle(Palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding()
        .background(Palette.background)
        .navigationTitle("99 Darts · \(sector)")
    }

    private var header: some View {
        VStack(spacing: 4) {
            Text("\(viewModel.state.totalPoints)")
                .font(.system(size: 46, weight: .bold, design: .rounded))
                .foregroundStyle(Palette.primary)
                .accessibilityIdentifier("nnTotal")
            Text("第 \(viewModel.state.currentRoundNumber) / \(viewModel.totalRounds) 轮 · 已投 \(viewModel.state.dartsThrown) / \(viewModel.totalDarts) 镖")
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
                .accessibilityIdentifier("nnProgress")
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var statsGrid: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                statCell("命中率", "\(viewModel.state.hitRatePercent)%")
                statCell("三倍率", "\(viewModel.state.tripleRatePercent)%")
            }
            HStack(spacing: 8) {
                statCell("最高单轮", "\(viewModel.state.maxRoundPoints)")
                statCell("满轮次数", "\(viewModel.state.perfectRounds)")
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
            Text("本轮 \(viewModel.state.currentRoundPoints) 分（\(viewModel.dartsInRound)/3 镖）")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { index in
                    let hits = viewModel.state.currentRoundThrows
                    DartSlot(text: index < hits.count ? hits[index].shortLabel : "")
                }
            }
        }
    }

    private var hitPad: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                hitButton("S", SectorHit.single)
                // Kotlin 枚举成员 DOUBLE 导出时被转义成 `double_`（ObjC 关键字冲突，同 doNewLeg / doNewTarget）
                hitButton("D", SectorHit.double_)
                hitButton("T", SectorHit.triple)
            }
            HStack(spacing: 8) {
                outlinedButton("MISS") { viewModel.record(SectorHit.miss) }
                outlinedButton("撤销") { viewModel.undo() }
            }
        }
    }

    private func hitButton(_ title: String, _ hit: SectorHit) -> some View {
        Button {
            viewModel.record(hit)
        } label: {
            Text(title)
                .font(.title2.weight(.bold))
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(hitBackground(hit))
                .foregroundStyle(Palette.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .accessibilityIdentifier("nnHit\(title)")
    }

    private func hitBackground(_ hit: SectorHit) -> Color {
        switch hit.points {
        case 3: return Palette.primary
        case 2: return Palette.accent
        default: return Palette.secondaryDark
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

/// 选扇区页，对齐 Android `ui/practice/NinetyNineSetupScreen.kt`。
struct NinetyNineSetupView: View {
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("先选一个要练的扇区")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(1...20, id: \.self) { sector in
                    NavigationLink {
                        NinetyNinePracticeView(sector: Int32(sector))
                    } label: {
                        Text("\(sector)")
                            .font(.headline)
                            .foregroundStyle(Palette.textPrimary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Palette.surface)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .accessibilityIdentifier("nnSector\(sector)")
                }
            }
            Spacer()
        }
        .padding()
        .background(Palette.background)
        .navigationTitle("99 Darts")
        // ⚠️ 同 Cricket / X01：本页现在是被 push 进来的，值型路由不生效，用视图型链接。
    }
}
