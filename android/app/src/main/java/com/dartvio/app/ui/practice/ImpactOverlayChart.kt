package com.dartvio.app.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.impact.ImpactFrame
import com.dartvio.app.domain.impact.OverlayResult
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ⑥ 新旧双色叠加图（V1.4 §5.4-⑥ / A-IMP-30）。
 *
 * 三条硬约束都在 [OverlayResult] 里判好，这里只负责画：
 * 1. **跨档位不叠**（[OverlayResult.mixedSpans]）—— 两张不同尺子的点云叠在一起会凭空造出「收敛」；
 * 2. **点数过多不画散点**（[OverlayResult.dense]）—— 改用质心 + R95 圆；
 * 3. **样本不足仍画图，但明说不能下结论**（[OverlayResult.conclusionReady]）——
 *    图是描述，结论是另一回事，两者必须分开说。
 *
 * 坐标：横轴 = 切向误差（右为正），纵轴 = 径向误差（**向上为偏外**），与靶盘、热力图一致。
 */
@Composable
fun ImpactOverlayChart(overlay: OverlayResult, modifier: Modifier = Modifier) {
    val Accent = com.dartvio.app.ui.theme.Accent
    val Secondary = com.dartvio.app.ui.theme.Secondary
    val TextPrimaryDark = com.dartvio.app.ui.theme.TextPrimaryDark
    val TextSecondaryDark = com.dartvio.app.ui.theme.TextSecondaryDark
    val Warning = com.dartvio.app.ui.theme.Warning

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "新旧叠加（此前 3 轮 vs 最近 3 轮）",
            color = TextPrimaryDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        if (overlay.mixedSpans) {
            Text(
                "两轮的窗口档位不同 —— 不同尺子的点云叠在一起会凭空造出「收敛」的假象，因此拒绝叠图。",
                color = Warning,
                fontSize = 12.sp
            )
            return@Column
        }

        val previous = overlay.previous
        val recent = overlay.recent
        if (previous.frames.isEmpty() && recent.frames.isEmpty()) {
            Text("还没有足够的两轮数据可叠加（每侧至少 3 轮、每轮 ≥12 镖）。", color = TextSecondaryDark, fontSize = 12.sp)
            return@Column
        }

        val radius = maxAbsOf(previous.frames + recent.frames)
            .coerceAtLeast(maxOf(previous.r95, recent.r95, 1.0))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val scale = (min(size.width, size.height) / 2f) * 0.88f / radius.toFloat()

            // 参考环：两轮各自的 R95（半径的尺子，不是「成绩」）。
            drawCircle(
                color = Secondary.copy(alpha = 0.55f),
                radius = (previous.r95 * scale).toFloat(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Accent.copy(alpha = 0.55f),
                radius = (recent.r95 * scale).toFloat(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )

            if (overlay.dense) {
                centroidOf(previous.frames)?.let { drawCrossMarker(it, cx, cy, scale, Secondary) }
                centroidOf(recent.frames)?.let { drawCrossMarker(it, cx, cy, scale, Accent) }
            } else {
                previous.frames.forEach { frame ->
                    drawCircle(
                        color = Secondary.copy(alpha = 0.45f),
                        radius = 3f,
                        center = Offset(cx + frame.eTan.toFloat() * scale, cy - frame.eRad.toFloat() * scale)
                    )
                }
                recent.frames.forEach { frame ->
                    drawCircle(
                        color = Accent.copy(alpha = 0.60f),
                        radius = 3f,
                        center = Offset(cx + frame.eTan.toFloat() * scale, cy - frame.eRad.toFloat() * scale)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "此前 3 轮：n=${previous.n} · R95 ${mm(previous.r95)} mm",
            color = Secondary,
            fontSize = 12.sp
        )
        Text(
            "最近 3 轮：n=${recent.n} · R95 ${mm(recent.r95)} mm",
            color = Accent,
            fontSize = 12.sp
        )
        Text(
            "R95 变化 ${if (overlay.deltaR95 >= 0) "+" else "−"}${mm(abs(overlay.deltaR95))} mm" +
                if (overlay.dense) "（点数过多，已改用质心 + R95 圆）" else "",
            color = TextPrimaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (!overlay.conclusionReady) {
            Spacer(Modifier.height(4.dp))
            Text(
                "两轮各 ≥12 镖、合计 ≥30 镖才下结论；现在只是把点画出来，不作任何判断。",
                color = TextSecondaryDark,
                fontSize = 11.sp
            )
        }
    }
}

/** 画布半径 = 点云与 R95 里的最大绝对值（留 12% 余量）。 */
private fun maxAbsOf(frames: List<ImpactFrame>): Double {
    var maxAbs = 0.0
    frames.forEach { frame ->
        maxAbs = max(maxAbs, max(abs(frame.eRad), abs(frame.eTan)))
    }
    return maxAbs
}

private fun centroidOf(frames: List<ImpactFrame>): Pair<Double, Double>? {
    if (frames.isEmpty()) return null
    return frames.sumOf { it.eRad } / frames.size to frames.sumOf { it.eTan } / frames.size
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrossMarker(
    centroid: Pair<Double, Double>,
    cx: Float,
    cy: Float,
    scale: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val x = cx + centroid.second.toFloat() * scale
    val y = cy - centroid.first.toFloat() * scale
    val arm = 8f
    drawLine(color, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = 2f)
    drawLine(color, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = 2f)
}

private fun mm(value: Double): String = ((value * 10).toInt() / 10.0).toString()
