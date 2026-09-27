package com.dartvio.app.domain.vision

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource

/**
 * 一帧待识别的静帧（PRD M12 §4）。
 *
 * 由相机侧产出；**不上传、不落盘**，仅在内存中流转。
 */
class RecognitionFrame(
    val width: Int,
    val height: Int,
    /** RGBA_8888 像素，长度 = width * height * 4。 */
    val pixels: IntArray,
)

/**
 * 单镖识别候选（PRD M12 §6）。
 *
 * ★ 候选**绝不直接计分** —— 必须经用户「确认」或「修正」后，
 * 才以 [Dart] 形式提交给 `GameViewModel.throwDart(dart, DartSource.PHONE_VISION)`。
 */
data class DartCandidate(
    val dart: Dart,
    /** 置信度 [0,1]，与 M8 同口径。 */
    val confidence: Double,
    val source: DartSource = DartSource.PHONE_VISION,
) {
    /** ≥ 90% 视为高置信度（M12 §5.3 / §6，与 M8 完全一致）。 */
    val isHighConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD

    companion object {
        /** 置信度阈值：与 M8 共用同一口径，M12 不新立标准。 */
        const val HIGH_CONFIDENCE_THRESHOLD = 0.90
    }
}

/**
 * 单镖识别器契约（PRD M12 §5 识别管线）。
 *
 * 本接口是「目标态」骨架：MVP 阶段只提供契约，不引入 CameraX / LiteRT 依赖
 * （具体模型选型、部署与验收判据见《M12 技术预研（PoC）计划》§11）。
 */
interface DartRecognizer {

    /** 模型与标定是否就绪；未就绪时调用方应回落 M3 手动面板。 */
    val isReady: Boolean

    /** 当前标定的反投影残差（mm）；未标定时为 null。 */
    val calibrationResidualMm: Double?

    /**
     * 识别一帧，返回单镖候选；无法判定时返回 null（调用方回落手动录入）。
     * 实现应在后台线程执行，禁止在主线程调用。
     */
    suspend fun recognize(frame: RecognitionFrame): DartCandidate?
}
