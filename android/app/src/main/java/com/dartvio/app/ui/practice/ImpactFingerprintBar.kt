package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.impact.ImpactFingerprint
import com.dartvio.app.domain.impact.ImpactFingerprintCalculator
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * ⑧ 投掷指纹（V1.4 §4.7 / §5.4-⑧ / A-IMP-28）。
 *
 * 这是**画像**，不是成绩单。因此这里：
 * - 不出现任何评分 / 星级 / 百分位 / 排名；
 * - `n < 100`（[ImpactFingerprintCalculator.MIN_DOMINANT_N]）时**不给**「你属于哪一类」的结论，
 *   只画两条占比条（`dominant == null`）；
 * - `n < 30` 时整块不渲染。
 *
 * 占比是**尺度无关**的，所以它允许跨目标、跨档位汇总；但出框率必须同档位才有意义，
 * 这一点由调用方（[ImpactReportViewModel]）保证。
 */
@Composable
fun ImpactFingerprintBar(fingerprint: ImpactFingerprint?, modifier: Modifier = Modifier) {
    if (fingerprint == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("投掷指纹", color = TextPrimaryDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "径向 ${(fingerprint.radialShare * 100).toInt()}%　·　" +
                "切向 ${(fingerprint.tangentShare * 100).toInt()}%　·　" +
                "出框 ${(fingerprint.outShare * 100).toInt()}%（n=${fingerprint.n}）",
            color = TextSecondaryDark,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        ShareBar(label = "径向", share = fingerprint.radialShare, color = Accent)
        Spacer(Modifier.height(4.dp))
        ShareBar(label = "切向", share = fingerprint.tangentShare, color = Secondary)
        Spacer(Modifier.height(8.dp))
        Text(
            fingerprint.portrait(),
            color = TextPrimaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (fingerprint.dominant == null) {
                "到 ${ImpactFingerprintCalculator.MIN_DOMINANT_N} 镖才给「你属于哪一类」的结论；现在只是占比。"
            } else {
                "指纹只描述误差的方向构成，不评分、不排名 —— 它变化通常意味着你的动作在变，而不是水平在变。"
            },
            color = TextSecondaryDark,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ShareBar(label: String, share: Double, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextSecondaryDark,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(SurfaceVariantDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
    }
}
