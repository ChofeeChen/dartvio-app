package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.CheckoutRushRepository
import com.dartvio.app.domain.practice.RushDifficulty
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 难度在入口页的展示顺序：**混合放最前**（它是默认档，也是大多数人要的），
 * 其后按由易到难。`RushDifficulty.entries` 的声明顺序是「入门/进阶/挑战/混合」，
 * 直接用它会把默认档甩到最后一项。
 */
private val RUSH_DIFFICULTY_ORDER = listOf(
    RushDifficulty.MIXED,
    RushDifficulty.ROOKIE,
    RushDifficulty.ADVANCED,
    RushDifficulty.CHALLENGE,
)

/**
 * 结镖训练入口页。
 *
 * ## 为什么只有一个入口卡（提示词 §五.1）
 * 用户侧「随机结镖」改名为**结镖训练**，但单人训练列表**不新增第二张卡** ——
 * 路线学习与极速挑战是「同一件事的两种练法」，不是两个训练项目。
 * 平铺成两张卡会让人以为要先选一个、练完才能换另一个。
 *
 * ## 两个子模式
 * - **路线学习**：沿用既有 `RandomCheckoutScreen` 的全部行为（默认展开首选路线、可看替代路线），
 *   本页不复制它的任何逻辑，只做跳转。
 * - **极速挑战**：新增计时与完整统计，进 [CheckoutRushScreen]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutTrainingEntryScreen(
    onBack: () -> Unit,
    onRouteStudy: () -> Unit,
    /** 开始极速挑战：把会话规模与难度交给调用方（由它负责开会话 id 并导航）。 */
    onStartRush: (RushSessionKind, RushDifficulty) -> Unit,
) {
    val context = LocalContext.current
    var isRush by remember { mutableStateOf(true) }
    var kindIndex by remember { mutableIntStateOf(0) }
    var difficultyIndex by remember { mutableIntStateOf(0) }

    // 累计题数只作自我介绍，读不到就整行不显示（不显示「0 题」这种噪声）。
    val scoredCount by produceState<Int?>(initialValue = null, context) {
        value = CheckoutRushRepository(context).scoredAttemptCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "结镖训练",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                )
            },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回", color = Primary) } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark,
                navigationIconContentColor = Primary,
                titleContentColor = TextPrimaryDark,
            ),
        )

        // 不滚动：选项卡按内容高度顶排，开始按钮与口径说明钉在底部 ——
        // 入口页只有「选什么 + 开始」两件事，滑一下才看到开始键没有道理。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(RushPagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RushCard(
                title = "练习内容",
                note = "路线学习看答案练记忆；极速挑战记时间练速度",
            ) {
                Spacer(Modifier.height(10.dp))
                RushChoiceRow(
                    options = listOf("路线学习", "极速挑战"),
                    selectedIndex = if (isRush) 1 else 0,
                    onSelect = { isRush = it == 1 },
                )
            }

            if (isRush) {
                Spacer(Modifier.height(12.dp))
                RushCard(title = "会话") {
                    Spacer(Modifier.height(10.dp))
                    RushChoiceRow(
                        options = RushSessionKind.entries.map { it.label },
                        selectedIndex = kindIndex,
                        onSelect = { kindIndex = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        RushSessionKind.entries[kindIndex].desc,
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))
                RushCard(
                    title = "难度",
                    note = "按最短路线镖数与分数高低分档；混合档按 40 / 35 / 25 权重抽取",
                ) {
                    Spacer(Modifier.height(10.dp))
                    RushChoiceRow(
                        options = RUSH_DIFFICULTY_ORDER.map { it.label },
                        selectedIndex = difficultyIndex,
                        onSelect = { difficultyIndex = it },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            RushPrimaryButton(
                text = if (isRush) "开始挑战" else "开始路线学习",
                onClick = {
                    if (isRush) {
                        onStartRush(
                            RushSessionKind.entries[kindIndex],
                            RUSH_DIFFICULTY_ORDER[difficultyIndex],
                        )
                    } else {
                        onRouteStudy()
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                buildString {
                    append("本训练为自报训练数据，不进入正式对局历史、")
                    append("正式 Checkout 率、成就排名与排行榜。")
                    val count = scoredCount
                    if (count != null && count > 0) {
                        append("\n累计已完成 $count 题。")
                    }
                },
                fontSize = 11.sp,
                color = TextSecondaryDark,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
