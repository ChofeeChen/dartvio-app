import SwiftUI
import shared

/**
 * 房间页：等候 → 对局 → 结算，**三种状态同一页**（房间状态是由事件流重放出来的，
 * 换页只会让"状态变了要跳去哪"变成一个额外的、容易错的状态机）。
 *
 * ## 对局显示的唯一来源是 `SpectatorSnapshot`
 *
 * 权威对局在 shared 里（`RoomMatchRules`），对局页与观战页读同一个快照，
 * 所以这里**不自己算分数**：自己算一份就会出现"我这边 320、对面看到 341"。
 */
struct RoomView: View {

    let roomId: String

    @State private var repository = OnlineRoomRepository()
    @State private var pendingDarts: [Dart] = []
    @State private var isSubmitting = false
    @State private var note: String?

    private var room: Room? { repository.state.room }
    private var match: RoomMatch? { repository.state.match }
    private var snapshot: SpectatorSnapshot? {
        guard let match else { return nil }
        return RoomMatchRules.shared.snapshot(match: match)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                if let room, room.status == RoomStatus.waiting {
                    waitingSection(room)
                } else if let snapshot {
                    matchSection(snapshot)
                } else if let room {
                    // 非 X01 房间：引擎给不出权威对局，如实说明，不要假装在加载。
                    Text("该房间不是 X01，P0 的权威联机对局不支持（观战请联系房主改建房设置）。")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                if let note {
                    Text(note).font(.caption).foregroundStyle(Palette.textMuted)
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("房间 #\(roomId)")
        .task { await repository.observe(roomId: roomId) }
        .onDisappear { repository.stop() }
    }

    // MARK: - 头部

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(room?.name ?? "房间 #\(roomId)")
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text(statusText)
                .font(.caption)
                .foregroundStyle(Palette.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var statusText: String {
        switch repository.status {
        case .notConfigured: return "联机后端未配置"
        case .connecting: return "连接中…"
        case .live: return "已连接（实时同步）"
        case .reconnecting: return "重连中…（另有 3 秒轮询兜底）"
        }
    }

    // MARK: - 等候

    private func waitingSection(_ room: Room) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("成员 \(room.members.count)/2")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.textPrimary)
            ForEach(room.members, id: \.id) { member in
                HStack {
                    Text(member.name)
                        .foregroundStyle(Palette.textPrimary)
                    if member.isCreator {
                        Text("房主")
                            .font(.caption2)
                            .foregroundStyle(Palette.primary)
                    }
                    Spacer()
                    Text(member.isReady ? "已准备" : "未准备")
                        .font(.caption)
                        .foregroundStyle(member.isReady ? Palette.primary : Palette.textMuted)
                }
            }

            Button {
                Task { await repository.setReady(!isReady(room)) }
            } label: {
                Text(isReady(room) ? "取消准备" : "准备")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Palette.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityIdentifier("roomReady")

            if let blocker = RoomRules.shared.startBlocker(room: room, memberId: repository.selfId) {
                Text(blocker.message)
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
            } else {
                Button {
                    Task { _ = await repository.startMatch() }
                } label: {
                    Text("开始对局")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Palette.primary)
                        .foregroundStyle(Palette.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .accessibilityIdentifier("roomStart")
            }

            Button(role: .destructive) {
                Task { await repository.leave() }
            } label: {
                Text("离开房间").font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("roomWaiting")
    }

    private func isReady(_ room: Room) -> Bool {
        room.members.first { $0.id == repository.selfId }?.isReady ?? false
    }

    // MARK: - 对局

    private func matchSection(_ snapshot: SpectatorSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            scores(snapshot)
            turns(snapshot)
            if snapshot.isFinished {
                result(snapshot)
            } else {
                input(snapshot)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("roomMatch")
    }

    private func scores(_ snapshot: SpectatorSnapshot) -> some View {
        VStack(spacing: 6) {
            ForEach(snapshot.players, id: \.id) { player in
                HStack {
                    Text(player.name)
                        .foregroundStyle(Palette.textPrimary)
                    if player.isActive {
                        Image(systemName: "arrow.right")
                            .font(.caption2)
                            .foregroundStyle(Palette.primary)
                    }
                    Spacer()
                    Text("\(player.score)")
                        .font(.title3.monospacedDigit())
                        .foregroundStyle(Palette.textPrimary)
                    Text("局 \(player.legsWon)")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                .padding(.vertical, 2)
            }
            Text("第 \(snapshot.leg) 局 · 先胜 \(snapshot.legsToWin) 局")
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
        }
        // 与 Cricket 那个坑同源：纯 VStack 不会暴露成无障碍节点，identifier 会丢。
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("roomScores")
    }

    private func turns(_ snapshot: SpectatorSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("最近回合").font(.caption).foregroundStyle(Palette.textMuted)
            ForEach(Array(snapshot.turns.suffix(5).reversed()), id: \.seq) { turn in
                HStack {
                    Text(turn.playerName)
                        .font(.caption)
                        .foregroundStyle(Palette.textSecondary)
                    Text(turn.darts)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(Palette.textPrimary)
                    Spacer()
                    Text(turn.isBust ? "BUST" : "+\(turn.scored) → \(turn.remaining)")
                        .font(.caption)
                        .foregroundStyle(turn.isBust ? Color(red: 0.9, green: 0.3, blue: 0.24) : Palette.textSecondary)
                }
            }
        }
    }

    private func result(_ snapshot: SpectatorSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            let winnerId = match?.winnerId
            let winner = snapshot.players.first { $0.id == winnerId }
            Text(winner == nil ? "对局结束" : "胜者：\(winner!.name)")
                .font(.headline)
                .foregroundStyle(Palette.primary)
            Button {
                Task { await repository.rematch() }
            } label: {
                Text("再来一局")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Palette.primary)
                    .foregroundStyle(Palette.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityIdentifier("roomRematch")
        }
    }

    private func input(_ snapshot: SpectatorSnapshot) -> some View {
        let isMyTurn = snapshot.players.first { $0.isActive }?.id == repository.selfId
        return VStack(alignment: .leading, spacing: 8) {
            if isMyTurn {
                Text("本回合已录 \(pendingDarts.count)/3 镖")
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
                KeypadView(
                    confirmTitle: "提交回合",
                    onDart: { dart in
                        guard pendingDarts.count < 3 else { return }
                        pendingDarts.append(dart)
                    },
                    onUndo: { if !pendingDarts.isEmpty { pendingDarts.removeLast() } },
                    onConfirm: { submit() }
                )
                .disabled(isSubmitting)
                if !pendingDarts.isEmpty {
                    Button("清空") { pendingDarts = [] }
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
            } else {
                Text("等待对手投镖…")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textSecondary)
            }
        }
    }

    private func submit() {
        guard !pendingDarts.isEmpty, !isSubmitting else { return }
        isSubmitting = true
        let darts = pendingDarts
        Task {
            let ok = await repository.submitTurn(darts: darts)
            if ok {
                pendingDarts = []
            } else {
                note = repository.errorMessage ?? "提交失败，已保留本回合录入"
            }
            isSubmitting = false
        }
    }
}
