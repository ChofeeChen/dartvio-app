package com.dartvio.app.ui.practice.versus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardLayout
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.domain.versus.BoardHit
import com.dartvio.app.domain.versus.KeyboardLayout
import com.dartvio.app.domain.versus.Ring
import com.dartvio.app.ui.components.BoardTap
import com.dartvio.app.ui.components.drawBoardViewport
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.drawingColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 对抗练习的靶盘输入区（替代旧版的高瘦分区按键）。
 *
 * **点哪儿记哪儿**：落点判定走 [BoardGeometry.dartAt]，与 M12 视觉识别、对局页的
 * `BoardTapPad` 同源 —— 同一个位置，手点与机器算出的分必然一致。
 * 这带来一个旧键盘给不了的能力：在 BULL 类模式里，点牛心记 BULL50、点外圈记 BULL25，
 * 「落在哪个分区」由位置本身回答，而不是让用户在三张卡里先做归类。
 *
 * 盘上的圆点：
 * - 已记录的镖画在其**分区的代表点**（该环带中线 × 分区中心角）—— [BoardHit] 不携带
 *   精确坐标，代表点是示意，不是实测；
 * - 一镖未投时画一个提示点在**目标分区的中心位置**（牛类 = 牛心），给出「瞄哪儿」的参照。
 *
 * 下方快捷键只保留「不用看清盘也能按」的那几个：单目标模式的 S/D/T、牛类模式的
 * BULL25 / BULL50、以及任何模式都有的 MISS。全分区模式（RingRace / Shanghai）的
 * 扇区命中一律点盘 —— 那正是靶盘存在的意义。
 */
@Composable
internal fun VersusBoardInput(
    layout: KeyboardLayout,
    targetSector: Int?,
    dartsInRound: List<BoardHit>,
    enabled: Boolean,
    onHit: (BoardHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = drawingColors()
    val bullFocus = layout.sectors.isEmpty() && layout.bull

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Canvas(
                modifier = Modifier
                    .size(side)
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(layout) {
                                detectTapGestures { offset ->
                                    val squareSide = min(size.width, size.height).toFloat()
                                    val mm = BoardLayout.toBoardMm(squareSide, offset.x, offset.y)
                                    onHit(toBoardHit(BoardGeometry.dartAt(mm.x, mm.y)))
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                drawBoardViewport(
                    colors,
                    viewport = BoardViewport.Full,
                    markers = boardMarkers(layout, targetSector, dartsInRound),
                    showNumbers = true,
                )
                drawTargetHighlight(colors.Primary, targetSector, bullFocus)
            }
        }

        Spacer(Modifier.height(12.dp))

        val quick = quickHits(layout)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quick.forEach { spec ->
                QuickChip(
                    label = spec.label,
                    enabled = enabled,
                    onClick = { onHit(spec.hit) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(
                if (enabled) SurfaceVariantDark else SurfaceVariantDark.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (enabled) TextPrimaryDark else TextDisabledDark,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------
// 命中 → 坐标的换算（示意点只用同一套几何常量，不引入第二处靶面数字）。
// ---------------------------------------------------------------------------------

/** 点击位置 → [BoardHit]：与 [com.dartvio.app.domain.versus] 的判定模型对齐。 */
private fun toBoardHit(dart: Dart): BoardHit = when {
    dart.isMiss -> BoardHit.MISS
    dart.number == 25 && dart.isSingle -> BoardHit.OUTER_BULL
    dart.number == 25 && dart.isDouble -> BoardHit.INNER_BULL
    else -> BoardHit(
        sector = dart.number,
        ring = when (dart.multiplier) {
            2 -> Ring.DOUBLE
            3 -> Ring.TRIPLE
            else -> Ring.SINGLE
        }
    )
}

private data class QuickHit(val label: String, val hit: BoardHit)

/** 快捷键清单：见类注释 —— 只放「不用看清盘也能按」的选项。 */
private fun quickHits(layout: KeyboardLayout): List<QuickHit> = buildList {
    if (layout.sectors.size == 1) {
        val sector = layout.sectors.first()
        layout.rings.forEach { ring ->
            add(QuickHit("${ringChar(ring)}$sector", BoardHit(sector, ring)))
        }
    }
    if (layout.bull) {
        add(QuickHit("BULL25", BoardHit.OUTER_BULL))
        add(QuickHit("BULL50", BoardHit.INNER_BULL))
    }
    if (layout.miss) {
        add(QuickHit("MISS", BoardHit.MISS))
    }
}

private fun ringChar(ring: Ring): String = when (ring) {
    Ring.SINGLE -> "S"
    Ring.DOUBLE -> "D"
    Ring.TRIPLE -> "T"
    else -> ""
}

/** 盘上的圆点：已记录的镖各一个示意点；一镖未投时是目标中心的提示点。 */
private fun boardMarkers(
    layout: KeyboardLayout,
    targetSector: Int?,
    darts: List<BoardHit>,
): List<BoardTap> {
    val recorded = darts.mapNotNull { hit ->
        hit.representativePoint()?.let { (x, y) -> BoardTap(x.toFloat(), y.toFloat(), hit.toDart()) }
    }
    if (recorded.isNotEmpty()) return recorded

    // 提示点：牛类 = 牛心；扇区目标 = 该分区单倍区中心。
    val hint = when {
        layout.sectors.isEmpty() && layout.bull -> 0.0 to 0.0
        targetSector != null -> pointOfSector(targetSector, Ring.SINGLE)
        else -> null
    }
    return hint?.let { (x, y) -> listOf(BoardTap(x.toFloat(), y.toFloat(), Dart.MISS)) }
        ?: emptyList()
}

/** [BoardHit] 的代表点：环带中线 × 分区中心角；MISS 与无效扇区没有位置。 */
private fun BoardHit.representativePoint(): Pair<Double, Double>? = when (ring) {
    Ring.MISS -> null
    Ring.INNER_BULL -> 0.0 to 0.0
    Ring.OUTER_BULL ->
        0.0 to (BoardGeometry.INNER_BULL_RADIUS_MM + BoardGeometry.OUTER_BULL_RADIUS_MM) / 2.0
    else -> sector?.let { pointOfSector(it, ring) }
}

/** 分区内某个环带的中心点（靶面 mm 坐标，y 轴向下与 Canvas 一致）。 */
private fun pointOfSector(sector: Int, ring: Ring): Pair<Double, Double>? {
    val index = BoardGeometry.SECTOR_ORDER.indexOf(sector)
    if (index < 0) return null
    val radius = when (ring) {
        Ring.SINGLE ->
            (BoardGeometry.TRIPLE_OUTER_RADIUS_MM + BoardGeometry.DOUBLE_INNER_RADIUS_MM) / 2.0
        Ring.TRIPLE ->
            (BoardGeometry.TRIPLE_INNER_RADIUS_MM + BoardGeometry.TRIPLE_OUTER_RADIUS_MM) / 2.0
        Ring.DOUBLE ->
            (BoardGeometry.DOUBLE_INNER_RADIUS_MM + BoardGeometry.DOUBLE_OUTER_RADIUS_MM) / 2.0
        else -> return null
    }
    val rad = Math.toRadians(index * BoardGeometry.SECTOR_ANGLE_DEG)
    return sin(rad) * radius to cos(rad) * radius
}

/** 目标高亮：牛类 = 牛眼整块；扇区目标 = 该分区从牛眼外沿到双倍环外沿的整条楔形。 */
private fun DrawScope.drawTargetHighlight(
    accent: Color,
    targetSector: Int?,
    bullFocus: Boolean,
) {
    val pxPerMm = BoardViewport.Full.pxPerMm(size.width, size.height)
    fun mm(v: Double): Float = (v * pxPerMm).toFloat()
    val center = Offset(size.width / 2f, size.height / 2f)
    // 主题色取自 drawingColors()（@Composable），不能在 DrawScope 里直接读，只能传进来。
    val tint = accent.copy(alpha = 0.30f)

    if (bullFocus) {
        drawCircle(color = tint, radius = mm(BoardGeometry.OUTER_BULL_RADIUS_MM), center = center)
        return
    }
    val index = targetSector?.let { BoardGeometry.SECTOR_ORDER.indexOf(it) } ?: -1
    if (index < 0) return
    val rMid = mm(
        (BoardGeometry.OUTER_BULL_RADIUS_MM + BoardGeometry.DOUBLE_OUTER_RADIUS_MM) / 2.0
    )
    val width = mm(BoardGeometry.DOUBLE_OUTER_RADIUS_MM - BoardGeometry.OUTER_BULL_RADIUS_MM)
    drawArc(
        color = tint,
        startAngle = (index * BoardGeometry.SECTOR_ANGLE_DEG).toFloat() - 99f,
        sweepAngle = BoardGeometry.SECTOR_ANGLE_DEG.toFloat(),
        useCenter = false,
        topLeft = Offset(center.x - rMid, center.y - rMid),
        size = Size(rMid * 2f, rMid * 2f),
        style = Stroke(width = width),
    )
}
