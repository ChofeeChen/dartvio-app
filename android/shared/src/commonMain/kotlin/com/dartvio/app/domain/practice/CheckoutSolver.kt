package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.Dart
import kotlin.random.Random

/**
 * 随机结镖练习的结镖路线求解器。
 *
 * - 目标范围 20..170（排除 1-19 与经典不可结镖分数 159/162/163/165/166/168/169）。
 * - 简单(20-60) 30%、中等(61-120) 40%、困难(121-170) 30% 分布。
 * - 预计算 1-3 镖内所有合法双结路线。
 */
object CheckoutSolver {

    /** 排除的目标分数。 */
    private val EXCLUDED_TARGETS = (1..19).toSet() + setOf(159, 162, 163, 165, 166, 168, 169)

    private val ALL_DARTS: List<Dart> = buildList {
        for (n in 1..20) {
            add(Dart.single(n))
            add(Dart.double(n))
            add(Dart.triple(n))
        }
        add(Dart.OUTER_BULL)
        add(Dart.INNER_BULL)
    }

    private val ROUTES: Map<Int, List<List<Dart>>> by lazy { precomputeRoutes() }

    /** 生成一个符合难度分布的随机结镖目标。 */
    fun generateTarget(): Int {
        val bucket = Random.nextInt(100)
        val range = when {
            bucket < 30 -> 20..60
            bucket < 70 -> 61..120
            else -> 121..170
        }
        val candidates = ROUTES.keys.filter { it in range && it !in EXCLUDED_TARGETS }
        check(candidates.isNotEmpty()) { "No checkout target available in range $range" }
        return candidates.random()
    }

    /** 获取指定目标的所有合法结镖路线（按镖数少→多、最高单镖分降序排列）。 */
    fun routesFor(target: Int): List<List<Dart>> = ROUTES[target] ?: emptyList()

    /** 把路线格式化为可读文本，例如 "T20(60) → T20(60) → BULL(50) = 170"。 */
    fun formatRoute(route: List<Dart>): String {
        val parts = route.map { "${it.label()}(${it.score})" }
        val total = route.sumOf { it.score }
        return "${parts.joinToString(" → ")} = $total"
    }

    private fun precomputeRoutes(): Map<Int, List<List<Dart>>> {
        val map = mutableMapOf<Int, MutableList<List<Dart>>>()
        val darts = ALL_DARTS

        fun canFinish(d: Dart): Boolean = d.isDouble || d.isInnerBull

        fun addRoute(route: List<Dart>) {
            val sum = route.sumOf { it.score }
            if (sum in 2..170) {
                map.getOrPut(sum) { mutableListOf() }.add(route)
            }
        }

        // 1 镖
        for (d in darts) {
            if (canFinish(d)) addRoute(listOf(d))
        }

        // 2 镖
        for (d1 in darts) {
            for (d2 in darts) {
                if (canFinish(d2)) addRoute(listOf(d1, d2))
            }
        }

        // 3 镖
        for (d1 in darts) {
            for (d2 in darts) {
                for (d3 in darts) {
                    if (canFinish(d3)) addRoute(listOf(d1, d2, d3))
                }
            }
        }

        return map.mapValues { (_, routes) ->
            routes
                .distinctBy { it.joinToString { d -> d.label() } }
                .sortedWith(
                    compareBy(
                        { it.size },
                        { -(it.maxOfOrNull { d -> d.score } ?: 0) }
                    )
                )
                .take(20)
        }
    }
}
