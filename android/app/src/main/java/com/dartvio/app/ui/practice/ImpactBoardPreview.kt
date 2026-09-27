package com.dartvio.app.ui.practice

import androidx.compose.ui.graphics.toArgb
import com.dartvio.app.ui.theme.DrawingColors
import com.dartvio.app.ui.theme.drawingColors
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.impact.ImpactFrames
import com.dartvio.app.domain.impact.ImpactMissBand
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentKind
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.ui.components.BoardTap
import com.dartvio.app.ui.components.drawBoardViewportIn
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning

/**
 * 局部等比放大预览（M11 落点诊断的核心交互面）。
 *
 * 三件事必须同时成立，否则这一页就没有意义：
 * ① **等比**：`pxPerMm` 两轴同一个值（[BoardViewport]），窗口塞不满矩形时**居中留边**而不拉伸 ——
 *    径向一旦被压扁，「偏内 / 偏外」就变成系统性缩放错误（设计 §2.1.1）；
 * ② **朝向与对局页一致、不镜像**：坐标翻转只发生在 [BoardViewport] 一处；
 * ③ **一次点击 = 一镖**：不做报分、不做二次确认，误点靠「撤销上一镖」兜底（设计 §5.3-10）。
 *
 * 预览矩形**外侧**是四条 miss 条带（内半 = 轻微出框、外半 = 远出框 / 靶外），
 * 四角留 `20×20 dp` 不响应以免斜向歧义 —— 命中判定全在 [ImpactMissBand] 里，本文件只管画。
 *
 * 触控全部由**整块控件**那一层接收（条带在预览矩形之外，内层矩形收不到），
 * 因此命中判定与绘制共用同一套 `insetPx` 几何。
 *
 * @param markers 本轮已记录的点位（靶面 mm），画在盘上。
 * @param lastTap 最近一镖，额外画一个十字准星（「上一镖」的定位参照）。
 * @param onPoint 点击结果：`outBand == 0` 表示窗内点，否则是条带命中。
 */
@Composable
fun ImpactBoardPreview(
    target: IntentTarget,
    spanMm: Double,
    markers: List<BoardTap>,
    modifier: Modifier = Modifier,
    lastTap: BoardTap? = null,
    onPoint: (xMm: Double, yMm: Double, outBand: Int, outLevel: Int) -> Unit
) {
    val Accent = com.dartvio.app.ui.theme.Accent
    val Divider = com.dartvio.app.ui.theme.Divider
    val SurfaceVariantDark = com.dartvio.app.ui.theme.SurfaceVariantDark
    val TextPrimaryDark = com.dartvio.app.ui.theme.TextPrimaryDark
    val TextSecondaryDark = com.dartvio.app.ui.theme.TextSecondaryDark
    val Warning = com.dartvio.app.ui.theme.Warning

    val colors = drawingColors()

    val density = LocalDensity.current.density
    // 条带宽度 + 4 dp 间隔线：预览矩形相对整个控件的内缩量。
    val bandPx = ImpactMissBand.BAND_MIN_DP * density
    val gapPx = 4f * density
    val insetPx = bandPx + gapPx
    val viewport = remember(target, spanMm) { ImpactWindow.viewportOf(target, spanMm) }

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(target, spanMm, insetPx, density) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val innerW = w - 2 * insetPx
                        val innerH = h - 2 * insetPx
                        if (innerW <= 0f || innerH <= 0f) return@detectTapGestures
                        // 条带判定用**整块控件**坐标（条带就贴在控件边缘）。
                        val band = ImpactMissBand.bandAt(offset.x, offset.y, w, h, density)
                        // mm 换算用**预览矩形**坐标：先减去内缩量。
                        val mm = viewport.toBoardMm(innerW, innerH, offset.x - insetPx, offset.y - insetPx)
                        if (band != null) {
                            val p = ImpactMissBand.missPointMm(viewport, mm.x, mm.y, band)
                            onPoint(p.x, p.y, band.outBand, band.outLevel)
                            return@detectTapGestures
                        }
                        val insideX = offset.x >= insetPx && offset.x <= w - insetPx
                        val insideY = offset.y >= insetPx && offset.y <= h - insetPx
                        // 四角角隙 + 4 dp 间隔线：都不响应（不记 miss、也不记点）。
                        if (insideX && insideY) onPoint(mm.x, mm.y, 0, 0)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val innerW = w - 2 * insetPx
            val innerH = h - 2 * insetPx
            if (innerW <= 0f || innerH <= 0f) return@Canvas
            drawMissBands(colors, w, h, bandPx, target)
            clipRect(
                left = insetPx,
                top = insetPx,
                right = insetPx + innerW,
                bottom = insetPx + innerH
            ) {
                translate(left = insetPx, top = insetPx) {
                    drawPreview(colors, Size(innerW, innerH), viewport, target, markers, lastTap)
                }
            }
            // 预览矩形描边：条带与主区之间的 4 dp 间隔线（设计 §5.3-9 的误触防护）。
            drawRect(
                color = Divider,
                topLeft = Offset(insetPx, insetPx),
                size = Size(innerW, innerH),
                style = Stroke(width = gapPx)
            )
        }

        // 条带短标签：由几何推出该方向是「偏内 / 偏外 / 切向哪一侧」，不硬编码。
        BandChip(target, 1, Modifier.align(Alignment.TopCenter))
        BandChip(target, 3, Modifier.align(Alignment.BottomCenter))
        BandChip(target, 4, Modifier.align(Alignment.CenterStart))
        BandChip(target, 2, Modifier.align(Alignment.CenterEnd))
    }
}

/** 靶面之外的中性灰（「靶外」必须看得见，不能被静默裁掉）。 */
// Outside color is captured from the current theme.

/**
 * 画四条 miss 条带。
 *
 * 条带固定在**屏幕四边**、预览也不旋转（设计 §5.3-9）：用户抬头看靶、低头点屏，
 * 方向感才一致；把「径向」硬映射成「上下」在双倍区目标上会直接骗人。
 * 内半 / 外半用一条中线分档（内半 = 轻微出框 `outLevel = 1`，外半 = 远出框 `outLevel = 2`），
 * 也就是「用条带内部的位置表达程度」，不额外增加操作。
 */
private fun DrawScope.drawMissBands(colors: DrawingColors, w: Float, h: Float, bandPx: Float, target: IntentTarget) {
    // 上 / 右 / 下 / 左（屏幕方向 1 / 2 / 3 / 4）。
    val bands = listOf(
        1 to Rect(0f, 0f, w, bandPx),
        2 to Rect(w - bandPx, 0f, w, h),
        3 to Rect(0f, h - bandPx, w, h),
        4 to Rect(0f, 0f, bandPx, h)
    )
    bands.forEach { (band, rect) ->
        val semantic = ImpactFrames.semanticOf(target, band)
        val tint = when (semantic) {
            ImpactFrames.BandSemantic.OUTWARD -> colors.Warning.copy(alpha = 0.18f)
            ImpactFrames.BandSemantic.INWARD -> colors.Accent.copy(alpha = 0.14f)
            else -> colors.SurfaceVariantDark.copy(alpha = 0.55f)
        }
        drawRect(color = tint, topLeft = rect.topLeft, size = rect.size)
        val width = 1.dp.toPx()
        when (band) {
            1 -> drawLine(colors.Divider, Offset(0f, bandPx / 2f), Offset(w, bandPx / 2f), width)
            3 -> drawLine(colors.Divider, Offset(0f, h - bandPx / 2f), Offset(w, h - bandPx / 2f), width)
            4 -> drawLine(colors.Divider, Offset(bandPx / 2f, 0f), Offset(bandPx / 2f, h), width)
            else -> drawLine(colors.Divider, Offset(w - bandPx / 2f, 0f), Offset(w - bandPx / 2f, h), width)
        }
    }
    // 四角 20×20 dp 非响应区：用背景色盖掉，让禁区肉眼可见。
    val corner = ImpactMissBand.CORNER_GAP_DP.dp.toPx()
    listOf(
        Offset(0f, 0f),
        Offset(w - corner, 0f),
        Offset(0f, h - corner),
        Offset(w - corner, h - corner)
    ).forEach { at ->
        drawRect(colors.SurfaceVariantDark, topLeft = at, size = Size(corner, corner))
    }
}

/**
 * 预览矩形内部（**局部坐标**：原点在预览矩形左上角）。
 *
 * 靶面本体直接复用对局页那一个绘制函数（[drawBoardViewportIn]），只是换成这个窗口的视口、
 * 并关掉 1-20 读数环（局部放大后环名才是线索，数字环反而挤占窗口）。
 */
private fun DrawScope.drawPreview(colors: DrawingColors, 
    inner: Size,
    viewport: BoardViewport,
    target: IntentTarget,
    markers: List<BoardTap>,
    lastTap: BoardTap?
) {
    val pxPerMm = viewport.pxPerMm(inner.width, inner.height)
    val board = viewport.toCanvasPx(inner.width, inner.height, 0.0, 0.0)
    val center = Offset(board.x.toFloat(), board.y.toFloat())
    val outerR = (BoardGeometry.DOUBLE_OUTER_RADIUS_MM * pxPerMm).toFloat()

    // ① 靶外：把「170 mm 之外」填成中性灰。
    //    用 EvenOdd 环带而不是「先画灰盘再覆盖」：绘制顺序不再影响结果，
    //    盘面上任何东西都不会被这层灰盖住。
    val huge = (inner.width + inner.height) * 1.5f
    val annulus = Path().apply {
        addOval(Rect(center, huge))
        addOval(Rect(center, outerR))
        fillType = PathFillType.EvenOdd
    }
    drawPath(annulus, colors.SurfaceVariantDark)

    // ② 靶面：同一函数、同一几何常量（跨视图一致性由此保证）。
    //    圆点半径取全盘默认（4.5mm）的一半：这个窗口是等比放大窗，全盘尺寸的点
    //    到这里会跟着放大，盖住环带分界；点小一号才像「落点示意」而不是「路障」。
    drawBoardViewportIn(
        colors,
        viewport = viewport,
        canvasSize = inner,
        markers = markers,
        showNumbers = false,
        markerRadiusMm = 2.25
    )

    // ③ 靶面外沿参考圆**已删除**：它是「靶面到这里为止」的提示，但盘面本身
    //    的配色边界已经把这件事说清楚了，多一圈灰线只是让目标块周围多一层框。
    //    脱靶依然看得见 —— 靶外那一整片中性灰（①）就是它的表达（2026-09-26 反馈）。

    // ④ 目标块高亮：填充 + 描边两层（见 [drawTargetHighlight]）。
    drawTargetHighlight(colors, center, pxPerMm, target)

    // ⑤ 环名（T20 / S20 / D20）**已删除**：这一版的窗口是「看自己偏了多少」，
    //    不是「认这是哪个区」—— 目标是什么由页面头部的「目标 T20」那一行回答。
    //    三个带底片的数字贴在目标正上方，恰好盖住最该看清的区域（2026-09-26 反馈）。

    // ⑥ 上一镖：十字准星（区别于普通圆点）。
    if (lastTap != null) {
        val at = viewport.toCanvasPx(inner.width, inner.height, lastTap.xMm.toDouble(), lastTap.yMm.toDouble())
        val x = at.x.toFloat()
        val y = at.y.toFloat()
        val arm = 7.dp.toPx()
        val width = 1.6.dp.toPx()
        drawLine(com.dartvio.app.ui.theme.BoardMarker, Offset(x - arm, y), Offset(x + arm, y), width * 2f)
        drawLine(com.dartvio.app.ui.theme.BoardMarker, Offset(x, y - arm), Offset(x, y + arm), width * 2f)
        drawLine(colors.Accent, Offset(x - arm, y), Offset(x + arm, y), width)
        drawLine(colors.Accent, Offset(x, y - arm), Offset(x, y + arm), width)
    }
}

/**
 * 目标分区高亮：**半透明填充 + 一圈描边**，而不是只描边。
 *
 * 只描边的问题：局部放大之后，一条 2dp 的弧线贴在同样饱和度的盘面色块上，
 * 在户外光下几乎读不出来 —— 而「我现在瞄的是哪儿」是这一页唯一必须先看清的事。
 * 加一层低透明度填充后，目标块是整片盘面里唯一被点亮的一块，扫一眼就能定位，
 * 不需要去找那根线（2026-09-26 反馈）。
 *
 * 起止角与 `BoardTapPad` 的扇区角度口径一致（`k*18 − 99`）。
 */
private fun DrawScope.drawTargetHighlight(colors: DrawingColors, center: Offset, pxPerMm: Float, target: IntentTarget) {
    val fill = colors.Accent.copy(alpha = 0.22f)
    val strokeWidth = 2.5.dp.toPx()

    if (target.kind == IntentKind.BULL) {
        val r = (BoardGeometry.INNER_BULL_RADIUS_MM * pxPerMm).toFloat()
        drawCircle(color = fill, radius = r, center = center)
        drawCircle(color = colors.Accent, radius = r, center = center, style = Stroke(width = strokeWidth))
        return
    }
    val index = target.sectorIndex()
    if (index < 0) return
    val radii = when (target.kind) {
        IntentKind.TRIPLE -> BoardGeometry.TRIPLE_INNER_RADIUS_MM to BoardGeometry.TRIPLE_OUTER_RADIUS_MM
        IntentKind.DOUBLE -> BoardGeometry.DOUBLE_INNER_RADIUS_MM to BoardGeometry.DOUBLE_OUTER_RADIUS_MM
        IntentKind.SINGLE_OUTER -> BoardGeometry.TRIPLE_OUTER_RADIUS_MM to BoardGeometry.DOUBLE_INNER_RADIUS_MM
        IntentKind.BULL -> return
    }
    val rIn = (radii.first * pxPerMm).toFloat()
    val rOut = (radii.second * pxPerMm).toFloat()
    val start = (index * BoardGeometry.SECTOR_ANGLE_DEG).toFloat() - 99f
    val sweep = BoardGeometry.SECTOR_ANGLE_DEG.toFloat()

    // 环带扇区 = 外弧正向 + 内弧反向再闭合；用 drawArc(useCenter=true) 会得到一整块饼，
    // 把比目标更靠内的区域也一起点亮，那是错的。
    val band = Path().apply {
        arcTo(
            rect = Rect(center, rOut),
            startAngleDegrees = start,
            sweepAngleDegrees = sweep,
            forceMoveTo = true
        )
        arcTo(
            rect = Rect(center, rIn),
            startAngleDegrees = start + sweep,
            sweepAngleDegrees = -sweep,
            forceMoveTo = false
        )
        close()
    }
    drawPath(band, fill)
    drawPath(band, colors.Accent, style = Stroke(width = strokeWidth))
}

/**
 * 条带短标签：`偏内 / 偏外 / 偏 N 侧` 由 [ImpactFrames.semanticOf] 与
 * [IntentTarget.neighbors] 推出（几何说话）。更长的「窗外是什么」放在预览下方图例里，
 * 免得把 40 dp 的条带塞满字。
 */
@Composable
private fun BandChip(target: IntentTarget, outBand: Int, modifier: Modifier) {
    val tangent = ImpactMissBand.tangentLabel(target, outBand)
    val short = tangent.ifBlank { ImpactFrames.semanticOf(target, outBand).label }
    Text(
        text = short,
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .background(SurfaceElevated.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
            .border(1.dp, Divider, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = TextPrimaryDark,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}
