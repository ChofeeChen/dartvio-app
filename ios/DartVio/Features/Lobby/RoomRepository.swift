import Foundation
import shared

/**
 * 房间仓库的能力集合：**真联机**与**本地试玩**共用这一套，UI 不区分两者。
 *
 * ## 为什么要抽这一层
 *
 * Android 默认跑 `LocalRoomRepository`（单机 mock），所以没有服务器也能走完
 * 「建房 → 准备 → 开局 → 投镖 → 结算」。iOS 之前只有 `OnlineRoomRepository`，
 * 后端没部署时整个模块**既跑不通也没法验**，只能靠肉眼判断"代码看起来对"。
 *
 * 抽出协议后：房间页代码只有一份，本地与联机跑的是同一套 UI 与同一个房间内核
 * （都走 `RoomEventReplay`），差别只在"事件从网络来还是本机造"。
 *
 * ## 为什么要求 `Observable`
 *
 * 房间页用 `let` 持有仓库（不是 `@State`）—— 仓库的生命周期由上游决定。
 * 声明 `Observable` 是为了让 SwiftUI 仍能追踪 `state` 的变化：
 * 追踪建立在**属性访问**上，与用不用 `@State` 包装无关。
 */
@MainActor
protocol RoomRepositoryProtocol: AnyObject, Observable {

    /// 重放结果：房间 + 权威对局。**唯一真相**，两边都从事件流重放出来。
    var state: RoomState { get set }
    var status: RoomStreamStatus { get set }
    var errorMessage: String? { get set }
    var roomId: String { get set }

    /** 是否本地试玩（不联网）。状态栏据此写"本地试玩"，**不能**因为没有连接过程就写"已连接"。 */
    var isLocal: Bool { get }

    /// 本机身份。本地仓库也要有一份，否则事件里的 actor 无从判断"是不是我"。
    var selfId: String { get }
    var selfName: String { get }

    /// `name` 非空时顺带更新本机昵称；只进房间不改名的场景走下面的默认实现。
    func observe(roomId: String, name: String?) async
    func stop()
    func setReady(_ ready: Bool) async
    func startMatch() async -> Bool
    func submitTurn(darts: [Dart]) async -> Bool
    func undoTurn() async
    func rematch() async
    func leave() async
}

extension RoomRepositoryProtocol {
    /// 进房间但不改名（房间页没有要改的昵称，联机的名字在加入/建房时已经写进事件了）。
    func observe(roomId: String) async { await observe(roomId: roomId, name: nil) }
}
