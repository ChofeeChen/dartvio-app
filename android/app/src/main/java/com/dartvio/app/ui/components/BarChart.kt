package com.dartvio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柱状图：**同一段的量级比较**（每周场次、每个分区的命中数）。
 *
 * ## 什么时候用柱子，什么时候用 [TrendLineChart]
 *
 * 柱子回答「哪一段多」，段与段之间**没有**中间态（第 3 周和第 4 周之间不存在第 3.5 周）；
 * 折线回答「这个量往哪儿走」，点与点之间是连续的时间。
 * 把每周场次画成折线，会凭空造出「周与周之间有过渡」的错觉。
 *
 * ## 为什么柱子之间留空、柱顶不留描边
 *
 * 柱子彼此贴住会让相邻两根读成同一个色块；描边则在小尺寸下把柱子的高度切掉一格，
 * 高度是这里唯一要读准的信息。
 */
@Composable
fun BarChart(
    labels: List<String>,
    values: List<Double>,
    color: Color,
    /** 基线颜色：主题色只能在可组合作用域里读，因此由调用方传进来。 */
    guideColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    background: Color = Color.Unspecified,
) {
    if (values.isEmpty()) return
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (background != Color.Unspecified) {
                    Modifier.background(background, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        drawBars(values = values, color = color, guideColor = guideColor)
    }
}

/**
 * 柱体本体。抽出来自 [drawTrendLine] 的兄弟函数：两处共用同一套留白与基线规则，
 * 图表的风格才不会一处一个样子。
 */
fun DrawScope.drawBars(values: List<Double>, color: Color, guideColor: Color) {
    val max = values.maxOrNull()?.toFloat()?.takeIf { it > 0f } ?: return
    val padX = 12.dp.toPx()
    val padY = 10.dp.toPx()
    val usableW = size.width - padX * 2
    val usableH = size.height - padY * 2
    val slot = usableW / values.size
    val barW = slot * 0.56f
    val baseline = padY + usableH
    drawLine(guideColor, Offset(padX, baseline), Offset(size.width - padX, baseline), 1f)
    values.forEachIndexed { index, value ->
        val ratio = (value.toFloat() / max).coerceIn(0f, 1f)
        val barH = (usableH * ratio).coerceAtLeast(1f)
        val left = padX + slot * index + (slot - barW) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, baseline - barH),
            size = Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
    }
}
