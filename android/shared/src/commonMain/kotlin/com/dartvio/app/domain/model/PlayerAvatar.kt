package com.dartvio.app.domain.model

/**
 * 真人头像。设置页底部提供 4 个可选真人头像。
 * 使用 emoji/文字 + 主题色绘制，避免依赖外部图片资源。
 */
enum class HumanAvatar(val key: String, val label: String, val emoji: String) {
    HUMAN_1("HUMAN_1", "玩家一", "\uD83D\uDC64"),
    HUMAN_2("HUMAN_2", "玩家二", "\uD83E\uDDD1"),
    HUMAN_3("HUMAN_3", "玩家三", "\uD83D\uDC71"),
    HUMAN_4("HUMAN_4", "玩家四", "\uD83E\uDDD9");

    companion object {
        val ALL: List<HumanAvatar> = entries.toList()
        fun fromKey(key: String): HumanAvatar =
            entries.firstOrNull { it.key == key } ?: HUMAN_1
    }
}

/**
 * AI 机器人头像。设置页提供 4 个可选 AI 对手，**头像只代表对手身份**（名字 + emoji）。
 *
 * 难度**不再编码在头像里**：`AiDifficulty` 是独立的设置项（设置页「AI 难度」卡），
 * 一张卡决定本局全部 AI 对手的强度。
 *
 * 为什么必须拆开（2026-09-12 体验裁决）：难度挂在头像上时（旧版 `AI_1` = 入门…），
 * 设置页那 4 个圆在读法上就是「4 档难度选项」，玩家根本看不出自己在**挑对手**；
 * 拆开后「选谁当对手」与「对手多强」各说各话，也才有位置把难度做成一张独立的卡。
 *
 * 命名与 [HumanAvatar] 的「玩家一 / 二 / 三 / 四」对齐 —— 都是**身份**，不是强度。
 */
enum class AiAvatar(
    val key: String,
    val label: String,
    val emoji: String,
) {
    AI_1("AI_1", "机器人一", "\uD83E\uDD16"),
    AI_2("AI_2", "机器人二", "\uD83D\uDC7E"),
    AI_3("AI_3", "机器人三", "\uD83D\uDC79"),
    AI_4("AI_4", "机器人四", "\uD83D\uDC51");

    companion object {
        val ALL: List<AiAvatar> = entries.toList()
        fun fromKey(key: String): AiAvatar =
            entries.firstOrNull { it.key == key } ?: AI_1
    }
}
