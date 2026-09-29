import Foundation

/**
 * PostgREST 的薄封装：一个请求 → 一个结果。**不含重试与业务语义**，那是 `OnlineRoomRepository` 的事。
 *
 * ## 三张表（与 Android 完全一致，含 `dartvio_` 前缀）
 *
 * | 表 | 用途 |
 * | --- | --- |
 * | `dartvio_rooms` | 房间索引：建房 / 改状态 / 大厅列表 |
 * | `dartvio_room_events` | 事件流：追加 / 增量拉取，约束 `unique(room_id, seq)` |
 * | `dartvio_room_state` | 大厅一行快照（P0 只读，不写） |
 *
 * ## 两个必须照搬的约定
 *
 * 1. 写操作要带 `Prefer: return=minimal`，否则 PostgREST 默认回 representation，体大且没必要；
 * 2. **409 = 冲突**（`unique(room_id, seq)` 撞了，或房间号已被建过）—— 这不是错误，
 *    调用方据此换 seq 重投，判成失败会让正常竞争变成"网络坏了"。
 */
struct OnlineRoomApi {

    enum Result {
        case ok
        case conflict
        case failed(String)
    }

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    // MARK: - 探活

    /// 云端是否可达：拉一行 `dartvio_rooms`，只看 HTTP 状态。
    func ping() async -> Bool {
        guard let request = makeRequest(path: "/rest/v1/dartvio_rooms?select=id&limit=1", method: "GET") else { return false }
        let (status, _) = await run(request)
        return (200..<300).contains(status)
    }

    // MARK: - 房间

    func createRoom(_ row: [String: Any]) async -> Result {
        await write(path: "/rest/v1/dartvio_rooms", method: "POST", body: row)
    }

    func patchRoom(id: String, fields: [String: Any]) async -> Result {
        guard let encoded = id.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return .failed("房间号无法编码")
        }
        return await write(path: "/rest/v1/dartvio_rooms?id=eq.\(encoded)", method: "PATCH", body: fields)
    }

    func fetchRooms(limit: Int = 30) async -> [WireRoom] {
        guard let request = makeRequest(
            path: "/rest/v1/dartvio_rooms?select=*&order=created_at.desc&limit=\(limit)",
            method: "GET"
        ) else { return [] }
        let (status, data) = await run(request)
        guard (200..<300).contains(status) else { return [] }
        let rows = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] ?? []
        return rows.compactMap(RoomCodec.room(from:))
    }

    func fetchRoom(id: String) async -> WireRoom? {
        guard let encoded = id.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let request = makeRequest(path: "/rest/v1/dartvio_rooms?select=*&id=eq.\(encoded)&limit=1", method: "GET")
        else { return nil }
        let (status, data) = await run(request)
        guard (200..<300).contains(status) else { return nil }
        let rows = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] ?? []
        return rows.first.flatMap(RoomCodec.room(from:))
    }

    // MARK: - 事件流

    func appendEvent(_ row: [String: Any]) async -> Result {
        await write(path: "/rest/v1/dartvio_room_events", method: "POST", body: row)
    }

    /**
     * 增量拉事件。
     *
     * ⚠️ `afterSeq == 0` 时**不能**带 `seq=gt.0`：服务端语义不同（有的部署会因此少一条），
     * 与 Android `OnlineRoomApi.fetchEvents` 保持一致 —— 全量就是不带条件。
     */
    func fetchEvents(roomId: String, afterSeq: Int = 0) async -> [WireEvent] {
        guard let encoded = roomId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return [] }
        var path = "/rest/v1/dartvio_room_events?select=*&room_id=eq.\(encoded)"
        if afterSeq > 0 { path += "&seq=gt.\(afterSeq)" }
        path += "&order=seq.asc"
        guard let request = makeRequest(path: path, method: "GET") else { return [] }
        let (status, data) = await run(request)
        guard (200..<300).contains(status) else { return [] }
        let rows = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] ?? []
        return rows.compactMap(RoomCodec.event(from:))
    }

    // MARK: - 请求构造与执行

    private func makeRequest(path: String, method: String, body: [String: Any]? = nil) -> URLRequest? {
        guard OnlineConfig.isConfigured, let url = URL(string: OnlineConfig.restUrl + path) else { return nil }
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.httpMethod = method
        for (name, value) in OnlineConfig.authHeaders() { request.setValue(value, forHTTPHeaderField: name) }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            // 只要状态码，不要 representation。
            request.setValue("return=minimal", forHTTPHeaderField: "Prefer")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        return request
    }

    private func write(path: String, method: String, body: [String: Any]) async -> Result {
        guard let request = makeRequest(path: path, method: method, body: body) else {
            return .failed("未配置联机后端")
        }
        let (status, data) = await run(request)
        if status == 409 { return .conflict }
        if status == 201 || status == 200 || status == 204 { return .ok }
        let detail = String(data: data, encoding: .utf8)?.prefix(200) ?? ""
        return .failed("HTTP \(status) \(detail)")
    }

    private func run(_ request: URLRequest) async -> (Int, Data) {
        do {
            let (data, response) = try await session.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            return (status, data)
        } catch {
            return (0, Data())
        }
    }
}
