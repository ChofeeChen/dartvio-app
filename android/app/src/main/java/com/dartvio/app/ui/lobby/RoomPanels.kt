package com.dartvio.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.model.HumanAvatar
import java.util.Locale
import com.dartvio.app.domain.room.SpectatorPlayer
import com.dartvio.app.domain.room.SpectatorSnapshot
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 「房间对局」的两个页面（观战页、对局页）共用的面板。
 *
 * ## 为什么要抽出来
 *
 * 一块比分板被两个页面各自实现一遍，最危险的差别不是「长得不一样」，而是**语义**不一样：
 * 观战页的 `score` 是权威帧里的剩余分，对局页若自己再算一次，就会出现「两个界面同屏对比时比分不同」
 * ——而那时你已经不知道该信哪一个了。这里让两个页面看到的分数来自同一段渲染代码，
 * 于是「不一致」在结构上不可能发生。
 *
 * 两个页面的差别（谁在录镖、按什么口径提示）留在各自页面里，不塞进这些组件。
 */

/**
 * 对局页 / 观战页的左右留白。
 *
 * 原来是 20dp：两块玩家卡片加上中间的 12dp 间隙之后，卡片宽度只剩屏幕的六成，
 * 三位数的剩余分在窄屏（vivo）上直接顶破卡片。这里减到 12dp（约 −40%），
 * 把宽度还给分数本身 —— 留白在这类页面上是纯装饰，分数才是被读的东西。
 */
internal val MATCH_GUTTER = 12.dp

/**
 * 房间类页面的通用顶栏（返回 + 标题 + 右侧一行扩充信息）。加状态栏避让，与 X01 对战页的顶行高度对齐。
 *
 * [meta] 是「此刻打到了哪儿」的那一行（`X01-501 · LEG 1 · R 3 · SI-DO`），放在**房间名右侧**
 * 而不是单独占一张卡（2026-09-27 反馈）：原本它单独一张横卡夹在房间名与玩家卡片之间，
 * 说的却是同一件事的定语 —— 独立成卡之后，屏幕上多出一条与比分无关的横条，
 * 而这一屏最贵的正是纵向空间。
 */
@Composable
internal fun RoomTopBar(
    title: String,
    onBack: () -> Unit,
    /** 右侧补充信息；空串 = 不占位置（观战页等场合）。 */
    meta: String = ""
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
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            // 长房间名时**让位给右侧的规则信息**：那一行才是要被读的，房间名认得出就行。
            modifier = Modifier.weight(1f, fill = false)
        )
        if (meta.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                meta,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                maxLines = 1
            )
        }
    }
}

/**
 * 顶栏右侧那一行：**玩法 + 局数 + 轮次 + 规则**。
 *
 * 四项顺序刻意固定：先看打的是什么（X01-501），再看打到第几局（LEG），
 * 然后才是这一局第几轮（R）与进出镖口径（SI-DO）。
 * 「对局中 / 你的回合」这类状态字眼**不再出现在这里** —— 谁在投由卡片的高亮与那只小圆点说，
 * 一句话重复同一件事，只会把规则信息挤下去。
 */
internal fun matchMetaOf(snapshot: SpectatorSnapshot): String = buildString {
    append(snapshot.configName)
    append(" · LEG ").append(snapshot.leg)
    append(" · R ").append(currentRound(snapshot))
    if (snapshot.rulesShort.isNotBlank()) append(" · ").append(snapshot.rulesShort)
}

/**
 * 对局信息条（玩法 + 局数）。
 *
 * [badge] 由调用方给：观战页写「观战中」，对局页写「对局中」/「你的回合」——
 * 这句话是页面**立场**的表达，不该由组件替页面决定。
 */
/**
 * 玩家卡片上方那一行：**这一刻打到了哪儿**（第几局 / 第几轮 / 什么规则）。
 *
 * [isSpectator] 为真时最右侧挂一枚灰眼睛：观战页与对局页共用这一行，
 * 而「我在看」和「我在打」对同一个人是两种完全不同的处境 ——
 * 少了这枚眼睛，观战者会在「轮到你了」之类的提示里困惑为什么自己动不了手。
 */
@Composable
internal fun MatchInfoBar(
    snapshot: SpectatorSnapshot,
    badge: String,
    isSpectator: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MATCH_GUTTER)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Secondary.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Secondary)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            snapshot.configName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        // LEG / R / 规则短码：三项都用「词 + 数字」的紧凑写法（对局页横向空间按字符算），
        // 规则短码为空（非 X01）时连分隔符一起省掉，不留一个空的「·」。
        Text(
            buildString {
                append("LEG ").append(snapshot.leg)
                append(" · R ").append(currentRound(snapshot))
                if (snapshot.rulesShort.isNotBlank()) append(" · ").append(snapshot.rulesShort)
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isSpectator) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Visibility,
                contentDescription = "观战中",
                tint = TextDisabledDark,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 当前第几轮（本局内），从 1 开始。
 *
 * 「轮」= 一位选手的一次三镖，与飞镖圈的叫法一致（一局里两人交替各投一轮）。
 * 本局的起点按**最后一次结镖**切：结镖之后回合流水不重来，但局面是新的，
 * 继续按总回合数报「R 27」会把新的一局说得像已经打了二十几轮。
 */
internal fun currentRound(snapshot: SpectatorSnapshot): Int {
    val lastCheckout = snapshot.turns.indexOfLast { it.isCheckout }
    val turnsThisLeg = snapshot.turns.size - (lastCheckout + 1)
    return turnsThisLeg + 1
}

/**
 * **本局**的实时 PPR（每轮三镖的平均得分），没在本局投过镖就返回 null。
 *
 * 为什么只算本局：卡上的「PPR 42.6」（取自成员档案）说的是**这个人**，
 * 而这两张卡在这一屏要回答的是**这一局谁打得更好** —— 混在一起，
 * 一个开局手感差的人会看起来永远落后，而这个数正在实时变化的事实就没人看到了。
 *
 * 为什么只用**已提交**的回合算（不含正在录的草稿）：草稿还没被主机裁定，
 * 把它算进来等于替主机宣布了一个尚未生效的分母。
 *
 * 爆分回合照常计入（得 0 分、镖数计入）：它真实占用了这一轮的镖，
 * 剔除它会让 PPR 变成一个只统计「发挥好的回合」的漂亮数字。
 */
internal fun currentLegPpr(snapshot: SpectatorSnapshot, playerName: String): Double? {
    val legStart = snapshot.turns.indexOfLast { it.isCheckout } + 1
    val mine = snapshot.turns.drop(legStart).filter { it.playerName == playerName }
    val scored = mine.sumOf { if (it.isBust) 0 else it.scored }
    val darts = mine.sumOf { countDarts(it.darts) }
    if (darts <= 0) return null
    return scored / (darts / 3.0)
}

@Composable
internal fun Scoreboard(
    snapshot: SpectatorSnapshot,
    /** 本机正在录镖时的实时扣减（草稿预览）：从正在投掷的玩家分数里当场减掉。 */
    previewDeduct: Int = 0,
    /** 「我」的 id（已投影成界面身份）；null = 本机不在这份帧里（观战）。 */
    selfId: String? = null
) {
    Row(
        // 上 8dp（与导航栏的间隙）、下 8dp（与下方区域的间隙）：
        // 2026-09-27 反馈把段间距统一收到 8dp —— 这一屏要塞五段内容，
        // 留白是五段共用的预算，每段多 6dp 整屏就少一截表格。
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MATCH_GUTTER)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        snapshot.players.forEach { player ->
            PlayerScoreCard(
                player = player,
                previewDeduct = previewDeduct,
                isSelf = selfId != null && player.id == selfId,
                legPpr = currentLegPpr(snapshot, player.name),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerScoreCard(
    player: SpectatorPlayer,
    modifier: Modifier = Modifier,
    /** 见 [Scoreboard.previewDeduct]；只对正在投掷的玩家生效。 */
    previewDeduct: Int = 0,
    /** 这一位是**本机玩家**（2026-09-27 反馈：两台手机上要一眼认出哪张卡是自己）。 */
    isSelf: Boolean = false,
    /** 本局实时 PPR（见 [currentLegPpr]）；null = 本局还没投过镖，不显示。 */
    legPpr: Double? = null
) {
    // fromKey 自带兜底（未知 key → HUMAN_1），因此这里不需要再包一层 try ——
    // 也**不能**包：吞掉异常会让「头像 key 拼错」变成永久显示默认头像，无人发现。
    val emoji = HumanAvatar.fromKey(player.avatar).emoji
    val accent = if (player.isActive) Primary else Divider
    // 权威分只在回合结束推进，逐镖录入时先本地扣掉已确认的镖（显示预览，不改权威状态）：
    // 不然到结镖回合，卡片上的分比确认键上的预览落后最多三镖，没法判断能不能一镖收尾。
    val shown = if (player.isActive) player.score - previewDeduct else player.score

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(if (player.isActive) 2.dp else 1.dp, accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：头像 + 昵称（上下排列），昵称与副文本同为小字号
        // weight(1f)：窄屏（如 vivo 的小宽度机型）上把剩余空间让给昵称，
        // 长昵称自己截断成省略号，而不是顶到右边把分数挤出去。
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 17.sp)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player.name,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                /*
                 * 本机玩家：昵称右侧括号标注（2026-09-27 真机反馈）。
                 *
                 * 「哪一边是我」此前只能靠「谁的名字是我自己起的」去猜 —— 两人同屏时，
                 * 一旦对手昵称也是常见名（或干脆同名），玩家会在错误的那张卡上等自己的回合。
                 * 用括号而不是换色：色态已经被「轮到谁」占用了，再加一层意思就分不清了。
                 */
                if (isSelf) {
                    Text(
                        "（本机）",
                        fontSize = 9.sp,
                        color = TextSecondaryDark,
                        maxLines = 1
                    )
                }
                if (player.isActive) {
                    Spacer(Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
            }
            /*
             * X01 转播惯例的两件事：**已赢局数** 与 **PPR**（2026-09-27 反馈）。
             *
             * 只剩一个大号剩余分是不够的：两张卡并排时，剩余分只说「这一局打到哪」，
             * 说不出「谁占优、还是只是这局手气好」。局分讲**这一场**，PPR 讲**这个人** ——
             * 转播里并排的两张卡正是靠这两行让人一眼看出强弱。
             *
             * PPR 拿不到就不写：写 0.0 会被读成「他很菜」，而真实含义只是「他还没打过正式赛」。
             */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "已赢 ${player.legsWon} 局",
                    fontSize = 10.sp,
                    color = TextSecondaryDark
                )
                val ppr = player.ppr?.takeIf { it > 0.0 }
                if (ppr != null) {
                    Text("  ·  ", fontSize = 10.sp, color = TextDisabledDark)
                    Text(
                        "PPR " + String.format(Locale.US, "%.1f", ppr),
                        fontSize = 10.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }
        // 右：当前分数（大号白字，右对齐占满余宽）。
        // 字号按位数收窄，且整体比早期小一档：三位（301/501）与四位（701/901）在窄屏上
        // 用 60sp 会顶破卡片、与左边昵称叠在一起（vivo 机型实测，2026-09-26）。
        // 现在的上限 46sp 是「卡片一半宽度放得下三位数」推出来的，不是拍的。
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    shown.toString(),
                    fontSize = when (shown.toString().length) {
                        1 -> 46.sp
                        2 -> 42.sp
                        3 -> 36.sp
                        else -> 28.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                /*
                 * 分数正下方：**本局实时 PPR**（2026-09-27 反馈）。
                 *
                 * 剩余分只说「这一局还剩多少」，说不出「谁今天手感更好」——
                 * 一个每轮 60 分、开局落后的人在数据表里其实占优，而这件事此前在界面上不存在。
                 * 它是**本局**的（不含档案里那个历史 PPR），因此会随着每一手实时变化，
                 * 用户可以拿它验证自己刚刚那一轮到底打得怎么样。
                 *
                 * 本局没投过镖就不显示：写 0.0 会被读成「他很菜」，而真实含义只是「还没轮到」。
                 */
                if (legPpr != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "本局 PPR " + String.format(Locale.US, "%.1f", legPpr),
                        fontSize = 10.sp,
                        color = TextSecondaryDark,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 记分表的一行：**左两列是左侧玩家、右两列是右侧玩家，中间一列是本局累计镖数**。
 *
 * 行的口径对齐 n01 转播记分表（2026-09-27 反馈，附截图）：
 * **一行 = 双方各投完一轮**，不是一方的一轮。此前「一人一轮一行」的流水式摆法，
 * 两人交替投时同一轮的成绩分散在上下两行，「你剩 40、我剩 100」要隔着行对；
 * n01 的摆法把同一轮并排放在一行里，两侧同屏对照 —— 这正是这张表存在的目的。
 *
 * [scored] 为 null 表示这一位**这一轮还没投**（不是 0 分）。
 */
/** 记分表一行的性质。 */
internal enum class ScoreRowKind {
    /** 某一局的**起始行**：只写双方初始剩余分（如 501），没有得分，镖数列为 0。 */
    LEG_START,

    /** 一轮：双方各自的回合总得分与剩余分（缺投的一侧为 null）。 */
    ROUND,
}

internal data class ScoreTableRow(
    val kind: ScoreRowKind,
    val legIndex: Int,
    /** 本局的初始分；[ScoreRowKind.LEG_START] 行显示的就是它。 */
    val startScore: Int,
    /**
     * 本局第几轮（[ScoreRowKind.LEG_START] 行为 0）。
     *
     * 镖数列显示 `roundIndex * 3`：n01 的镖数列从首行起是 0、3、6、9…（2026-09-27 反馈），
     * 它回答的是「打到第几轮」而不是「这一轮投了几镖」—— 后者是三镖槽位的事。
     * 收镖那轮可能只投 1..2 镖，仍按 3 计：镖数列是**轮**的刻度，不是镖的流水。
     */
    val roundIndex: Int,
    /** 左侧玩家这一轮的回合总得分；null = 还没投。爆分写 "X"。 */
    val leftScored: String?,
    /** 右侧玩家这一轮的回合总得分；null = 还没投。 */
    val rightScored: String?,
    val leftToGo: Int,
    val rightToGo: Int,
    /** 0 = 完整行；1 = 左侧正在投（格子显示占位）；2 = 右侧正在投。 */
    val pendingSide: Int = 0,
)

/** 三镖文本 → 镖数（"T20 T20 D20" → 3）。空文本记 0，不会让累计镖数虚高。 */
internal fun countDarts(darts: String): Int = darts.split(" ").count { it.isNotBlank() }

/**
 * 把回合流（[SpectatorSnapshot.turns]，按 seq 升序）回放成记分表。
 *
 * 纯函数、不碰 Compose：两侧剩余分的推进规则（爆分不变、收镖归零、结镖后双方回到初始分）
 * 是这段逻辑的全部难点，抽出来才能单测，而不是靠打一局真机去发现。
 *
 * ## 按轮配对
 *
 * 回合流是「一人一轮」的流水；这里把它**两两配对**成 n01 式的一行
 * （同一轮里双方的得分并排）。一轮在两种时刻落表：
 * 双方都投完（完整行），或一方收镖（本局结束，缺的一侧不再有这一轮）。
 * 流水末尾只剩一方投完时，落成 [ScoreTableRow.pendingSide] 占位行 ——
 * 它指出「对方的数字将出现在哪一格」。
 *
 * ## 多局的处理
 *
 * 一局收镖（[SpectatorTurn.isCheckout]）之后，双方剩余分**回到初始分**，
 * 并插入一行 [ScoreRowKind.LEG_START]：它既是新局的起点，界面上也靠它在两局之间画粗分隔线。
 */
internal fun buildScoreTable(snapshot: SpectatorSnapshot): List<ScoreTableRow> {
    val leftName = snapshot.players.firstOrNull()?.name ?: return emptyList()
    val turns = snapshot.turns
    if (turns.isEmpty()) return emptyList()

    // 起始分由第一回合反推：爆分时 remaining 是「回合前的剩余」，得分为 0，
    // 于是两种情况下 `remaining + (爆分 ? 0 : scored)` 都等于回合开始前的分数。
    val first = turns.first()
    val startScore = first.remaining + if (first.isBust) 0 else first.scored

    val rows = mutableListOf<ScoreTableRow>()
    var legIndex = 1
    var leftToGo = startScore
    var rightToGo = startScore

    fun legStartRow() = ScoreTableRow(
        kind = ScoreRowKind.LEG_START,
        legIndex = legIndex,
        startScore = startScore,
        roundIndex = 0,
        leftScored = null,
        rightScored = null,
        leftToGo = leftToGo,
        rightToGo = rightToGo
    )

    rows += legStartRow()

    var leftScored: String? = null
    var rightScored: String? = null

    fun flushRound(pendingSide: Int = 0) {
        if (leftScored == null && rightScored == null) return
        val roundIndex = rows.count { it.kind == ScoreRowKind.ROUND && it.legIndex == legIndex } + 1
        rows += ScoreTableRow(
            kind = ScoreRowKind.ROUND,
            legIndex = legIndex,
            startScore = startScore,
            roundIndex = roundIndex,
            leftScored = leftScored,
            rightScored = rightScored,
            leftToGo = leftToGo,
            rightToGo = rightToGo,
            pendingSide = pendingSide
        )
        leftScored = null
        rightScored = null
    }

    for (turn in turns) {
        val leftIsThrower = turn.playerName == leftName
        // 防御：同一人连投两轮（规则上不会发生，但帧是不可信输入）时先把上一轮落表，
        // 否则新一轮的得分会覆盖上一轮 —— 表上凭空少一行，剩余分却少扣了一次。
        if (leftIsThrower && leftScored != null) flushRound()
        if (!leftIsThrower && rightScored != null) flushRound()
        // 爆分不减分，写成 X 而不是 0 —— 0 会被读成「这一镖没得分」，X 才是「这一回合白投」。
        val text = if (turn.isBust) "X" else turn.scored.toString()
        if (leftIsThrower) {
            leftScored = text
            leftToGo = turn.remaining
        } else {
            rightScored = text
            rightToGo = turn.remaining
        }
        if (turn.isCheckout) {
            flushRound()
            legIndex += 1
            leftToGo = startScore
            rightToGo = startScore
            rows += legStartRow()
        } else if (leftScored != null && rightScored != null) {
            flushRound()
        }
    }
    // 收尾：这一轮只有一方投完（对方正在投 / 掉线前没投）→ 占位行，缺的一侧由界面画「···」。
    if (leftScored != null || rightScored != null) {
        flushRound(if (leftScored == null) 1 else 2)
    }
    return rows
}

/*
 * 记分表底色：比玩家卡片**更暗、更弱**。
 *
 * 同一屏里最亮的必须是剩余分（那是要被读的东西），而卡片式的浅底会让
 * 「一格一格的方块」抢走注意力 —— 表格要像表格，不要像一排按钮。
 */
private val TABLE_BACKGROUND: Color
    @Composable get() = SurfaceVariantDark.copy(alpha = 0.45f)

/** 表头字号：**与比赛大厅里的房间名同级**（16sp）—— 表头是这一屏的列名，不是注释。 */
private val TABLE_HEADER_SIZE = 16.sp

/**
 * 表格里**所有数字**的统一字号（14sp）。
 *
 * 此前「得分 11sp / 剩余 14sp / 镖数 10sp」三档：本意是分层，实际效果是同一行里三个数字
 * 基线不同、纵向缺一条可跟随的读数线（2026-09-27 反馈）。现在三列同字号、默认**不加粗**，
 * 层级交给位置与颜色；只有 ≥100 的得分与结镖才被加重（见 [scoredStyleOf]）。
 */
private val TABLE_NUMBER_SIZE = 14.sp

/**
 * 表格数字的常规底色：白里掺灰。
 *
 * 压 onSurface 的透明度而不是写死一个灰值：换主题时它跟着走，
 * 而写死的灰会在别的配色里变成「深灰字压在浅底」的另一套东西。
 */
private const val TABLE_NUMBER_ALPHA = 0.72f

/** 占位字符（投掷进行中）：它不是数据，因此用省略号而不是 0 —— 0 会被读成「得分就是 0」。 */
private const val PENDING_TEXT = "···"

/** 得分是否值得加重：**≥100 的回合**是这一局里值得回头看的那一轮。 */
private fun scoreFontWeightOf(scored: String?): FontWeight {
    val value = scored?.toIntOrNull() ?: return FontWeight.Normal
    return if (value >= 100) FontWeight.Bold else FontWeight.Normal
}

/**
 * 得分的颜色：结镖绿（剩余分归零的那一侧）、爆分红（文本 "X"），其余交给常规底色。
 *
 * 结镖用「剩余分 == 0」判断而不是多存一个布尔：剩余归零只有收镖一种解释，
 * 数据模型里不必为此多带一个字段。
 */
@Composable
private fun scoredColorOf(scored: String?, toGo: Int, base: Color): Color = when {
    scored == null -> base
    scored == "X" -> Accent
    toGo == 0 -> Success
    else -> base
}

/**
 * 回合记分表（开局 → 最新，自上而下）。[modifier] 决定它占多少高度。
 *
 * 表格跟着最新一行走：新回合进来时自动滚到底，用户不必自己往下拖。
 *
 * 「回合记录」这个标题已去掉（2026-09-27 反馈）：表头那两行已经写清了谁对着哪两列，
 * 在这之上再来一个标题，是给同一件事取了第三个名字。
 *
 * 占位行有两个来源：
 * - [buildScoreTable] 落的 `pendingSide` 行（一方已投、对方还没投，来自帧里的流水）；
 * - 这里现场补的一行（双方都已落表、但**有人正要投**——表格尾部空着时，
 *   看的人分不清是卡住了还是还没轮到）。
 */
@Composable
internal fun TurnHistory(
    snapshot: SpectatorSnapshot,
    modifier: Modifier = Modifier,
    /** 「我」的 id；用于在本侧列名旁标注「（本机）」。 */
    selfId: String? = null,
    /**
     * **正在投掷**的人（名字）；表尾缺占位行时现场补一行。
     *
     * 这一行回答的是「下一组数字会出现在哪儿」（2026-09-27 反馈）。
     */
    pendingName: String? = null
) {
    val rows = remember(snapshot) { buildScoreTable(snapshot) }
    val listState = rememberLazyListState()

    val left = snapshot.players.getOrNull(0)
    val right = snapshot.players.getOrNull(1)
    val leftName = left?.name ?: "选手 1"
    val rightName = right?.name ?: "选手 2"

    // 表尾缺占位行（最后一行是完整行 / 起始行 / 表为空）时，现场补一行。
    // 它只描述「谁正要投」，数字全部沿用上一行带下来的真实剩余分。
    val pendingRow: ScoreTableRow? = if (pendingName != null && rows.lastOrNull()?.pendingSide == 0) {
        val last = rows.lastOrNull()
        val currentLeg = last?.legIndex ?: 1
        ScoreTableRow(
            kind = ScoreRowKind.ROUND,
            legIndex = currentLeg,
            startScore = last?.startScore ?: snapshot.players.firstOrNull()?.score ?: 0,
            roundIndex = rows.lastOrNull {
                it.kind == ScoreRowKind.ROUND && it.legIndex == currentLeg
            }?.roundIndex?.plus(1) ?: 1,
            leftScored = null,
            rightScored = null,
            leftToGo = last?.leftToGo ?: (left?.score ?: 0),
            rightToGo = last?.rightToGo ?: (right?.score ?: 0),
            pendingSide = if (pendingName == leftName) 1 else 2
        )
    } else {
        null
    }

    val itemCount = rows.size + if (pendingRow != null) 1 else 0
    // 待投掷那一行也算「最新一行」：投的人在看的正是它。
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (rows.isEmpty() && pendingRow == null) {
            Text(
                "对局刚刚开始，暂无回合记录",
                fontSize = 12.sp,
                color = TextSecondaryDark,
                modifier = Modifier.padding(horizontal = MATCH_GUTTER)
            )
            return@Column
        }

        // 表头两行：**谁**（占本侧两列）与**五个字段**（各占一列，横向对齐）。
        // 五个字段的字号与大厅房间名同级、列居中、彼此同一基线 ——
        // 之前列名是 10sp 且分散在两行里，扫一眼看不出哪两列属于同一个人。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MATCH_GUTTER)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(TABLE_BACKGROUND)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TableCell(
                    text = leftName + if (selfId != null && left?.id == selfId) "（本机）" else "",
                    modifier = Modifier.weight(2f),
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.Medium,
                    align = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
                TableCell(
                    text = rightName + if (selfId != null && right?.id == selfId) "（本机）" else "",
                    modifier = Modifier.weight(2f),
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.Medium,
                    align = TextAlign.Center
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TableHeaderCell("得分")
                TableHeaderCell("剩余")
                TableHeaderCell("镖数")
                TableHeaderCell("得分")
                TableHeaderCell("剩余")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = MATCH_GUTTER, end = MATCH_GUTTER, bottom = 24.dp)
        ) {
            itemsIndexed(rows) { index, row ->
                // 局的边界画**粗线**：同一局里行与行只隔一条细线，
                // 局与局之间必须是另一种重量 —— 否则「打了几局」要靠人数 501 出现的次数。
                if (row.kind == ScoreRowKind.LEG_START && row.legIndex > 1) {
                    LegDivider()
                }
                ScoreTableRowView(row)
            }
            if (pendingRow != null) {
                item { ScoreTableRowView(pendingRow) }
            }
        }
    }
}

/**
 * 局与局之间的**粗分隔线**。
 *
 * 为什么不是再加一行「第 2 局」：那一行的信息（第几局）已经在顶栏的 `LEG x` 里，
 * 重复一遍就等于用一整行的高度买同一个事实；而线本身就是最省高度的分段手段。
 */
@Composable
private fun LegDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Primary.copy(alpha = 0.55f))
    )
}

@Composable
private fun RowScope.TableHeaderCell(text: String) {
    TableCell(
        text = text,
        modifier = Modifier.weight(1f),
        fontSize = TABLE_HEADER_SIZE,
        color = TextSecondaryDark,
        fontWeight = FontWeight.Medium,
        align = TextAlign.Center
    )
}

/**
 * 一行记分（表格线版）：起始行、完整轮与占位轮共用同一个渲染。
 *
 * 此前一行是一张**卡片**（圆角 + 浅底 + 4dp 间隔）：一行占了近 40dp，
 * 一屏只放得下七八个回合，而一局往往有二十几个 —— 于是「看趋势」这件事
 * 只能靠滚动脑补。现在一行只是一条**细线 + 一行数字**。
 *
 * 数字同字号（14sp）、默认不加粗、颜色是掺了灰的白：
 * 真正要加重的是 ≥100 的那一轮与绿色的结镖，而不是每种字段各自争先。
 *
 * - 起始行：得分列空、镖数列 0、双方剩余分 = 初始分（如 501）—— 这一局的 zero。
 * - 轮行：镖数列 = 本局累计（3 的整数倍，0 起头）；得分列写**回合总得分**，
 *   剩余列写减完这一轮之后的分；没投的一侧留空。
 * - 占位轮（[ScoreTableRow.pendingSide]）：待投侧写 `···` 并给整行一层极淡主题色底
 *   —— 指出下一组数字会落在哪一格（2026-09-27 反馈）。
 */
@Composable
private fun ScoreTableRowView(row: ScoreTableRow) {
    val numberColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TABLE_NUMBER_ALPHA)
    val pendingColor = Primary

    val leftScoredText = when {
        row.leftScored != null -> row.leftScored
        row.pendingSide == 1 -> PENDING_TEXT
        else -> ""
    }
    val rightScoredText = when {
        row.rightScored != null -> row.rightScored
        row.pendingSide == 2 -> PENDING_TEXT
        else -> ""
    }
    val leftColor = if (row.pendingSide == 1 && row.leftScored == null) {
        pendingColor
    } else {
        scoredColorOf(row.leftScored, row.leftToGo, numberColor)
    }
    val rightColor = if (row.pendingSide == 2 && row.rightScored == null) {
        pendingColor
    } else {
        scoredColorOf(row.rightScored, row.rightToGo, numberColor)
    }
    val dartsText = if (row.kind == ScoreRowKind.LEG_START) "0" else (row.roundIndex * 3).toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TABLE_BACKGROUND)
            .then(
                if (row.pendingSide != 0) Modifier.background(pendingColor.copy(alpha = 0.10f)) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(
                text = leftScoredText,
                modifier = Modifier.weight(1f),
                fontSize = TABLE_NUMBER_SIZE,
                color = leftColor,
                fontWeight = scoreFontWeightOf(row.leftScored),
                align = TextAlign.Center
            )
            TableCell(
                text = row.leftToGo.toString(),
                modifier = Modifier.weight(1f),
                fontSize = TABLE_NUMBER_SIZE,
                color = numberColor,
                fontWeight = if (row.kind == ScoreRowKind.LEG_START) FontWeight.Medium else FontWeight.Normal,
                align = TextAlign.Center
            )
            TableCell(
                text = dartsText,
                modifier = Modifier.weight(1f),
                fontSize = TABLE_NUMBER_SIZE,
                color = numberColor,
                fontWeight = FontWeight.Normal,
                align = TextAlign.Center
            )
            TableCell(
                text = rightScoredText,
                modifier = Modifier.weight(1f),
                fontSize = TABLE_NUMBER_SIZE,
                color = rightColor,
                fontWeight = scoreFontWeightOf(row.rightScored),
                align = TextAlign.Center
            )
            TableCell(
                text = row.rightToGo.toString(),
                modifier = Modifier.weight(1f),
                fontSize = TABLE_NUMBER_SIZE,
                color = numberColor,
                fontWeight = if (row.kind == ScoreRowKind.LEG_START) FontWeight.Medium else FontWeight.Normal,
                align = TextAlign.Center
            )
        }
        // 表格线：取代卡片。用分隔色（而非主题色）且调到半透明 ——
        // 线的职责是「把两行分开」，不是「被看见」。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider.copy(alpha = 0.45f))
        )
    }
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    fontWeight: FontWeight,
    align: TextAlign
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        textAlign = align,
        maxLines = 1
    )
}

/**
 * 居中的整屏提示：一个标题、一句说明、可选的补充口径与一个出口按钮。
 *
 * [actionLabel] 由调用方给：观战页的出口是「返回大厅」，对局页的出口是「退出对局」——
 * 两者对用户是不同的事（后者意味着放弃这一局），组件不该替页面把话说死。
 */
@Composable
internal fun NoticePanel(
    title: String,
    message: String,
    hint: String?,
    showProgress: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                color = Primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                color = TextDisabledDark
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Primary)
                .clickable(onClick = onAction)
                .padding(horizontal = 22.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                actionLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimary
            )
        }
    }
}
