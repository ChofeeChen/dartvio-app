package com.dartvio.app.ui.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.impact.InterventionReport
import com.dartvio.app.domain.impact.SessionSummary
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ⑨ 干预对照（V1.4 §4.8-⑥ / §5.4-⑨ / A-IMP-31）。
 *
 * 措辞门（硬约束，写在 [InterventionReport.footnote] 与句子里）：
 * - 只说「标注过的轮次」与其它轮次的数据差异，**不出现因果词**；
 * - 有效轮次不足（< 3 轮或 < 30 镖）时**只列数据、不给对比句** —— 宁可不说，也不要给一句
 *   似是而非的「所以改了站位有效」。
 *
 * **本组件不带标题**：调用方把它放进 `SectionCard("干预对照")`，
 * 早先这里自己又写一遍同样四个字，页面上是「干预对照」出现两次（2026-09-26 反馈）。
 * 卡内的左右留白也留给 SectionCard —— 自己再垫 16dp，表头就比其它卡的正文更靠里。
 */
@Composable
fun ImpactInterventionTable(report: InterventionReport?, modifier: Modifier = Modifier) {
    if (report == null || report.rows.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Caption("还没有可对照的历史轮次。")
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Caption("把「你标注过的轮次」与其它轮次的数据排在一起看，仅此而已。")
        Spacer(Modifier.height(8.dp))

        HeaderRow()
        report.rows.asReversed().take(10).forEach { row ->
            Spacer(Modifier.height(4.dp))
            SummaryRow(row)
        }

        if (report.comparisons.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            report.comparisons.forEach { comparison ->
                Text(
                    comparison.text
                        ?: "「${comparison.note}」：${comparison.rounds} 轮 / ${comparison.darts} 镖 —— 有效轮次不足，只列数据。",
                    color = if (comparison.text != null) TextPrimaryDark else TextSecondaryDark,
                    fontSize = BODY_SIZE
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(report.footnote, color = TextSecondaryDark, fontSize = CAPTION_SIZE)
    }
}

/** 与报告页同一套字号（正文 / 表注），不在此处自成一档。 */
private val BODY_SIZE = 12.sp
private val CAPTION_SIZE = 11.sp

@Composable
private fun Caption(text: String) {
    Text(text, color = TextSecondaryDark, fontSize = CAPTION_SIZE)
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Cell("时间", 0.30f, TextSecondaryDark, FontWeight.Bold)
        Cell("干预", 0.26f, TextSecondaryDark, FontWeight.Bold)
        Cell("n", 0.10f, TextSecondaryDark, FontWeight.Bold)
        Cell("偏", 0.12f, TextSecondaryDark, FontWeight.Bold)
        Cell("R95", 0.12f, TextSecondaryDark, FontWeight.Bold)
        Cell("达成", 0.10f, TextSecondaryDark, FontWeight.Bold)
    }
}

@Composable
private fun SummaryRow(row: SessionSummary) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cell(timeText(row.hitAt), 0.30f, TextSecondaryDark, FontWeight.Normal)
        Cell(row.note.ifBlank { "—" }, 0.26f, if (row.note.isBlank()) TextSecondaryDark else Accent, FontWeight.Bold)
        Cell("${row.n}", 0.10f, TextPrimaryDark, FontWeight.Normal)
        Cell(mm(row.bias), 0.12f, TextPrimaryDark, FontWeight.Normal)
        Cell(mm(row.r95), 0.12f, Secondary, FontWeight.Normal)
        Cell(
            text = when (row.achieved) {
                true -> "达标"
                false -> "未达"
                null -> "—"
            },
            weight = 0.10f,
            color = when (row.achieved) {
                true -> Success
                false -> Error
                null -> TextSecondaryDark
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: FontWeight
) {
    Text(
        text,
        color = color,
        fontSize = CAPTION_SIZE,
        fontWeight = fontWeight,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}

private val timeFormat = SimpleDateFormat("M/d HH:mm", Locale.getDefault())

private fun timeText(epoch: Long): String = timeFormat.format(Date(epoch))

private fun mm(value: Double): String = ((value * 10).toInt() / 10.0).toString()
