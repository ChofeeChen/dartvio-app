package com.dartvio.app.ui.beta

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.BuildConfig
import com.dartvio.app.R
import com.dartvio.app.data.beta.BetaAccess
import com.dartvio.app.data.beta.BetaInviteApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/*
 * Beta Demo 的**首启渠道页**（2026-09-20 前叫「激活页」）。
 *
 * 立场变了，这一页的性质就得跟着变：邀请码**不再是门禁**，而是渠道标签。
 * 所以它现在的任务只剩两个：
 * 1. 给一个「填一下码」的机会，并说清填了是干什么的（只用于统计哪个渠道带来的
 *    用户在用，不绑任何个人信息）；
 * 2. 让没码的人**体面地直接进去** —— 「跳过，直接体验」必须是同等级别的选择，
 *    不能做成灰色小字，否则它就还是一扇门。
 *
 * 只在 beta 变体的首次启动出现一次；填码或跳过之后永不再见（见 `BetaAccess.hasDecided`）。
 */
@Composable
fun BetaAccessScreen(
    onActivated: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val prefs = context.getSharedPreferences(BetaAccess.PREFS, Context.MODE_PRIVATE)

    /*
     * 激活顺序：**先问后台，后台不在才退回本地白名单**。
     *
     * 后台说什么就是什么（这是「后台审核」的唯一意义）；只有后台**没回应**——
     * 没配后端、地铁里没信号、后端暂时坏了——才退化到包里的白名单，并且记成「待补验」，
     * 下次联网时 MainActivity 会静默再问一次后台。
     * 反过来做（先本地放行、后台慢慢来）等于把校验降级成装饰。
     */
    fun tryActivate() {
        if (checking) return
        val code = BetaAccess.normalize(input)
        if (code.isBlank()) {
            // 输入框空着就点「填好了」：按跳过处理，而不是弹一句错 —— 什么都没填不是错。
            onSkip()
            return
        }
        checking = true
        errorMsg = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (BetaInviteApi.available()) {
                    BetaInviteApi.verify(code, BetaAccess.installId(prefs))
                } else {
                    BetaInviteApi.Result.Unavailable
                }
            }
            checking = false
            when (result) {
                is BetaInviteApi.Result.Accepted -> {
                    BetaAccess.grantVerified(prefs, code)
                    onActivated()
                }

                is BetaInviteApi.Result.Rejected -> {
                    errorMsg = BetaInviteApi.messageFor(result.reason)
                }

                BetaInviteApi.Result.Unavailable -> {
                    // 后台不在：退回本地白名单，但仍然允许用户跳过（邀请码从来不是门禁）。
                    if (BetaAccess.grant(prefs, code)) {
                        BetaAccess.markPending(prefs)
                        onActivated()
                    } else {
                        errorMsg = "邀请码无效；不填也能直接用，点下方跳过即可"
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            "DartVio",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Beta Demo 试用版 · v${BuildConfig.VERSION_NAME}",
            fontSize = 13.sp,
            color = TextSecondaryDark,
        )

        Spacer(Modifier.height(26.dp))

        Panel {
            Text(
                "有邀请码就填一下（可选）",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "邀请码只用来统计是哪个渠道带来的试用者，不绑定你的任何个人信息。" +
                    "没有码也完全可以正常使用。",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = TextSecondaryDark,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; errorMsg = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("邀请码（选填）") },
                isError = errorMsg != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { tryActivate() }),
            )
            if (errorMsg != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    errorMsg ?: "",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { tryActivate() },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checking) "校验中…" else "填好了，进入体验", fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = onSkip,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("没有码，直接体验", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(26.dp))

        Panel {
            Image(
                painter = painterResource(R.drawable.beta_wechat_qr),
                contentDescription = "微信二维码",
                modifier = Modifier.size(180.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "想反馈问题或拿邀请码？加微信",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            "邀请码可选 · 不填也能用 · 试用期至 2026-10-19",
            fontSize = 11.sp,
            color = TextDisabledDark,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** 激活页的标准卡片（与 Beta 横幅/清单同一套边框语言）。 */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
