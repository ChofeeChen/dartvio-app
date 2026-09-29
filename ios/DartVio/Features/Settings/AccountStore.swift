import AuthenticationServices
import Foundation
import Observation

/**
 * 账号层：**只做「通过 Apple 登录」一种登录方式 + 游客态**。
 *
 * ## 为什么只做 Apple 登录
 *
 * Apple 审核 4.8 规定：只要 App 提供第三方/社交登录（微信、Google…），就必须**同时**提供
 * Sign in with Apple，且它的按钮不能比其他登录方式小、不能藏在二级页里。只做 Apple 登录
 * 就没有"并列"问题；同时它是唯一登录方式，不存在"必须给替代方案"的合规负担。
 *
 * 不做手机号/邮箱注册是**合规选择**而不是偷懒：手机号要接短信（模板报备、验证码风控），
 * 且「不填手机号就不能用」在国内监管口径下容易被认定超出最小必要。
 * 现在不登录照样能用全部功能，登录只换来"跨设备同步 + 昵称/战绩"。
 *
 * ## 存什么
 *
 * - `credential.user`：Apple 给的**稳定主体标识**（同一 Apple ID 在同一开发者账号下永远不变），
 *   存 **Keychain**（不放 UserDefaults：UserDefaults 明文落在备份里，换设备即丢）。
 * - 昵称：只在**首次授权**时随 `fullName` 返回，之后每次登录都是 nil，所以拿到就存下来。
 * - **不申请邮箱**：我们不发邮件、不做密码找回，按最小必要原则连作用域都不索要。
 */
@Observable
final class AccountStore {

    enum AccountState: Equatable {
        case guest
        case signedIn(userId: String, displayName: String)
    }

    static let shared = AccountStore()

    private static let userIdKey = "apple.userId"
    private static let displayNameKey = "apple.displayName"

    var state: AccountState = .guest
    /// 登录失败 / 未启用能力时给 UI 的人话提示（不是系统原始错误码）。
    var errorMessage: String?

    private init() {
        // ⚠️ `@Observable` 会把存储属性展开成计算属性，init 里回读 `self.state` 会判「用到未初始化的 self」，
        // 所以这里只调方法、不读属性。
        restore()
    }

    // MARK: - 登录

    /**
     * 配置授权请求（SwiftUI `SignInWithAppleButton` 的 `onRequest` 回调）。
     *
     * 只请求 `.fullName`：昵称用于联机显示。不请求 `.email` —— 我们在中国法下按最小必要收集，
     * 用不到的字段不索要（少一项"收集"，隐私标签也少一项）。
     */
    func configure(_ request: ASAuthorizationAppleIDRequest) {
        request.requestedScopes = [.fullName]
    }

    /// 处理授权结果（`onCompletion` 回调）。取消也属于正常路径，不算错误。
    func handle(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .failure(let error):
            let ns = error as NSError
            if ns.domain == ASAuthorizationError.errorDomain, ns.code == ASAuthorizationError.canceled.rawValue {
                errorMessage = nil // 用户主动取消，不必打扰
                return
            }
            // code 1000 = unknown，在工程里绝大多数是「没开 Sign in with Apple 能力 / 描述文件不含该权限」。
            errorMessage = ns.code == 1000
                ? "当前构建未启用「通过 Apple 登录」：需在 Xcode → Signing & Capabilities 里添加 Sign in with Apple 能力。"
                : "登录未完成（错误码 \(ns.code)），可以稍后重试。"
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                errorMessage = "返回的凭证不是 Apple ID 凭证。"
                return
            }
            let userId = credential.user
            KeychainStore.save(userId, for: Self.userIdKey)

            // 中文序：姓在前；只有首次授权才有值，之后的登录为 nil，此时保留已存昵称。
            let fullName = [credential.fullName?.familyName, credential.fullName?.givenName]
                .compactMap { $0 }
                .filter { !$0.isEmpty }
                .joined()
            let name = fullName.isEmpty ? (KeychainStore.read(Self.displayNameKey) ?? "Apple 用户") : fullName
            KeychainStore.save(name, for: Self.displayNameKey)

            errorMessage = nil
            state = .signedIn(userId: userId, displayName: name)
        }
    }

    // MARK: - 恢复 / 校验 / 退出 / 删除

    /// 冷启动恢复登录态。
    func restore() {
        guard let userId = KeychainStore.read(Self.userIdKey) else {
            state = .guest
            return
        }
        let name = KeychainStore.read(Self.displayNameKey) ?? "Apple 用户"
        state = .signedIn(userId: userId, displayName: name)
        refreshCredentialState()
    }

    /**
     * 校验 Apple 账户授权是否仍然有效。
     *
     * 用户在「设置 → Apple ID → 密码与安全性 → 使用 Apple ID 的 App」里可以撤销授权，
     * 撤销后本机不该继续保持登录态（否则本地留着一个已失效的账号标识，属于"过期未清理"）。
     */
    func refreshCredentialState() {
        guard case .signedIn(let userId, _) = state else { return }
        ASAuthorizationAppleIDProvider().getCredentialState(forUserID: userId) { [weak self] credentialState, _ in
            guard let self else { return }
            Task { @MainActor in
                switch credentialState {
                case .revoked, .notFound, .transferred:
                    self.errorMessage = "Apple 账户授权已失效，已退出登录。"
                    self.signOut(keepError: true)
                case .authorized:
                    break
                @unknown default:
                    break
                }
            }
        }
    }

    /// 退出登录：只清账号凭证，联机用的本机身份（游客）保留。
    func signOut(keepError: Bool = false) {
        KeychainStore.delete(Self.userIdKey)
        KeychainStore.delete(Self.displayNameKey)
        if !keepError { errorMessage = nil }
        state = .guest
    }

    /**
     * 删除账号。
     *
     * Apple 5.1.1(v) 与《移动互联网应用程序信息服务管理规定》都要求「App 内可便捷注销」，
     * 只给"退出登录"不合格 —— 退出是断开本机与账号，删除是账号数据本身不再保留。
     *
     * 现在没有后端账号接口，所以删的是本机一切身份痕迹（含联机用的 UUID 与昵称）；
     * 等后端账号表就绪，这里要再加一步「调用后端删除接口」。
     */
    func deleteAccount() {
        KeychainStore.delete(Self.userIdKey)
        KeychainStore.delete(Self.displayNameKey)

        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: "online.selfId")
        defaults.removeObject(forKey: "online.selfName")

        errorMessage = nil
        state = .guest
        // TODO(后端账号接口就绪后): 调用后端「删除账号」接口，清除服务端的账号与联机数据。
    }
}

// MARK: - Keychain

/**
 * 极薄 Keychain 封装（通用密码项）。
 *
 * 用 Keychain 而不是 UserDefaults 存账号标识：UserDefaults 明文进 iTunes/云备份，
 * 换机就丢；这里用 `ThisDeviceOnly` —— 账号标识不该跟着备份跑到别的设备上。
 */
enum KeychainStore {

    private static let service = "com.dartvio.app"

    static func save(_ value: String, for account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        if SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess {
            SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        } else {
            SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
    }

    static func read(_ account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
