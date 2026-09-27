package com.dartvio.app.ui.practice

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.ImpactRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.impact.ImpactFingerprint
import com.dartvio.app.domain.impact.ImpactFingerprintCalculator
import com.dartvio.app.domain.impact.ImpactPrescription
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentKind
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.Prescription
import com.dartvio.app.domain.impact.PrescriptionMetric
import com.dartvio.app.domain.impact.SessionMetrics
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「这轮我打算改什么」的快捷填入（§5.2）；点一下填文本，用户可改，始终存**纯文本**。 */
private val INTERVENTION_CHIPS = listOf("站位", "瞄点", "握镖", "节奏", "发力")

/** 选目标页状态。 */
data class ImpactSetupUiState(
    val target: IntentTarget = IntentTarget.triple(20),
    val spanMm: Double = ImpactWindow.STANDARD_SPAN_MM,
    val sessionSize: Int = ImpactSession.DEFAULT_SESSION_SIZE,
    val goal: ImpactGoal = ImpactGoal.BOTH,
    /** 本机累计录入的镖数（落点诊断来源）。 */
    val totalDarts: Int = 0,
    /** 当前目标已录入的镖数（同一窗口档位）。 */
    val targetDarts: Int = 0,
    /** 本轮目标；`null` = 关闭（报告不出处方卡）。 */
    val prescription: Prescription? = null,
    /** 用户是否手动改过目标 —— 改过之后系统建议不再覆盖。 */
    val prescriptionTouched: Boolean = false,
    /** 干预标签纯文本（`''` = 不填，不拦「开始」）。 */
    val noteText: String = "",
    /** 投掷指纹（同档位、跨目标）；`n < 30` 为 `null`（不出这一行）。 */
    val fingerprint: ImpactFingerprint? = null,
    /** 上一轮的实测（用于给建议值）；`null` = 还没有可比的一轮。 */
    val previous: SessionMetrics? = null
)

/**
 * 精准工坊 · 选目标页的 ViewModel。
 *
 * 只做有状态的事：记住「这一轮要练什么」（目标 / 档位 / 轮大小 / 想改善什么 / 本轮目标 / 干预标签），
 * 以及把库里的历史读回来给两个即时提示 —— 「还差多少镖」与「本机历史倾向」（§4.7）。
 */
class ImpactSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ImpactRepository(
        DartVioDatabase.get(app.applicationContext).dartHitDao()
    )

    private val profileId: String = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.localProfileId
        else -> ProfileStore.ensure(ctx).profileId
    }

    private val _uiState = MutableStateFlow(ImpactSetupUiState())
    val uiState: StateFlow<ImpactSetupUiState> = _uiState.asStateFlow()

    fun start() {
        viewModelScope.launch {
            val total = repository.totalDarts()
            _uiState.update { it.copy(totalDarts = total) }
            refreshDerived()
        }
    }

    fun select(target: IntentTarget) {
        _uiState.update { it.copy(target = target) }
        viewModelScope.launch { refreshDerived() }
    }

    fun setSpan(spanMm: Double) {
        _uiState.update { it.copy(spanMm = spanMm) }
        viewModelScope.launch { refreshDerived() }
    }

    fun setSessionSize(size: Int) = _uiState.update { it.copy(sessionSize = size) }

    fun setGoal(goal: ImpactGoal) {
        _uiState.update { it.copy(goal = goal) }
        viewModelScope.launch { refreshDerived() }
    }

    /** 切换口径（保持目标阈值不变会让数字变得没意义 ⇒ 一律重取建议值）。 */
    fun setMetric(metric: PrescriptionMetric) {
        val state = _uiState.value
        val prev = state.previous
        _uiState.update {
            it.copy(
                prescription = ImpactPrescription.suggestPrescription(prev, metric),
                prescriptionTouched = true
            )
        }
    }

    /** 关闭本轮目标（报告不出 ⑤ 卡）。 */
    fun clearPrescription() = _uiState.update {
        it.copy(prescription = null, prescriptionTouched = true)
    }

    /** 恢复系统建议值。 */
    fun useSuggested() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                prescription = ImpactPrescription.suggestPrescription(state.previous, metricOf(state.goal)),
                prescriptionTouched = false
            )
        }
    }

    fun nudgeTarget(direction: Int) {
        val state = _uiState.value
        val current = state.prescription ?: return
        val step = if (current.metric.unit == "%") 0.05 else 0.5
        val next = (current.target + direction * step).coerceAtLeast(0.0)
        _uiState.update {
            it.copy(prescription = current.copy(target = next), prescriptionTouched = true)
        }
    }

    fun setNote(text: String) = _uiState.update {
        it.copy(noteText = text.take(ImpactPrescription.MAX_NOTE_LENGTH))
    }

    /** 快捷 chip：填入文本（可再改），不是枚举选择。 */
    fun appendNote(text: String) = _uiState.update {
        val merged = if (it.noteText.isBlank()) text else "${it.noteText}·$text"
        it.copy(noteText = merged.take(ImpactPrescription.MAX_NOTE_LENGTH))
    }

    /** 开一轮：会话 id 由仓库生成，参数写进 [ImpactSession]，然后回调去导航。 */
    fun begin(onReady: () -> Unit) {
        val state = _uiState.value
        ImpactSession.begin(
            newSessionId = repository.newSessionId(),
            target = state.target,
            spanMm = state.spanMm,
            sessionSize = state.sessionSize,
            goal = state.goal,
            prescription = state.prescription,
            interventionNote = state.noteText.trim()
        )
        onReady()
    }

    /** 目标 / 档位一变就要重算的三个量：本目标镖数、上一轮实测、同档位指纹。 */
    private suspend fun refreshDerived() {
        val state = _uiState.value
        val hits = repository.historyFor(
            profileId = profileId,
            target = state.target,
            windowSpanMm = state.spanMm,
            limit = ImpactRepository.HISTORY_LIMIT
        )
        val previous = lastSessionMetrics(hits, state.target)
        val fingerprint = ImpactFingerprintCalculator.of(
            repository.fingerprintSamples(profileId, state.spanMm)
        )
        _uiState.update {
            val suggestion = ImpactPrescription.suggestPrescription(previous, metricOf(it.goal))
            it.copy(
                targetDarts = hits.size,
                previous = previous,
                fingerprint = fingerprint,
                prescription = if (it.prescriptionTouched) it.prescription else suggestion
            )
        }
    }

    /** 最近一轮（完成过的）的实测值；`null` = 还没有够样本的一轮。 */
    private fun lastSessionMetrics(
        hits: List<DartHitEntity>,
        target: IntentTarget
    ): SessionMetrics? {
        val sessions = ImpactRepository.sessionsOf(hits)
        val last = sessions.lastOrNull() ?: return null
        return ImpactRepository.metricsOf(last, target)
    }

    private fun metricOf(goal: ImpactGoal): PrescriptionMetric = when (goal) {
        ImpactGoal.OFFSET -> PrescriptionMetric.BIAS_ABS
        ImpactGoal.SCATTER -> PrescriptionMetric.R95
        ImpactGoal.BOTH -> PrescriptionMetric.RMSE
    }
}

/**
 * 精准工坊 · 选目标页（设计 §5.2）。
 *
 * 首句话就把「这不是普通练习」说清楚：普通练习的输入是分数，这里的输入是**落点**。
 * 选项全部是「一轮内不再变化」的量（目标 / 窗口 / 轮大小 / 想改善什么 / 本轮目标 / 干预标签）——
 * 这也是把它们放在独立页、而不是塞进录制页的原因：**本轮进行中不得改目标**，
 * 改了达成判定就失去意义（§5.2）。
 *
 * ## 版式（2026-09-18 重排）
 *
 * 之前是「标题 + 一片 chip + 一片说明文字」的平铺长列表：同一屏里既有浮在空白上的独立文字，
 * 也有被 `FlowRow` 排成参差锯齿的 chip 行 —— 读起来是散的，右侧从来没对齐过。重排后：
 *
 * ① 每个设置项一张卡（[SetupCard]）：卡宽都等于内容宽度，标题、选项、脚注共用一条左边界，
 *    空的地方从「两块之间的空白」变成「卡内的呼吸」—— 不再是没来由的大块留白；
 * ② chip 走等宽网格（[ChipGrid]）而不是 `FlowRow`：同一行的格子 `weight(1f)` 等宽，
 *    换行按列数整除，多行同宽；
 * ③ 长短不一的选项（窗口档位）不走网格，走整行的 [OptionRow]，不会因为文案长而被压成两行；
 * ④ 统计信息从「一句话正文」改成分栏的 [InfoRow]，标签与数值各占一列，一眼是表格不是段落。
 */
@Composable
fun ImpactSetupScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    viewModel: ImpactSetupViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var customDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            // 顶部自绘头部必须自己吃状态栏内边距：`MainActivity` 开了 `enableEdgeToEdge`，
            // 不加这一行标题会压到状态栏上与时间/信号重叠。
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("返回", color = Primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "精准工坊",
                color = TextPrimaryDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            BriefCard()
            Spacer(Modifier.height(12.dp))

            SetupCard(
                title = "瞄准目标",
                note = "一轮只练一个分区；自定义在最后一格。"
            ) {
                TargetGrid(
                    chips = IntentTarget.PRIMARY_CHIPS,
                    selected = state.target
                ) { viewModel.select(it) }
                Spacer(Modifier.height(6.dp))
                TargetGrid(
                    chips = IntentTarget.CHECKOUT_CHIPS,
                    selected = state.target
                ) { viewModel.select(it) }
                Spacer(Modifier.height(6.dp))
                val otherLabels = IntentTarget.OTHER_CHIPS.map { it.label } + "自定义…"
                val otherIndex = IntentTarget.OTHER_CHIPS.indexOf(state.target)
                ChipGrid(
                    items = otherLabels,
                    selectedIndex = if (otherIndex >= 0) otherIndex else otherLabels.lastIndex,
                    columns = 4,
                    onSelect = { index ->
                        if (index == otherLabels.lastIndex && otherIndex < 0) {
                            customDialog = true
                        } else {
                            viewModel.select(IntentTarget.OTHER_CHIPS[index])
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("本机累计", "${state.totalDarts} 镖")
                InfoRow("本目标已录", "${state.targetDarts} 镖", statHint(state.targetDarts))
                state.fingerprint?.let { fingerprint ->
                    InfoRow(
                        "历史倾向",
                        "径向 ${(fingerprint.radialShare * 100).toInt()}% · " +
                            "切向 ${(fingerprint.tangentShare * 100).toInt()}% · " +
                            "出框 ${(fingerprint.outShare * 100).toInt()}%（n=${fingerprint.n}）"
                    )
                    InfoRow("画像", fingerprint.portrait())
                }
            }

            Spacer(Modifier.height(12.dp))
            SetupCard(
                title = "本轮目标镖数",
                note = sessionSizeHint(state.sessionSize)
            ) {
                ChipGrid(
                    items = ImpactSession.SESSION_SIZE_CHOICES.map { "$it" },
                    selectedIndex = ImpactSession.SESSION_SIZE_CHOICES.indexOf(state.sessionSize),
                    columns = 3,
                    onSelect = { viewModel.setSessionSize(ImpactSession.SESSION_SIZE_CHOICES[it]) }
                )
            }

            Spacer(Modifier.height(12.dp))
            SetupCard(
                title = "想改善什么",
                note = "轮末只给这一条动作：选了「散布」，就不会再同时提系统偏移。"
            ) {
                ChipGrid(
                    items = ImpactGoal.entries.map { it.label },
                    selectedIndex = ImpactGoal.entries.indexOf(state.goal),
                    columns = 3,
                    onSelect = { viewModel.setGoal(ImpactGoal.entries[it]) }
                )
            }

            Spacer(Modifier.height(12.dp))
            SetupCard(
                title = "窗口档位",
                note = "换档后成绩不可比 —— 窗口越小，毫米分辨率越高，也越容易把散布截掉。"
            ) {
                ImpactWindow.SPAN_PRESETS.forEach { span ->
                    OptionRow(
                        text = ImpactWindow.labelOf(span).substringBefore("（"),
                        sub = ImpactWindow.labelOf(span)
                            .substringAfter("（")
                            .removeSuffix("）"),
                        selected = span == state.spanMm,
                        onClick = { viewModel.setSpan(span) }
                    )
                    if (span != ImpactWindow.SPAN_PRESETS.last()) Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            SetupCard(
                title = "这轮的目标",
                note = "本轮开始后不能再改 —— 改了达成判定就没有意义。"
            ) {
                PrescriptionSection(state, viewModel)
            }

            Spacer(Modifier.height(12.dp))
            SetupCard(
                title = "这轮打算改什么（可选）",
                note = "只作**对照标签**：在报告里把「标注过的轮次」与其它轮次放在一起看，不作因果结论。"
            ) {
                ChipGrid(
                    items = INTERVENTION_CHIPS,
                    selectedIndex = -1,
                    columns = 5,
                    onSelect = { viewModel.appendNote(INTERVENTION_CHIPS[it]) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.noteText,
                    onValueChange = { viewModel.setNote(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("写「打算改什么」，不是「改了什么」", fontSize = 12.sp) }
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = { viewModel.begin(onReady = onStart) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("开始这一轮", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (customDialog) {
        CustomTargetDialog(
            initial = state.target,
            onDismiss = { customDialog = false },
            onConfirm = {
                viewModel.select(it)
                customDialog = false
            }
        )
    }
}

/** 目标的等宽 chip 行：预设目标一行排满，不多不少。 */
@Composable
private fun TargetGrid(
    chips: List<IntentTarget>,
    selected: IntentTarget,
    onSelect: (IntentTarget) -> Unit
) {
    ChipGrid(
        items = chips.map { it.label },
        selectedIndex = chips.indexOf(selected),
        columns = chips.size,
        onSelect = { onSelect(chips[it]) }
    )
}

/** 一句话说明：原来这里有三段正文，中间隔着大空白，改写成一张紧凑卡。 */
@Composable
private fun BriefCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            "选一个目标分区，把每一镖的落点点出来",
            color = Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "一轮里只做这一件事：不看分数、不做二次确认 —— 一次点击就是一镖，点错用「撤销上一镖」改。",
            color = TextSecondaryDark,
            fontSize = 12.sp
        )
    }
}

/**
 * 设置卡：页上所有选项都用它，因此**左右边界天然对齐**。
 *
 * 走「一张卡装一组设置」而不是「标题 + 内容」平铺，是为了同时消掉两个问题：
 * 一是相邻两组之间那片没有归属的空白；二是说明文字浮在正文里，读起来像第三个选项。
 * 现在辅助说明一律收在卡底的固定位置（同一条左边界、同一号字）。
 */
@Composable
private fun SetupCard(
    title: String,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(title, color = TextPrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        content()
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(note, color = TextSecondaryDark, fontSize = 11.sp)
        }
    }
}

/** 标签 / 数值两栏：**标签列宽度固定 34%**，上下行的数值起点在同一条竖线上。 */
@Composable
private fun InfoRow(label: String, value: String, hint: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = TextSecondaryDark,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.34f)
        )
        Column(modifier = Modifier.weight(0.66f)) {
            Text(
                value,
                color = TextPrimaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (hint.isNotBlank()) {
                Text(hint, color = TextSecondaryDark, fontSize = 11.sp)
            }
        }
    }
}

/**
 * 等宽 chip 网格。
 *
 * 换行必须是**按列整除**：之前 `FlowRow` 让每行按内容宽度各自收尾，行尾锯齿 + 右端不对齐。
 * 这里每行 `columns` 个格子各占 `weight(1f)`，末行不足则用等宽占位补上，列宽从头到尾一致。
 */
@Composable
private fun ChipGrid(
    items: List<String>,
    selectedIndex: Int,
    columns: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, text ->
                    GridChip(
                        text = text,
                        selected = rowIndex * columns + colIndex == selectedIndex,
                        onClick = { onSelect(rowIndex * columns + colIndex) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 末行不足一格：占位保持列宽一致（空白在**右侧行尾**，不是行中间）。
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 网格里的一个格子；选中态除了换色还有「✓」—— 状态不能只靠颜色表达。 */
@Composable
private fun GridChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .background(
                if (selected) Accent.copy(alpha = 0.18f) else SurfaceVariantDark,
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Accent else Divider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Text("✓", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text,
                color = if (selected) Accent else TextPrimaryDark,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 整行选项：文案长短不一时用它，避免被 `weight` 压成两行后被截断。 */
@Composable
private fun OptionRow(
    text: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(
                if (selected) Accent.copy(alpha = 0.18f) else SurfaceVariantDark,
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Accent else Divider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Text("✓", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text,
                color = if (selected) Accent else TextPrimaryDark,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(sub, color = TextSecondaryDark, fontSize = 11.sp)
        }
    }
}

/** 本轮目标：口径网格 + 阈值步进 + 来源说明。 */
@Composable
private fun PrescriptionSection(state: ImpactSetupUiState, viewModel: ImpactSetupViewModel) {
    val metrics = PrescriptionMetric.entries
    val items = listOf("不设") + metrics.map { it.label }
    val selectedIndex = when {
        state.prescription == null -> 0
        else -> metrics.indexOf(state.prescription.metric) + 1
    }
    ChipGrid(
        items = items,
        selectedIndex = selectedIndex,
        columns = 3,
        onSelect = { index ->
            if (index == 0) viewModel.clearPrescription() else viewModel.setMetric(metrics[index - 1])
        }
    )

    val current = state.prescription
    if (current == null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "没设目标：轮末只给描述性结论。下一轮开局可以选一个，否则结束只剩感觉。",
            color = TextSecondaryDark,
            fontSize = 11.sp
        )
        return
    }

    Spacer(Modifier.height(10.dp))
    // 「收紧」= 让标准更难达标：`lowerIsBetter` 的指标要**减小**阈值（平均偏差 ≤ 更小），
    // 比例类（命中率 ≥ 更高）反过来 —— 所以方向由 `lowerIsBetter` 决定，不能写死 − / ＋。
    val tighten = if (current.metric.lowerIsBetter) -1 else 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepChip("放宽", Modifier.width(76.dp)) { viewModel.nudgeTarget(-tighten) }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                current.metric.label,
                color = TextSecondaryDark,
                fontSize = 11.sp
            )
            Text(
                "${if (current.metric.lowerIsBetter) "≤" else "≥"} ${current.targetText()}",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        StepChip("收紧", Modifier.width(76.dp)) { viewModel.nudgeTarget(tighten) }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        prescriptionSource(state),
        color = TextSecondaryDark,
        fontSize = 11.sp
    )
    if (state.prescriptionTouched) {
        Row {
            TextButton(onClick = { viewModel.useSuggested() }) {
                Text("用建议值", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 阈值步进：整行两端等宽，中间数值居中 —— 之前「− 数值 ＋」挤在左侧。 */
@Composable
private fun StepChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
            .border(1.dp, Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun prescriptionSource(state: ImpactSetupUiState): String = when {
    state.prescriptionTouched ->
        "已自定义：手动收紧/放宽后就不能再拿来自比。建议值 = 上一轮实测 × 0.5（下限 1.5 mm）。"
    state.previous == null ->
        "系统建议值：还没有可比的一轮，先给一个够得着的起点。"
    else ->
        "系统建议值：上一轮实测 ${mm(state.previous.bias)} mm × 0.5，并保底 1.5 mm。"
}

/** 自定义目标：分区与环都从几何常量里选，不写死「1-20 之外还有别的」。 */
@Composable
private fun CustomTargetDialog(
    initial: IntentTarget,
    onDismiss: () -> Unit,
    onConfirm: (IntentTarget) -> Unit
) {
    var kind by remember { mutableStateOf(initial.kind) }
    var sector by remember { mutableStateOf(if (initial.kind == IntentKind.BULL) 20 else initial.sector) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        title = { Text("自定义目标", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val kinds = listOf(IntentKind.TRIPLE, IntentKind.DOUBLE, IntentKind.SINGLE_OUTER)
                ChipGrid(
                    items = kinds.map {
                        when (it) {
                            IntentKind.TRIPLE -> "三倍"
                            IntentKind.DOUBLE -> "双倍"
                            else -> "外单倍"
                        }
                    },
                    selectedIndex = kinds.indexOf(kind),
                    columns = 3,
                    onSelect = { kind = kinds[it] }
                )
                Spacer(Modifier.height(12.dp))
                Text("分区", color = TextSecondaryDark, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                // 20 个分区排成 5 列 × 4 行：**列对齐**，不再因「1 比 20 窄」而错位。
                ChipGrid(
                    items = (1..20).map { "$it" },
                    selectedIndex = sector - 1,
                    columns = 5,
                    onSelect = { sector = it + 1 }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(IntentTarget(kind, sector)) }) {
                Text("选它", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondaryDark) }
        }
    )
}

private fun mm(value: Double): String = ((value * 10).toInt() / 10.0).toString()

/** 攒到 30 镖才够出散布结论（[com.dartvio.app.domain.impact.ImpactCalculator.MIN_FULL_N]）。 */
private fun statHint(existing: Int): String =
    if (existing >= com.dartvio.app.domain.impact.ImpactCalculator.MIN_FULL_N) {
        "（已够出散布结论）"
    } else {
        "（还差 ${com.dartvio.app.domain.impact.ImpactCalculator.MIN_FULL_N - existing} 镖可出散布结论）"
    }

private fun sessionSizeHint(size: Int): String = when {
    size < com.dartvio.app.domain.impact.ImpactCalculator.MIN_BIAS_N ->
        "少于 ${com.dartvio.app.domain.impact.ImpactCalculator.MIN_BIAS_N} 镖不够出均值结论，建议至少 12。"
    size < com.dartvio.app.domain.impact.ImpactCalculator.MIN_FULL_N ->
        "$size 镖只能出均值结论（系统偏移），散布还看不出来。"
    else -> "$size 镖够出散布结论，但一轮时间偏长；状态下滑时优先保证质量。"
}
