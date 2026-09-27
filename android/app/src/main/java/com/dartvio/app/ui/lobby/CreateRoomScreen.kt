package com.dartvio.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.room.RoomSchedule
import com.dartvio.app.domain.room.RoomVisibility
import com.dartvio.app.domain.room.StartMode
import com.dartvio.app.ui.components.SegmentedSelector
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 创建房间（M6 F6.2）。
 *
 * 房间设置沿用本地对局的 [com.dartvio.app.domain.model.MatchConfig] 语义，
 * 保证「本地对局」与「在线房间」的规则完全一致，未来无需两套解析。
 */
@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateRoomViewModel = viewModel()
) {
    // 预约那张卡底部有一句「还剩 …」的倒计时：它必须每秒在动，
    // 停着不动的数字会被读成「点了没反应」（2026-09-26 真机反馈）。
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // 术语统一为「创建比赛」：大厅里那个主动作就叫创建比赛，
            // 点进来之后标题变成「创建房间」，像是在创建另一种东西（2026-09-26 真机反馈）。
            Text(
                "创建比赛",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 每一行都是「名称在左、选项在右」：名称占固定宽度，选项因此沿同一条
            // 左边界对齐成一列，页面比「标题独占一行 + 控件另起一行」紧凑得多
            // （2026-09-26 真机反馈）。
            SettingCard("房间名") {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    singleLine = true,
                    placeholder = {
                        Text("例如：周末 501 局", color = TextDisabledDark, fontSize = 13.sp)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))
            SettingCard("比赛类型") {
                SegmentedSelector(
                    options = listOf(MatchType.X01, MatchType.CRICKET),
                    selected = viewModel.matchType,
                    label = { if (it == MatchType.X01) "X01" else "Cricket" },
                    onSelect = viewModel::updateMatchType
                )
            }

            if (viewModel.matchType == MatchType.X01) {
                Spacer(Modifier.height(10.dp))
                SettingCard("目标分") {
                    SegmentedSelector(
                        options = listOf(301, 501, 701),
                        selected = viewModel.targetScore,
                        label = { it.toString() },
                        onSelect = viewModel::updateTargetScore
                    )
                }
            }

            // Double-Out 紧跟目标分：两者是同一件事的两半（打到多少分 / 怎么收尾），
            // 隔在中间的「比赛模式 / 先赢局数」会把它们拆成两段，读起来像两个无关的开关。
            if (viewModel.matchType == MatchType.X01) {
                Spacer(Modifier.height(10.dp))
                // 与其余设置行同一张卡（[SettingCard]）：早先它是一张没有描边、
                // 背景更亮、还带一行小字的自绘卡，混在一列卡片里像另一个层级的东西。
                // 「Double-Out 是什么」不再写在卡片上 —— 规则解释统一进右上角规则页。
                SettingCard("Double-Out") {
                    RoomSwitch(checked = viewModel.doubleOut, onCheckedChange = viewModel::updateDoubleOut)
                }
            }

            Spacer(Modifier.height(10.dp))
            SettingCard("比赛模式") {
                SegmentedSelector(
                    options = listOf(MatchMode.CASUAL, MatchMode.MULTI_LEG),
                    selected = viewModel.mode,
                    label = { if (it == MatchMode.CASUAL) "休闲局" else "多局赛" },
                    onSelect = viewModel::updateMode
                )
            }

            if (viewModel.mode == MatchMode.MULTI_LEG) {
                Spacer(Modifier.height(10.dp))
                SettingCard("先赢局数") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        viewModel.legsOptions.forEach { legs ->
                            NumberPill(
                                value = legs,
                                selected = viewModel.legsToWin == legs,
                                onClick = { viewModel.updateLegsToWin(legs) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SettingCard("开赛方式") {
                SegmentedSelector(
                    options = StartMode.entries,
                    selected = viewModel.startMode,
                    label = { it.label },
                    onSelect = viewModel::updateStartMode
                )
            }

            if (viewModel.startMode == StartMode.SCHEDULED) {
                Spacer(Modifier.height(10.dp))
                ScheduleCard(viewModel = viewModel, now = now)
            }

            Spacer(Modifier.height(10.dp))
            SettingCard("房间可见性") {
                SegmentedSelector(
                    options = listOf(RoomVisibility.PUBLIC, RoomVisibility.PRIVATE),
                    selected = viewModel.visibility,
                    label = { if (it == RoomVisibility.PUBLIC) "公开" else "私密" },
                    onSelect = viewModel::updateVisibility,
                    // 私密**暂时不可选**：它要做的事（不出现在大厅列表）现在还没有人的问题需要解决 ——
                    // 大厅里常年就几间房，「让房间不被看见」只会让本就稀缺的对手更难找。
                    // 等人数上来了再按同一条策略开放。
                    enabled = { it == RoomVisibility.PUBLIC }
                )
            }
            Text(
                "私密房间暂未开放：现在人手还少，房间默认对大厅可见才容易配上人。",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextDisabledDark,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp)
            )

            Spacer(Modifier.height(10.dp))
            SettingCard("允许观战") {
                RoomSwitch(checked = viewModel.allowSpectators, onCheckedChange = viewModel::updateAllowSpectators)
            }

            Spacer(Modifier.height(22.dp))
            ConfigPreview(
                name = viewModel.name,
                configSummary = viewModel.config.summary(),
                visibility = viewModel.visibility,
                allowSpectators = viewModel.allowSpectators,
                startText = startTextOf(viewModel, now)
            )
            Spacer(Modifier.height(24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Primary)
                    .clickable { onCreated(viewModel.create().id) },
                contentAlignment = Alignment.Center
            ) {
                Text("创建并进入", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
            }
        }
    }
}

/**
 * 设置行：**名称在左、选项在右**（同一行）。
 *
 * 名称一律占 [SETTING_LABEL_WIDTH]，于是各行的选项沿同一条左边界对齐成一列 ——
 * 早年「标题一行 + 控件一行」的写法让页面长出两倍的高度，翻不完（2026-09-26 真机反馈）。
 */
@Composable
private fun SettingCard(
    label: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(SETTING_LABEL_WIDTH)
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            control()
        }
    }
}

/** 设置名称的统一宽度（见 [SettingCard]）：所有选项的左边界靠它对齐。 */
private val SETTING_LABEL_WIDTH = 84.dp

@Composable
private fun NumberPill(value: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else SurfaceVariantDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) OnPrimary else TextSecondaryDark
        )
    }
}

/** 卡片里那个开关：只管颜色，尺寸与位置一律交给 [SettingCard]。 */
@Composable
private fun RoomSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = OnPrimary,
            checkedTrackColor = Primary,
            uncheckedThumbColor = TextSecondaryDark,
            uncheckedTrackColor = Divider
        )
    )
}

/**
 * 预约时间：**月 / 日 / 时**，三档并排，每档左右各一个三角箭头。
 *
 * ## 为什么是箭头而不是横滑条
 *
 * 三档各有 12 / 31 / 24 个取值，横滑要把它们在一条 44dp 高的轨道里连成一列，
 * 用户得先数到第几个才知道自己是几天后。箭头一次只走一格，脚下（确认行）立刻翻成
 * 一句人话 —— 「约到后天晚上八点」这个动作本来就该是几下点击，不是一次滑动搜索。
 *
 * ## 为什么不用系统日期选择器
 *
 * 它在深色主题里是一整块高亮圆盘，会把「约个时间」这件小事变成一次全屏决策；
 * 另外它的确认按钮叫「确定」，用户点完还得猜自己选的是不是刚才那一格 ——
 * 下面那句常驻的确认行把这件事说死了。
 *
 * ## 没有「分」
 *
 * 「约几点」在社会语境里就是整点。多给一排分钟，只会让用户以为必须选一次才能继续。
 */
@Composable
private fun ScheduleCard(viewModel: CreateRoomViewModel, now: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            "开赛时间",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScheduleUnit(
                text = "${viewModel.scheduledMonth}月",
                onPrev = { viewModel.stepMonth(-1) },
                onNext = { viewModel.stepMonth(1) },
                modifier = Modifier.weight(1f)
            )
            ScheduleUnit(
                text = "${viewModel.scheduledDay}日",
                onPrev = { viewModel.stepDay(-1) },
                onNext = { viewModel.stepDay(1) },
                modifier = Modifier.weight(1f)
            )
            ScheduleUnit(
                text = "${viewModel.scheduledHour}时",
                onPrev = { viewModel.stepHour(-1) },
                onNext = { viewModel.stepHour(1) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        // 确认行：选完立刻变成一句人话，不需要用户自己在三档之间做心算。
        Text(
            startTextOf(viewModel, now),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
        )
    }
}

/**
 * 一档时间：三角箭头**嵌在值的左右两侧**（同一条背景里，不是两侧各一个按钮列）。
 *
 * 箭头与值共用一个 12dp 圆角容器，视觉上读作「一个控件」；拆成三个并列的按钮，
 * 用户会以为中间的格子也是可以点的，而它只是个显示位。
 */
@Composable
private fun ScheduleUnit(
    text: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepArrow(onClick = onPrev, contentDescription = "上一档", left = true)
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        StepArrow(onClick = onNext, contentDescription = "下一档", left = false)
    }
}

@Composable
private fun StepArrow(onClick: () -> Unit, contentDescription: String, left: Boolean) {
    Icon(
        imageVector = if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = contentDescription,
        tint = Primary,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    )
}

@Composable
private fun ConfigPreview(
    name: String,
    configSummary: String,
    visibility: RoomVisibility,
    allowSpectators: Boolean,
    startText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(
            "比赛设置预览",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        PreviewLine("比赛名", name.ifBlank { "（未命名）" })
        PreviewLine("比赛设置", configSummary)
        PreviewLine("开赛", startText)
        PreviewLine("可见性", if (visibility == RoomVisibility.PUBLIC) "公开，出现在大厅列表" else "私密，不出现在大厅列表")
        PreviewLine("观战", if (allowSpectators) "允许" else "禁止")
    }
}

/** 开赛文案：建好就打 / 具体时刻（不足一小时给倒计时）。与房间卡片共用 [RoomSchedule] 的口径。 */
private fun startTextOf(viewModel: CreateRoomViewModel, now: Long = System.currentTimeMillis()): String {
    val startsAt = viewModel.startsAt ?: return "建好就开始"
    return RoomSchedule.cardLabel(startsAt, now) ?: "建好就开始"
}

@Composable
private fun PreviewLine(key: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(key, fontSize = 12.sp, color = TextSecondaryDark, modifier = Modifier.width(72.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
