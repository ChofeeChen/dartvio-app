import Foundation

/**
 * 向境外提供个人信息的**单独同意**状态（《个人信息保护法》第 39 条）。
 *
 * ## 为什么"单独同意"不能省
 *
 * 联机后端在新加坡（腾讯云境外节点），所以只要进联机就是**个人信息出境**：
 * 昵称、房间内的操作与成绩会传到境外服务器。第 39 条要求这种情形必须取得单独同意 ——
 * 它不能混在"勾选隐私政策即代表同意全部"里，必须是就这一件事单独作出的同意。
 *
 * 所以流程是：进大厅先看到同意卡（列明境外接收方、目的、信息种类、撤回方式），
 * 同意后才发第一个网络请求；设置页可以随时撤回，撤回后大厅回到未同意状态。
 *
 * ## 为什么不做成"一次同意永久有效"
 *
 * 同意必须可撤回，且撤回后处理活动要真的停下来（不是只在界面上打个勾）。
 * 这里撤回后 `LobbyView` 不再发任何请求 —— 大厅列表与建房入口一并隐藏。
 */
enum DataTransferConsent {

    private static let key = "privacy.crossBorderConsent"

    // TODO(正式版): 换成真实的运营主体名称与联系方式，这是第 39 条要求告知的"境外接收方"信息。
    static let recipient = "DartVio 自建联机服务（运营主体名称待补充）"
    static let purposes = "同步联机对局状态，使对局双方看到同一份进展"
    static let categories = "昵称、你在房间内的落镖操作与成绩、房间标识"

    /// 区域来自 `DataRegion`（配置），不在这里写死"新加坡"。
    static var region: String { DataRegion.current.placeName }

    /**
     * 是否**需要**出境单独同意。
     *
     * 正式版迁回境内后 `DataRegion == .mainland`，这一步就不该再拦用户 ——
     * 对一个没有出境的场景索要"出境同意"，本身就是告知错误。
     */
    static var isRequired: Bool { DataRegion.current.isOverseas }

    static var isGranted: Bool { UserDefaults.standard.bool(forKey: key) }

    static func grant() { UserDefaults.standard.set(true, forKey: key) }

    /// 撤回：只改开关还不够 —— 调用方要保证撤回后不再发起出境传输（`LobbyView` 已按此实现）。
    static func revoke() { UserDefaults.standard.set(false, forKey: key) }
}
