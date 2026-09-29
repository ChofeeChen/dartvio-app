import Foundation

/// 与 Android `RoomEventStream.StreamStatus` 同义，去掉了 iOS 用不到的 `POLLING_ONLY`。
enum RoomStreamStatus: Equatable {
    case notConfigured
    case connecting
    case live
    case reconnecting
}

/**
 * 房间事件流：Supabase Realtime（Phoenix 协议）的 WebSocket 客户端。
 *
 * ## 三个不直观但必须照做的点
 *
 * 1. **topic 不是 `room:<id>`**，而是 Postgres Changes 形态 `realtime:public:dartvio_room_events`，
 *    房间号放在 join 的 `filter` 里（`room_id=eq.<id>`）。自定义 broadcast topic 一律回 `unmatched topic`
 *    —— 这是 Android 踩过的（见 `RoomEventStream.kt:54`）。
 * 2. **订阅不补发 join 之前的事件**，所以 `OnlineRoomRepository` 必须先拉全量再订阅；
 *    这里只负责"之后的增量"。
 * 3. Realtime 存在「join 成功但一条推送都不来」的情况，所以仓库另有 3 秒轮询兜底 ——
 *    这个 stream 不是唯一数据来源。
 */
@MainActor
final class RoomEventStream {

    var onEvent: ((WireEvent) -> Void)?
    var onStatus: ((RoomStreamStatus) -> Void)?

    private var session: URLSession?
    private var task: URLSessionWebSocketTask?
    private var roomId = ""
    private var heartbeat: Timer?
    private var retrySeconds = 1
    private var stopped = true

    func start(roomId: String) {
        guard let url = OnlineConfig.realtimeUrl else {
            onStatus?(.notConfigured)
            return
        }
        stopped = false
        self.roomId = roomId
        onStatus?(.connecting)

        session = URLSession(configuration: .default, delegate: nil, delegateQueue: nil)
        // 建连这一步是普通 HTTP 握手：Android 侧也是 GET + 10s 握手超时。
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue(url.host ?? "", forHTTPHeaderField: "Host")
        let socket = session!.webSocketTask(with: request)
        task = socket
        socket.resume()
        sendJoin()
        startHeartbeat()
        listen()
    }

    func stop() {
        stopped = true
        heartbeat?.invalidate()
        heartbeat = nil
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        session?.invalidateAndCancel()
        session = nil
    }

    // MARK: - 协议帧

    private func sendJoin() {
        let join: [String: Any] = [
            "topic": "realtime:public:dartvio_room_events",
            "event": "phx_join",
            "payload": [
                "config": [
                    "postgres_changes": [[
                        "event": "INSERT",
                        "schema": "public",
                        "table": "dartvio_room_events",
                        "filter": "room_id=eq.\(roomId)",
                    ]]
                ]
            ],
            "ref": "1",
            "join_ref": "1",
        ]
        send(json: join)
    }

    /// 心跳 topic 是 `phoenix`（不是房间的 topic），间隔 25 秒 —— 与 Android 一致，改了会被服务端判死。
    private func startHeartbeat() {
        heartbeat?.invalidate()
        heartbeat = Timer.scheduledTimer(withTimeInterval: 25, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor in
                // `ref` 在线上是 JSON null（不是"没有这个键"），用 NSNull 显式表达。
                self.send(json: ["topic": "phoenix", "event": "heartbeat", "payload": [:], "ref": NSNull()])
            }
        }
    }

    private func send(json: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: json) else { return }
        task?.send(.data(data)) { _ in }
    }

    // MARK: - 收帧

    private func listen() {
        guard let task else { return }
        task.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                switch result {
                case .success(let message):
                    if case .string(let text) = message { self.handle(text: text) }
                    if !self.stopped { self.listen() }
                case .failure:
                    if !self.stopped { self.scheduleReconnect() }
                }
            }
        }
    }

    private func handle(text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let event = json["event"] as? String
        else { return }

        onStatus?(.live)
        retrySeconds = 1

        // 只认 INSERT：phx_reply / system / 心跳回执一律忽略。
        let record: [String: Any]?
        if event == "postgres_changes" {
            let payload = json["payload"] as? [String: Any]
            let change = payload?["data"] as? [String: Any]
            guard change?["type"] as? String == "INSERT" else { return }
            record = change?["record"] as? [String: Any]
        } else if event == "insert" {
            // 兼容旧格式：{"event":"insert","payload":{"record":{...}}}
            record = (json["payload"] as? [String: Any])?["record"] as? [String: Any]
        } else {
            return
        }

        guard let record, let wire = RoomCodec.event(from: record), wire.roomId == roomId else { return }
        onEvent?(wire)
    }

    // MARK: - 重连

    private func scheduleReconnect() {
        onStatus?(.reconnecting)
        let delay = retrySeconds
        retrySeconds = min(retrySeconds * 2, 30)
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000_000)
            guard !self.stopped else { return }
            self.start(roomId: self.roomId)
        }
    }
}
