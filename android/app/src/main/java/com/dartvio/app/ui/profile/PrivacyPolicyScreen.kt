package com.dartvio.app.ui.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.BuildConfig
import com.dartvio.app.data.beta.BetaConsent
import com.dartvio.app.data.beta.BetaPrivacyPolicy
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/*
 * 隐私声明**二级页**。
 *
 * ## 为什么必须存在这一页
 *
 * 首启那次「同意 / 不同意」是一次性动作，而用户后来一定会想知道两件事：
 * 「我当初同意了什么」和「我能不能改」。政策正文第四条已经承诺了
 * 「之后可在『我的』页查看本政策」—— 没有这一页，那句承诺就是空的，
 * 而**写下来的承诺落空**比没写更伤信任。
 *
 * ## 为什么这里能直接关掉匿名统计
 *
 * 唯一会离开设备的数据就是那条匿名统计，它不是服务所必需（PIPL 第十六条口径）。
 * 把开关放在政策正文下面，用户读到这里正好可以立刻做决定；
 * 藏进「设置」里，就会变成「说了能改，但没说在哪改」。
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { BetaConsent.prefs(context) }
    // 每次进入重读：选择可能在别处（首启页）改过，缓存的旧值会把开关显示反。
    var granted by remember(context) { mutableStateOf(BetaConsent.granted(prefs)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "隐私声明",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("返回", color = Primary) }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = Primary,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "隐私政策 ${BetaPrivacyPolicy.id}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
            )
            Spacer(Modifier.height(10.dp))

            // 摘要放最上面：大多数人读完这四行就够了，全文是给认真核对的人准备的。
            Panel {
                BetaPrivacyPolicy.summary.forEach { line ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("·", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- 匿名统计开关 ----
            // 只有真会上报的包才给开关：不会发的包摆一个开关，等于暗示「我们有在记录」。
            if (BuildConfig.TELEMETRY_ENABLED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(14.dp))
                        .border(1.dp, Divider, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "匿名使用统计",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (granted) {
                                "已开启：每天上报一次「今天有人用过」，不含昵称与成绩。"
                            } else {
                                "已关闭：不会发送任何数据，功能一个不少。"
                            },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = TextSecondaryDark,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = granted,
                        onCheckedChange = { next ->
                            BetaConsent.save(prefs, next)
                            granted = BetaConsent.granted(prefs)
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Text(
                    "本包不发送任何统计数据。",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryDark,
                )
                Spacer(Modifier.height(14.dp))
            }

            Text(
                "完整条款",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(12.dp))
            Text(
                "你的对局与练习数据只写在这台手机的 App 私有目录里，卸载即删除；" +
                    "数据页显示的一切都来自本机，不上传。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = TextDisabledDark,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp),
        content = content,
    )
}
