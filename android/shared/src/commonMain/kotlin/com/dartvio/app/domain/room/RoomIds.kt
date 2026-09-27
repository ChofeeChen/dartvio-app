package com.dartvio.app.domain.room

import kotlin.random.Random

/**
 * 6 位数字房间号的生成与校验（M5 实时同步 / M6 F6.1 房间号加入）。
 *
 * 刻意做成**纯函数 + 注入随机源**：房间号是唯一的加入凭证，重复即「加进别人的房间」，
 * 属于静默错误。把随机源参数化后，「避让已占用号码」与「退化随机源下不无限重试」
 * 这两件事都能被单测钉死，而不是只能靠运气。
 *
 * 与 [LobbyViewModel][com.dartvio.app.ui.lobby.LobbyViewModel] 的 `ROOM_ID_LENGTH`
 * 语义相同（都是 6）；此处是生成侧的权威定义，UI 侧那个是输入框校验，保持不变。
 */
object RoomIds {

    /** 房间号长度。 */
    const val LENGTH = 6

    /** 取值范围下界（含）：保证不出现前导零，房间号恒为 6 位字符串。 */
    private const val RANGE_FROM = 100_000

    /** 取值范围上界（不含）。 */
    private const val RANGE_UNTIL = 1_000_000

    /**
     * 连续冲突时的重试上限。
     *
     * 128 个房间全占用时，单次命中概率仅 0.0128%，连撞 32 次约 1e-64 —— 正常永远走不到。
     * 之所以设上限而非 `while (true)`：随机源被替换（测试桩、弱随机实现）时，
     * 死循环会表现为「点创建房间没反应」，而抛异常能立刻暴露。
     */
    const val MAX_ATTEMPTS = 32

    /** 校验房间号格式（长度 + 全数字）。不校验是否真实存在。 */
    fun isValid(candidate: String): Boolean =
        candidate.length == LENGTH && candidate.all { it in '0'..'9' }

    /**
     * 生成一个尚未被占用的房间号。
     *
     * @param existing 已占用的房间号集合（服务端权威状态）。
     * @param nextInt 取值范围随机源，默认 [Random]；测试注入以便确定性覆盖。
     * @throws IllegalStateException 连续 [MAX_ATTEMPTS] 次都撞上已占用号码。
     */
    fun newRoomId(
        existing: Set<String>,
        nextInt: (from: Int, until: Int) -> Int = { from, until -> Random.nextInt(from, until) }
    ): String {
        repeat(MAX_ATTEMPTS) {
            val candidate = nextInt(RANGE_FROM, RANGE_UNTIL).toString()
            if (candidate !in existing) return candidate
        }
        throw IllegalStateException(
            "连续 $MAX_ATTEMPTS 次未能生成未占用的 $LENGTH 位房间号（当前已占用 ${existing.size} 个）"
        )
    }
}
