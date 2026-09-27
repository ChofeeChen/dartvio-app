package com.dartvio.app.ui.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.dartvio.app.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.beta.BetaAccess
import com.dartvio.app.data.room.PlayerProfileStore
import com.dartvio.app.ui.beta.BetaDemoBanner
import com.dartvio.app.ui.beta.BetaDemoDialog
import com.dartvio.app.data.beta.BetaFeedback
import com.dartvio.app.data.beta.BetaFeedbackAck
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.data.telemetry.UsageIdentity
import com.dartvio.app.domain.achievement.AchievementCatalog
import com.dartvio.app.domain.achievement.AchievementSnapshot
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.profile.LocalProfile
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.ui.practice.NinetyNineStatsStore
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch

/**
 * 「我的」一级页面：**只回答「我是谁」**。
 *
 * 当前阶段账号体系尚未接入，这里只有本机身份（昵称 / 头像）与主题设置。
 * 统计、成就、排行榜、清除数据这些**数据动作**已整体搬进底部「数据」tab ——
 * 它们回答的是「我的数据怎么样」，与身份不是同一件事（2026-09-26 反馈）。
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as? DartVioApp
    val scope = rememberCoroutineScope()
    var showBetaManifest by remember { mutableStateOf(false) }

    // ---- 本机玩家档案（③B 前置 P0） ----
    // 身份卡从写死的展示改成真实档案：昵称可编辑、头像可选，profileId 由 store 保证稳定。
    // 这里读的是档案本身而不是 app.localProfileId：后者只缓存 ID，昵称改了必须立刻反映。
    val profilePrefs = remember(context) { ProfileStore.prefs(context) }
    var profile by remember { mutableStateOf(ProfileStore.ensure(profilePrefs)) }
    var showProfileDialog by remember { mutableStateOf(false) }

    /*
     * 匿名统计的**透明化**：把「我们记了什么」直接摊给用户看，而不是藏在隐私政策里。
     *
     * 两条边界：
     * ① 只有**真会上报**的包才显示（TELEMETRY_ENABLED）。没开统计却摆一个 ID 出来，
     *    等于暗示「我们记录了你」—— 那是骗人，比不显示更糟；
     * ② 每次重组重读，不做缓存：身份是在 Application 里异步建立的，首启刚装好时
     *    还没落盘，读一次 SP 很轻，缓存反而会一直显示「没有」。
     */
    val usageId = when {
        !BuildConfig.TELEMETRY_ENABLED -> null
        else -> UsageIdentity.shortId(UsageIdentity.read(UsageIdentity.prefs(context))?.userId)
            .takeIf { it.isNotBlank() }
    }

    /*
     * 反馈闭环的数字：**每次重组重读，不做 remember**。
     *
     * 与下面 usageId 同一个道理 —— 这个计数是在二级页里改的，回到这一页时
     * 缓存过的旧值不会自己更新，用户会看到「点了 +1 回来还是 0」。
     * 读一次 SP 很轻，这里没有性能理由去省它。
     */
    val feedbackPrefs = remember(context) { BetaFeedback.prefs(context) }
    val feedbackSent = BetaFeedback.sentCount(feedbackPrefs)
    val adoptedCount = BetaFeedbackAck.items.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "我的",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ---- 本地身份卡（点击编辑昵称 / 头像） ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, PROFILE_CARD_SHAPE)
                    .border(1.dp, Divider, PROFILE_CARD_SHAPE)
                    .clickable { showProfileDialog = true }
                    .padding(PROFILE_CARD_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.avatar.emoji,
                        fontSize = 26.sp
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.nickname,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "本机身份 · 数据仅保存在本机 · 未登录",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "编辑",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }

            /*
             * 早期体验者徽章（2026-09-26 反馈：「早期用户的身份认同，比排名更值钱」）。
             *
             * 只发给 Beta 试用包的人：正式版人人都能下载时再挂这枚徽章，它就什么都没表示。
             * 认同感靠的是**稀缺 + 被听见**，所以徽章底下那句话才是重点 ——
             * 「你的反馈我们逐条评估」，而这句由「我的反馈」页里那份采纳清单负责兜住。
             * 不写「会写进下一个版本」：那是一句我们兑现不了的承诺 ——
             * 采纳与否取决于它值不值得做，而不是取决于我们说过什么（2026-09-27 反馈）。
             */
            if (BuildConfig.BETA_DEMO) {
                EarlyTesterBadge()

                /*
                 * Beta 说明卡（原在首页，2026-09-27 反馈后移到这里）。
                 *
                 * 位置：紧跟徽章 —— 两件事说的是同一件事（这是试用版），
                 * 而首页的用途是「开一局」，版本声明不该挡在开局的路上。
                 */
                Spacer(Modifier.height(PROFILE_CARD_GAP))
                BetaDemoBanner(onClick = { showBetaManifest = true })
            }

            Spacer(Modifier.height(PROFILE_SECTION_GAP))

            /*
             * 这一页现在**只留身份卡与卡片式入口**，两张卡片之间是同一个 12dp、
             * 段与段之间是同一个 20dp（2026-09-27 反馈：卡片风格与间隙要统一）。
             *
             * 「对局总数 / 已完成」两块数字卡已删：前者把联机局也算进去、
             * 与「数据」tab 里的口径对不上，后者只是前者的一个子集 ——
             * 两个都答不上「所以呢」的数字，放在身份页里只是占位置。
             *
             * 原先卡片列表上方还有一个「设置」标题：下面第一张卡本身就写着「设置」，
             * 标题与卡片同名，是给同一件事取了两个名字（2026-09-27 反馈：去掉）。
             *
             * 「统计与历史 / 成就墙 / 排行榜 / 清除本地数据」四个入口已整体搬进底部
             * 的「数据」tab：它们回答的都是「我的数据怎么样」，而这一页回答的是
             * 「我是谁」。混在一页上，用户要在同一个列表里同时找身份入口和统计入口，
             * 而两者的使用频率差了一个数量级（2026-09-26 反馈）。
             */

            // 入口是否显示由 FULL_ENTRIES 决定，**不是** BuildConfig.DEBUG：
            // Beta Demo 包要求「入口全开 + 不可调试」，这两件事必须能分别取值。
            if (BuildConfig.FULL_ENTRIES) {
                ProfileItem(
                    title = "设置",
                    desc = "外观与主题 · 版本与关于",
                    onClick = onOpenSettings,
                )
                Spacer(Modifier.height(PROFILE_CARD_GAP))
            }

            /*
             * 隐私声明**无条件显示**（不受 FULL_ENTRIES 约束）：
             * 政策正文第四条写着「之后可在『我的』页查看本政策」，入口若被变体开关藏掉，
             * 那句承诺就成了空话。它也不是「高级入口」—— 任何人都有权知道自己被记了什么。
             */
            ProfileItem(
                title = "隐私声明",
                desc = "数据只存本机 · 匿名统计可随时关闭",
                onClick = onOpenPrivacy,
            )
            Spacer(Modifier.height(PROFILE_CARD_GAP))

            /*
             * 「我的反馈」放在**设置段之外**，也不受 FULL_ENTRIES 约束：
             * 它是 Beta 参与者身份的一部分（提了什么、被采纳多少），
             * 不是「加分的高级入口」。清单里有东西就显示 —— 每版出包时由开发者维护，
             * 空清单意味着这一版没有可回报的采纳，入口也就不该出现。
             */
            if (BetaFeedbackAck.items.isNotEmpty()) {
                ProfileItem(
                    title = "我的反馈",
                    desc = "已提交 $feedbackSent 条 · 已采纳 $adoptedCount 条",
                    onClick = onOpenFeedback,
                )
            }

            Spacer(Modifier.height(20.dp))

            // 体验 ID：让用户能自己核对「被记了什么」。
            // 「不收集隐私」这句话只有能被用户验证时才算数。
            if (usageId != null) {
                Text(
                    "体验 ID $usageId · 只记录这个随机编号与当天日期",
                    fontSize = 11.sp,
                    color = TextDisabledDark,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(6.dp))
            }

            /*
             * 版本号在这里只留**一行小字**（与主流 App 一致：微信 / 抖音的「我的」页不放版本号，
             * 它住在「设置 → 关于」里）。这一行存在的唯一理由是**反馈对得上版本** ——
             * 用户报问题时报的就是这个数。完整的「版本与关于」（Build 号、本版更新说明）
             * 放在「设置」页底部，那里才是「查这是哪个包」的地方（2026-09-27 反馈）。
             */
            Text(
                "DartVio v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            initial = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { rawNickname, avatar ->
                // 先改昵称再改头像：updateXxx 在未建档时返回 null（不隐式建档），
                // 这里只要有一个成功就用最新档案刷新 UI，两个都失败则保持原样。
                val updated = ProfileStore.updateNickname(profilePrefs, rawNickname)
                    ?.let { ProfileStore.updateAvatar(profilePrefs, avatar) }
                if (updated != null) profile = updated
                /*
                 * 立刻同步到「大厅用的那份身份」（房间卡上的房主名、等候页上的成员名
                 * 都读 [LocalUser]，而它是在启动时灌进去的）。
                 *
                 * 走 `PlayerProfileStore.hydrateLocalUser` 而不是直接给 [LocalUser] 赋值：
                 * 昵称 / 头像的**读法**只有一处（[ProfileStore] 是唯一真源），
                 * 在这里自己拼一次读法，就等于又造了一个真源 ——
                 * 上一次正是这么漏的：只改了 `LocalUser.name`，没改房间那份存储，
                 * 于是「我的」页显示新名字、房间里还是旧名字（2026-09-27 真机反馈）。
                 */
                PlayerProfileStore.hydrateLocalUser(context)
                showProfileDialog = false
            },
        )
    }

    if (showBetaManifest) {
        BetaDemoDialog(onDismiss = { showBetaManifest = false })
    }
}

/**
 * 本机身份编辑：昵称 + 头像。
 *
 * 头像复用 [HumanAvatar]（与设置页同一套 4 个真人头像），不引入图片资源。
 * 昵称的空白兜底在这里**预告**（supportingText）而不是保存后静默改名 ——
 * 用户看到「留空将保存为「玩家 1」」才不会以为自己输入丢了。
 */
@Composable
private fun ProfileEditDialog(
    initial: LocalProfile,
    onDismiss: () -> Unit,
    onSave: (rawNickname: String, avatar: HumanAvatar) -> Unit,
) {
    var nickname by remember { mutableStateOf(initial.nickname) }
    var avatar by remember { mutableStateOf(initial.avatar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = TextPrimaryDark,
        textContentColor = TextSecondaryDark,
        title = { Text("本机身份") },
        text = {
            Column {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    singleLine = true,
                    label = { Text("昵称") },
                    placeholder = { Text(LocalProfile.DEFAULT_NICKNAME, color = TextDisabledDark) },
                    supportingText = {
                        Text(
                            if (nickname.isBlank()) {
                                "留空将保存为「${LocalProfile.DEFAULT_NICKNAME}」"
                            } else {
                                "排行榜与对局记录里会显示这个名字"
                            },
                            fontSize = 11.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "头像",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HumanAvatar.ALL.forEach { option ->
                        val selected = option == avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (selected) Primary else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp),
                                )
                                .border(
                                    1.dp,
                                    if (selected) Primary else Divider,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { avatar = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(option.emoji, fontSize = 22.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            // 保存时统一走 normalizeNickname 兜底，UI 侧不再各自判断空串
            TextButton(onClick = { onSave(LocalProfile.normalizeNickname(nickname), avatar) }) {
                Text("保存", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondaryDark)
            }
        },
    )
}

/**
 * 「早期体验者」徽章。
 *
 * 纯展示、不可点击：徽章一旦看着像按钮，用户就会点一下，点了没反应就是纯减分。
 * 到期日取 `BetaAccess.EXPIRE_LABEL` 这个唯一出处，避免和到期页说的日子打架。
 */
@Composable
private fun EarlyTesterBadge() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 卡片四件套与这一页其它卡片**逐项相同**（圆角 / 内边距 / 底色 / 边框）：
            // 只有边框色是主色 —— 徽章是这一页唯一「被授予」的东西，
            // 用主色描边而不是换一套底色，风格统一与「它不一样」才能同时成立。
            .background(SurfaceDark, PROFILE_CARD_SHAPE)
            .border(1.dp, Primary, PROFILE_CARD_SHAPE)
            .padding(PROFILE_CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("早", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "早期体验者",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "DartVio Beta 2026 · 试用期至 ${BetaAccess.EXPIRE_LABEL} · 你的反馈我们逐条评估",
                fontSize = 11.sp,
                color = TextSecondaryDark,
            )
        }
    }
}

/*
 * 卡片样式的**唯一出处**（2026-09-27 反馈：卡片风格要统一）。
 *
 * 此前同一页里有 14dp / 16dp 两种圆角、14dp / 16dp 两种内边距、
 * SurfaceDark / SurfaceVariantDark 两种底色，还有「有边框 / 没边框」两种卡片 ——
 * 四条差异全都不是为了表达什么，只是不同时间写下的。
 * 现在这一页每张卡都取这里的同一组值，改一次就全页生效。
 */
private val PROFILE_CARD_SHAPE = RoundedCornerShape(16.dp)
private val PROFILE_CARD_PADDING = 16.dp
/** 卡与卡之间：同一列里的相邻关系。 */
private val PROFILE_CARD_GAP = 12.dp
/** 段与段之间：身份段 / 入口段 / 页脚。比卡距大一档，段界才看得出来。 */
private val PROFILE_SECTION_GAP = 20.dp

@Composable
private fun ProfileItem(
    title: String,
    desc: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else Accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, PROFILE_CARD_SHAPE)
            .border(1.dp, Divider, PROFILE_CARD_SHAPE)
            .clickable(onClick = onClick)
            .padding(PROFILE_CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp, 40.dp)
                .background(accent, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.size(14.dp))
        Column {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
