package com.dartvio.app.domain.model

/**
 * 一镖（或一个回合）的录入来源。
 *
 * 对应 PRD M3 §3.1 的 `input_source` 枚举；M9 §5.5 依据本枚举区分数据质量档：
 * - 仅 [HARDWARE_VISION]（M8 外置硬件）可标注「硬件认证」；
 * - [PHONE_VISION]（M12 手机视觉计分）为半自动辅助，属中等质量，**不得**标注「硬件认证」。
 *
 * 说明：本枚举为 M12 前置改造新增，是 M3 / M9 中 `phone_vision` 由「目标态」落到代码的第一步。
 */
enum class DartSource(
    /** UI 展示名。 */
    val label: String,
    /** 数据质量档（M9 §5.5）。 */
    val quality: QualityTier,
) {
    /** 键盘逐镖录入（M3 默认）。 */
    DART_BY_DART("逐镖录入", QualityTier.MANUAL),

    /** 快速总分：一次录入一回合总分。 */
    QUICK_TOTAL("快速总分", QualityTier.MANUAL),

    /** 靶面点选录入。 */
    BOARD_TAP("靶面点选", QualityTier.MANUAL),

    /** M8 外置硬件自动计分 —— 唯一可标注「硬件认证」的来源。 */
    HARDWARE_VISION("硬件自动计分", QualityTier.CERTIFIED),

    /** M12 手机视觉计分（半自动：识别候选 + 用户确认 / 修正）。 */
    PHONE_VISION("手机视觉计分", QualityTier.SEMI_AUTO),

    /** 语音录入。 */
    VOICE("语音录入", QualityTier.MANUAL),

    /** AI 对手托管生成。 */
    AI_GENERATED("AI 生成", QualityTier.MANUAL),

    /** 落点诊断训练（M11 新增子练习）：记录「瞄准意图 + 手点落点」。 */
    IMPACT_DRILL("落点训练", QualityTier.MANUAL),
    ;

    /** 是否为「硬件认证」数据源（仅 M8 外置硬件）。 */
    val isCertified: Boolean get() = this == HARDWARE_VISION

    /** 是否来自自动识别（硬件或手机视觉）。 */
    val isAuto: Boolean get() = this == HARDWARE_VISION || this == PHONE_VISION

    companion object {
        /** 默认来源：键盘逐镖录入。 */
        val DEFAULT: DartSource = DART_BY_DART

        /** 由枚举名反查（大小写不敏感）；未知值回落 [DEFAULT]。 */
        fun fromName(name: String?): DartSource =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** 数据质量档（PRD M9 §5.5）。 */
enum class QualityTier(val label: String) {
    /** 硬件认证（仅 M8 外置硬件）。 */
    CERTIFIED("硬件认证"),

    /** 中等质量（手机视觉半自动，M12）。 */
    SEMI_AUTO("中等（半自动）"),

    /** 人工录入。 */
    MANUAL("人工录入"),
}
