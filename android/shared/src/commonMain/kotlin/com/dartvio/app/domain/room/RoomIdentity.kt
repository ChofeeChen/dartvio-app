package com.dartvio.app.domain.room

/**
 * 「我是谁」的本地投影（M5 实时同步的关键前置）。
 *
 * ## 为什么需要这一层
 *
 * M6 的 UI 全部用 [LocalUser].`ID`（常量 `"local_me"`）表示「我」：
 * `creatorId == LocalUser.ID` 判房主、`member.id == LocalUser.ID` 判「（你）」。
 *
 * 但两台手机编译进的是**同一个常量**。如果直接把 `"local_me"` 放到线上，A、B 会撞成同一个
 * 成员 id —— 房主判定与「你」标记同时错乱，而且不崩溃、只是安静地显示错。
 *
 * 因此约定：
 * - **线上身份** = 每个连接握手时由服务端生成的 UUID（连房主也是），只用于传输；
 * - **[LocalUser].`ID`** = 纯 UI 别名，仅在本机内存里出现；
 * - 从传输层进入 UI 之前，必须经过 [withLocalIdentity] 投影一次。
 *
 * ## 不变式（由 `RoomIdentityTest` 钉死）
 * 1. 投影后房间内**至多**出现 1 个 [LocalUser].`ID`（我在房内时恰好 1 个）；
 * 2. `creatorId == LocalUser.ID` ⟺ 我是房主；
 * 3. 除我自己外，任何成员的 id 都不可能等于 [LocalUser].`ID`；
 * 4. 出站反向映射 [toWireMemberId] 与投影互逆（`toWire(withLocal(room)) == room`）。
 */
/**
 * 把「我」的线上身份投影成 UI 别名。
 *
 * @param selfWireId 本机连接的线上身份（握手时服务端分配的 UUID）。
 * @throws IllegalArgumentException 传入 [LocalUser].`ID` 本身——那说明调用方把 UI 别名当线上身份用了，
 * 此时投影会静默把所有原本叫 `"local_me"` 的远程成员一起改名，属于必须先炸出来的编程错误。
 */
fun Room.withLocalIdentity(selfWireId: String): Room {
    require(selfWireId != LocalUser.ID) {
        "selfWireId 必须是握手分配到的线上身份，不能是 UI 别名 ${LocalUser.ID}"
    }
    // 我不在这个房间里（例如只是浏览大厅列表）：没有任何东西需要投影。
    if (members.none { it.id == selfWireId }) return this

    return copy(
        creatorId = if (creatorId == selfWireId) LocalUser.ID else creatorId,
        members = members.map { member ->
            if (member.id == selfWireId) member.copy(id = LocalUser.ID) else member
        }
    )
}

/**
 * 出站方向：把 UI 别名翻译回线上身份。
 *
 * 只有我自己会被改写；别人的 id 本来就已经是线上身份，原样透传。
 */
fun toWireMemberId(localId: String, selfWireId: String): String =
    if (localId == LocalUser.ID) selfWireId else localId

/**
 * 把成员的**历史 PPR** 补进对局帧的选手里（按线上 id 匹配）。
 *
 * PPR 与比分是两路数据：比分在事件里（每回合都在变），PPR 在成员档案里（很久不变）。
 * 快照这一层只负责把它们**并到同一张卡上** —— 并完之后，界面读一张卡就能同时回答
 * 「这局几分」与「这个人什么水平」；让界面自己去 join 两路数据，
 * 迟早会出现「换了一帧之后 PPR 丢了」。
 *
 * 匹配不上（对手没带 PPR / 帧里的 id 已不是成员 id）时保持 `null`：
 * 卡片据此不显示这一行，而不是显示 0 ——「PPR 0.0」会被读成「他很菜」。
 */
fun SpectatorSnapshot.withPpr(pprByMemberId: Map<String, Double?>): SpectatorSnapshot =
    if (pprByMemberId.isEmpty()) this
    else copy(
        players = players.map { player ->
            val ppr = pprByMemberId[player.id]?.takeIf { it > 0.0 } ?: player.ppr
            if (ppr == player.ppr) player else player.copy(ppr = ppr)
        }
    )
