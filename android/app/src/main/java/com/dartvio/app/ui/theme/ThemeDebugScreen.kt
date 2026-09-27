package com.dartvio.app.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.dartvio.app.ui.profile.AboutVersionCard
import kotlinx.coroutines.launch

/** Stable control colors ensure even an unreadable draft can be cancelled or restored. */
@Composable
fun ThemeDebugScreen(initial: ThemePalette, onApply: suspend (ThemePalette) -> Boolean, onBack: () -> Unit) {
    var encoded by rememberSaveable { mutableStateOf(initial.encode()) }
    val draft = ThemePalette.decode(encoded) ?: ThemePalette()
    var selectedName by rememberSaveable { mutableStateOf(ThemeRole.PRIMARY.name) }
    val selected = ThemeRole.valueOf(selectedName)
    var hex by rememberSaveable { mutableStateOf(draft.hex(selected)) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    BackHandler { if (!saving) onBack() }

    fun edit(value: String) {
        hex = value
        draft.edit(selected, value)?.let { encoded = it.encode() }
        message = ""
    }

    DartVioTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("主题调试面板") }, navigationIcon = {
                TextButton(onClick = onBack, enabled = !saving) { Text("返回") }
            }) },
            bottomBar = {
                Surface(shadowElevation = ThemeElevation.card) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(ThemeSpacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.small)) {
                        OutlinedButton(onClick = onBack, enabled = !saving, modifier = Modifier.weight(1f).testTag("theme-cancel")) { Text("取消") }
                        Button(onClick = {
                            saving = true
                            scope.launch {
                                val success = try { onApply(draft) } catch (_: java.io.IOException) { false }
                                saving = false
                                message = if (success) "已应用到全局，仅保存在本机" else "保存失败，原配色保持不变，请重试"
                            }
                        }, enabled = !saving && normalizeHex(hex) != null,
                            modifier = Modifier.weight(1f).testTag("theme-apply")) { Text(if (saving) "保存中…" else "应用") }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(ThemeSpacing.medium), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium)) {
                Text("全局配色试验", style = MaterialTheme.typography.titleLarge)
                Text("修改仅影响下方预览，点击应用后全局生效并保存在本机。恢复默认配色会立即保存并生效。",
                    style = MaterialTheme.typography.bodyMedium)
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(selected.label) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ThemeRole.entries.forEach { role ->
                            DropdownMenuItem(text = { Text(role.label) }, onClick = {
                                selectedName = role.name; hex = draft.hex(role); menu = false; message = ""
                            })
                        }
                    }
                }
                OutlinedTextField(value = hex, onValueChange = ::edit, enabled = !saving,
                    label = { Text("HEX 色值") }, prefix = { Text("#") }, singleLine = true,
                    isError = normalizeHex(hex) == null,
                    supportingText = { Text(if (normalizeHex(hex) == null) "请输入六位十六进制色值，例如 336699" else "不含透明度；支持粘贴 #RRGGBB") },
                    modifier = Modifier.fillMaxWidth().testTag("theme-hex"))
                Surface(color = draft.color(selected), shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(ThemeSpacing.small / 8, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().height(ThemeSize.swatch)) {}
                // RGB sliders form a continuous color picker; no external library or fixed swatches.
                listOf("红 R", "绿 G", "蓝 B").forEachIndexed { index, label ->
                    val current = draft.hex(selected).substring(index * 2, index * 2 + 2).toInt(16)
                    Text("$label：$current", style = MaterialTheme.typography.labelLarge)
                    Slider(value = current.toFloat(), onValueChange = { value ->
                        val replacement = value.toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
                        edit(draft.hex(selected).replaceRange(index * 2, index * 2 + 2, replacement))
                    }, valueRange = 0f..255f, enabled = !saving,
                        modifier = Modifier.testTag("theme-channel-$index"))
                }
                TextButton(onClick = {
                    saving = true
                    scope.launch {
                        val defaults = ThemePalette()
                        val success = try { onApply(defaults) } catch (_: java.io.IOException) { false }
                        if (success) { encoded = defaults.encode(); hex = defaults.hex(selected) }
                        saving = false
                        message = if (success) "已恢复默认配色并保存" else "保存失败，原配色保持不变，请重试"
                    }
                }, enabled = !saving, modifier = Modifier.testTag("theme-reset")) { Text("恢复默认配色") }
                if (message.isNotEmpty()) Text(message, modifier = Modifier.testTag("theme-message"))
                Text("实时预览", style = MaterialTheme.typography.titleMedium)
                DartVioTheme(darkTheme = false, paletteOverride = draft) { ThemePreviewContent() }
                Text("可读性检查", style = MaterialTheme.typography.titleMedium)
                val warnings = draft.contrastWarnings()
                Text(if (warnings.isEmpty()) "✓ 已检查的文字配对均达到 4.5:1" else "⚠ 对比度不足，仅提示，不阻止试色：")
                warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun ThemePreviewContent() {
    val status = LocalStatusColors.current
    Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(ThemeSpacing.small / 8, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(ThemeSpacing.medium), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small)) {
            Text("DartVio · 配色预览", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, tonalElevation = ThemeElevation.card) {
                Text("容器与正文", Modifier.fillMaxWidth().padding(ThemeSpacing.medium))
            }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("主要操作") }
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary), modifier = Modifier.fillMaxWidth()) { Text("辅助操作") }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("不可用 · 禁用状态") }
            listOf(Triple("错误：请检查输入", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError),
                Triple("警告：请确认操作", status.warning, status.onWarning),
                Triple("成功：已完成", status.success, status.onSuccess)).forEach { (text, background, foreground) ->
                Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.small) {
                    Text(text, Modifier.fillMaxWidth().padding(ThemeSpacing.small))
                }
            }
        }
    }
}

@Preview(name = "Light tokens", showBackground = true)
@Composable
private fun LightTokensPreview() { DartVioTheme(darkTheme = false) { ThemePreviewContent() } }

@Preview(name = "Dark tokens", showBackground = true)
@Composable
private fun DarkTokensPreview() { DartVioTheme(darkTheme = true) { ThemePreviewContent() } }

/**
 * 设置页外壳（「设置」与「外观与主题」两级共用）。
 *
 * [showAbout]：只有**一级「设置」页**挂「关于 DartVio」（2026-09-27 反馈：版本信息放哪里）。
 * 二级「外观与主题」页不挂 —— 它是一次具体任务的页面（改配色），
 * 页脚再挂一块与主题无关的版本卡，等于在那次任务的中途插一句别的话。
 *
 * 关于卡钉在**内容末尾**（`weight(1f)` 之后）：它是这一页最不常用的东西，
 * 而「找版本」的人会一路滚到底 —— 这正好是它该在的位置。
 */
@Composable
fun ThemeSettingsPage(
    title: String,
    entry: String,
    onOpen: () -> Unit,
    onBack: () -> Unit,
    onRestore: (suspend () -> Boolean)? = null,
    showAbout: Boolean = false,
) {
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    BackHandler { if (!saving) onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack, enabled = !saving) { Text("返回") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(ThemeSpacing.medium)) {
            OutlinedButton(onClick = onOpen, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(entry) }
            if (onRestore != null) {
                OutlinedButton(onClick = {
                    saving = true
                    scope.launch {
                        val success = try { onRestore() } catch (_: java.io.IOException) { false }
                        saving = false
                        message = if (success) "已恢复默认配色并保存" else "保存失败，原配色保持不变，请重试"
                    }
                }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("settings-theme-reset")) { Text("恢复默认配色") }
            }
            if (message.isNotEmpty()) Text(message)
            if (showAbout) {
                Spacer(Modifier.weight(1f))
                AboutVersionCard()
            }
        }
    }
}
