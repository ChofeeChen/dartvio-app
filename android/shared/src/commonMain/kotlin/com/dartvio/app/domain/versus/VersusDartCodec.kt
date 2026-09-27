package com.dartvio.app.domain.versus

/**
 * 逐镖落库的编解码（`versus_round_records.dartsCsv`）。
 *
 * 记号直接取 [BoardHit.label]（`T20` / `D16` / `5` / `25` / `BULL` / `MISS`）——
 * 它本来就是 UI 与战报展示用的那一种写法，单独再设计一套编码只会多出一处对齐负担。
 *
 * 解析**宽容**：空串 → 空列表，无法识别的记号 **跳过**（不抛异常）。
 * 理由与 `targetSetCsv` 一致：这个字段是新版本写、老版本读（或反之）的边界，
 * 一行坏记号不该让整场战报打不开。
 */
object VersusDartCodec {

    const val SEPARATOR = "|"

    fun encode(darts: List<BoardHit>): String = darts.joinToString(SEPARATOR) { it.label() }

    fun decode(csv: String): List<BoardHit> =
        if (csv.isEmpty()) emptyList()
        else csv.split(SEPARATOR).mapNotNull { parse(it) }

    /** 单个记号 → 命中；无法识别返回 `null`。 */
    fun parse(token: String): BoardHit? {
        val text = token.trim()
        return when {
            text.isEmpty() -> null
            text == "MISS" -> BoardHit.MISS
            text == "BULL" -> BoardHit.INNER_BULL
            text == "25" -> BoardHit.OUTER_BULL
            text.startsWith("T") -> text.drop(1).toIntOrNull()?.let { BoardHit.triple(it) }
            text.startsWith("D") -> text.drop(1).toIntOrNull()?.let { BoardHit.double(it) }
            else -> text.toIntOrNull()?.let { BoardHit.single(it) }
        }
    }
}
