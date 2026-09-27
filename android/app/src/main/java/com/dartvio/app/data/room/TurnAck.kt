package com.dartvio.app.data.room

/**
 * 一次回合提交在客户端拿到的**答复**（M5 T6）。
 *
 * ## 为什么需要它
 *
 * [RoomRepository.submitTurn] 一直是「发出去就完」的语义 —— 裁定结果本来就是作为一帧
 * 广播回来的。但有三件事只有答复能回答：主机到底收没收到、收了之后是**生效**还是**拒绝**、
 * 以及拒绝是不是因为**我手里的帧过期了**。没有它，界面只能靠「等 5 秒」把这三种一律当成
 * 「不知道，你自己再试试」，于是用户连点第二次 —— 那正是 T6 要修的现象。
 *
 * ## 为什么只有三种、且**不含协议码**
 *
 * [Conflict] 与 [Rejected] 在界面上的处置完全相反：前者必须清掉草稿、以最新帧为准，
 * 后者应当保留草稿让用户改完重试。把错误码透给界面，只会让每个界面各自去背一份码表，
 * 而漏掉一种的下场是「冲突了却把旧镖留着」。分流因此放在数据层做。
 */
sealed interface TurnAck {

    /** 已生效（或命中幂等表重放 —— 服务端没有重复结算）。[version] 是生效后的权威版本。 */
    data class Accepted(val version: Int) : TurnAck

    /** 我手里的帧过期了：本地草稿必须作废，以最新帧为准。[currentVersion] 是权威版本。 */
    data class Conflict(val currentVersion: Int) : TurnAck

    /** 被拒（不是我的回合 / 对局已结束 / 镖非法）。[message] 直接面向用户，草稿保留可改。 */
    data class Rejected(val message: String) : TurnAck
}
