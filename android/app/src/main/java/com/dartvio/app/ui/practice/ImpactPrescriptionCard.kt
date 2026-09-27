package com.dartvio.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.impact.Prescription
import com.dartvio.app.domain.impact.PrescriptionMetric
import com.dartvio.app.domain.impact.Verdict
import com.dartvio.app.domain.impact.VerdictResult
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * ⑤ 处方卡（V1.4 §5.4-⑤ / A-IMP-24、A-IMP-25）。
 *
 * 这一张卡回答三个问题，顺序不能换：
 * 1. **这轮目标是什么**（口径 + 阈值）；
 * 2. **达成没有**（`现算`，不落库 —— 落库会让「改了阈值」变成篡改历史）；
 * 3. **这个达成是不是真的**（达成但散布变大 ⇒ 追问，而不是庆祝）。
 *
 * 没设目标时**整卡不渲染**（不是渲染成空卡），否则「没设目标」会被读成「没达成」。
 */
@Composable
fun ImpactPrescriptionCard(
    prescription: Prescription?,
    verdict: VerdictResult?,
    modifier: Modifier = Modifier
) {
    if (prescription == null) return

    val verdictColor = when (verdict?.verdict) {
        Verdict.ACHIEVED -> Success
        Verdict.MISSED -> Error
        Verdict.INSUFFICIENT -> Secondary
        else -> TextSecondaryDark
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("本轮目标", color = TextPrimaryDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "${prescription.metric.label} ${
                if (prescription.metric.lowerIsBetter) "≤" else "≥"
            } ${prescription.targetText()}",
            color = Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        verdict?.let {
            Text(
                "${verdictLabel(it.verdict)}　${it.note}",
                color = verdictColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        verdict?.crossWarning?.let { warning ->
            Spacer(Modifier.height(6.dp))
            Text(warning, color = Error, fontSize = 12.sp)
        }
        verdict?.exitHint?.let { hint ->
            Spacer(Modifier.height(6.dp))
            Text(hint, color = Secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(metricExplainer(prescription.metric), color = TextSecondaryDark, fontSize = 11.sp)
    }
}

private fun verdictLabel(verdict: Verdict): String = when (verdict) {
    Verdict.ACHIEVED -> "[达标]"
    Verdict.MISSED -> "[未达标]"
    Verdict.INSUFFICIENT -> "[样本不足]"
    Verdict.NOT_SET -> "[未设]"
}

/** 每个口径只说一件事：它到底在衡量什么。多一句就会变成「综合评分」。 */
private fun metricExplainer(metric: PrescriptionMetric): String = when (metric) {
    PrescriptionMetric.BIAS_ABS ->
        "平均偏差 = 所有镖的误差向量取平均后再求长度。它衡量「你整体偏在哪」，不是「你抖不抖」。"
    PrescriptionMetric.R95 ->
        "R95 = 95% 的窗内落点都落在半径这么长的圆内。它衡量散布（手感一致性），出框镖不计入。"
    PrescriptionMetric.RMSE ->
        "综合偏离 = 每个落点到目标的距离的均方根，把「偏」和「散」合成一个数；想看单一问题时别用它。"
    PrescriptionMetric.HIT_RATE ->
        "命中率 = 命中目标区的镖数 / 本轮全部镖数（**包含出框**）。目标值越高越好。"
    PrescriptionMetric.OUT_RATE ->
        "出框率 = 落在当前窗口之外的镖数 / 本轮全部镖数。目标值越低越好；它先告诉你「窗口选得对不对」。"
}
