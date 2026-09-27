package com.dartvio.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.domain.room.RoomStatus

/**
 * 仅比分观战（M6 F6.6，P0）。
 *
 * 只同步比分与回合历史，不传输视频流；靶盘动画观战为 P2，后续接入。
 *
 * ## 三种状态，而不是两种
 *
 * 「有快照 → 比分板 / 无快照 → 观战已结束」漏掉了最常见的第三种：**首帧还在路上**。
 * 联机下服务端按 `room_snapshot` → `match_started` → `spectator_snapshot` 的顺序广播，
 * 而等候页看到 `status == PLAYING` 就立刻跳过来 —— 首帧必然**晚于**本页创建。
 * 若把这种「还没到」当成「已结束」，用户会在开局瞬间读到一句错误的终态文案，
 * 再被一秒后的比分板推翻（与 `SyncState` 里「重连中 ≠ 已断开」是同一类取舍）。
 *
 * ## 与对局页的分工
 *
 * 本页是**只读**的：它拿 `observeSpectator`，没有键盘、不提交任何东西。
 * 参与者走 `RoomMatchScreen`（它拿 `observeMatch`，轮到自己时能录镖）。
 * 两页共用 `RoomPanels` 里的比分板与回合记录，因此「谁看到的比分」始终是同一份权威帧。
 */
@Composable
fun SpectateScreen(
    roomId: String,
    onBack: () -> Unit
) {
    // 观战数据源同样跟随 RoomRepositoryProvider：单机时是 Mock 的模拟推进，
    // 联机时是主机推来的真实观战快照。这里刻意在**进入观战页的那一刻**取一次：
    // 观战页是只在「对局进行中」才可达的一次性页面，不存在需要中途换源的场景。
    val repo = RoomRepositoryProvider.current
    val snapshot by repo.observeSpectator(roomId).collectAsState(initial = null)

    // 房间也要看：它是「首帧未到」与「观战已结束」的**唯一**判据。
    val room by repo.observeRoom(roomId).collectAsState(initial = repo.room(roomId))
    val current = snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RoomTopBar(title = current?.roomName ?: room?.name ?: "观战", onBack = onBack)

        when {
            current != null -> {
                // 观战页：徽标写「观战中」，右上角那枚灰眼睛也由同一个判据给出 ——
                // 两处都在回答「你现在只是看客」，不该让其中一处落后于另一处。
                MatchInfoBar(current, badge = "观战中", isSpectator = true)
                Scoreboard(current)
                Spacer(Modifier.height(16.dp))
                TurnHistory(current, Modifier.weight(1f))
            }

            // 房间在局中却没快照：只可能是「还没送到」，不是「结束了」。
            room?.status == RoomStatus.PLAYING -> SyncingNotice(onBack = onBack)

            else -> UnavailableNotice(onBack = onBack)
        }
    }
}

/** 「首帧未到」：可自愈的中间态。用转圈 + 明确的等待口径，而不是让用户以为观战已经结束。 */
@Composable
private fun SyncingNotice(onBack: () -> Unit) {
    NoticePanel(
        title = "正在同步比分…",
        message = "房间已开局，主机推来的观战首帧还没到。",
        hint = "若长时间没有变化，说明观战数据没有送达（可能连接已断开）。返回大厅后重新进入即可。",
        showProgress = true,
        actionLabel = "返回大厅",
        onAction = onBack
    )
}

/** 「观战已结束」：终态。房间不在对局中，再等也不会有数据。 */
@Composable
private fun UnavailableNotice(onBack: () -> Unit) {
    NoticePanel(
        title = "观战已结束",
        message = "该房间不在对局中，或房主关闭了观战",
        hint = null,
        showProgress = false,
        actionLabel = "返回大厅",
        onAction = onBack
    )
}
