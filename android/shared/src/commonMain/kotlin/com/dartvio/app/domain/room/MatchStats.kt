package com.dartvio.app.domain.room

/**
 * 在线对局的赛后统计（M2 R4）。
 *
 * ## 数据只来自一帧，而这一帧**不完整**
 *
 * [SpectatorSnapshot.turns] 最多带 [RoomMatchRules.MAX_TURNS_IN_FRAME]（30）条流水 ——
 * 帧是要广播给所有人的，不能随对局无限变长。所以长局的早期回合**不在这份数据里**。
 *
 * 这里**不补、不估**（与 `LanMatchMapper` 同一取舍）：缺的流水就让它缺着，
 * 并由 [MatchStatsReport.truncated] 如实标出来，界面说一句「统计基于最近 30 轮」。
 * 按剩余分反推投了多少镖也能凑出一个分母，但它错得悄无声息。
 *
 * ## 口径（照 n01 的卡片）
 * - **3 镖平均** = 总得分 ÷ 轮数（每轮 3 镖；**爆分轮计 0 分但算一轮** ——那一轮确实投了）。
 * - **First 9** = 前 3 轮的平均分；不足 3 轮时给 null，不给半个答案。
 * - **100+ / 140+ / 180** = 单轮得分区间计数（180 同时计入前两项，与卡片习惯一致）。
 * - **High Finish** = 结镖那一轮的**得分**（D20 收 40 就是 40），不是结镖后的剩余分（那是 0）。
 * - **Best Leg** = 赢下一局用掉的**最少轮数**；没有完整结镖的局不参与。
 */
object MatchStats {

    /** First 9 = 前 3 轮（9 镖）。 */
    const val FIRST9_ROUNDS = 3

    fun of(snapshot: SpectatorSnapshot): MatchStatsReport {
        val turnsByName = LinkedHashMap<String, MutableList<SpectatorTurn>>()
        snapshot.turns.forEach { turn ->
            turnsByName.getOrPut(turn.playerName) { ArrayList() }.add(turn)
        }
        val bestLegs = bestLegByPlayer(snapshot.turns)

        val players = snapshot.players.map { player ->
            val mine = turnsByName[player.name] ?: emptyList()
            val total = mine.sumOf { it.scored }
            val first9 = mine.take(FIRST9_ROUNDS)
                .takeIf { it.size == FIRST9_ROUNDS }
                ?.sumOf { it.scored }

            PlayerMatchStats(
                playerName = player.name,
                legsWon = player.legsWon,
                turns = mine.size,
                threeDartAvg = if (mine.isEmpty()) 0.0 else total.toDouble() / mine.size,
                first9Avg = first9?.let { it.toDouble() / FIRST9_ROUNDS },
                count100 = mine.count { it.scored >= 100 },
                count140 = mine.count { it.scored >= 140 },
                count180 = mine.count { it.scored >= 180 },
                highFinish = mine.filter { it.isCheckout }.maxOfOrNull { it.scored },
                bestLegTurns = bestLegs[player.name]
            )
        }

        return MatchStatsReport(
            players = players,
            // 达到上限即说明「还有更早的轮次不在这份数据里」—— 这是**可能**不完整，
            // 不是一定不完整（正好打满 30 轮的长局并不缺），措辞由界面留余地。
            truncated = snapshot.turns.size >= RoomMatchRules.MAX_TURNS_IN_FRAME
        )
    }

    /**
     * 每人「赢下一局用掉的最少轮数」。
     *
     * 局的边界只能靠 [SpectatorTurn.isCheckout] 认：帧里没有「第几局」这种字段。
     * 因此**未结镖就结束的局**（弃权、打满轮数）不参与 —— 那种局的轮数没有可比性。
     */
    private fun bestLegByPlayer(turns: List<SpectatorTurn>): Map<String, Int> {
        val best = LinkedHashMap<String, Int>()
        var legTurns = 0
        turns.forEach { turn ->
            legTurns++
            if (!turn.isCheckout) return@forEach
            val current = best[turn.playerName]
            if (current == null || legTurns < current) best[turn.playerName] = legTurns
            legTurns = 0
        }
        return best
    }
}

/** 一方的赛后统计。所有「没打出来」的项都是 null，界面显示「—」而不是 0。 */
data class PlayerMatchStats(
    val playerName: String,
    val legsWon: Int,
    /** 这份数据里有他的多少轮。 */
    val turns: Int,
    /** 每轮平均得分（3 镖平均）。 */
    val threeDartAvg: Double,
    /** First 9 平均；不足 3 轮为 null。 */
    val first9Avg: Double?,
    val count100: Int,
    val count140: Int,
    val count180: Int,
    /** 最高结镖分；没有结镖为 null。 */
    val highFinish: Int?,
    /** 最快赢下的一局用了几轮；没有完整结镖为 null。 */
    val bestLegTurns: Int?
)

data class MatchStatsReport(
    val players: List<PlayerMatchStats>,
    /** 流水是否可能已被截断（见 [MatchStats]）。 */
    val truncated: Boolean
)
