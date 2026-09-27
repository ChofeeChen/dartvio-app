package com.dartvio.app.ui.components

import androidx.compose.ui.graphics.toArgb
import com.dartvio.app.ui.theme.DrawingColors
import com.dartvio.app.ui.theme.drawingColors
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.Toast
import androidx.compose.ui.unit.dp
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardLayout
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.ui.theme.BoardBlack
import com.dartvio.app.ui.theme.BoardCream
import com.dartvio.app.ui.theme.BoardGreen
import com.dartvio.app.ui.theme.BoardRed
import com.dartvio.app.ui.theme.BoardWire
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// 输入方式
// =====================================================================================

/**
 * 单镖的输入方式。
 *
 * - [KEYPAD]：原有键盘（S/D/T + 数字 + 牛眼），最快，但**不产生点位**；
 * - [BOARD]：在靶盘上点真实落点，产生点位（[BoardTap]）；
 * - [AI_VISION]：M12 视觉识别，预留位，当前不可选。
 */
enum class InputMode(val label: String, val enabled: Boolean = true) {
    KEYPAD("键盘"),
    BOARD("靶盘"),
    AI_VISION("AI视觉", enabled = false),
}

/**
 * 一次靶面点击的结果：位置（靶面 mm 坐标）+ 由该位置判定出的镖。
 *
 * 判定走 [BoardGeometry.dartAt]，与 M12 视觉识别**同源**：
 * 同一个落点，手点与机器点算出的分必然一致。
 */
data class BoardTap(
    val xMm: Float,
    val yMm: Float,
    val dart: Dart
)

/**
 * 输入方式切换键（下拉菜单）。
 *
 * 位置在两种输入方式下**完全一致**（输入区顶部右端），
 * 切换方式时其余按键不会位移，避免破坏键盘用户的肌肉记忆。
 */
@Composable
fun InputModeSwitch(
    current: InputMode,
    onSelect: (InputMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(modifier = modifier) {
        PadButton(
            label = "输入方式\n${current.label} \u25BE",
            enabled = true,
            accent = SurfaceElevated,
            textColor = TextPrimaryDark,
            modifier = Modifier.fillMaxSize(),
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InputMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (mode.enabled) mode.label else "${mode.label}（演示版 · 未实现）",
                            color = if (mode.enabled) TextPrimaryDark else TextDisabledDark
                        )
                    },
                    // 未实现那一项也**要能点中**：点下去毫无反应，别人只会以为菜单坏了。
                    // 点开了把它说清楚，比静默无响应强。
                    enabled = true,
                    onClick = {
                        expanded = false
                        if (mode.enabled) {
                            onSelect(mode)
                        } else {
                            Toast.makeText(
                                context,
                                "「${mode.label}」演示版未实现：视觉识别管线尚未接入，请先用「键盘」或「靶盘」",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }
}

// =====================================================================================
// 靶盘点击输入
// =====================================================================================

// mm ↔ px 的换算统一走 [BoardLayout]：UI（画）与判分（算）共用同一套映射，
// 「屏幕上的任一点 → 靶面 mm → Dart」的方向与比例只有一处定义。

/**
 * 靶盘点击输入盘。
 *
 * 尺寸：取「可用宽度与剩余高度的较小者」作正方形边长，使圆尽可能大；
 * 真正的 170mm 计分区半径为 `边长/2 减去数字环`，因此**两侧自然留出读数间隙**。
 *
 * 数字 1-20 一律**水平正向**绘制（不随扇区旋转），保证可读。
 */
@Composable
fun BoardTapPad(
    onTap: (BoardTap) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 本回合已记录的点位（靶面 mm 坐标），用于在盘上标出刚才点的位置。 */
    markers: List<BoardTap> = emptyList()
) {
    val colors = drawingColors()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value).dp
        Canvas(
            modifier = Modifier
                .size(side)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(enabled) {
                            detectTapGestures { offset ->
                                val squareSide = min(size.width, size.height).toFloat()
                                val mm = BoardLayout.toBoardMm(squareSide, offset.x, offset.y)
                                onTap(
                                    BoardTap(
                                        xMm = mm.x.toFloat(),
                                        yMm = mm.y.toFloat(),
                                        dart = BoardGeometry.dartAt(mm)
                                    )
                                )
                            }
                        }
                    } else Modifier
                )
        ) {
            drawBoardViewport(colors, viewport = BoardViewport.Full, markers = markers, showNumbers = true)
        }
    }
}

/**
 * 绘制靶盘（**任意视口**）。
 *
 * 全盘 = [BoardViewport.Full]（[BoardTapPad] 的调用方式，行为与抽取前逐位一致）；
 * 落点诊断的局部放大窗 = 自己的 `(center, span)` 视口 —— 同一函数、同一套几何常量，
 * 因此「屏幕上看到的环」与「点下去判出的环」不可能对不上。
 *
 * 局部窗下圆会画到画布之外，由 Canvas 自行裁剪；[showNumbers] 关掉 1-20 读数环，
 * 局部窗改画环名（由调用方叠一层，见 `ImpactBoardPreview`）。
 *
 * 扇区角度自 12 点方向起、顺时针度量（与 [BoardGeometry.sectorAt] 一致）：
 * Canvas 角度以 3 点方向为 0、顺时针为正，故「12 点方向」= -90°，
 * 第 k 个扇区起始角 = k*18 - 99（18° 为该扇区的张角）。
 *
 * @param markers 已记录的点位（靶面 mm 坐标），画成圆点标在盘上。
 * @param markerRadiusMm 圆点半径（靶面 mm）。全盘输入用默认值；局部放大窗里同一颗点
 *   会被放大很多倍，传小一号的值（见 `ImpactBoardPreview`）。
 */
internal fun DrawScope.drawBoardViewport(colors: DrawingColors,
    viewport: BoardViewport,
    markers: List<BoardTap> = emptyList(),
    showNumbers: Boolean = true,
    markerRadiusMm: Double = 4.5
) = drawBoardViewportIn(colors, viewport, size, markers, showNumbers, markerRadiusMm)

/**
 * [drawBoardViewport] 的显式尺寸版本。
 *
 * 存在的唯一理由：落点诊断的局部窗要把靶盘画进**预览矩形**（比控件本身小一圈，
 * 外面还留着 miss 条带），而 `DrawScope.size` 在 `clipRect` / `translate` 里**不会变小**。
 * 与其在调用方硬塞尺寸再重映射一遍坐标，不如把「画布尺寸」变成一个显式参数 ——
 * 换算仍然只走 [BoardViewport]，没有第二处几何。
 *
 * @param canvasSize 视口所映射的那块矩形（局部坐标原点在其左上角）。
 */
internal fun DrawScope.drawBoardViewportIn(colors: DrawingColors,
    viewport: BoardViewport,
    canvasSize: Size,
    markers: List<BoardTap> = emptyList(),
    showNumbers: Boolean = true,
    markerRadiusMm: Double = 4.5
) {
    val pxPerMm = viewport.pxPerMm(canvasSize.width, canvasSize.height)
    // 靶心（0, 0）在**本视口**下的画布位置：全盘时即画布中心，局部窗时偏到画布之外。
    val boardCenter = viewport.toCanvasPx(canvasSize.width, canvasSize.height, 0.0, 0.0)
    val cx = boardCenter.x.toFloat()
    val cy = boardCenter.y.toFloat()
    fun mm(v: Double): Float = (v * pxPerMm).toFloat()

    for (k in 0 until BoardGeometry.SECTOR_COUNT) {
        val startDeg = (k * BoardGeometry.SECTOR_ANGLE_DEG).toFloat() - 99f
        val sweepDeg = BoardGeometry.SECTOR_ANGLE_DEG.toFloat()
        val singleColor = if (k % 2 == 0) BoardBlack else BoardCream
        val ringColor = if (k % 2 == 0) BoardRed else BoardGreen
        ring(cx, cy, startDeg, sweepDeg, BoardGeometry.OUTER_BULL_RADIUS_MM, BoardGeometry.TRIPLE_INNER_RADIUS_MM, singleColor, pxPerMm)
        ring(cx, cy, startDeg, sweepDeg, BoardGeometry.TRIPLE_INNER_RADIUS_MM, BoardGeometry.TRIPLE_OUTER_RADIUS_MM, ringColor, pxPerMm)
        ring(cx, cy, startDeg, sweepDeg, BoardGeometry.TRIPLE_OUTER_RADIUS_MM, BoardGeometry.DOUBLE_INNER_RADIUS_MM, singleColor, pxPerMm)
        ring(cx, cy, startDeg, sweepDeg, BoardGeometry.DOUBLE_INNER_RADIUS_MM, BoardGeometry.DOUBLE_OUTER_RADIUS_MM, ringColor, pxPerMm)
    }

    // 牛眼：先画外圈再画内圈，覆盖式落到正确的同心关系。
    drawCircle(BoardGreen, radius = mm(BoardGeometry.OUTER_BULL_RADIUS_MM), center = Offset(cx, cy))
    drawCircle(BoardRed, radius = mm(BoardGeometry.INNER_BULL_RADIUS_MM), center = Offset(cx, cy))

    drawRingOutlines(cx, cy, pxPerMm)
    if (showNumbers) drawNumbers(colors, viewport, canvasSize, pxPerMm)

    // 已记录的点位。描边宽随点半径缩放（1.4 / 4.5 ≈ 0.31），小圆点不会反过来被粗边吞掉。
    markers.forEach { tap ->
        val at = viewport.toCanvasPx(canvasSize.width, canvasSize.height, tap.xMm.toDouble(), tap.yMm.toDouble())
        val px = at.x.toFloat()
        val py = at.y.toFloat()
        drawCircle(color = colors.Primary, radius = mm(markerRadiusMm), center = Offset(px, py))
        drawCircle(
            color = com.dartvio.app.ui.theme.BoardMarker,
            radius = mm(markerRadiusMm),
            center = Offset(px, py),
            style = Stroke(width = mm(markerRadiusMm * 0.31))
        )
    }
}

/** 扇区环形片段：用粗描边居中落在 [innerMm, outerMm] 上。 */
private fun DrawScope.ring(
    cx: Float,
    cy: Float,
    startDeg: Float,
    sweepDeg: Float,
    innerMm: Double,
    outerMm: Double,
    color: Color,
    pxPerMm: Float
) {
    val rMid = ((innerMm + outerMm) / 2.0 * pxPerMm).toFloat()
    val width = ((outerMm - innerMm) * pxPerMm).toFloat()
    val diameter = rMid * 2f
    drawArc(
        color = color,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(cx - rMid, cy - rMid),
        size = Size(diameter, diameter),
        style = Stroke(width = width)
    )
}

/**
 * 环线：只画单倍 / 三倍 / 双倍之间的**同心分界**。
 *
 * **不画径向线**：这里早先按每格「角平分线」画过一组辐条，那等于把每一格从中间劈开 ——
 * 既是多余的视觉噪声，又让分区看起来比实际窄，点选时反而更容易犹豫。
 * 相邻分区的黑 / 米色交替本身就已经划出了分界。
 */
private fun DrawScope.drawRingOutlines(cx: Float, cy: Float, pxPerMm: Float) {
    val wireWidth = (0.7 * pxPerMm).toFloat()
    listOf(
        BoardGeometry.OUTER_BULL_RADIUS_MM,
        BoardGeometry.TRIPLE_INNER_RADIUS_MM,
        BoardGeometry.TRIPLE_OUTER_RADIUS_MM,
        BoardGeometry.DOUBLE_INNER_RADIUS_MM,
        BoardGeometry.DOUBLE_OUTER_RADIUS_MM
    ).forEach { rMm ->
        drawCircle(
            color = BoardWire,
            radius = (rMm * pxPerMm).toFloat(),
            center = Offset(cx, cy),
            style = Stroke(width = wireWidth)
        )
    }
}

/**
 * 数字环：1-20 **水平正向**画在双倍环之外，便于读数 ——
 * 这正是「数字不旋转」要求的落点：旋转会让 9/6、16/19 在左右两侧难以辨认。
 */
private fun DrawScope.drawNumbers(colors: DrawingColors, viewport: BoardViewport, canvasSize: Size, pxPerMm: Float) {
    val paint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = colors.TextPrimaryDark.toArgb()
        textSize = BoardLayout.NUMBER_FONT_MM.toFloat() * pxPerMm
    }
    val baselineShift = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    for (k in 0 until BoardGeometry.SECTOR_COUNT) {
        val rad = Math.toRadians(k * BoardGeometry.SECTOR_ANGLE_DEG)
        val anchor = viewport.toCanvasPx(
            canvasSize.width,
            canvasSize.height,
            sin(rad) * BoardLayout.NUMBER_RADIUS_MM,
            cos(rad) * BoardLayout.NUMBER_RADIUS_MM
        )
        drawContext.canvas.nativeCanvas.drawText(
            BoardGeometry.SECTOR_ORDER[k].toString(),
            anchor.x.toFloat(),
            anchor.y.toFloat() + baselineShift,
            paint
        )
    }
}
