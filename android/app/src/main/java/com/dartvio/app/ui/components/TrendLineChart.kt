package com.dartvio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * **同一份时间趋势曲线**：报告页的「综合偏离」与数据页的「三镖均」共用这一个画法。
 *
 * ## 为什么画**线**而不是柱子
 *
 * 要读的是「有没有在往一个方向走」：柱子只能比大小，斜率要用户自己脑补；
 * 而折线把「走势」直接画出来了 —— 这是这两个地方唯一要回答的问题。
 *
 * ## 为什么要点标记
 *
 * 没有点，一条折线就丢掉了横轴的**离散性**：分段的 Width 是由数据点决定的，
 * 让它看起来像一条连续曲线，会让人以为中间也有采样。
 *
 * ## 为什么必须有基线
 *
 * 只给一条孤立曲线的高度没有任何参照；画到 0 的基线之后，
 * 「从 40 降到 30」与「从 200 降到 150」在视觉上立刻分开了。
 */
@Composable
fun TrendLineChart(
    values: List<Double>,
    /** 曲线与数据点的颜色（语义色：改善 = 绿、变差 = 红、无结论 = 次文本色）。 */
    color: Color,
    /** 基线颜色：调用方传入 —— 主题色只能在可组合作用域里读，而这个组件要被 Canvas 回调复用。 */
    guideColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    background: Color = Color.Unspecified,
) {
    if (values.size < 2) return
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (background != Color.Unspecified) Modifier.background(background, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        drawTrendLine(values = values, color = color, guideColor = guideColor)
    }
}

/** 曲线本体。抽出来是为了 UI 各处共用同一个**画法**，而不是各自 copy 一份画布代码。 */
fun DrawScope.drawTrendLine(values: List<Double>, color: Color, guideColor: Color) {
    val max = values.maxOrNull()?.toFloat()?.takeIf { it > 0f } ?: return
    val padX = 12.dp.toPx()
    val padY = 10.dp.toPx()
    val usableW = size.width - padX * 2
    val usableH = size.height - padY * 2
    fun pointAt(index: Int, value: Double): Offset {
        val ratio = (value.toFloat() / max).coerceIn(0f, 1f)
        return Offset(
            x = padX + usableW * (index.toFloat() / (values.size - 1).coerceAtLeast(1)),
            y = padY + usableH * (1f - ratio),
        )
    }
    // 基线（0）：值全部非负，基线就是这一段的原点参考。
    drawLine(guideColor, Offset(padX, padY + usableH), Offset(size.width - padX, padY + usableH), 1f)
    values.forEachIndexed { index, value ->
        if (index > 0) {
            drawLine(
                color = color,
                start = pointAt(index - 1, values[index - 1]),
                end = pointAt(index, value),
                strokeWidth = 2f,
            )
        }
        drawCircle(color = color, radius = 3f, center = pointAt(index, value))
    }
}

/**
 * 曲线图下的图注：`最新 42.6　最早 38.1`。
 *
 * 有了两端的绝对数值，曲线不再只是「一个形状」—— 用户能说出自己进步了多少，
 * 这正是这张图存在的原因。
 */
@Composable
fun TrendLegend(
    values: List<Double>,
    // 定死 Locale：部分机型的默认 Locale 是阿拉伯语系，`%f` 会输出阿拉伯数字。
    format: (Double) -> String = { String.format(Locale.US, "%.1f", it) },
) {
    if (values.size < 2) return
    Text(
        "最早 ${format(values.first())}　最新 ${format(values.last())}　共 ${values.size} 场",
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
