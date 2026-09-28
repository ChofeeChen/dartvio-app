package com.dartvio.app.ui.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.data.room.TurnAck
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.room.RoomMatchRules
import com.dartvio.app.domain.room.RoomMatchView
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.ui.components.DartKeypad
import com.dartvio.app.ui.components.Multiplier
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 提交之后等权威帧的上限。超过它就把键盘还回来（草稿保留，可重试）。
 *
 * 这个数字不是体验参数而是**兜底**：局域网内一次提交的回程在几十毫秒量级，
 * 5 秒还没到基本就是丢了（被拒、丢包、或主机不理）。没有它，一次失败的提交会把键盘
 * 永久锁在「等待确认」上，用户唯一能做的是退出重进。
 */
private const val SUBMIT_ACK_TIMEOUT_MS = 5_000L

/**
 * 撤销倒计时的走秒间隔（M5 T7）。
 *
 * 与 [SUBMIT_ACK_TIMEOUT_MS] 不同，这个数字**只影响提示**：按钮走到 0 就自己消失，
 * 而真正的裁定始终是服务端那一次拒绝。因此它不必精确，取一秒只是为了让数字看起来在走。
 */
private const val UNDO_TICK_MS = 1_000L

/**
 * 缺席倒计时的走秒间隔（M5 T8）。
 *
 * 与 [UNDO_TICK_MS] 一样只影响**提示**：数字走到 0 就自己消失，
 * 而判负由服务端说了算 —— 它只会作为一帧（[RoomMatchView.finish]）到达。
 */
private const val ABSENCE_TICK_MS = 1_000L

/**
 * 键盘区的**固定高度**（2026-09-28 反馈：按键太小容易按错，在 200dp 基础上**加高 40%** ⇒ 280dp）。
 *
 * 键盘常驻屏幕底部且高度不随回合变化：它一收缩/展开，上面的记分表就跟着跳，
 * 用户刚找到「最新一行在哪儿」又得重新找。
 *
 * 原 200dp 是「五排按键每排约 36dp + 间隙」的下限，实测偏矮、容易误触；
 * 加高到 280dp 后每排约 50dp，误触明显下降。上方记分表是 `weight(1f)`，
 * 会自动吸收这 80dp 的增量，不会把其它区挤出屏幕。
 */
private val KEYBOARD_HEIGHT = 280.dp

/**
 * 联机对局页（M5 实时同步）。
 *
 * ## 它与观战页的根本差别
 *
 * 观战页是**只读**的：谁都不能改比分，只能看主机推来的帧。
 * 本页多了一件事：轮到我的时候，我能在自己的手机上录三支镖，并把它**提交给主机裁定**。
 * 但注意 —— 本页自己**不算分**：[RoomMatchRules] 那一套只在主机上跑，
 * 我提交的是「我投了什么」（[Dart] 列表），回来的才是「现在几分」。
 * 因此这个界面从头到尾没有任何一处「本地改分」的代码路径（BL-001）。
 *
 * ## 键盘什么时候出现
 *
 * 只在权威帧说「轮到你了」的时候（[RoomMatchView.canThrow]）。
 * 这个判断放在数据层做，因为帧里的 id 是**连接 id**，只有数据层知道本机在线上是谁。
 * 若放到界面里比 `LocalUser.ID`，键盘会在联机下永不出现 —— 而且不报任何错。
 *
 * ## 有意不做
 *
 * - **不预判爆分/收镖**：确认键右侧的预览只做「目标分 − 待提交分」，不做规则试算。
 *   试算等于在客户端复制一份 X01 规则，它一旦与主机不一致，用户看到的就是
 *   「预览说能收、主机的帧说爆分」。宁可预览朴素，也不给一个可能与权威矛盾的答案。
 * - **撤回已做（T7）**：上一手投完的 5 秒内可撤回。按钮与倒计时都由**帧**里的撤销窗口驱动，
 *   而真正的裁定在服务端 —— 窗口外点了会被拒，本页只是多一句提示，不会本地改分。
 *   本页也不判断「该不该关窗口」：它只显示主机说「还能撤」，并把自己这一回合的
 *   **第一次落指**上报给主机（`player_activated`）—— 对手一动手，上一手的窗口就关了。
 * - **不做跨回合撤回**：只撤最后一手。允许指定回合号去撤更早的一手，等于让对局历史可编辑。
 * - **版本冲突已做（T6）**：提交带 `expect_version`，主机发现我手里的帧过期就回冲突，
 *   本页据此**清掉草稿**并以最新帧为准；而超时或被拒则**保留草稿**让用户重试。
 *   两者处置相反，理由见 [TurnAck]。
 */
@Composable
fun RoomMatchScreen(
    roomId: String,
    onExit: () -> Unit,
    /**
     * 对局结束 → 结算页（M5 T9）。**一次性**回调，见下方 `navigatedToResult`。
     */
    onMatchOver: (String) -> Unit
) {
    val repo = RoomRepositoryProvider.current
    /*
     * 系统返回键与顶栏箭头走同一条路（2026-09-27 真机反馈）。
     *
     * 此前只有顶栏箭头会退出，系统返回键被 NavHost 直接出栈：于是在真机上
     * 「用返回键退出」的那一次房间永远收不到离开事件 —— 大厅留下一间没人接手的房。
     * 退出是一个**业务动作**（要通知服务端），不能只由导航组件顺手完成。
     */
    BackHandler(onBack = onExit)

    val view by repo.observeMatch(roomId).collectAsState(initial = null)
    val room by repo.observeRoom(roomId).collectAsState(initial = repo.room(roomId))

    // 本回合的草稿：已录好的镖 + 正在输入的数字 + 当前倍率。
    // 草稿只活在这个界面里，从不参与算分，因此它可以被随意清空而不会影响权威状态。
    var darts by remember { mutableStateOf<List<Dart>>(emptyList()) }
    var buffer by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var awaitingAck by remember { mutableStateOf(false) }

    /**
     * 本回合是否已经上报过「我开始操作了」（T7）。
     *
     * 只在**第一次落指**时上报一次：每录一支镖都发一次是毫无意义的刷屏，
     * 而「对手已经动手」这件事，主机知道一次就够了。
     */
    var activated by remember { mutableStateOf(false) }

    /**
     * 本次「提交意图」的 id（T6）。
     *
     * **重试必须复用同一个**：主机据此认出「这是同一个意图又来了一次」并回首次的结果，
     * 而不是再结算一遍。换一个回合（或改过镖之后）则必须换新的 ——
     * 否则这一回合的镖会被当成上一回合的重放，直接回上一回合的结果。
     */
    var attemptTurnId by remember { mutableStateOf(newTurnId()) }

    /** 提交之后的提示语（版本冲突 / 超时 / 被拒）。null 表示没有话要说。 */
    var notice by remember { mutableStateOf<String?>(null) }

    /**
     * 撤回窗口的剩余毫秒（T7）。起点由**帧**给出（服务端定的窗口长度），之后在本地每秒走一格。
     *
     * 不为它回问服务端：它只是提示，真正的裁定是服务端那一次拒绝。
     * 主机提前关窗（对手已动手）时会推一帧，帧里没有窗口 → 这里直接归零，按钮也随之消失。
     */
    var undoLeftMs by remember { mutableStateOf(0L) }

    // 对手掉线的缺席倒计时（T8）：起点由服务端下发，之后在本地每秒走一格。
    // 它只是一句提示 —— 判负由服务端说了算，结果会作为一帧到达（[RoomMatchView.finish]）。
    val absence by repo.observeAbsence(roomId).collectAsState(initial = emptyMap())
    var absenceLeftMs by remember { mutableStateOf(0L) }
    LaunchedEffect(absence) {
        absenceLeftMs = absence.values.maxOrNull() ?: 0L
    }
    LaunchedEffect(absenceLeftMs) {
        if (absenceLeftMs > 0) {
            delay(ABSENCE_TICK_MS)
            absenceLeftMs = (absenceLeftMs - ABSENCE_TICK_MS).coerceAtLeast(0)
        }
    }

    /**
     * 结算导航的**一次性**闸门（M5 T9）。
     *
     * 对局结束只会发生一次，但帧会**重发**（重连补帧、他人提交的广播）。
     * 靠「帧变了」去推断该不该导航，会在补帧时把已经站在结算页的人再推一次 ——
     * 同样地，也不靠 `isOver` 的 true/false 翻转去判断（撤回复活之类的边界会让翻转不止一次）。
     */
    var navigatedToResult by remember { mutableStateOf(false) }
    LaunchedEffect(view) {
        if (view?.isOver == true && !navigatedToResult) {
            navigatedToResult = true
            onMatchOver(roomId)
        }
    }

    val current = view
    // 帧的推进方式：**回合序号 + 是否轮到我**。后者也要进 key，因为重开一局（0 号成员先手）
    // 可能让序号归零、而「轮到谁」才是用户真正感知到的那次变化。
    val frameKey = current?.let { it.snapshot.turns.lastOrNull()?.seq ?: 0 to it.myTurn }

    // 权威帧一旦推进，我这次提交就已经生效：清空草稿。
    // 以「帧」而不是「时间」为准是有意的 —— 提交有没有生效只有主机说了算，
    // 客户端自己清空草稿，等于替主机宣布了一个它还没宣布的结果。
    LaunchedEffect(frameKey) {
        darts = emptyList()
        buffer = ""
        multiplier = Multiplier.SINGLE
        awaitingAck = false
        notice = null
        // 新的一回合 = 新的一次提交意图。沿用上一回合的 id，主机就会把这一回合
        // 当成上一回合的重放（幂等命中），新镖永远进不去。
        attemptTurnId = newTurnId()
        activated = false
        // 撤销窗口以**帧**为准，不留上一回合的剩余时间：那是另一手的窗口，
        // 而「还剩几秒」若沿用旧值，就会出现「别人刚投完，我这边倒计时接着走」。
        undoLeftMs = current?.undoWindowMs ?: 0
    }

    // 主机对「我这一次提交」的答复（T6）。三种结局处置不同，见 [TurnAck]。
    LaunchedEffect(Unit) {
        repo.submitAcks.collect { ack ->
            when (ack) {
                // 生效：剩下的交给帧（帧一到就会清空草稿并解锁键盘）。
                is TurnAck.Accepted -> {
                    awaitingAck = false
                    notice = null
                }

                // 我的帧已经落后于主机：这三支镖是照着旧局面录的，留着只会被再拒一次。
                is TurnAck.Conflict -> {
                    darts = emptyList()
                    buffer = ""
                    multiplier = Multiplier.SINGLE
                    awaitingAck = false
                    attemptTurnId = newTurnId()
                    notice = "状态已更新，已按最新比分刷新"
                }

                // 被拒（不是我的回合 / 镖非法 / 主机没回音）：草稿留着，改完可以再点一次。
                is TurnAck.Rejected -> {
                    awaitingAck = false
                    notice = ack.message
                }
            }
        }
    }

    // 等不到答复就把键盘还回来。见 [SUBMIT_ACK_TIMEOUT_MS]。
    LaunchedEffect(awaitingAck) {
        if (awaitingAck) {
            delay(SUBMIT_ACK_TIMEOUT_MS)
            // 超时**不等于没生效**：那一镖可能已经算了，只是回程丢了。
            // 因此草稿与 attemptTurnId 都留着 —— 用户再点一次会被主机认成重放，不会多走一回合。
            awaitingAck = false
            notice = "没等到主机确认，可再试一次"
        }
    }

    // 撤销窗口的走秒（T7）。见 [UNDO_TICK_MS]：这只是提示，裁定在服务端那一侧。
    LaunchedEffect(undoLeftMs) {
        if (undoLeftMs > 0) {
            delay(UNDO_TICK_MS)
            undoLeftMs = (undoLeftMs - UNDO_TICK_MS).coerceAtLeast(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        /*
         * 标题区：**房间名 + 右侧一行规则信息**（2026-09-27 反馈）。
         *
         * 玩法 / LEG / R / SI-DO 原本单独占一张横卡，夹在房间名与玩家卡片之间。
         * 它们其实是房间名的定语（「这是哪一局」），并起来省下整整一条卡片的高度，
         * 而这一屏最缺的就是纵向空间；「对局中」这类状态字眼随之去掉 ——
         * 谁在投由卡片边框与昵称旁的小圆点说，不需要再有一句常亮的话重复它。
         */
        RoomTopBar(
            title = current?.snapshot?.roomName ?: room?.name ?: "对局",
            meta = current?.snapshot?.let { matchMetaOf(it) } ?: "",
            onBack = onExit
        )

        when {
            // 帧没到：与观战页同样分「还没送到」与「已经结束」——房间里在对局中，
            // 却拿不到帧，只可能是还在路上（开局帧必然晚于跳到本页的那一次导航）。
            current == null -> if (room?.status == RoomStatus.PLAYING) {
                SyncingMatchNotice(onExit = onExit)
            } else {
                MatchGoneNotice(onExit = onExit)
            }

            else -> {
                val snapshot = current.snapshot

                /**
                 * 本回合的**第一次落指**（T7）：告诉主机「我开始了」，它会关掉上一手的撤销窗口。
                 *
                 * 为什么要由动手的一方上报：上一手是对方投的，而只有当我开始照着那份新比分
                 * 录镖，「对手已经接受了这个比分」这件事才成立 —— 在那之后再撤回，就是让他白录。
                 */
                val markActivated: () -> Unit = {
                    if (!activated) {
                        activated = true
                        repo.notifyTurnActivated(roomId)
                    }
                }

                /** 记一支镖。[buffer] 一并清空：它代表「还没确认的那一下」。 */
                val record: (Dart) -> Unit = { dart ->
                    if (darts.size < RoomMatchRules.MAX_DARTS_PER_TURN) {
                        markActivated()
                        darts = darts + dart
                        buffer = ""
                    }
                }

                // 玩家卡片实时扣掉本回合已确认的镖（草稿预览，权威分仍以主机帧为准）：
                // 不扣的话到结镖回合，卡片上的分还是回合起点的，没法判断能否一镖收尾。
                Scoreboard(
                    snapshot,
                    previewDeduct = darts.sumOf { it.number * it.multiplier },
                    selfId = current.selfId
                )
                /*
                 * 三镖槽位**常驻**（2026-09-27 反馈：每个区的位置固定）。
                 * 轮到对手时槽位照常摆在那儿（空格），只在他投完、帧推进时才会动 ——
                 * 若按「轮到我才出现」，三镖槽位与记分表会随回合跳上跳下，
                 * 用户刚记住「分数在屏幕哪儿」的位置就被挪走了。
                 */
                TurnSlots(darts, active = current.canThrow)
                /*
                 * 「等待 XX 投掷 / 上一回合……」那张卡整体去掉（2026-09-27 反馈）：
                 * 它占据的高度换来的是三件已经在别处写着的事 ——
                 * 轮到谁（卡片边框 + 昵称旁圆点）、刚刚发生了什么（记分表最新一行）、
                 * 我录到第几镖（上面的三个槽位）。留着只是把记分表往下挤。
                 *
                 * 仍然保留的是**非常规提示**（提交没回音 / 版本冲突 / 对手掉线）：
                 * 它们只在出状况时出现，平时不占行。这类提示消失不了，
                 * 否则「我点了确认但没反应」会变成没有人解释的空白。
                 */
                val exception = notice
                    ?: if (absenceLeftMs > 0) {
                        "对手掉线，${(absenceLeftMs + ABSENCE_TICK_MS - 1) / ABSENCE_TICK_MS} 秒后判负"
                    } else if (awaitingAck) {
                        "已提交，等待主机确认…"
                    } else {
                        null
                    }
                if (exception != null) {
                    NoticeLine(exception, Accent)
                }
                // 撤回只在主机说「还能撤」时出现（窗口由帧带来）。它不与「完成投掷」抢位置：
                // 那一手已经投完了，此刻的主操作是等对方，撤回只是顺带的一个补救。
                if (undoLeftMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    UndoRow(
                        // 向上取整：最后一秒显示「1」而不是「0」，否则按钮看起来会先失效一秒。
                        seconds = (undoLeftMs + UNDO_TICK_MS - 1) / UNDO_TICK_MS,
                        onUndo = {
                            repo.undoTurn(roomId)
                            // 不等答复：撤成了会来一帧（比分回退），没撤成会来一句提示。
                            // 先把按钮收掉，否则用户会以为「点了没反应」而连点 ——
                            // 而连点的第二次必然失败（窗口里那一手已经被撤掉了）。
                            undoLeftMs = 0
                            notice = null
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))

                /*
                 * 记分表**紧跟三镖槽位**（2026-09-27 反馈：回合得分区固定在卡片区之下）。
                 * 它记的正是卡片与槽位上这些数字怎么来的。
                 *
                 * [pendingName] = 正在投掷的人：表尾那行占位指出下一组数字会落在哪格。
                 */
                val pendingName = if (current.isOver) {
                    null
                } else {
                    snapshot.players.firstOrNull { it.isActive }?.name
                }
                TurnHistory(
                    snapshot,
                    Modifier.weight(1f),
                    selfId = current.selfId,
                    pendingName = pendingName
                )

                when {
                    // 终态：比分板与记录都留着（用户要看结果），只把键盘换成出口。
                    current.isOver -> ExitRow(onExit = onExit)

                    /*
                     * 键盘**常驻屏幕底部、高度固定**（2026-09-28 反馈：原 200dp 按键偏小易误触，加高 40% ⇒ 280dp）。
                     *
                     * 固定而不是随「轮到谁」出现/消失：五个区的位置一旦可动，
                     * 「分数在哪儿」的肌肉记忆就作废了。轮到对手时键盘整块**禁用**
                     * （能看不能按）—— 它展示的是对面正在用的同一套操作，
                     * 也让「现在轮不到我」这件事由按键自己的灰态说，不用再写一行字。
                     */
                    else -> DartKeypad(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KEYBOARD_HEIGHT)
                            .padding(start = MATCH_GUTTER, end = MATCH_GUTTER, bottom = 6.dp),
                        // 键盘自身不再留边：默认那 8dp 会让**最左/最右一列按键**比上方的
                        // 玩家卡片再内缩 8dp，看起来就是「键盘比上面窄一圈」。
                        // 外层的 MATCH_GUTTER 已经把左右对齐好了，这里必须归零。
                        contentPadding = PaddingValues(0.dp),
                        enabled = current.canThrow && !awaitingAck,
                        multiplier = multiplier,
                        onMultiplierChange = { multiplier = it },
                        buffer = buffer,
                        // 预览基数取「当前回合玩家的剩余分」。回合内不做爆分/收镖试算，理由见类注释。
                        currentRemaining = current.currentPlayer?.score,
                        turnDartsScore = darts.sumOf { it.number * it.multiplier },
                        turnDartsCount = darts.size,
                        // 按数字键也算落指：它可能只是「先输个 1」，但对手的撤销窗口
                        // 该不该关，取决于我有没有开始动这一回合，而不是我录完了几支镖。
                        onDigit = { digit ->
                            markActivated()
                            buffer = appendDigit(buffer, digit)
                        },
                        // 牛眼与其它数字键走同一条路：先落进输入缓冲，确认键上显示
                        // 「S25 / S50」并给出剩余分预览，再按「确认」记这一镖。
                        // 直接记下会让确认键永远写着「确认」，用户看不出自己点了什么
                        // （2026-09-26 两机联机实测反馈）。牛眼没有 D/T，倍率强制回 S。
                        onBull25 = {
                            markActivated()
                            multiplier = Multiplier.SINGLE
                            buffer = "25"
                        },
                        onBull50 = {
                            markActivated()
                            multiplier = Multiplier.SINGLE
                            buffer = "50"
                        },
                        onMiss = { record(Dart.MISS) },
                        onBackspace = {
                            // 退格先吃掉还没确认的数字，再退已录的镖 —— 与输入法的直觉一致。
                            when {
                                buffer.isNotEmpty() -> buffer = buffer.dropLast(1)
                                darts.isNotEmpty() -> darts = darts.dropLast(1)
                                else -> Unit
                            }
                        },
                        onConfirm = {
                            when {
                                // 有未确认的数字 → 先把它记为一支镖（键盘上「确认」的第一层含义）。
                                buffer.isNotEmpty() -> buffer.toIntOrNull()?.let { number ->
                                    record(Dart(number, multiplier.factor))
                                }
                                // 没有待输入的数字、但已经录过镖 → 结束回合，交给主机裁定。
                                // 允许只录 1..2 镖就结束：收镖时本来就不需要投满三支。
                                darts.isNotEmpty() -> {
                                    // 带上 attemptTurnId：重试（超时后再点一次）会复用同一个，
                                    // 主机据此只结算一次。见该状态的注释。
                                    repo.submitTurn(roomId, darts, attemptTurnId)
                                    awaitingAck = true
                                    notice = null
                                }

                                else -> Unit
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 一次「提交意图」的 id（T6）。
 *
 * 用 UUID 而不是让服务端发号：发号要为「取号」再添一次往返，
 * 而这里的唯一要求是「同一次意图两次到达时是同一个值」，UUID 天生满足。
 */
private fun newTurnId(): String = UUID.randomUUID().toString()

/**
 * 撤回按钮（M5 T7）。只在主机给的窗口内出现，数字是**剩余秒数**。
 *
 * 它看起来像一个简单的按钮，但有两件事必须说清：
 * - 按钮出现与否**只由帧决定**（帧里带着撤销窗口），本页不自己判断「我能不能撤」；
 * - 点了之后不锁、不等：撤成了会来一帧（比分回退），没撤成会来一句提示。
 *   在界面上假装「已经撤了」是更糟的选择 —— 那会让用户以为比分变过了。
 */
@Composable
private fun UndoRow(seconds: Long, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MATCH_GUTTER),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Divider, RoundedCornerShape(20.dp))
                .clickable(onClick = onUndo)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "撤回上一回合（${seconds}s）",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Secondary
            )
        }
    }
}

/**
 * 一行**非常规**提示：版本冲突 / 提交没回音 / 对手掉线。
 *
 * 与原先那张「状态卡」的区别是它没有卡片、没有常驻位置：**没有话要说时它不存在**。
 * 常规的「轮到谁 / 上一回合」已经由卡片高亮、三个槽位与记分表各自承担；
 * 一个永远占着一行的面板，只会把真正要看的数字挤下去（2026-09-27 反馈）。
 */
@Composable
private fun NoticeLine(text: String, color: Color) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MATCH_GUTTER, vertical = 4.dp)
    )
}

/**
 * 把一位数字收进当前输入。
 *
 * 规则与本地对局的键盘一致：只接受能构成**合法落点**的输入（1..20）。
 * 于是 "0" 打头、"25"、"103" 这类都会被忽略，而 "10"/"20" 照常成立 ——
 * 25 分走专门的 BULL 键（它的倍率与其他数字不同）。
 */
private fun appendDigit(buffer: String, digit: Int): String {
    val next = buffer + digit
    val value = next.toIntOrNull() ?: return buffer
    return if (value in 1..20) next else buffer
}

/**
 * 本回合已录的三支镖。空位显示占位符，让「还差几支」一眼可见。
 *
 * 槽位**常驻**（`active = false` 时也只是更灰，不消失）：三镖分数区在五段布局里
 * 有自己的固定位置，随「轮到谁」出现/消失会让下面的记分表上下跳。
 *
 * 已录的镖**不变色**（背景/边框/文字都与空位一致，只有内容从占位符换成镖值）：
 * 之前用主题色高亮整格，三格全红在深色页面上非常刺眼，而「录没录过」看内容就够了。
 */
@Composable
private fun TurnSlots(darts: List<Dart>, active: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MATCH_GUTTER)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(RoomMatchRules.MAX_DARTS_PER_TURN) { index ->
            val dart = darts.getOrNull(index)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark.copy(alpha = if (active) 1f else 0.5f))
                    .border(1.dp, Divider, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dart?.label() ?: "—",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        dart != null -> MaterialTheme.colorScheme.onSurface
                        active -> TextDisabledDark
                        else -> TextDisabledDark.copy(alpha = 0.5f)
                    }
                )
            }
        }
    }
}

@Composable
private fun ExitRow(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MATCH_GUTTER, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Primary)
            .clickable(onClick = onExit)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("返回大厅", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
    }
}

/** 「帧还没到」：可自愈的中间态，与观战页同一取舍（重连中 ≠ 已断开）。 */
@Composable
private fun SyncingMatchNotice(onExit: () -> Unit) {
    NoticePanel(
        title = "正在同步对局…",
        message = "房间已开局，主机推来的对局帧还没到。",
        hint = "若长时间没有变化，说明数据没有送达（可能连接已断开）。退出后重新进入房间即可跟上。",
        showProgress = true,
        actionLabel = "退出对局",
        onAction = onExit
    )
}

/** 「对局不在了」：终态。房间不在对局中，再等也不会有帧。 */
@Composable
private fun MatchGoneNotice(onExit: () -> Unit) {
    NoticePanel(
        title = "对局已结束",
        message = "该房间不在对局中，或对局已解散",
        hint = null,
        showProgress = false,
        actionLabel = "返回大厅",
        onAction = onExit
    )
}
