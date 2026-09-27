package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 结镖训练三页共用的版式原语。
 *
 * 抽出来的唯一理由：**这三页的外边距、卡片圆角、选项等宽必须一致** ——
 * 入口页选完难度进训练页，卡片忽然换个圆角或间距，会让人以为是另一套功能。
 * 规则与计时逻辑一律不在这里，本文件只管「长什么样」。
 */

/** 页面统一的横向留白。 */
val RushPagePadding = 16.dp

/** 卡片内上下留白（比页面留白小一档，靠卡片自身的圆角区分层级）。 */
private val CardInnerPadding = 14.dp

/**
 * 分区卡片。
 *
 * `note` 是卡片标题下的那一行小字（约束说明 / 口径提示），
 * 把它放在标题旁边而不是塞进内容区 —— 内容区只放**用户要做的事**。
 */
@Composable
fun RushCard(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(CardInnerPadding)
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark,
        )
        if (!note.isNullOrBlank()) {
            Text(
                note,
                fontSize = 11.sp,
                color = TextSecondaryDark,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        content()
    }
}

/**
 * 等宽选项行。
 *
 * **每个选项 `weight(1f)`**：这样 2 项 / 4 项都不会出现最后一项短一截的参差，
 * 文字居中、单行不换行（长文案在 `onSelect` 之外由调用方保证简短）。
 */
@Composable
fun RushChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) Accent.copy(alpha = 0.16f) else SurfaceElevated,
                        RoundedCornerShape(10.dp)
                    )
                    .then(
                        if (selected) Modifier.border(1.5.dp, Accent, RoundedCornerShape(10.dp))
                        else Modifier.border(1.dp, Divider, RoundedCornerShape(10.dp))
                    )
                    .clickable(enabled = enabled) { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        !enabled -> TextDisabledDark
                        selected -> Accent
                        else -> TextPrimaryDark
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 主操作按钮（一屏只有一个）。 */
@Composable
fun RushPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (enabled) Primary else SurfaceElevated,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else TextDisabledDark,
        )
    }
}

/** 次操作按钮（跳过 / 重试 / 结束）。 */
@Composable
fun RushSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .background(SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextPrimaryDark else TextDisabledDark,
        )
    }
}
