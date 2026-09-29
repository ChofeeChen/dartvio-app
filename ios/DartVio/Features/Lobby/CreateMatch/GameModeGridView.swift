import SwiftUI

/**
 * 【游戏模式】3 列网格单选页。
 *
 * 从创建比赛弹窗的「下拉箭头」进来：**选中即返回**，所以这一页没有"确定"按钮 ——
 * 有确定按钮就会出现"选了但没确认"的中间态，而需求要的是"选完自动回填游戏名称"。
 *
 * ## P0 / P1 都要能看见，但不能混淆
 *
 * 徽章写档位；能不能开由 `onlineReady` 决定，并在卡片上写明 ——
 * STANDARD CRICKET 是 P0 但联机未开放，如果只标 P0，用户会以为选了就能建房。
 */
struct GameModeGridView: View {

    let selected: GameCatalog.Game
    let onSelect: (GameCatalog.Game) -> Void
    let onClose: () -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider().background(Palette.divider)
            ScrollView {
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(GameCatalog.all) { game in
                        cell(game)
                    }
                }
                .padding(16)
            }
        }
        .background(Palette.background)
        // 与 `roomWaiting` 同源：纯 VStack 不声明成容器，identifier 会落到子按钮上，
        // 容器本身查不到（`otherElements["gameModeGrid"]` 不存在）。
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("gameModeGrid")
    }

    private var header: some View {
        ZStack {
            Text("选择游戏")
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            HStack {
                Button { onClose() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .frame(width: 30, height: 30)
                        .background(Palette.surfaceVariant)
                        .clipShape(Circle())
                }
                .accessibilityIdentifier("gameModeClose")
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private func cell(_ game: GameCatalog.Game) -> some View {
        let isSelected = game.id == selected.id
        return Button { onSelect(game) } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 4) {
                    Text(game.tier.rawValue)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(game.tier == .p0 ? CreateMatchStyle.selected : Palette.textMuted)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(
                            Capsule().stroke(
                                game.tier == .p0 ? CreateMatchStyle.selected : Palette.divider, lineWidth: 1
                            )
                        )
                    Spacer()
                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(CreateMatchStyle.selected)
                    }
                }
                Text(game.title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                statusLine(game)
            }
            .frame(minHeight: 82, alignment: .topLeading)
            .padding(10)
            .background(isSelected ? CreateMatchStyle.selected.opacity(0.16) : Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? CreateMatchStyle.selected : Palette.divider, lineWidth: isSelected ? 2 : 1)
            )
        }
        .accessibilityIdentifier("gameMode_\(game.id)")
    }

    @ViewBuilder
    private func statusLine(_ game: GameCatalog.Game) -> some View {
        if game.onlineReady {
            Text("可创建")
                .font(.system(size: 9))
                .foregroundStyle(CreateMatchStyle.selected)
        } else if game.tier == .p0 {
            Text("联机待开放")
                .font(.system(size: 9))
                .foregroundStyle(Palette.warning)
        } else {
            Text("尚未开放")
                .font(.system(size: 9))
                .foregroundStyle(Palette.textMuted)
        }
    }
}
