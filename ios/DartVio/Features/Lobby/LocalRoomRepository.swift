import Foundation
import shared

/**
 * 本地试玩仓库（Android `LocalRoomRepository` 的对等物）：**不联网**，单机跑完整个 X01 流程。
 *
 * ## 它存在的理由
 *
 * 联机后端还没部署（服务器在境外节点上尚未起服务），如果只有 `OnlineRoomRepository`，
 * 那么「建房 → 准备 → 开局 → 投镖 → 结算」这条链**永远无法被执行到** ——
 * 代码写完了但没有一条路径能证明它是对的。这个仓库就是那条路径：
 * 事件在本机造，其余一切与联机**完全同源**（同一份 `RoomEventReplay`、同一个房间内核）。
 *
 * ## 与联机刻意保持一致的部分
 *
 * - 状态仍然来自**事件重放**，不是直接改对局对象 —— 否则本地跑通了、联机照样错；
 * - 事件仍然带 `seq`，按序追加；
 * - 对手的回合**也写成一条 `turn_submitted` 事件**，而不是偷偷改分数。
 *
 * ## 与联机刻意不同（且只允许这几处不同）的部分
 *
 * - 没有网络，也就没有 409 重试与轮询兜底；
 * - 对手由 `X01Ai` 代打（与单机 AI 同源，不是随便减个分数）。
 */
@MainActor
@Observable
final class LocalRoomRepository: RoomRepositoryProtocol {

    var state = RoomState(room: nil, match: nil, seq: 0)
    /// 本地没有连接过程，恒为 `.live`：状态栏要如实写"本地试玩"，由 `isLocal` 区分，不假称已连服务器。
    var status: RoomStreamStatus = .live
    var errorMessage: String?
    var roomId: String = ""
    var isLocal: Bool { true }

    var selfId: String
    var selfName: String

    private var events: [RoomEvent] = []
    private var config: MatchConfig?
    private let opponentId = "local.opponent"
    private let opponentName = "本地对手"
    private let difficulty: AiDifficulty
    private var isOpponentThinking = false

    init(difficulty: AiDifficulty = AiDifficulty.beginner) {
        self.difficulty = difficulty
        let defaults = UserDefaults.standard
        // 与 Android `LocalProfile.DEFAULT_NICKNAME` 对齐（"玩家 1"），不另起一个"iOS 玩家"。
        let id = defaults.string(forKey: "online.selfId") ?? UUID().uuidString
        if defaults.string(forKey: "online.selfId") == nil { defaults.set(id, forKey: "online.selfId") }
        selfId = id
        selfName = defaults.string(forKey: "online.selfName") ?? "玩家 1"
    }

    func saveName(_ name: String) {
        selfName = name
        UserDefaults.standard.set(name, forKey: "online.selfName")
    }

    // MARK: - 建房 / 进入

    /** 建房并自动补一个对手（对齐 Android 的 `simulateCompanionJoin`：不然单机永远开不了局）。 */
    func createRoom(name: String, config: MatchConfig) async -> String {
        self.config = config
        roomId = OnlineRoomRepository.newRoomId()
        events = []

        append(
            type: RoomEventType.roomCreated,
            actorId: selfId,
            payload: RoomEventPayloadRoomCreated(
                name: name,
                config: config,
                visibility: RoomVisibility.public_,
                allowSpectators: true,
                creatorName: selfName,
                creatorAvatar: "HUMAN_1",
                creatorPpr: 0
            )
        )
        join(name: selfName, actorId: selfId)
        join(name: opponentName, actorId: opponentId)
        append(type: RoomEventType.memberReady, actorId: opponentId,
               payload: RoomEventPayloadMemberReady(ready: true))
        return roomId
    }

    private func join(name: String, actorId: String) {
        append(type: RoomEventType.memberJoined, actorId: actorId,
               payload: RoomEventPayloadMemberJoined(name: name, avatar: "HUMAN_1", ppr: 0))
    }

    func observe(roomId: String, name: String? = nil) async {
        if let name { saveName(name) }
        self.roomId = roomId
        // 本地没有"拉全量"这回事：事件本来就在内存里，重放一次即可。
        state = RoomEventReplay.shared.replay(events: events)
    }

    func stop() { }

    // MARK: - 动作

    func setReady(_ ready: Bool) async {
        append(type: RoomEventType.memberReady, actorId: selfId,
               payload: RoomEventPayloadMemberReady(ready: ready))
    }

    func startMatch() async -> Bool {
        guard state.room?.status == RoomStatus.waiting else { return false }
        append(type: RoomEventType.matchStarted, actorId: selfId, payload: RoomEventPayloadEmpty.shared)
        return true
    }

    func submitTurn(darts: [Dart]) async -> Bool {
        guard !darts.isEmpty else { return false }
        append(
            type: RoomEventType.turnSubmitted,
            actorId: selfId,
            payload: RoomEventPayloadTurnSubmitted(darts: darts, clientTurnId: UUID().uuidString)
        )
        await opponentTurnIfNeeded()
        return true
    }

    func undoTurn() async {
        append(type: RoomEventType.turnUndone, actorId: selfId, payload: RoomEventPayloadEmpty.shared)
    }

    func rematch() async {
        append(type: RoomEventType.matchRematch, actorId: selfId, payload: RoomEventPayloadEmpty.shared)
    }

    func leave() async {
        events = []
        state = RoomState(room: nil, match: nil, seq: 0)
    }

    // MARK: - 对手

    /**
     * 轮到对手时替它投一回合。
     *
     * ⚠️ 对手的落镖同样写成事件再重放 —— 直接改对局对象会让本地这条链与联机不同源，
     * "本地能跑通"就证明不了"联机也能跑通"。
     */
    private func opponentTurnIfNeeded() async {
        guard !isOpponentThinking, let match = state.match, let config else { return }
        guard let snapshot = Optional(RoomMatchRules.shared.snapshot(match: match)),
              !snapshot.isFinished else { return }
        guard snapshot.players.first(where: { $0.isActive })?.id == opponentId else { return }
        guard let opponent = snapshot.players.first(where: { $0.id == opponentId }) else { return }

        isOpponentThinking = true
        // 停顿一下再出手：连续两回合瞬间完成会让"轮到谁"这个状态看不出变化。
        try? await Task.sleep(nanoseconds: 700_000_000)
        isOpponentThinking = false

        // ⚠️ 随机源必须走 `SharedAccess.newRandom()`：`KotlinRandom()` 是构造抽象类，
        // 一初始化就 SIGABRT（V4 测试崩过一次）。
        let darts = X01Ai.shared.generateTurn(
            remaining: Int32(opponent.score),
            difficulty: difficulty,
            config: config,
            random: SharedAccess.newRandom()
        )
        append(
            type: RoomEventType.turnSubmitted,
            actorId: opponentId,
            payload: RoomEventPayloadTurnSubmitted(darts: darts, clientTurnId: UUID().uuidString)
        )
    }

    // MARK: - 追加事件

    private func append(type: RoomEventType, actorId: String, payload: RoomEventPayload) {
        events.append(RoomEvent(
            roomId: roomId,
            seq: Int32(events.count + 1),
            actorId: actorId,
            type: type,
            payload: payload
        ))
        state = RoomEventReplay.shared.replay(events: events)
    }
}
