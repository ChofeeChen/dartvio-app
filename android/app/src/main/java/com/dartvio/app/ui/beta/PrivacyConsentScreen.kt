package com.dartvio.app.ui.beta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.beta.BetaPrivacyPolicy
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/*
 * **首启隐私说明**（必须由用户明确选择后才进 App）。
 *
 * ## 为什么是全屏而不是一个小对话框
 *
 * 「同意」应该是主界面之前的一个**独立动作**，不是弹在半透明遮罩上的一次性确认：
 * 叠在 UI 上的小弹窗会被当成「下一步？是/否」顺手点掉，那样既没达到告知的效果，
 * 又留下一条我们自己都不敢用（没法证明征得同意）的记录。
 *
 * ## 为什么「不同意」是一个**同级别**的按钮
 *
 * 本 App 唯一离开设备的数据是可开关的匿名统计，它不是服务所必需的个人信息处理，
 * 因此按 PIPL 第十六条的口径，不能以拒绝为由拒绝提供服务。所以这里不写「退出 App」，
 * 而写「不同意，只在本机使用」—— 并且这条选择**同样会被记住**，不会每次启动来烦。
 *
 * ## 摘要与全文为什么要分两层
 *
 * 摘要是大多数人实际会读的全部内容（四行，读完知道会发生什么）；
 * 全文是给认真看的人和合规核对用的。把全文直接铺开，结果是没人读 ——
 * 一份没人读的政策等于没有政策。
 */
@Composable
fun PrivacyConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fullText by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                "先看两句话",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "隐私政策 ${BetaPrivacyPolicy.id} · 试用版专项",
                fontSize = 12.sp,
                color = TextSecondaryDark,
            )
            Spacer(Modifier.height(20.dp))

            Panel {
                BetaPrivacyPolicy.summary.forEach { line ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("·", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                        Spacer(Modifier.width(8.dp))
                        Text(line, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (fullText) {
                Panel {
                    BetaPrivacyPolicy.sections.forEach { section ->
                        Text(
                            section.heading,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            section.body,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TextSecondaryDark,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = { fullText = !fullText }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (fullText) "收起全文" else "查看全文条款",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("同意并继续", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("不同意，只在本机使用", fontWeight = FontWeight.Bold, color = TextSecondaryDark)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "选「不同意」不会减少任何功能，只是关闭匿名使用统计。",
            fontSize = 11.sp,
            color = TextDisabledDark,
        )
    }
}

/** 说明页的标准卡片（与 Beta 激活页同一套边框语言）。 */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp),
        content = content,
    )
}
