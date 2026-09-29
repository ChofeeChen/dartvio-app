import Foundation
import shared

/**
 * 线格式（`dartvio_rooms` / `dartvio_room_events` 的行）与 shared 领域对象之间的编解码。
 *
 * ## 为什么这一层必须在 iOS 再写一遍
 *
 * 房间**内核**（`RoomRules` / `RoomMatchRules` / `RoomEventReplay`）在 shared 里，两端共用；
 * 但**线格式**在 `android/app/net/online`（`RoomEventCodec` / `RoomStateCodec`）里，
 * commonMain 要求零依赖，放不进去。所以 iOS 必须自己实现一份**逐字对齐**的编解码 ——
 * 这里的每个字符串都是协议，改之前先看 Android 的 `RoomEventCodec.kt`。
 *
 * 字段名之所以用蛇形（房间表列名）与驼峰（jsonb 里的 payload）混着来，是因为协议本身就这样：
 * 表列是 Postgres 的蛇形，jsonb 里是 Kotlin 的驼峰。别"顺手统一"。
 */

// MARK: - 线格式

/// `dartvio_rooms` 的一行。
struct WireRoom: Identifiable {
    let id: String
    let name: String
    let creatorId: String
    /// jsonb：见 `RoomCodec.decodeConfig`。
    let config: [String: Any]
    let visibility: String
    let allowSpectators: Bool
    let createdAt: Int64
    let status: String
    let hostName: String
    let expiresAt: Int64?
}

/// `dartvio_room_events` 的一行。`payload` 保持原始 JSON —— 怎么解释取决于 `type`，推迟到解码时分支。
struct WireEvent {
    let roomId: String
    let seq: Int
    let actorId: String
    let type: String
    let payload: [String: Any]
}

// MARK: - 编解码

enum RoomCodec {

    // MARK: 房间行

    static func room(from json: [String: Any]) -> WireRoom? {
        guard let id = json["id"] as? String else { return nil }
        return WireRoom(
            id: id,
            name: json["name"] as? String ?? "",
            creatorId: json["creator_id"] as? String ?? "",
            config: json["config"] as? [String: Any] ?? [:],
            visibility: json["visibility"] as? String ?? "PUBLIC",
            allowSpectators: json["allow_spectators"] as? Bool ?? true,
            createdAt: int64(json["created_at"]),
            status: json["status"] as? String ?? "WAITING",
            hostName: json["host_name"] as? String ?? "",
            expiresAt: json["expires_at"] as? Int64 ?? (json["expires_at"] as? NSNumber)?.int64Value
        )
    }

    /**
     * 建房的行。`join_policy` / `host_name` / `expires_at` / `starts_at` 在服务端是**可选列**
     * （Android 会先逐列探测），所以这里给出「完整版」与「最小版」两份：
     * 完整版 400/404 就退回最小版重试，而不是直接判失败。
     */
    static func roomRow(
        id: String,
        name: String,
        creatorId: String,
        config: MatchConfig,
        hostName: String,
        expiresAt: Int64?,
        minimal: Bool
    ) -> [String: Any] {
        var row: [String: Any] = [
            "id": id,
            "name": name,
            "creator_id": creatorId,
            "config": configJson(config),
            "status": "WAITING",
        ]
        guard !minimal else { return row }
        row["visibility"] = "PUBLIC"
        row["allow_spectators"] = true
        row["join_policy"] = "open"
        row["host_name"] = hostName
        row["host_avatar"] = "HUMAN_1"
        // 等候房 5 分钟 TTL（与 Android `RoomExpiry.WAITING_TTL_MS` 一致）；
        // null 时整键省略，不写空字符串。
        if let expiresAt { row["expires_at"] = expiresAt }
        return row
    }

    // MARK: 事件行

    static func eventRow(roomId: String, seq: Int, actorId: String, type: String, payload: [String: Any]) -> [String: Any] {
        ["room_id": roomId, "seq": seq, "actor_id": actorId, "type": type, "payload": payload]
    }

    static func event(from json: [String: Any]) -> WireEvent? {
        guard let roomId = json["room_id"] as? String,
              let type = json["type"] as? String,
              let actorId = json["actor_id"] as? String else { return nil }
        return WireEvent(
            roomId: roomId,
            seq: int(json["seq"]),
            actorId: actorId,
            type: type,
            payload: json["payload"] as? [String: Any] ?? [:]
        )
    }

    // MARK: 配置（jsonb）

    /** 键名是**驼峰**（`RoomEventCodec.kt:150`），值取 `enum.name`，与 Android 完全同源。 */
    static func configJson(_ config: MatchConfig) -> [String: Any] {
        [
            "matchType": config.matchType.name,
            "targetScore": Int(config.targetScore),
            "mode": config.mode.name,
            "legsToWin": Int(config.legsToWin),
            "outMode": config.outMode.name,
            "inMode": config.inMode.name,
            "bullMode": config.bullMode.name,
            "maxRounds": Int(config.maxRounds),
        ]
    }

    static func config(from json: [String: Any]) -> MatchConfig? {
        guard let matchType = json["matchType"] as? String else { return nil }
        // P0 权威联机对局只支持 X01（引擎 `RoomMatchRules.supportsLiveMatch`），
        // 别的玩法在列表里就不该出现「开始联机」。
        guard matchType == "X01" else { return nil }
        return SharedFactory.x01Config(
            targetScore: Int32(int(json["targetScore"], fallback: 501)),
            mode: json["mode"] as? String == "MULTI_LEG" ? MatchMode.multiLeg : MatchMode.casual,
            legsToWin: Int32(int(json["legsToWin"], fallback: 1)),
            outMode: outMode(json["outMode"] as? String),
            inMode: inMode(json["inMode"] as? String),
            smartAi: false
        )
    }

    // MARK: 落镖

    /** 一镖的线格式：`{"n":20,"m":3}`（`RoomEventCodec.kt:174`）。 */
    static func dartsJson(_ darts: [Dart]) -> [[String: Any]] {
        darts.map { ["n": Int($0.number), "m": Int($0.multiplier)] }
    }

    static func darts(from json: Any?) -> [Dart] {
        guard let list = json as? [[String: Any]] else { return [] }
        return list.map { SharedFactory.dart(number: Int32(int($0["n"])), multiplier: Int32(int($0["m"], fallback: 1))) }
    }

    // MARK: 事件 payload

    /**
     * payload → (线格式 JSON, 事件类型 key)。
     *
     * ⚠️ `RoomCreated.creatorPpr` 与 `MemberJoined.ppr` **不在线上传输**
     * （Android 的 codec 也不写），所以这里不要照着数据类把 ppr 编进去 —— 编了就与 Android 不一致。
     */
    static func payloadJson(_ payload: RoomEventPayload) -> ([String: Any], String)? {
        switch payload {
        case let created as RoomEventPayloadRoomCreated:
            return ([
                "name": created.name,
                "config": configJson(created.config),
                "visibility": created.visibility.name,
                "allowSpectators": created.allowSpectators,
                "creatorName": created.creatorName,
                "creatorAvatar": created.creatorAvatar,
            ], "room_created")
        case let joined as RoomEventPayloadMemberJoined:
            return (["name": joined.name, "avatar": joined.avatar], "member_joined")
        case let ready as RoomEventPayloadMemberReady:
            return (["ready": ready.ready], "member_ready")
        case let kicked as RoomEventPayloadMemberKicked:
            return (["memberId": kicked.memberId], "member_kicked")
        case let turn as RoomEventPayloadTurnSubmitted:
            return (["darts": dartsJson(turn.darts), "turnId": turn.clientTurnId], "turn_submitted")
        case let settings as RoomEventPayloadSettingsChanged:
            var json: [String: Any] = [:]
            if let visibility = settings.visibility { json["visibility"] = visibility.name }
            if let allowSpectators = settings.allowSpectators { json["allowSpectators"] = allowSpectators }
            return (json, "settings_changed")
        default:
            // member_left / match_started / turn_undone / match_rematch：payload 为空对象。
            return ([:], "")
        }
    }

    // MARK: 小工具

    static func int(_ value: Any?, fallback: Int = 0) -> Int {
        (value as? NSNumber)?.intValue ?? (value as? Int) ?? fallback
    }

    static func int64(_ value: Any?) -> Int64 {
        (value as? NSNumber)?.int64Value ?? (value as? Int64) ?? 0
    }

    private static func outMode(_ raw: String?) -> OutMode {
        switch raw {
        case "STRAIGHT_OUT": return OutMode.straightOut
        case "MASTER_OUT": return OutMode.masterOut
        default: return OutMode.doubleOut
        }
    }

    private static func inMode(_ raw: String?) -> InMode {
        switch raw {
        case "DOUBLE_IN": return InMode.doubleIn
        case "MASTER_IN": return InMode.masterIn
        default: return InMode.straightIn
        }
    }
}
