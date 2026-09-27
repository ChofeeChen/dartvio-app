package com.dartvio.app.ui.beta

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.R
import com.dartvio.app.data.beta.BetaAccess
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning

/*
 * 试用期到期页与到期提醒条。
 *
 * ## 为什么到期是**一整页**而不是一个对话框
 *
 * 到期之后 App 已经不能用了，这时候一个「确定」按钮的弹窗会被当成崩溃前的报错；
 * 而一整页能同时说清三件事：**发生过什么**（这是试用包，到期了）、
 * **你的东西没事**（数据还在本机，更新版本继续用）、**下一步**（扫码拿新版）。
 *
 * ## 为什么**不**做「继续使用」按钮
 *
 * 试用包的意义就是到期失效。留一个能跳过的口子，等于把「有效期」这件事变成一句玩笑，
 * 之后每次提醒都不会有人当真 —— 而有效期本来是拿来做线上│回收与版本收敛的。
 *
 * ## 为什么不清数据
 *
 * 见 [BetaAccess.usable]：试用者的本地数据是他自己的资产，到期不等于授权我们删除。
 */
@Composable
fun BetaExpiredScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text("DartVio", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Primary)
        Spacer(Modifier.height(6.dp))
        Text("Beta 试用版", fontSize = 13.sp, color = TextSecondaryDark)

        Spacer(Modifier.height(26.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(14.dp))
                .border(1.dp, Divider, RoundedCornerShape(14.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "试用版已到期",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Warning,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "这个试用包到 2026-10-19 为止。\n" +
                    "你的训练与对局数据都还在本机，更新到新版本后原样继续，" +
                    "卸载（而非覆盖安装）才会清掉。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = TextSecondaryDark,
            )
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(R.drawable.beta_wechat_qr),
                contentDescription = "微信二维码",
                modifier = Modifier.size(180.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "扫码拿新版本，或反馈试用感受",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            "v${com.dartvio.app.BuildConfig.VERSION_NAME} · 试用版 ${BetaAccess.EXPIRE_LABEL}",
            fontSize = 11.sp,
            color = TextSecondaryDark,
        )
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * 到期前一周的**顶部提醒条**。
 *
 * 带天数而不是只写「即将到期」：「还剩 3 天」是可以安排的行动信息，
 * 「即将到期」只是一句形容词，用户无法据此判断今天要不要去群里问。
 */
@Composable
fun BetaExpiryBanner(daysLeft: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Warning.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "试用版还剩 $daysLeft 天到期 —— 数据在本机，更新新版本即可继续。",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Warning,
            modifier = Modifier.weight(1f),
        )
    }
}

