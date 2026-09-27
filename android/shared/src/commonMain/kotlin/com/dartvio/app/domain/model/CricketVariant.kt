package com.dartvio.app.domain.model

/**
 * Cricket 得分归属策略（M2 §4.8①）。
 *
 * 只回答一个问题：**这一镖的分，记到谁的账上**。
 */
enum class ScoreSink {
    /** 计入投掷者自己（standard，原口径）。 */
    SELF,

    /** 计入对手（cut_throat，生死局）。 */
    OPPONENTS,

    /** 不累计任何得分，双方 score 恒为 0（no_score）。 */
    NONE
}

/**
 * 「已关闭全部分区」之后的分数比较策略（M2 §4.8①）。
 *
 * 注意比较方向：standard 是高分领先（GE），cut_throat 是**低分领先**（LE，含等号）。
 */
enum class WinCompare {
    /** 本方分 ≥ 对手分（standard）。 */
    GE,

    /** 本方分 ≤ 对手分（cut_throat，低分领先，含等号）。 */
    LE,

    /** 不比较分数，先关满者胜（no_score）。 */
    NONE
}

/**
 * Cricket 玩法变体（M2 §4.8，一期 3 种）。
 *
 * 变体之间的差异**只有两处**：得分归属（[scoreSink]）与胜负比较（[winCompare]）。
 * 关闭规则（每分区累计 3 次计数即关闭）与回合结构（3 镖/回合、无 Bust、无回合上限）
 * 三种变体完全一致 —— 所以规则层只需要把这两个口径参数化，不必为每个变体各写一套。
 *
 * Tactics（9/12 分区 + 双倍/三倍档）与 Random（随机分区集）是二期：本期只留
 * [fromKey] 的宽容解析位，不实现功能，避免旧端读到新取值时直接崩。
 */
enum class CricketVariant(
    val scoreSink: ScoreSink,
    val winCompare: WinCompare,
    /** UI 展示名（M7 P7.4 要求结果页 / 历史都标注本局玩法名）。 */
    val label: String,
    /** 设置项必须带的一句人话解释（M7 P7.1：不能只给名词）。 */
    val hint: String
) {
    STANDARD(
        scoreSink = ScoreSink.SELF,
        winCompare = WinCompare.GE,
        label = "标准",
        hint = "关闭 15-20 与 Bull，分数高者胜（默认）"
    ),

    NO_SCORE(
        scoreSink = ScoreSink.NONE,
        winCompare = WinCompare.NONE,
        label = "不计分",
        hint = "只比谁先关闭全部分区，不看分数"
    ),

    CUT_THROAT(
        scoreSink = ScoreSink.OPPONENTS,
        winCompare = WinCompare.LE,
        label = "生死局",
        hint = "打中得分算给对手，分数低者领先（建议 3 人以上）"
    );

    /** 落库 / 线协议的稳定键，与 M2 §4.8 的取值一致（standard / no_score / cut_throat）。 */
    val key: String get() = name.lowercase()

    companion object {
        /** 缺省变体；旧存档 / 旧快照缺字段一律按它处理（M4 §6.2、A4.27）。 */
        val DEFAULT = STANDARD

        /**
         * 宽容解析：忽略大小写与下划线，**未知或缺失一律回落 [DEFAULT]，不抛异常**。
         *
         * 这条不只是「旧快照兼容」（M4 §6.2 / A4.27）：二期新增 tactics / random 取值后，
         * 老版本客户端读到新键也不能崩，所以这里的兜底是长期契约而不是临时补丁。
         */
        fun fromKey(raw: String?): CricketVariant {
            val normalized = raw?.trim()?.lowercase()?.replace("_", "").orEmpty()
            // 比较两侧都要先去下划线：raw 侧去掉后是 "cutthroat"，
            // 若拿它去比枚举名 "cut_throat" 会永远不相等，cut_throat / no_score 会静默退回 standard。
            return entries.firstOrNull { it.key.replace("_", "") == normalized } ?: DEFAULT
        }
    }
}
