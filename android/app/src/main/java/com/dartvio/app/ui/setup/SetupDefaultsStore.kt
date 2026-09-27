package com.dartvio.app.ui.setup

import android.content.Context
import android.content.SharedPreferences
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode

// ============================================================================
// 设置记忆的两级作用域
// ============================================================================
//
// 一个设置「记在哪一档」必须和它「出现在哪一层」是同一件事：
// 页面层级与存档粒度不一致时，玩家会看到自己在 A 页改的东西被 B 页改掉。
//
//   比赛层（**跨玩法共享一份**，键前缀 `match.`）
//     └ 对战模式 / 对手 / AI 难度 / 智能难度 —— 回答「和谁打」
//   玩法层（**每个玩法各一份**，键前缀 `cricket.<MODE>.`）
//     └ 赛制 / 局数 / 轮数上限 / Overkill —— 回答「这一局怎么算」
//
// 为什么对手不能按玩法存：它出现在**一级页**（选完玩法就要定对手），
// 若按玩法分档，玩家一切换玩法，刚选好的对手就被换成另一个玩法那份旧值 ——
// 界面位置与存档粒度必须同进同退。
//
// **扩展点**（新增一层设置 = 三步，读写逻辑一行都不用改）：
//   1. 定义该层的纯数据类（`XxxDefaults`），标量 + 基础类型，不引序列化；
//   2. 给出它的键前缀（全局一份 → 常量；按项分档 → 由 [CricketMode] 之类的枚举算出）；
//   3. 用文件下方的 `xxxAt(prefix, ...)` 辅助函数写一对 `readXxx / writeXxx`。
// 新增比赛类型（如未来的 Shanghai / Around the Clock）就再写一份项目层数据类，
// 并决定它读不读比赛层 —— 「和谁打」是共用语义，通常应当直接读 [SetupDefaultsStore.readMatch]。

/**
 * 比赛层设置：**和谁打**。
 *
 * 跨玩法共享一份（未接入的项目如 X01 也可直接复用它，届时 X01 设置页会与 Cricket 同步对手）。
 */
data class MatchDefaults(
    val versusAi: Boolean = false,
    /**
     * 真人对手 key 串（逗号分隔，顺序即席位顺序）。
     *
     * 记的是**对手**，不含本人：本人的昵称与头像唯一来源是本机档案，
     * 存进设置里会让「改昵称后旧设置的对手栏还挂着旧名字」。
     */
    val humanOpponentKeys: String = "",
    /** AI 对手 key 串（逗号分隔，顺序即席位顺序；**只记身份，不记强度**）。 */
    val aiOpponentKeys: String = "",
    /**
     * AI 难度：整局一档、对**全部** AI 对手生效（2026-09-12 与头像解耦）。
     *
     * 解耦前难度藏在头像里（`AI_1` = 入门…），读回来的头像本身就决定了强度；
     * 现在头像只表示「是谁」，强度必须单独记一档，否则「4 号机器人 + 专业难度」这种组合存不住。
     */
    val aiDifficulty: AiDifficulty = AiDifficulty.INTERMEDIATE,
    /**
     * 智能难度开关（AI 随真人水平在**本档内**自适应）。
     *
     * 与 [aiDifficulty] 是两件事：难度定「档位」，本开关定「要不要在档位内跟着真人水平微调」。
     */
    val smartAi: Boolean = true,
) {
    companion object {
        val DEFAULT = MatchDefaults()
    }
}

/**
 * Cricket 玩法层设置：**这一局怎么算**。每个玩法各存一份。
 *
 * **Random 的目标集不进这里**：它是「开局随机抽 5 个分区」的局级常量（M2 §4.9.5①）。
 * 记住上一局的 5 个号等于把随机模式变成固定模式，与玩法定义直接相悖 ——
 * 每次选中 Random 都必须在那一刻重抽。
 */
data class CricketRuleDefaults(
    val matchMode: MatchMode = MatchMode.MULTI_LEG,
    // 1 局：与页面上的默认一致（打完一局 = 打完一场 = 落库，2026-09-27 反馈）。
    val legsToWin: Int = 1,
    val tacticsRoundLimit: Int = DEFAULT_TACTICS_ROUND_LIMIT,
    /**
     * Overkill 照存：它对 no_score / cut_throat 恒为 false（[CricketMode.supportsOverkill]），
     * 读回来也只会被同样吞掉，不会跨玩法串味。
     */
    val overkill: Boolean = false,
) {
    companion object {
        val DEFAULT = CricketRuleDefaults()
    }
}

/**
 * 「保存设置」的落地：按作用域记住上一次真正用过的设置，下次进入可直接开局。
 *
 * 只存扁平基本类型，不引入序列化 —— 键的总数是个位数，套一层 JSON 只多出一个会随时间漂移的 schema。
 *
 * **读取一律宽容**：存过但个别键损毁 → **只有那个键**回落默认，其余照读
 * （整块回退会让玩家改了 7 项、坏第 8 项时全部前功尽弃）。
 */
object SetupDefaultsStore {

    const val PREFS_NAME = "dartvio_setup_defaults"

    // ---- 作用域前缀 ----
    private const val PREFIX_MATCH = "match."
    private fun prefixRules(mode: CricketMode) = "cricket.${mode.name}."

    /** 上一次用过的玩法（Cricket 玩法层的一级索引）。 */
    private const val KEY_LAST_MODE = "cricket.lastMode"

    // ---- 键名 ----
    // 键名是**磁盘契约**：改了就等于把老玩家的设置静默丢掉（新键读不到 → 回落默认）。
    // 因此 `versusAi` 从「按玩法存」搬到「比赛层存」时，**键名一字未改**，只换了前缀。
    private const val KEY_VERSUS_AI = "versusAi"
    private const val KEY_HUMAN_OPPONENTS = "humanOpponents"
    private const val KEY_AI_OPPONENTS = "aiOpponents"
    private const val KEY_AI_DIFFICULTY = "aiDifficulty"
    private const val KEY_SMART_AI = "smartAi"
    private const val KEY_MATCH_MODE = "matchMode"
    private const val KEY_LEGS_TO_WIN = "legsToWin"
    private const val KEY_ROUND_LIMIT = "roundLimit"
    private const val KEY_OVERKILL = "overkill"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========================================================================
    // Cricket 玩法层
    // ========================================================================

    /** 上一次用过的玩法；从未保存过、或存下的名字已失效时返回 null，由调用方决定默认选中谁。 */
    fun readLastMode(prefs: SharedPreferences): CricketMode? =
        prefs.getString(KEY_LAST_MODE, null)?.let { name ->
            CricketMode.entries.firstOrNull { it.name == name }
        }

    /** 读回 [mode] 这一档的玩法层设置。 */
    fun readRules(prefs: SharedPreferences, mode: CricketMode): CricketRuleDefaults {
        val prefix = prefixRules(mode)
        val fallback = CricketRuleDefaults.DEFAULT
        return CricketRuleDefaults(
            matchMode = prefs.enumAt(prefix, KEY_MATCH_MODE, MatchMode.entries, fallback.matchMode),
            legsToWin = prefs.intAt(prefix, KEY_LEGS_TO_WIN, fallback.legsToWin)
                .let { if (it in LEGS_TO_WIN_CHOICES) it else fallback.legsToWin },
            tacticsRoundLimit = prefs.intAt(prefix, KEY_ROUND_LIMIT, fallback.tacticsRoundLimit)
                .let { if (it in MatchConfig.MAX_ROUNDS_CHOICES) it else fallback.tacticsRoundLimit },
            overkill = prefs.booleanAt(prefix, KEY_OVERKILL, fallback.overkill),
        )
    }

    /** 写入 [mode] 这一档，并把它记为「上次用过的玩法」。 */
    fun writeRules(prefs: SharedPreferences, mode: CricketMode, defaults: CricketRuleDefaults) {
        val prefix = prefixRules(mode)
        prefs.writeScoped {
            putString(KEY_LAST_MODE, mode.name)
            putString(prefix + KEY_MATCH_MODE, defaults.matchMode.name)
            putInt(prefix + KEY_LEGS_TO_WIN, defaults.legsToWin)
            putInt(prefix + KEY_ROUND_LIMIT, defaults.tacticsRoundLimit)
            putBoolean(prefix + KEY_OVERKILL, defaults.overkill)
        }
    }

    // ========================================================================
    // 比赛层（跨玩法共享）
    // ========================================================================

    /**
     * 读比赛层设置。
     *
     * **旧版本的对手是跟着玩法存的**（`cricket.<MODE>.versusAi`…）。搬到比赛层后，
     * 老玩家盘上只有旧键：这里取「上次用过的玩法」那一份作为迁移源，
     * 一个都没存过才回落默认 —— 否则升级一次就把人家挑好的对手清空一遍。
     * （新键一旦写下就以新键为准，旧键只是只读的迁移源，不删。）
     */
    fun readMatch(prefs: SharedPreferences): MatchDefaults {
        if (prefs.containsAt(PREFIX_MATCH, KEY_VERSUS_AI)) return readMatchAt(prefs, PREFIX_MATCH)
        legacyMatchPrefix(prefs)?.let { return readMatchAt(prefs, it) }
        return MatchDefaults.DEFAULT
    }

    fun writeMatch(prefs: SharedPreferences, defaults: MatchDefaults) {
        prefs.writeScoped {
            putBoolean(PREFIX_MATCH + KEY_VERSUS_AI, defaults.versusAi)
            putString(PREFIX_MATCH + KEY_HUMAN_OPPONENTS, defaults.humanOpponentKeys)
            putString(PREFIX_MATCH + KEY_AI_OPPONENTS, defaults.aiOpponentKeys)
            putString(PREFIX_MATCH + KEY_AI_DIFFICULTY, defaults.aiDifficulty.name)
            putBoolean(PREFIX_MATCH + KEY_SMART_AI, defaults.smartAi)
        }
    }

    /** 旧版把对手存在玩法档里：迁移源 = 上次用过的玩法，其次按枚举顺序第一个存过的档。 */
    private fun legacyMatchPrefix(prefs: SharedPreferences): String? {
        val byLastMode = readLastMode(prefs)
            ?.let { prefixRules(it) }
            ?.takeIf { prefs.containsAt(it, KEY_VERSUS_AI) }
        return byLastMode
            ?: CricketMode.entries.map { prefixRules(it) }
                .firstOrNull { prefs.containsAt(it, KEY_VERSUS_AI) }
    }

    private fun readMatchAt(prefs: SharedPreferences, prefix: String): MatchDefaults {
        val fallback = MatchDefaults.DEFAULT
        return MatchDefaults(
            versusAi = prefs.booleanAt(prefix, KEY_VERSUS_AI, fallback.versusAi),
            humanOpponentKeys = prefs.stringAt(prefix, KEY_HUMAN_OPPONENTS)
                ?: fallback.humanOpponentKeys,
            aiOpponentKeys = prefs.stringAt(prefix, KEY_AI_OPPONENTS)
                ?: fallback.aiOpponentKeys,
            aiDifficulty = prefs.enumAt(
                prefix,
                KEY_AI_DIFFICULTY,
                AiDifficulty.entries,
                fallback.aiDifficulty
            ),
            smartAi = prefs.booleanAt(prefix, KEY_SMART_AI, fallback.smartAi),
        )
    }
}

// ============================================================================
// 作用域读写辅助（新增一层设置时复用，避免把「前缀拼接」抄得到处都是）
// ============================================================================

private fun SharedPreferences.containsAt(prefix: String, key: String): Boolean =
    contains(prefix + key)

private fun SharedPreferences.booleanAt(prefix: String, key: String, fallback: Boolean): Boolean =
    getBoolean(prefix + key, fallback)

private fun SharedPreferences.stringAt(prefix: String, key: String): String? =
    getString(prefix + key, null)

private fun SharedPreferences.intAt(prefix: String, key: String, fallback: Int): Int =
    getInt(prefix + key, fallback)

/** 枚举**宽容读取**：存下的名字查不到（版本删过档位）就回落默认，不抛异常也不留空。 */
private fun <E : Enum<E>> SharedPreferences.enumAt(
    prefix: String,
    key: String,
    values: List<E>,
    fallback: E,
): E = stringAt(prefix, key)?.let { name -> values.firstOrNull { it.name == name } } ?: fallback

/** 一次编辑事务里写完一组键（`Editor` 作接收者，调用处直接 `putXxx`）。 */
private fun SharedPreferences.writeScoped(block: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.block()
    editor.apply()
}
