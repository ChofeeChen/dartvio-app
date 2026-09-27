package com.dartvio.app.ui.lobby

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 创建房间（S1：底部弹层，替代原来的整页表单）。
 *
 * ## 为什么只放三个高频决策
 *
 * 建一张桌子真正让人犹豫的只有「打几分」和「打几局」，其余规则（IN / OUT / 轮数上限）
 * 绝大多数人一辈子不改。把它们平铺在首屏，等于让每次创建都要做一遍自己不懂的选择；
 * 收进折叠区之后，默认值就能覆盖绝大多数局（PRD D2）。
 *
 * ## 为什么不再有公开 / 私密与房间号
 *
 * 房间一经创建即在大厅公开列出，任何人点 [加入] 即可进来（PRD D5）：
 * 私密房与房间号都失去了意义，创建者不需要再为「谁能进来」做一次选择。
 *
 * @param onCreate 回调「配置 + 是否允许观战」，由调用方负责落到数据源 ——
 *   弹层只收集意图，不认识仓库。
 */
@Composable
fun CreateRoomSheet(
    onDismiss: () -> Unit,
    onCreate: (MatchConfig, Boolean) -> Unit
) {
    // P0 只做 X01：Cricket 的创建（目标集 / 变体）要等 M7，先不给一个半通的入口。
    var targetScore by remember { mutableStateOf(501) }
    var legsToWin by remember { mutableStateOf(2) }
    var casual by remember { mutableStateOf(false) }
    var allowSpectators by remember { mutableStateOf(true) }

    var advancedExpanded by remember { mutableStateOf(false) }
    var outMode by remember { mutableStateOf(OutMode.DOUBLE_OUT) }
    var inMode by remember { mutableStateOf(InMode.STRAIGHT_IN) }
    var maxRounds by remember { mutableStateOf(20) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "创建房间",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = TextSecondaryDark
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            ChoiceLabel("目标分")
            Spacer(Modifier.height(8.dp))
            StepperRow(
                value = targetScore,
                onMinus = {
                    targetScore = TARGET_SCORES[
                        (TARGET_SCORES.indexOf(targetScore) - 1 + TARGET_SCORES.size) % TARGET_SCORES.size
                    ]
                },
                onPlus = {
                    targetScore = TARGET_SCORES[
                        (TARGET_SCORES.indexOf(targetScore) + 1) % TARGET_SCORES.size
                    ]
                }
            )

            Spacer(Modifier.height(18.dp))
            ChoiceLabel("赛制")
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LegChip("休闲单局", selected = casual) {
                    casual = true
                }
                Spacer(Modifier.width(8.dp))
                LEGS_OPTIONS.forEach { legs ->
                    LegChip("先到 $legs 局", selected = !casual && legsToWin == legs) {
                        casual = false
                        legsToWin = legs
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            ReadOnlyRow("对阵人数", "2 人（满员即开赛）")

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("允许观战", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = allowSpectators,
                    onCheckedChange = { allowSpectators = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Primary,
                        checkedTrackColor = Primary.copy(alpha = 0.35f)
                    )
                )
            }
            Text(
                "关掉之后，这间房不会出现在观战台。",
                fontSize = 11.sp,
                color = TextDisabledDark,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark)
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (advancedExpanded) "收起高级规则" else "高级规则设置",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (advancedExpanded) "▴" else "▸",
                    fontSize = 14.sp,
                    color = TextSecondaryDark
                )
            }

            if (advancedExpanded) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    ChoiceLabel("开局（IN）")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InMode.entries.forEach { mode ->
                            LegChip(mode.label, selected = inMode == mode) { inMode = mode }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ChoiceLabel("结镖（OUT）")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutMode.entries.forEach { mode ->
                            LegChip(mode.label, selected = outMode == mode) { outMode = mode }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ChoiceLabel("回合上限")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MAX_ROUNDS_OPTIONS.forEach { rounds ->
                            LegChip(
                                if (rounds == 0) "无上限" else "$rounds 轮",
                                selected = maxRounds == rounds
                            ) { maxRounds = rounds }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "5 分钟内没人加入，房间会自动解散。",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Primary)
                    .clickable {
                        onCreate(
                            MatchConfig(
                                targetScore = targetScore,
                                mode = if (casual) MatchMode.CASUAL else MatchMode.MULTI_LEG,
                                legsToWin = if (casual) 0 else legsToWin,
                                outMode = outMode,
                                inMode = inMode,
                                maxRounds = maxRounds
                            ),
                            allowSpectators
                        )
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("创建并等待", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
            }
        }
    }
}

/**
 * 玩法规则说明（大厅顶栏 ⋮ 进入，只读）。
 *
 * 与「本房间规则」分开：这里回答的是「X01 到底是什么」，而不是「这一局怎么打」；
 * 后者在房间等候页的 ⋮ 里（S2）。
 */
@Composable
fun HallRulesSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "玩法规则",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = TextSecondaryDark)
                }
            }
            Spacer(Modifier.height(8.dp))
            RuleBlock(
                "X01（301 / 501 / 701）",
                "每人每轮投 3 镖，从目标分往下减，恰好减到 0 者胜出。\n" +
                    "双倍结束：最后一镖必须命中双倍区（或双倍牛眼）；直出则不作要求。\n" +
                    "减成负数或剩 1 分（双倍结束时）算爆分，本轮成绩作废。"
            )
            RuleBlock(
                "局（Leg）与赛制",
                "一局 = 一次从目标分减到 0 的过程。\n" +
                    "休闲单局：只打一局。\n" +
                    "先到 N 局：先赢满 N 局者获胜这场比赛。"
            )
            RuleBlock(
                "Cricket",
                "轮流关闭 20 / 19 / 18 / 17 / 16 / 15 与牛眼：先集满 3 次标记的一方可以开始在该数字上计分。\n" +
                    "所有目标都关闭且分数不低于对手者获胜。"
            )
            Text(
                "先在谁先投：房间内以「争红心」决定（离圆心更近者先投）。",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun RuleBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(14.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.sp, lineHeight = 18.sp, color = TextSecondaryDark)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ChoiceLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondaryDark
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
    }
}

@Composable
private fun StepperRow(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        StepperButton(Icons.Filled.Remove, onMinus)
        Spacer(Modifier.width(20.dp))
        Text(
            value.toString(),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Accent
        )
        Spacer(Modifier.width(20.dp))
        StepperButton(Icons.Filled.Add, onPlus)
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LegChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) Primary else Color.Transparent
    val border = if (selected) Primary else Divider
    val content = if (selected) OnPrimary else TextSecondaryDark
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

private val TARGET_SCORES = listOf(301, 501, 701)
private val LEGS_OPTIONS = listOf(2, 3, 5)
private val MAX_ROUNDS_OPTIONS = listOf(0, 15, 20, 50)
