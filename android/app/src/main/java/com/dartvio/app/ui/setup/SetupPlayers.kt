package com.dartvio.app.ui.setup

import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.PlayerType
import com.dartvio.app.domain.profile.LocalProfile

/** 对战组成：真人 vs 真人 / 真人 vs AI。 */
enum class VersusMode {
    HUMAN_VS_HUMAN,
    HUMAN_VS_AI;

    /** 该模式下「对手」才有的头像体系：真人对战选真人头像，AI 对战选机器人（含难度）。 */
    val picksAiAvatar: Boolean get() = this == HUMAN_VS_AI
}

/** 单场对局最多 4 人（M4 §6.2 上限；`GameViewModel.startMatch` 同样只接受 1..4）。 */
const val MAX_PLAYERS = 4

/** 本人固定占第 1 席 ⇒ 可选对手上限 = 3。 */
const val MAX_OPPONENTS = MAX_PLAYERS - 1

/**
 * 对手选择 —— **「点选头像」本身就是「几人参战」的唯一输入**。
 *
 * 为什么删掉独立的「玩家人数」控件（2026-09-12 体验裁决）：
 * 人数与头像是同一件事的两个说法，两个控件并存时必然出现「选了 4 人却只认 3 个头像」这类
 * 互相打架的状态。合并成一处后，「选中几个头像 = 几个对手」是**看得到**的事实，
 * 不再需要一个只能看数字的档位条。
 *
 * 三条边界口径：
 * 1. **至少 1 名对手**：再点一次最后一名对手不会把它取消（否则会得到一场没有对手的对局）；
 * 2. **最多 [MAX_OPPONENTS] 名**：触顶后未选中的头像变为不可点（而不是点了没反应）；
 * 3. 真人档与 AI 档**各存一份**：来回切「对战模式」不会丢掉另一边已经挑好的人。
 *
 * 做成不可变数据类（而不是 Compose 的 state list）：增删逻辑因此能在纯 JVM 单测里覆盖，
 * UI 只需要把它装进 `mutableStateOf`。
 */
data class OpponentSelection(
    val humans: List<HumanAvatar> = listOf(HumanAvatar.HUMAN_2),
    val ais: List<AiAvatar> = listOf(AiAvatar.AI_1),
) {

    /** 对手人数。 */
    fun opponentCount(versus: VersusMode): Int =
        if (versus.picksAiAvatar) ais.size else humans.size

    /** **含本人在内**的参战人数 —— 对战页的卡片数量就是它。 */
    fun playerCount(versus: VersusMode): Int = 1 + opponentCount(versus)

    fun toggleHuman(avatar: HumanAvatar): OpponentSelection =
        copy(humans = toggled(humans, avatar))

    fun toggleAi(ai: AiAvatar): OpponentSelection =
        copy(ais = toggled(ais, ai))

    /** 真人对手 key 串（落 `SetupDefaultsStore`）。 */
    fun humanKeys(): String = humans.joinToString(SEPARATOR) { it.key }

    /** AI 对手 key 串（落 `SetupDefaultsStore`）。 */
    fun aiKeys(): String = ais.joinToString(SEPARATOR) { it.key }

    companion object {

        const val SEPARATOR = ","

        /** 该头像此刻能否被点：已选中的可以取消（除非它是最后一名），未选中的可以加入（除非已满）。 */
        fun canToggleHuman(selection: OpponentSelection, avatar: HumanAvatar): Boolean = canToggle(
            selected = selection.humans.contains(avatar),
            size = selection.humans.size,
        )

        fun canToggleAi(selection: OpponentSelection, ai: AiAvatar): Boolean = canToggle(
            selected = selection.ais.contains(ai),
            size = selection.ais.size,
        )

        /**
         * 从落盘字符串还原。
         *
         * **宽容读取**：拆开之后逐个查表，未知 key（换版本删过头像）直接跳过；
         * 若一个都没活下来（或压根存的是空串），整档回落默认选择 ——
         * 空选择会让设置页看不到任何选中态，比给一个默认对手更糟。
         */
        fun fromKeys(humanKeys: String?, aiKeys: String?): OpponentSelection {
            val humans = humanKeys.orEmpty().split(SEPARATOR)
                .mapNotNull { key -> HumanAvatar.ALL.firstOrNull { it.key == key.trim() } }
                .take(MAX_OPPONENTS)
            val ais = aiKeys.orEmpty().split(SEPARATOR)
                .mapNotNull { key -> AiAvatar.ALL.firstOrNull { it.key == key.trim() } }
                .take(MAX_OPPONENTS)
            val fallback = OpponentSelection()
            return OpponentSelection(
                humans = humans.ifEmpty { fallback.humans },
                ais = ais.ifEmpty { fallback.ais },
            )
        }

        private fun <T> toggled(list: List<T>, item: T): List<T> = when {
            list.contains(item) -> if (list.size > 1) list - item else list
            list.size < MAX_OPPONENTS -> list + item
            else -> list
        }

        private fun canToggle(selected: Boolean, size: Int): Boolean =
            if (selected) size > 1 else size < MAX_OPPONENTS
    }
}

/**
 * 组装参赛名单：**第 1 席恒为本人**，其后是对手。
 *
 * 第 1 席必须是本人，除了「最左侧卡片显示登录账号」这个视觉要求，还有一条落库约束：
 * `MatchMapper` 把**第一个非 AI 席位**的 `Player.id` 当作本机身份，替换成档案 `profileId`。
 * 本人一旦不在首位，历史战绩就会挂到别人头上。
 *
 * 对手名字由头像推导（真人 ⇒ 「玩家 N」，AI ⇒ 头像自带的机器人名），
 * 不再提供逐位输入框 —— 与 X01 设置页统一，也免得出现「名单里 3 个人、输入框只有 2 个」的错位。
 */
fun buildSetupPlayers(
    versus: VersusMode,
    selection: OpponentSelection,
    selfName: String,
    selfAvatar: HumanAvatar,
    aiDifficulty: AiDifficulty,
): List<Player> {
    val self = Player(
        id = SELF_SEAT_ID,
        name = LocalProfile.normalizeNickname(selfName),
        type = PlayerType.HUMAN,
        avatar = selfAvatar.key,
    )
    val opponents: List<Player> = if (versus.picksAiAvatar) {
        selection.ais.mapIndexed { index, ai ->
            Player(
                id = seatId(index + 2),
                name = ai.label,
                type = PlayerType.AI,
                // 难度来自设置页那张「AI 难度」卡（**本局全部 AI 对手统一**），
                // 不再从头像推导 —— 头像现在只表示「是谁」，不表示「多强」。
                aiDifficulty = aiDifficulty,
                avatar = ai.key,
            )
        }
    } else {
        selection.humans.mapIndexed { index, human ->
            Player(
                id = seatId(index + 2),
                name = "玩家 ${index + 2}",
                type = PlayerType.HUMAN,
                avatar = human.key,
            )
        }
    }
    return listOf(self) + opponents
}

/** 本人席位 ID。位置 ID，落库时由 `MatchMapper` 换成档案 `profileId`。 */
const val SELF_SEAT_ID = "p1"

/** 第 [seat] 席的占位 ID（从 1 开始）。 */
fun seatId(seat: Int): String = "p$seat"

/**
 * 设置页 ↔ 设置子页共享的状态。
 *
 * **为什么必须有它**：Navigation Compose 在跳转时会**销毁**上一个目的地的组合，
 * `remember { }` 里的状态随之丢失。而「游戏设置」现在是**独立页面**，
 * 从它返回时若状态被重建，玩家刚改的设置和刚选好的对手会当场消失。
 * 因此把状态提到进程内的单例上（与既有的 `MatchSession` 同一套做法）。
 *
 * 刻意不存进 `rememberSaveable` / ViewModel：需要跨的是**返回栈上兄弟目的地之间**，
 * 不是进程重建；进程被杀后设置页重新初始化是合理行为。
 */
object SetupSession {

    /** Cricket 游戏选择页 ↔ Cricket 游戏设置页。 */
    var cricket: CricketSetupState? = null

    /** X01 设置页 ↔ X01 游戏详细设置页。 */
    var x01: X01SetupState? = null

    /** 取 X01 设置状态（首次创建即缓存）。 */
    fun x01State(): X01SetupState = x01 ?: X01SetupState().also { x01 = it }

    /**
     * 取 Cricket 设置状态（首次创建即缓存）。
     *
     * [restore] 只在**新建实例**时执行一次 —— 用它灌入「上次用过的玩法 + 该档设置」，
     * 这样每次回到游戏选择页都不会覆盖玩家刚在设置页改好的东西。
     */
    fun cricketState(restore: (CricketSetupState) -> Unit = {}): CricketSetupState =
        cricket ?: CricketSetupState().also {
            restore(it)
            cricket = it
        }

    /** 仅供测试与「重置」动作使用。 */
    fun clear() {
        cricket = null
        x01 = null
    }
}
