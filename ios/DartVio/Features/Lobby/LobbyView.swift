import SwiftUI
import shared

/**
 * 比赛大厅。
 *
 * ## P0 的边界是引擎给的，不是 UI 定的
 *
 * `RoomMatchRules.supportsLiveMatch` 只认 X01，房间 `MAX_MEMBERS = 2`（1v1）——
 * 所以这里**不提供** Cricket 的联机入口：给一个引擎算不了权威对局的玩法挂"联机"按钮，
 * 等于承诺一件做不到的事。
 *
 * ## 未配置后端时必须如实说
 *
 * 联机后端是自建的（Postgres + PostgREST + Supabase Realtime），没有默认值。
 * 与其让列表永远空着、看起来像"没人建房"，不如直接告诉用户缺什么。
 */
struct LobbyView: View {

    @State private var rooms: [WireRoom] = []
    @State private var message: String?
    @State private var isLoading = false
    @State private var showConfig = false
    @State private var showCreate = false
    @State private var showJoin = false
    @State private var activeRoomId: String?
    @State private var showRoom = false
    private let api = OnlineRoomApi()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                scopeCard
                if OnlineConfig.isConfigured {
                    actionRow
                    roomList
                } else {
                    notConfiguredCard
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("比赛大厅")
        .task { await reload() }
        .refreshable { await reload() }
        .sheet(isPresented: $showConfig) { ConfigSheet { Task { await reload() } } }
        .sheet(isPresented: $showCreate) { CreateRoomSheet { openRoom($0) } }
        .sheet(isPresented: $showJoin) { JoinRoomSheet { openRoom($0) } }
        // 建房 / 加入成功后直接进房间：用 sheet 而不是 push，因为「刚创建的房间」不在列表里，
        // 没有可点的行（也就不必为了让 push 生效去折腾 navigationDestination）。
        .sheet(isPresented: $showRoom) {
            NavigationStack { RoomView(roomId: activeRoomId ?? "") }
        }
    }

    // MARK: - 卡片

    private var scopeCard: some View {
        infoCard(title: "P0 范围", lines: [
            "玩法：仅 X01（引擎的权威联机对局目前只支持 X01）",
            "人数：1v1 房间",
            "流程：建房 / 加入 → 等候 → 对局 → 结算 → 再来一局",
        ])
    }

    private func infoCard(title: String, lines: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline).foregroundStyle(Palette.textPrimary)
            ForEach(lines, id: \.self) { line in
                Text("· \(line)")
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var notConfiguredCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("联机后端未配置")
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text("联机需要一个自建后端（Postgres + PostgREST + Supabase Realtime），"
                 + "它没有能烧进 App 的默认值。配好地址与匿名密钥后即可建房对战。")
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Button("配置联机后端") { showConfig = true }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.onPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Palette.primary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("lobbyConfig")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var actionRow: some View {
        HStack(spacing: 10) {
            actionButton("建房", symbol: "plus.circle", identifier: "lobbyCreate") { showCreate = true }
            actionButton("用房间号加入", symbol: "number.circle", identifier: "lobbyJoin") { showJoin = true }
            actionButton("配置", symbol: "gearshape", identifier: "lobbyConfig") { showConfig = true }
        }
    }

    private func actionButton(
        _ title: String,
        symbol: String,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: symbol)
                Text(title).font(.caption)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .accessibilityIdentifier(identifier)
    }

    @ViewBuilder
    private var roomList: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("房间").font(.headline).foregroundStyle(Palette.textPrimary)
                Spacer()
                if isLoading { ProgressView() }
            }
            if rooms.isEmpty {
                Text("还没有房间，建一个或输入朋友的房间号。")
                    .font(.caption)
                    .foregroundStyle(Palette.textMuted)
            } else {
                ForEach(rooms) { room in
                    NavigationLink { RoomView(roomId: room.id) } label: { roomRow(room) }
                }
            }
            if let message {
                Text(message).font(.caption).foregroundStyle(Palette.textMuted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("lobbyRoomList")
    }

    private func roomRow(_ room: WireRoom) -> some View {
        let config = RoomCodec.config(from: room.config)
        return HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(room.name.isEmpty ? "房间 \(room.id)" : room.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.textPrimary)
                Text("房主 \(room.hostName.isEmpty ? room.creatorId : room.hostName) · \(statusLabel(room.status))")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text("#\(room.id)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(Palette.textSecondary)
                if let config {
                    Text("\(config.targetScore)")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                } else {
                    Text("非 X01")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                }
            }
        }
        .padding(.vertical, 6)
        // 让整行可点（行内是 HStack，不声明的话只有文字是热区）。
        .contentShape(Rectangle())
    }

    private func statusLabel(_ status: String) -> String {
        switch status {
        case "PLAYING": return "对局中"
        case "ENDED": return "已结束"
        default: return "等候中"
        }
    }

    // MARK: - 动作

    private func reload() async {
        guard OnlineConfig.isConfigured else { return }
        isLoading = true
        defer { isLoading = false }
        rooms = await api.fetchRooms()
    }

    private func openRoom(_ id: String) {
        activeRoomId = id
        showRoom = true
    }
}

// MARK: - 配置

private struct ConfigSheet: View {
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var url = OnlineConfig.url
    @State private var key = OnlineConfig.anonKey

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("https://<your-project>.supabase.co", text: $url)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("anon key", text: $key)
                } header: {
                    Text("PostgREST 地址与匿名密钥")
                } footer: {
                    Text("只存在本机（UserDefaults），不会上传。WebSocket 地址由它推导，不用另外填。")
                }
            }
            .navigationTitle("联机后端")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("保存") {
                        OnlineConfig.save(url: url, anonKey: key)
                        onSaved()
                        dismiss()
                    }
                    .disabled(url.isEmpty || key.isEmpty)
                    .accessibilityIdentifier("lobbyConfigSave")
                }
            }
        }
    }
}

// MARK: - 建房

private struct CreateRoomSheet: View {
    let onCreated: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var targetScore: Int32 = 501
    @State private var legsToWin: Int32 = 3
    @State private var isCreating = false
    @State private var error: String?
    @State private var repository = OnlineRoomRepository()

    private let targets: [Int32] = [301, 501, 701]

    var body: some View {
        NavigationStack {
            Form {
                Section("房间") {
                    TextField("房间名（可留空）", text: $name)
                    Picker("目标分", selection: $targetScore) {
                        ForEach(targets, id: \.self) { Text("\($0)").tag($0) }
                    }
                    Stepper(value: $legsToWin, in: 1...7) {
                        Text("先胜 \(legsToWin) 局")
                    }
                }
                // 联机只做 X01：这句不是"暂不支持"，是引擎边界。
                Section {
                    Text("P0 联机只开放 X01 · 1v1。")
                        .font(.caption)
                        .foregroundStyle(Palette.textMuted)
                }
                if let error {
                    Section { Text(error).foregroundStyle(Color(red: 0.9, green: 0.3, blue: 0.24)) }
                }
            }
            .navigationTitle("建房")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(isCreating ? "创建中…" : "创建") { create() }
                        .disabled(isCreating)
                        .accessibilityIdentifier("lobbyCreateConfirm")
                }
            }
        }
    }

    private func create() {
        isCreating = true
        Task {
            let config = SharedFactory.x01Config(
                targetScore: targetScore,
                mode: legsToWin > 1 ? MatchMode.multiLeg : MatchMode.casual,
                legsToWin: legsToWin,
                outMode: OutMode.doubleOut,
                inMode: InMode.straightIn,
                smartAi: false
            )
            let roomName = name.isEmpty ? "\(repository.selfName) 的对局" : name
            if let id = await repository.createRoom(name: roomName, config: config) {
                onCreated(id)
                dismiss()
            } else {
                error = repository.errorMessage ?? "创建失败"
                isCreating = false
            }
        }
    }
}

// MARK: - 加入

private struct JoinRoomSheet: View {
    let onJoined: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var code = ""
    @State private var isJoining = false
    @State private var error: String?
    @State private var repository = OnlineRoomRepository()

    var body: some View {
        NavigationStack {
            Form {
                Section("房间号") {
                    TextField("6 位数字", text: $code)
                        .keyboardType(.numberPad)
                        .font(.title3.monospacedDigit())
                }
                if let error {
                    Section { Text(error).foregroundStyle(Color(red: 0.9, green: 0.3, blue: 0.24)) }
                }
            }
            .navigationTitle("加入房间")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(isJoining ? "加入中…" : "加入") { join() }
                        .disabled(isJoining || code.count != 6)
                        .accessibilityIdentifier("lobbyJoinConfirm")
                }
            }
        }
    }

    private func join() {
        isJoining = true
        Task {
            if await repository.join(roomId: code, name: repository.selfName) {
                onJoined(code)
                dismiss()
            } else {
                error = repository.errorMessage ?? "加入失败"
                isJoining = false
            }
        }
    }
}
