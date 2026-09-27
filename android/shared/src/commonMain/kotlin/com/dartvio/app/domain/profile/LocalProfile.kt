package com.dartvio.app.domain.profile

import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.platform.randomIdHex

/**
 * 本机玩家档案（第③期 ③B 前置，P0）。
 *
 * **解决的问题**：`Player.id` 从设置页带下来的是**位置 ID**（`"p1"` / `"p2"` …），
 * 语义是「玩家 1」而不是「某个人」。位置 ID 落在 `match_players.player_id` 上之后，
 * 同一台设备上所有对局的第一席位都会合并成同一个人，排行榜无从下手。
 * 因此引入一个**本机唯一、此后永不变**的 [profileId]，落库时用它替换本机那一行的位置 ID。
 *
 * ⚠️ [profileId] 一旦生成**不得再改**：它已经是历史数据里已落库的外键，
 * 改了等于把此人过去的战绩全部作废（本模块不做迁移，见 ③B 交付说明）。
 */
data class LocalProfile(
    /** 稳定身份 ID。生成规则见 [newProfileId]。 */
    val profileId: String,
    /** 展示昵称。空白名由 [normalizeNickname] 兜底，不存空串。 */
    val nickname: String,
    /** 头像，沿用 M2 已有的真人头像体系。 */
    val avatar: HumanAvatar = HumanAvatar.HUMAN_1,
) {
    companion object {

        /** 默认昵称。与 M6 的房间昵称兜底同源（`LocalUser.DEFAULT_NAME` + 序号）。 */
        const val DEFAULT_NICKNAME = "玩家 1"

        /**
         * 昵称兜底：与 `RoomRules.normalizeDisplayName` 同一套做法 ——
         * 只 trim、空白名回落默认值。**不做长度截断 / 敏感词**，那些属于 M6 的房间规则，
         * 本地档案不需要（本地名不进任何上行报文）。
         */
        fun normalizeNickname(raw: String?): String =
            raw.orEmpty().trim().ifBlank { DEFAULT_NICKNAME }

        /**
         * 生成新的档案 ID：随机十六进制串截断。
         * （原本用 `java.util.UUID`，随 `domain/` 下沉 KMP 后改走 [randomIdHex]：
         *   Android 端仍是 UUID，iOS 端是 NSUUID，两端都是 32 位十六进制。）
         *
         * 截断到 16 位十六进制（64 bit）：本地档案数量在本机是个位数，
         * 碰撞概率可忽略；同时避免把 36 位 UUID 直接落进 `player_id`，
         * 让历史表里的 ID 保持短、可读、便于在 DB 里肉眼核对。
         */
        fun newProfileId(): String = randomIdHex().take(16)
    }
}
