import Foundation
import shared

/**
 * 联机房间仓库：把「网络」与「房间内核」接起来。
 *
 * ## 唯一的真相是事件流
 *
 * 房间状态**不是**从服务端拉一个现成对象，而是把所有事件按 `seq` 重放出来
 * （`RoomEventReplay`）。这样两台手机只要拿到同一串事件，就一定看到同一个局面 ——
 * 不需要服务端"算"，也就不需要信服务端算得对。
 *
 * 由此推出三条实现纪律：
 *
 * 1. **排序只认 `seq`**，绝不用数据库自增 id 或 `created_at`（两台手机时钟本来就不要求一致，
 *    这是 Android `RoomEvent.kt:95` 的注释，照抄不改）；
 * 2. 写事件撞 `unique(room_id, seq)`（409）是**正常竞争**：拉全量、换 `seq+1` 重投，最多 3 次；
 * 3. **订阅不补发 join 之前的事件**，所以先拉全量再订阅；而且 Realtime 存在
 *    「join 成功但一条都不推」的情况，故此仓库另有 3 秒轮询兜底 —— WebSocket 不是唯一来源。
 */
@MainActor
@Observable
final class OnlineRoomRepository {

    // MARK: 对外状态

    /// 重放结果：房间 + 权威对局（P0 只有 X01 会有 `match`）。
    var state = RoomState(room: nil, match: nil, seq: 0)
    var status: RoomStreamStatus = .notConfigured
    var errorMessage: String?
    var roomId: String = ""
    var isBusy = false

    /// 本机身份：一个持久化的随机 id。⚠️ 不能叫 `local_me` 之类固定值 ——
    /// 两台手机各用固定值会撞成同一个成员（Android 用 `RoomIdentity` 做这层投影）。
    var selfId: String
    var selfName: String

    // MARK: 内部

    private var events: [RoomEvent] = []
    private let api = OnlineRoomApi()
    private let stream = RoomEventStream()
    private var pollTask: Task<Void, Never>?

    init() {
        let defaults = UserDefaults.standard
        // ⚠️ 先落局部常量再赋值：`@Observable` 会把存储属性展开成计算属性，
        // init 里回读 `self.selfId` 会被判「用到未初始化的 self」。
        let id = defaults.string(forKey: "online.selfId") ?? UUID().uuidString
        if defaults.string(forKey: "online.selfId") == nil { defaults.set(id, forKey: "online.selfId") }
        selfId = id
        selfName = defaults.string(forKey: "online.selfName") ?? "iOS 玩家"
    }

    func saveName(_ name: String) {
        selfName = name
        UserDefaults.standard.set(name, forKey: "online.selfName")
    }

    // MARK: - 大厅

    func fetchRooms() async -> [WireRoom] {
        guard OnlineConfig.isConfigured else {
            status = .notConfigured
            return []
        }
        return await api.fetchRooms()
    }

    // MARK: - 进入 / 离开

    func observe(roomId: String, name: String? = nil) async {
        if let name { saveName(name) }
        stop()
        self.roomId = roomId
        errorMessage = nil

        stream.onStatus = { [weak self] value in self?.status = value }
        stream.onEvent = { [weak self] wire in self?.merge([wire]) }
        stream.start(roomId: roomId)

        await refresh()
        startPolling()
    }

    func stop() {
        pollTask?.cancel()
        pollTask = nil
        stream.stop()
    }

    /// 拉全量事件并重放。增量推送之外的一切补洞都靠它。
    func refresh() async {
        let fetched = await api.fetchEvents(roomId: roomId)
        merge(fetched)
    }

    private func startPolling() {
        pollTask?.cancel()
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                guard let self, !self.roomId.isEmpty else { return }
                // 增量：只拉比本地最大 seq 新的。
                let fetched = await self.api.fetchEvents(roomId: self.roomId, afterSeq: self.maxSeq)
                self.merge(fetched)
            }
        }
    }

    private var maxSeq: Int { events.map { Int($0.seq) }.max() ?? 0 }

    // MARK: - 重放

    private func merge(_ wires: [WireEvent]) {
        guard !wires.isEmpty else { return }
        var bySeq: [Int: RoomEvent] = Dictionary(uniqueKeysWithValues: events.map { (Int($0.seq), $0) })
        for wire in wires {
            guard let event = domain(wire) else { continue }
            bySeq[Int(event.seq)] = event
        }
        events = bySeq.values.sorted { $0.seq < $1.seq }
        state = RoomEventReplay.shared.replay(events: events)
    }

    private func domain(_ wire: WireEvent) -> RoomEvent? {
        guard let type = RoomEventType.companion.fromKey(key: wire.type) else { return nil }
        let payload: RoomEventPayload
        switch type {
        case .roomCreated:
            guard let config = RoomCodec.config(from: wire.payload["config"] as? [String: Any] ?? [:]) else { return nil }
            payload = RoomEventPayloadRoomCreated(
                name: wire.payload["name"] as? String ?? "",
                config: config,
                visibility: RoomVisibility.public_,
                allowSpectators: wire.payload["allowSpectators"] as? Bool ?? true,
                creatorName: wire.payload["creatorName"] as? String ?? "",
                creatorAvatar: wire.payload["creatorAvatar"] as? String ?? "",
                creatorPpr: 0
            )
        case .memberJoined:
            payload = RoomEventPayloadMemberJoined(
                name: wire.payload["name"] as? String ?? "",
                avatar: wire.payload["avatar"] as? String ?? "",
                ppr: 0
            )
        case .memberReady:
            payload = RoomEventPayloadMemberReady(ready: wire.payload["ready"] as? Bool ?? true)
        case .memberKicked:
            payload = RoomEventPayloadMemberKicked(memberId: wire.payload["memberId"] as? String ?? "")
        case .turnSubmitted:
            payload = RoomEventPayloadTurnSubmitted(
                darts: RoomCodec.darts(from: wire.payload["darts"]),
                clientTurnId: wire.payload["turnId"] as? String ?? ""
            )
        case .settingsChanged:
            payload = RoomEventPayloadSettingsChanged(visibility: nil, allowSpectators: nil)
        default:
            payload = RoomEventPayloadEmpty.shared
        }
        return RoomEvent(roomId: wire.roomId, seq: Int32(wire.seq), actorId: wire.actorId, type: type, payload: payload)
    }

    // MARK: - 动作

    /// 建房：先占房间号，再写 `room_created` 事件（seq 从 1 起）。
    func createRoom(name: String, config: MatchConfig) async -> String? {
        guard OnlineConfig.isConfigured else { status = .notConfigured; return nil }
        isBusy = true
        defer { isBusy = false }

        for _ in 0..<5 {
            let id = Self.newRoomId()
            let expiresAt = Int64(Date().timeIntervalSince1970 * 1000) + 5 * 60_000
            let row = RoomCodec.roomRow(
                id: id, name: name, creatorId: selfId, config: config,
                hostName: selfName, expiresAt: expiresAt, minimal: false
            )
            var result = await api.createRoom(row)
            if case .failed = result {
                // 可选列在服务端不存在（老 schema）：退回最小列集再试。
                result = await api.createRoom(RoomCodec.roomRow(
                    id: id, name: name, creatorId: selfId, config: config,
                    hostName: selfName, expiresAt: nil, minimal: true
                ))
            }
            guard case .ok = result else { continue }

            let created = RoomEventPayloadRoomCreated(
                name: name,
                config: config,
                visibility: RoomVisibility.public_,
                allowSpectators: true,
                creatorName: selfName,
                creatorAvatar: "HUMAN_1",
                creatorPpr: 0
            )
            if await write(type: "room_created", payload: created) {
                await observe(roomId: id)
                return id
            }
        }
        errorMessage = "建房失败：房间号连续冲突或后端不可写"
        return nil
    }

    func join(roomId: String, name: String) async -> Bool {
        guard OnlineConfig.isConfigured else { status = .notConfigured; return false }
        guard await api.fetchRoom(id: roomId) != nil else {
            errorMessage = "房间 \(roomId) 不存在"
            return false
        }
        saveName(name)
        // 先拉全量：写入 seq 必须是「本地已知最大 +1」，否则会撞 unique 约束。
        self.roomId = roomId
        await refresh()
        let joined = RoomEventPayloadMemberJoined(name: name, avatar: "HUMAN_1", ppr: 0)
        guard await write(type: "member_joined", payload: joined) else { return false }
        await observe(roomId: roomId, name: name)
        return true
    }

    func setReady(_ ready: Bool) async {
        _ = await write(type: "member_ready", payload: RoomEventPayloadMemberReady(ready: ready))
    }

    /** 开局：只有房主能开，且引擎会在重放时校验（非法事件被忽略，不会写脏状态）。 */
    func startMatch() async -> Bool {
        guard await write(type: "match_started", payload: RoomEventPayloadEmpty.shared) else { return false }
        // 房间表的 status 是给大厅列表看的（否则列表里仍是 WAITING，别人还会往里挤）。
        _ = await api.patchRoom(id: roomId, fields: ["status": "PLAYING"])
        return true
    }

    func submitTurn(darts: [Dart]) async -> Bool {
        guard !darts.isEmpty else { return false }
        let clientTurnId = UUID().uuidString
        return await write(
            type: "turn_submitted",
            payload: RoomEventPayloadTurnSubmitted(darts: darts, clientTurnId: clientTurnId)
        )
    }

    func undoTurn() async {
        _ = await write(type: "turn_undone", payload: RoomEventPayloadEmpty.shared)
    }

    func rematch() async {
        guard await write(type: "match_rematch", payload: RoomEventPayloadEmpty.shared) else { return }
        _ = await api.patchRoom(id: roomId, fields: ["status": "WAITING"])
    }

    func leave() async {
        _ = await write(type: "member_left", payload: RoomEventPayloadEmpty.shared)
        stop()
    }

    // MARK: - 写事件

    /**
     * 写一个事件：seq = 本地最大 +1，撞 409 就拉全量换号重投。
     *
     * ⚠️ 写成功后**不**直接改 `state`，而是走 `merge` 重放 ——
     * 本机也算一个普通端，"自己写的就一定对"这种乐观更新一旦与重放规则有出入就会错位。
     */
    private func write(type: String, payload: RoomEventPayload) async -> Bool {
        guard let json = RoomCodec.payloadJson(payload) else { return false }
        let (payloadJson, _) = json
        for attempt in 0..<3 {
            let seq = maxSeq + 1
            let row = RoomCodec.eventRow(
                roomId: roomId, seq: seq, actorId: selfId, type: type, payload: payloadJson
            )
            switch await api.appendEvent(row) {
            case .ok:
                merge([WireEvent(roomId: roomId, seq: seq, actorId: selfId, type: type, payload: payloadJson)])
                return true
            case .conflict:
                await refresh()
                if attempt == 2 { errorMessage = "事件写入冲突（seq \(seq)）" }
            case .failed(let message):
                errorMessage = message
                return false
            }
        }
        return false
    }

    // MARK: - 房间号

    /// 6 位数字房间号，方便口头报号；撞号由写事件的 409/建房重试兜住。
    static func newRoomId() -> String {
        String(format: "%06d", Int.random(in: 100_000..<1_000_000))
    }
}
