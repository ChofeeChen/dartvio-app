package com.dartvio.app.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dartvio.app.data.beta.BetaFeedback
import com.dartvio.app.data.beta.BetaFeedbackAck
import com.dartvio.app.data.telemetry.UsageIdentity
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的反馈」二级页：**把闭环摆在同一屏里**。
 *
 * 三块内容按用户读的顺序排：
 * ① 我提了多少（左）／被采纳多少（右）—— 一眼看出「有人听」；
 * ② 写一条反馈 —— 点了就带上体验 ID、版本号、日期和 9 个固定字段，
 *    交给**系统分享面板**，发到微信还是邮件由他自己选；
 * ③ 这一版采纳了什么 —— 逐条列出落地版本，可以立刻去核对。
 *
 * 为什么不用内置的输入框直接上报给我们（常见的「意见反馈」做法）：
 * 那需要收集他的反馈内容，而我们承诺过**不收集任何内容类信息**；而且那样做的代价是
 * 反馈要等后台有人看，闭环感反而更弱。走他自己的微信，他还能顺手发语音、贴图、@人。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { BetaFeedback.prefs(context) }
    // 计数是**本地**的，反复进出页面要读到最新值，所以进页时重读一次 SP。
    var sentCount by remember { mutableIntStateOf(BetaFeedback.sentCount(prefs)) }
    // 刚拉起过分享面板、还没得到用户确认
    var awaitingConfirm by remember { mutableStateOf(false) }
    val adopted = BetaFeedbackAck.items

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "我的反馈",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimaryDark,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LoopCounterCard(
                    value = "$sentCount",
                    unit = "条",
                    label = "你已提交",
                    accent = Primary,
                    modifier = Modifier.weight(1f),
                )
                LoopCounterCard(
                    value = "${adopted.size}",
                    unit = "条",
                    label = "已采纳 · 全体体验者",
                    accent = Secondary,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            /*
             * 措辞的边界（2026-09-27 反馈）：**不承诺「会写进下一个版本」**。
             *
             * 采纳与否取决于这条建议值不值得做、代价多大，而不是取决于我们说过什么；
             * 一旦写下「下一个版本」，每一条没进下一版的建议都会变成一次没兑现的承诺，
             * 而兑现不了的那次会连累「已采纳」清单里其它条目的可信度。
             * 这里能承诺的只有**过程**：逐条看、认真评估、想清楚了再动。
             */
            Text(
                "每一条反馈我们都会认真看、逐条评估：有价值的建议会被排进后续版本，" +
                    "暂时不做的也会留在这份清单之外而不是消失。" +
                    "已经落地的按版本列在下面，提了之后有没有人听，这一页你自己查得到 —— 不用问我。",
                fontSize = 12.sp,
                color = TextSecondaryDark,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    shareFeedback(context, feedbackBody(context))
                    awaitingConfirm = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = OnPrimary,
                ),
            ) {
                Text("写一条反馈", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "会打开系统的分享面板：填好后的字段发到微信还是邮件，由你决定。" +
                    "我们不代发、也看不到你写了什么。",
                fontSize = 11.sp,
                color = TextDisabledDark,
            )

            /*
             * 计数为什么不点在「发送」那一瞬间：
             * 分享出去之后到底有没有发出去，我们**无从核实**（也不该去核实）。
             * 所以这里明问一句，让用户自己认。猜的答案第三次就会被发现是假的，
             * 而假数字毁掉的是整个数字的意义。
             */
            if (awaitingConfirm) {
                Spacer(Modifier.height(12.dp))
                ConfirmSentCard(
                    onSent = {
                        sentCount = BetaFeedback.recordSent(prefs)
                        awaitingConfirm = false
                    },
                    onNotSent = { awaitingConfirm = false },
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "这一版采纳了什么",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            adopted.forEach { item ->
                AdoptedRow(item)
                Spacer(Modifier.height(10.dp))
            }

            if (sentCount > 0) {
                Spacer(Modifier.height(4.dp))
                // 数字必须能退回来（点错 / 那条其实是空内容），否则它就是装饰。
                TextButton(onClick = { sentCount = BetaFeedback.undoLast(prefs) }) {
                    Text("撤回最近一条", fontSize = 13.sp, color = TextSecondaryDark)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "条数只存在你这台机器上，卸载即清空，也不参与任何统计上报。",
                fontSize = 11.sp,
                color = TextDisabledDark,
            )
        }
    }
}

/** 一条反馈的正文：带上能让它对得上版本与设备的上下文，其余字段留给用户填。 */
private fun feedbackBody(context: Context): String {
    val usageId = if (BuildConfig.TELEMETRY_ENABLED) {
        UsageIdentity.shortId(UsageIdentity.read(UsageIdentity.prefs(context))?.userId)
    } else {
        null
    }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return BetaFeedback.buildMessage(
        usageId = usageId.takeIf { it?.isNotBlank() == true },
        versionName = BuildConfig.VERSION_NAME,
        dateText = today,
    )
}

/**
 * 拉起系统分享。
 *
 * 用 `ACTION_SEND` 而不是写死邮箱：人的反馈冲动是三秒钟的事，中间只要问一句
 * 「发给谁」，一半的人就放弃了。面板里有他已经装好的所有去处。
 */
private fun shareFeedback(context: Context, body: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(
            Intent.createChooser(intent, "把反馈发给我们")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "这台设备上没有可以发送内容的应用", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LoopCounterCard(
    value: String,
    unit: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.size(3.dp))
            Text(unit, fontSize = 13.sp, color = TextSecondaryDark)
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConfirmSentCard(onSent: () -> Unit, onNotSent: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark, RoundedCornerShape(14.dp))
            .border(1.dp, Primary, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            "刚才那条发出去了吗？",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "发出去了给右边的数字 +1；没发出去就当什么都没发生。",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSent,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = OnPrimary,
                ),
            ) {
                Text("发出去了", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onNotSent) {
                Text("没发出去", fontSize = 14.sp, color = TextSecondaryDark)
            }
        }
    }
}

@Composable
private fun AdoptedRow(item: BetaFeedbackAck.Adopted) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            item.shippedIn,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Secondary,
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.what,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${item.date} · ${item.source}",
                fontSize = 11.sp,
                color = TextDisabledDark,
            )
        }
    }
}
