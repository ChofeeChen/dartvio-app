package com.dartvio.app.domain.room

/**
 * 昵称规则：长度、敏感词与重名消歧短码。
 *
 * ## 为什么昵称不要求唯一
 *
 * 没有账号体系时，「昵称」只是一个**显示标签**，不是身份。要求唯一需要一块服务端的
 * 全局命名空间，而且会被抢注 —— 先注册的人能把后来者挡在门外，代价转嫁给了正常用户。
 * 参考 n01：它的报名项同样只有一个可重复的 `name`，身份另有一套系统分配的 ID。
 *
 * ## 那怎么区分两个同名的人
 *
 * 身份用 `playerId`（设备级持久 ID，见 `OnlineIdentity`），展示用 [shortCode] 从它派生
 * 4 位短码。短码**只影响显示、不改存储**：数据库里存的永远是 `playerId`。
 */
object NicknameRules {

    /** 昵称长度上限：大厅列表一行要放得下「昵称 + 4 位短码」，超过 12 字符会挤压比分。 */
    const val MAX_LENGTH = 12

    /** 昵称未设置 / 被清空时的兜底。 */
    const val FALLBACK = "玩家"

    /**
     * 基础敏感词（小写比较）。
     *
     * 只做**最小必要集**：导流广告与明显侮辱。它不是审核系统 —— 昵称在大厅公开可见，
     * 一个「加我微信」比一句脏话更伤产品；真正的审核要靠服务端，这里只拦住第一眼。
     */
    private val BLOCKED = listOf(
        "傻逼", "煞笔", "沙比", "妈的", "狗屎", "白痴", "智障", "骗子",
        "代练", "外挂", "加微信", "加微", "加v", "vx", "威信", "客服", "广告", "推广", "返利", "赌博"
    )

    /**
     * 易混字符替换表：短码是**念出来 / 抄下来**用的，`O` 与 `0`、`I` 与 `1` 在镖房灯光下
     * 分不出来。替换而非删除 —— 删除会让短码变短，长度就不再是稳定的 4 位。
     */
    private val CONFUSING = mapOf(
        'I' to 'J', 'O' to 'Q', 'L' to 'M',
        '0' to '2', '1' to '7', '5' to 'S', '8' to 'B'
    )

    /** 短码长度。 */
    const val CODE_LENGTH = 4

    /** 短码不足位时的填充字符（取一个不在易混表里的字母）。 */
    private const val PAD = 'X'

    /** 昵称校验结果。 */
    sealed interface Check {

        /** 通过，[value] 是已清洗过的可直接存储的值。 */
        data class Ok(val value: String) : Check

        /** 拒绝：界面按 [reason] 出提示，不要自己拼文案。 */
        data class Rejected(val reason: Reject) : Check
    }

    /** 拒绝原因（文案集中在这里，避免三个界面各写一套）。 */
    enum class Reject(val message: String) {
        BLANK("昵称不能为空"),
        TOO_LONG("昵称最多 $MAX_LENGTH 个字符"),
        BLOCKED("这个昵称不可用，换一个吧")
    }

    /**
     * 清洗：去首尾空白与控制字符，并截断到 [MAX_LENGTH]。
     *
     * 供输入时使用 —— 与其等提交后报错，不如让输入框里根本打不出非法值。
     */
    fun sanitize(raw: String): String =
        raw.replace(CONTROL, "").trim().take(MAX_LENGTH)

    /** 校验（先清洗再判断）。 */
    fun check(raw: String): Check {
        val value = sanitize(raw)
        if (value.isBlank()) return Check.Rejected(Reject.BLANK)
        if (raw.trim().length > MAX_LENGTH) return Check.Rejected(Reject.TOO_LONG)
        if (isBlocked(value)) return Check.Rejected(Reject.BLOCKED)
        return Check.Ok(value)
    }

    /**
     * 敏感词命中判断：忽略大小写、空格与常见分隔符，防止「加 微 信」这类插空绕过。
     */
    fun isBlocked(value: String): Boolean {
        val compact = value.lowercase().replace(SEPARATORS, "")
        if (compact.isBlank()) return false
        return BLOCKED.any { compact.contains(it) }
    }

    /**
     * 由 `playerId` 派生 4 位短码。
     *
     * 同一个 ID 永远得到同一个短码（纯函数、无随机），因此同一台设备在大厅里
     * 每次出现都是同一串字符 —— 用户靠它认人，不稳定就等于没有。
     */
    fun shortCode(playerId: String): String {
        val mapped = playerId
            .filter { it.isLetterOrDigit() }
            .uppercase()
            .map { CONFUSING[it] ?: it }
            .joinToString("")
        return mapped.padEnd(CODE_LENGTH, PAD).take(CODE_LENGTH)
    }

    /** 大厅/列表里的展示名：`昵称·短码`。 */
    fun display(nickname: String, playerId: String): String {
        val name = nickname.trim().takeIf { it.isNotBlank() } ?: FALLBACK
        return "$name·${shortCode(playerId)}"
    }

    /**
     * 房间里**重名**的昵称（不同 id、同一个名字）。
     *
     * 大厅里靠短码区分，但**同一个房间内**短码帮不上忙：报分喊的是名字，
     * 两个同名的人会让「该你投了」这句话有两个应答者。在开局前提示改一个，
     * 比打完一轮才发现要便宜得多 —— 那时比分已经不知道算谁的。
     *
     * 只提示、不拦：**允许同名**是前提（见本文件开头），这里是提醒不是门禁。
     */
    fun duplicates(members: List<RoomMember>): List<String> =
        members
            .groupBy { it.name.trim() }
            .filter { (name, group) -> name.isNotBlank() && group.size > 1 }
            .keys
            .toList()

    private val CONTROL = Regex("[\\p{Cntrl}]")
    private val SEPARATORS = Regex("[\\s\\-_.·•,，。]")
}
