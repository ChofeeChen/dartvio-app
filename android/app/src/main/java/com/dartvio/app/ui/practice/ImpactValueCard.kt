package com.dartvio.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.impact.CevOption
import com.dartvio.app.domain.impact.CevResult
import com.dartvio.app.domain.impact.FinishRate
import com.dartvio.app.domain.impact.ImpactValue
import com.dartvio.app.domain.impact.IntentKind
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * ⑦ 目标收益对比（V1.4 §4.6 / §5.4-⑦ / A-IMP-27）。
 *
 * 展示纪律（不是排版偏好，是防误导）：
 * 1. **首屏只比 2 个**：当前目标 vs 最优候选。一次给 9 个候选，用户会挑那个样本最少的；
 * 2. **候选只在同 `IntentKind` 内比**（由 [ImpactValue] 保证）—— T 区与 D 区不同尺；
 * 3. 收益 **< [ImpactValue.SHOW_GAIN_RATIO]** 时明确说「不值得改」，
 *    而不是把 0.3 分/镖的差异包装成「潜在提升」；
 * 4. 结镖双倍区目标改看**结镖率**（命中该双倍区的比例），因为收镖的价值不在平均得分；
 * 5. 口径说明里必须写**不含适应期**。
 */
@Composable
fun ImpactValueCard(
    cev: CevResult?,
    finishRates: List<FinishRate>,
    target: IntentTarget,
    modifier: Modifier = Modifier
) {
    if (cev == null) return
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("改瞄点值不值", color = TextPrimaryDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        if (cev.n < ImpactValue.MIN_CEV_N) {
            Text(
                "本轮 ${cev.n} 镖，不足 ${ImpactValue.MIN_CEV_N} 镖 —— 不出收益对比（样本不够时任何「提升」都是运气）。",
                color = TextSecondaryDark,
                fontSize = 12.sp
            )
            return@Column
        }

        Text(
            if (cev.worthShowing) {
                "有候选值得一试（收益 ≥ ${(ImpactValue.SHOW_GAIN_RATIO * 100).toInt()}%）"
            } else {
                "最优候选相对当前不足 ${(ImpactValue.SHOW_GAIN_RATIO * 100).toInt()}% —— 不值得为它改瞄点。"
            },
            color = if (cev.worthShowing) Accent else TextSecondaryDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        cev.shortlist.forEach { option ->
            CevRow(
                option = option,
                baseline = cev.baseline.cev,
                isBest = option.target == cev.best.target && cev.worthShowing
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "配对检验：${if (cev.significant) "差异显著" else "还看不出显著差异"}（逐镖取新旧目标下的得分差）" +
                " · n=${cev.n}",
            color = TextSecondaryDark,
            fontSize = 11.sp
        )

        if (target.kind == IntentKind.DOUBLE && finishRates.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("结镖率（命中该双倍区的比例，分母含全部出框镖）", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            finishRates.take(4).forEach { rate ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(rate.label, color = TextPrimaryDark, fontSize = 12.sp)
                    Text(
                        "${(rate.rate * 100).toInt()}%（${rate.hits}/${rate.n}）",
                        color = if (rate.target == target) Accent else TextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                if (expanded) "收起候选" else "查看更多候选（${(cev.options.size - cev.shortlist.size).coerceAtLeast(0)} 个）",
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }
        if (expanded) {
            cev.options.filter { option -> cev.shortlist.none { it.target == option.target } }
                .forEach { option ->
                    CevRow(option = option, baseline = cev.baseline.cev, isBest = false)
                }
        }

        Spacer(Modifier.height(6.dp))
        Text(cev.note, color = TextSecondaryDark, fontSize = 11.sp)
    }
}

@Composable
private fun CevRow(option: CevOption, baseline: Double, isBest: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            option.label,
            color = if (isBest) Accent else TextPrimaryDark,
            fontSize = 13.sp,
            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            "${mm(option.cev)} ± ${mm(option.ci95)} 分/镖" +
                if (option.cev > baseline) "　(+${mm(option.cev - baseline)})" else "",
            color = if (isBest) Accent else Secondary,
            fontSize = 13.sp
        )
    }
}

private fun mm(value: Double): String = ((value * 10).toInt() / 10.0).toString()
