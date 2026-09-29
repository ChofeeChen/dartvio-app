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
 *
 * ## 联机 = 出境，所以前面还有一道同意
 *
 * 后端在**新加坡**（腾讯云境外节点），进联机就是把昵称与对局数据传到境外 ——
 * 按《个人信息保护法》第 39 条这要**单独同意**，所以 `DataTransferConsent` 没同意时
 * **一个请求都不发**（不是先拉列表再问，那样问之前就已经传了）。
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
    @State private var localRepository: LocalRoomRepository?
    @State private var showLocalRoom = false
    @State private var isStartingLocal = false
    private let api = OnlineRoomApi()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                scopeCard
                localPlayCard
                if !OnlineConfig.isConfigured {
                    notConfiguredCard
                } else if DataTransferConsent.isRequired && !DataTransferConsent.isGranted {
                    // 同意之前不建房、不拉列表：拉取列表本身就要把请求发到境外服务器。
                    consentCard
                } else {
                    actionRow
                    roomList
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
        // 本地试玩同样用 sheet：它不在房间列表里，没有可点的行。
        .sheet(isPresented: $showLocalRoom) {
            if let repo = localRepository {
                NavigationStack { RoomView(repository: repo, roomId: repo.roomId) }
            }
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

    /**
     * 本地试玩入口（不联网）。
     *
     * 联机后端还在境外节点上没起服务，只有 `OnlineRoomRepository` 的话，
     * 「建房 → 开局 → 投镖 → 结算」这条链**一次都跑不到** —— 写完的代码没有一条路径能证明它对。
     * 这条入口就是那条路径：事件在本机造，房间内核与事件重放与联机完全同源。
     */
    private var localPlayCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("本地试玩（不联网）")
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text("与联机跑同一份房间内核与同一套事件重放，只是事件在本机产生、对手由 AI 代打。"
                 + "用于在联机后端就绪前走通整条对局流程。")
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                startLocalPlay()
            } label: {
                Text(isStartingLocal ? "创建中…" : "开始本地试玩")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.onPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Palette.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .disabled(isStartingLocal)
            .accessibilityIdentifier("lobbyLocalPlay")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
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

    /// 出境单独同意卡（PIPL 第 39 条：接收方 / 目的 / 信息种类 / 撤回方式都要说清）。
    private var consentCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("联机需要你的单独同意")
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text("联机服务器目前在 \(DataTransferConsent.region)，"
                 + "使用联机即表示你的个人信息会被提供到境外。")
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            infoCard(title: "将提供到境外的内容", lines: [
                "接收方：\(DataTransferConsent.recipient)",
                "所在地：\(DataTransferConsent.region)",
                "目的：\(DataTransferConsent.purposes)",
                "信息种类：\(DataTransferConsent.categories)",
                "撤回：设置 → 隐私 → 向境外提供个人信息，可随时关闭",
            ])
            Button("同意并继续") { DataTransferConsent.grant() }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.onPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Palette.primary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("lobbyConsentAgree")
            NavigationLink { PrivacyPolicyView() } label: {
                Text("查看隐私政策")
                    .font(.caption)
                    .foregroundStyle(Palette.primary)
            }
            .accessibilityIdentifier("lobbyConsentPolicy")
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

    /** 本地试玩：建房 → 自动补一个对手 → 进房间页（对手已准备，本机点「准备」后可开局）。 */
    private func startLocalPlay() {
        isStartingLocal = true
        Task {
            let repository = LocalRoomRepository()
            let config = SharedFactory.x01Config(
                targetScore: 501,
                mode: MatchMode.casual,
                legsToWin: 1,
                outMode: OutMode.doubleOut,
                inMode: InMode.straightIn,
                smartAi: false
            )
            _ = await repository.createRoom(name: "本地试玩", config: config)
            localRepository = repository
            isStartingLocal = false
            showLocalRoom = true
        }
    }

    private func reload() async {
        guard OnlineConfig.isConfigured, DataTransferConsent.isGranted else { return }
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
