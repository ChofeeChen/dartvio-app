package com.dartvio.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 紧凑型分段选择器（横向排布，减少纵向占用）。
 * 用于模式、难度等少量选项切换。
 *
 * [enabled] 让某一档**置灰但仍在原位**：拿掉它（只显示开放的那一档）会留下一条
 * 以后要重新补回来的空位，而用户的困惑会变成「这东西本来只有一种模式吗」。
 * 灰掉它，既说清楚「有这个东西」，也说清楚「现在选不了」。
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val usable = enabled(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected && usable) Primary else Color.Transparent)
                    .then(if (usable) Modifier.clickable { onSelect(option) } else Modifier)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    color = when {
                        !usable -> TextDisabledDark
                        isSelected -> OnPrimary
                        else -> TextSecondaryDark
                    },
                    fontWeight = if (isSelected && usable) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 步进器主数值的默认字号。
 *
 * 26sp 是给「501」这类**纯数字**定的：数字只有半角宽，放大后既不挤也醒目。
 * 换成含汉字的长文案（如「先赢 4 局」）时必须显式调小 —— 5 个全角字形在 26sp 下会顶满整行，
 * 与同一张卡里 12sp 的标题差出三档，读起来像章标题而不是一个取值。
 */
val CompactStepperValueSize: TextUnit = 26.sp

/**
 * 紧凑型数值步进器：左右箭头 + 中间大数值 + 可选副标题。
 * 用于 301/501/... 分数选择等，占用极小的纵向空间。
 *
 * [valueFontSize] 供**文案型取值**调小字号（默认档见 [CompactStepperValueSize]）。
 */
@Composable
fun CompactStepper(
    valueLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueFontSize: TextUnit = CompactStepperValueSize
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一个",
                tint = Primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valueLabel,
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = valueFontSize
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一个",
                tint = Primary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

/**
 * 可选头像圆形按钮。选中时高亮描边 + 主色文字。
 *
 * [enabled] 表示**能否点击**，用于「对手已选满 / 只剩最后一名对手」这类触顶场景：
 * 屏蔽点击而不是做成「点了没反应」（那会被读成按钮坏了）。
 *
 * 压暗的判断是 `enabled || selected`，**不是** `enabled`：
 * 已选中但此刻不可点的头像（它就是最后一名对手）必须保持正常亮度 ——
 * 按 `enabled` 压暗会让它看起来像「没选中」，玩家会以为选择失效了。
 * 只有「未选中且已触顶」的头像才压暗，用来说明「加不进去了」。
 */
@Composable
fun AvatarOption(
    emoji: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier.alpha(if (enabled || selected) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(if (selected) Primary.copy(alpha = 0.22f) else SurfaceVariantDark)
                .border(
                    BorderStroke(2.dp, if (selected) Primary else Color.Transparent),
                    CircleShape
                )
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            color = if (selected) Primary else TextSecondaryDark,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// ===== 设置项排版原语（2026-09-12：取消独立章节标题，并入父卡片）=====

/**
 * 「标题同行」布局里标题列的固定宽度。
 *
 * 取 58dp 的依据：12sp 汉字宽 ≈ 12dp，现有最长标题「对战模式 / 比赛模式 / 轮数上限」都是 4 字 = 48dp，
 * 余下 10dp 用来与选项区拉开距离。**5 字以上标题会换行** —— 将来加长标题请同步调宽，
 * 不要改成 `wrapContentSize`：那会让同一张卡里各行的选项区左边缘参差不齐。
 */
val SettingTitleWidth: Dp = 58.dp

/**
 * 设置父卡片：把原来散落的独立章节标题 + 控件收进同一张卡片。
 *
 * [title] 非空即在卡片内顶部显示分组标题（「标题独占一行」形态）；传 null 时卡片只有内容，
 * 适合整块只含一个设置项、标题由 [SettingRow] 带到同一行的情况。
 */
@Composable
fun SettingGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceVariantDark, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (title != null) {
            Text(text = title, color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

/**
 * **标题同行**：标题左对齐占固定宽度，选项区吃掉剩余宽度并垂直居中。
 *
 * ⚠️ 只用于**选项数 ≤ 3** 的设置。口径（360dp 窄屏、页边距 16dp、父卡片内边距 12dp）：
 * 卡片内可用宽 ≈ 304dp，扣掉标题列 58dp 后选项区 ≈ 246dp ——
 * 3 项各 77dp（舒适）、4 项跌到 56dp（偏挤）、5 项只剩 43dp，
 * 而「无上限」三个汉字 @14sp 本身就要 42dp，必然贴边。
 * 因此 **≥4 项一律改用 [SettingStack]**，更不准把 5 项硬塞进一行。
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标题用**主文字色**（与 SwitchSettingRow 的标题同色）：标题是「这一项是什么」，
        // 不是说明；用次级灰会让同一张卡里出现两种标题色（「模式」灰、「Overkill」白），
        // 看起来像模式那一行被禁用了。
        Text(
            text = title,
            color = TextPrimaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(SettingTitleWidth)
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/**
 * **标题独占一行**：标题在上、选项在下。
 *
 * 用于选项数 ≥ 4 的设置（目标分 / 局数 / 轮数上限），以及自带描述文案的整块内容
 * （变体卡列表、头像行、输入框）。这些内容压高度只会牺牲可读性，不值得。
 */
@Composable
fun SettingStack(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 同 [SettingRow]：标题保持主文字色，只有描述性小字（HintText / desc）才是次级灰。
        Text(text = title, color = TextPrimaryDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * 设置页底部主行动按钮的统一形态（高 54dp / 圆角 14dp / 主色底 / 深色字）。
 *
 * 只管「长什么样」——钉在屏幕底部由调用方用 `Scaffold(bottomBar = ...)` 完成，
 * 并且底栏必须自己处理 `navigationBarsPadding()`（`MainActivity` 开了 `enableEdgeToEdge`）。
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = OnPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 卡片式单选行（标题 + 描述 + 行内 `RadioButton`）。
 *
 * 行本身可点，所以 `RadioButton` 传 `onClick = null` —— 否则一次点击会触发两次回调。
 * 这类卡片天生占满整行宽度，**不参与「标题同行」**：它自己就是内容。
 */
@Composable
fun VariantCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) Primary.copy(alpha = 0.15f) else SurfaceDark,
                RoundedCornerShape(14.dp)
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Primary else Divider,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = TextSecondaryDark, fontSize = 12.sp)
        }
    }
}

/**
 * 开关型设置行：标题（+ 可选说明）在左，`Switch` 右对齐。
 * 只有 1 个控件且贴右，天然满足「标题同行」，因此不带标题列。
 *
 * [enabled] 为 false 时文案压暗、`Switch` 变为 Material 的禁用态（传 null 回调），
 * 用于「这一项在当前组合下根本不生效」的场景（如真人对战时那一整张 AI 卡）。
 */
@Composable
fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.4f)
        ) {
            Text(title, color = TextPrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (desc != null) {
                Text(desc, color = TextSecondaryDark, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            modifier = Modifier.size(width = 46.dp, height = 26.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = Primary,
                uncheckedThumbColor = TextSecondaryDark,
                uncheckedTrackColor = SurfaceVariantDark
            )
        )
    }
}

/**
 * 跳转入口卡：标题 + 一行说明 + **当前真实取值**摘要 + 右箭头 `›`。
 *
 * 用于「游戏设置」：设置项太长、需要独立页面承载，但入口必须一直看得见
 * （位置固定在「开始比赛」之上，见 `Scaffold(bottomBar = ...)`）。
 *
 * [highlight] 是入口卡唯一的信息来源 —— 必须写当前取值，不能写静态说明；
 * 否则玩家改完设置返回后，卡片上看不出自己改了什么。
 *
 * 箭头刻意用 `›`（跳转）而不是 `⌄ / ⌃`（展开）：语义不同，不能复用同一种箭头。
 */
@Composable
fun SettingsEntryCard(
    title: String,
    highlight: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceVariantDark, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = TextSecondaryDark, fontSize = 12.sp)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Primary.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(highlight, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "›", color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 等宽选项 chip 行：同行内各项等宽等高（`weight(1f)` + 固定 48dp）。
 *
 * 宽度交给 `weight` 自适应，所以**不追求跨组等宽** —— 2 项组的单元天然比 5 项组宽，
 * 硬做成固定宽度只会让 2 项组右侧空出大片死白，比不等宽更难看。
 * 真正统一的是：高度 48dp / 圆角 10dp / 边框 1dp / 选中态（主色底 + 主色边）。
 *
 * ⚠️ 选项数 ≥ 4 时不要放进 [SettingRow] 的同行位置，用 [SettingStack]（理由见 [SettingRow]）。
 *
 * [enabled] 为 false 时整行置灰且不可点（压暗规则与 [AvatarOption] 一致：**已选中项保持亮**，
 * 否则玩家会以为「刚才选的那个丢了」）。
 */
@Composable
fun <T> OptionChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        options.forEach { option ->
            OptionChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 单个等宽选项 chip（[OptionChipRow] 的单元；也可单独使用）。 */
@Composable
fun OptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled || selected) 1f else 0.35f)
            .background(
                if (selected) Primary else SurfaceDark,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (selected) Primary else Divider,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = if (selected) OnPrimary else TextPrimaryDark,
            fontSize = 14.sp
        )
    }
}
