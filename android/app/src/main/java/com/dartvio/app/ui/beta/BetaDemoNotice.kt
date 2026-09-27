package com.dartvio.app.ui.beta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.BuildConfig
import com.dartvio.app.data.beta.BetaAccess
import com.dartvio.app.data.beta.BetaCrashLog
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextDisabledDark

/**
 * Beta Demo 包的**未实现清单**。
 *
 * 这份清单的立场：试用包要把入口全打开（让人看清产品的完整形状），
 * 于是「没做完」这件事就必须**说在明处** —— 让朋友点到一个空壳，
 * 他只会认为这是 bug；而告诉他「这是演示/未实现」，他给的反馈才是有用的。
 *
 * 所以每条都写清两件事：**现在点下去会怎样**，以及**为什么还没做完**。
 */
object DemoManifest {

    /** 未实现的几种「未完成程度」，界面上直接显示这个标签。 */
    enum class State(val label: String) {
        /** 能点通，但数据是本地模拟的。 */
        DEMO("演示数据"),

        /** 逻辑已写好，入口未开放。 */
        LOCKED("未开放"),

        /** 入口在，功能没有。 */
        NOT_IMPL("未实现"),

        /** 本期根本不做，界面上也不提供。 */
        HIDDEN("本期不做"),

        /** 已实现，但口径与直觉不同，需要说明。 */
        POLICY("口径说明"),
    }

    data class Item(
        val area: String,
        val state: State,
        val note: String,
    )

    val items: List<Item> = listOf(
        Item(
            area = "大厅 / 房间 / 观战（未联机时）",
            state = State.DEMO,
            note = "不联机时房间列表与观战比分是本地模拟数据（每 2.2 秒自动推进），不是真实房间。" +
                "真实联机：两台手机连同一个 WiFi，走「大厅 → 联机」，一台开主机、一台填 IP 加入。",
        ),
        Item(
            area = "对抗练习 · 后 3 个模式",
            state = State.LOCKED,
            note = "双倍环游 / 上海争霸 / 减半挑战：规则引擎已写好，尚未开放。" +
                "卡片可点（点了会给提示），「规则」按钮可以查看完整规则说明。",
        ),
        Item(
            area = "AI 视觉输入（手机视觉计分）",
            state = State.NOT_IMPL,
            note = "输入方式里的「AI视觉」不可选：识别管线（CameraX + LiteRT）尚未接入，" +
                "几何与判定骨架已就位。当前请用「键盘」或「靶盘」。",
        ),
        Item(
            area = "观战页 · 靶盘动画",
            state = State.NOT_IMPL,
            note = "观战页目前只有比分板，没有逐镖落点动画。",
        ),
        Item(
            area = "硬件专属指标（瞄准后命中率等）",
            state = State.HIDDEN,
            note = "依赖硬件视觉设备，本期不展示。统计页脚注里已注明。",
        ),
        Item(
            area = "账号 / 跨设备恢复",
            state = State.HIDDEN,
            note = "本机身份只在本机有效，换机或重装不做恢复。",
        ),
        Item(
            area = "官方擂台时间窗",
            state = State.DEMO,
            note = "20:00–22:00 是待确认的占位值，不是最终运营时间。",
        ),
        Item(
            area = "联机对局与战绩的关系",
            state = State.POLICY,
            note = "联机局会进历史列表（标注来源 LAN），但**不计入**统计 / 成就 / 排行榜 —— " +
                "联机没有裁判，且可能以判负收场，计入会让「拔网线」变成一种划算的策略。",
        ),
    )
}

/**
 * 首页顶部的 Beta 横幅。
 *
 * 常驻一行，不弹窗打扰：它既是版本标识（朋友一眼知道这不是正式版），
 * 也是这份清单的唯一入口。
 */
@Composable
fun BetaDemoBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BETA DEMO",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "部分功能为演示 / 未实现，点此查看清单 ›",
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 未实现清单弹窗（Beta 横幅点开后的内容）。 */
@Composable
fun BetaDemoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var crashes by remember { mutableStateOf(BetaCrashLog.read(context)) }
    // 展示本机激活用的邀请码：分发者靠它对账（谁报问题，码对上台账就找到人）。
    val activatedCode by remember {
        mutableStateOf(
            BetaAccess.activatedCode(
                context.getSharedPreferences(BetaAccess.PREFS, Context.MODE_PRIVATE)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Beta Demo 试用版",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME} · 入口已全部开放，下面是还没做完的部分",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (activatedCode != null) {
                    Text(
                        "你的邀请码：$activatedCode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                    )
                    Text(
                        "反馈问题时请附上它，便于对账。",
                        fontSize = 11.sp,
                        color = TextDisabledDark,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                DemoManifest.items.forEach { item ->
                    ManifestRow(item)
                    Spacer(Modifier.height(10.dp))
                }

                if (crashes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "最近异常（${crashes.size}）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                        )
                        TextButton(
                            onClick = {
                                BetaCrashLog.clear(context)
                                crashes = emptyList()
                            }
                        ) {
                            Text("清空", fontSize = 12.sp, color = Accent)
                        }
                    }
                    crashes.asReversed().forEach { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                        )
                    }
                    Text(
                        "把这段截图发我，就能定位到你手机上的问题。",
                        fontSize = 11.sp,
                        color = TextDisabledDark,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了", color = Accent, fontWeight = FontWeight.Bold) }
        },
    )
}

/**
 * 点到「还没做完」的入口时的**统一**提示。
 *
 * 未实现的东西散落在好几处（对抗练习后 3 个模式、AI 视觉输入…），
 * 各写一份提示就会各写一种说法 —— 有的像 bug，有的像故障。
 * 统一在一个组件里出，口径才不会漂。
 */
@Composable
fun UnavailableFeatureDialog(
    title: String,
    note: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text("演示版 · 未开放", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Accent)
            }
        },
        text = {
            Text(
                note,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun ManifestRow(item: DemoManifest.Item) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.area,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(item.state.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Accent)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.note,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
