import Foundation

/**
 * 联机后端的地址与匿名密钥。
 *
 * Android 侧来自 `local.properties → BuildConfig`（`android/app/.../net/online/OnlineConfig.kt`）。
 * iOS 没有 BuildConfig，所以读 Info.plist（`OnlineUrl` / `OnlineAnonKey`），
 * 并且**允许在 App 内覆盖**（存 UserDefaults）：联机后端是自建的，没有能烧进包里的默认值，
 * 硬编码一个假地址只会让「已接好」看起来成立、实际一点就失败。
 *
 * ⚠️ WebSocket 地址**没有独立配置项**：由 REST 地址换 scheme 推导而来（与 Android 一致），
 * 所以只配 URL 一处，别再让用户输入第二个地址。
 */
enum OnlineConfig {

    private static let urlStoreKey = "online.url"
    private static let keyStoreKey = "online.anonKey"

    /**
     * 联机后端地址：腾讯云轻量服务器（**Ubuntu-X90X，IPv4 43.156.5.140**），域名 `dartvio.win`
     *（2026-09-29 实测 `dig dartvio.win` 已解析到 43.156.5.140）。
     *
     * 地址是公开的，可以当默认值；**密钥不行** —— anonKey 永远要另外配（App 内「配置」或 Info.plist），
     * 所以 `isConfigured` 仍然要求两项都有值，不会因为有默认地址就假装已配好。
     *
     * ⚠️ 联不通时不要改代码：App 内「比赛大厅 → 配置」可以临时指向别处（例如 `http://43.156.5.140:3000`）。
     */
    private static let defaultUrl = "https://dartvio.win"

    static var url: String {
        UserDefaults.standard.string(forKey: urlStoreKey)
            ?? (Bundle.main.object(forInfoDictionaryKey: "OnlineUrl") as? String)
            ?? defaultUrl
    }

    static var anonKey: String {
        UserDefaults.standard.string(forKey: keyStoreKey)
            ?? (Bundle.main.object(forInfoDictionaryKey: "OnlineAnonKey") as? String)
            ?? ""
    }

    static func save(url: String, anonKey: String) {
        let trimmedUrl = url.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedKey = anonKey.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(trimmedUrl, forKey: urlStoreKey)
        UserDefaults.standard.set(trimmedKey, forKey: keyStoreKey)
    }

    static var isConfigured: Bool { !url.isEmpty && !anonKey.isEmpty }

    /// PostgREST 基址：去掉结尾斜杠，拼 `/rest/v1/...` 时不再重复处理。
    static var restUrl: String { url.trimmingCharacters(in: CharacterSet(charactersIn: "/")) }

    static var realtimeUrl: URL? {
        guard isConfigured else { return nil }
        var base = restUrl
        if base.hasPrefix("https://") {
            base = "wss://" + base.dropFirst("https://".count)
        } else if base.hasPrefix("http://") {
            base = "ws://" + base.dropFirst("http://".count)
        }
        return URL(string: "\(base)/realtime/v1/websocket?apikey=\(anonKey)&vsn=1.0.0")
    }

    /// 读写都要带的两条鉴权头；写操作另加 `Prefer: return=minimal`。
    static func authHeaders() -> [String: String] {
        ["apikey": anonKey, "Authorization": "Bearer \(anonKey)"]
    }
}
