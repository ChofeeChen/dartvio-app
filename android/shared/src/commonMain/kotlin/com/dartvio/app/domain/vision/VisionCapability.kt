package com.dartvio.app.domain.vision

/**
 * 手机视觉计分的能力门槛（PRD M12 F12.5 / §4）。
 *
 * ★ 这是**特性级 gate**，不修改全局 `minSdk = 24`：
 * 不达标时仅在 UI 层隐藏 M12 入口，并回落 M3 手动录入。
 */
data class VisionCapability(
    val sdkInt: Int,
    val totalRamBytes: Long,
    val hasCamera: Boolean,
) {
    /** 三项全部满足才视为支持。 */
    val isSupported: Boolean
        get() = hasCamera && sdkInt >= MIN_SDK && totalRamBytes >= MIN_RAM_BYTES

    /** 未达标原因（用于可读提示）；全部达标时为 null。 */
    val unsupportedReason: String?
        get() = when {
            !hasCamera -> "设备无可用摄像头"
            sdkInt < MIN_SDK -> "系统版本过低（需 Android 10 及以上）"
            totalRamBytes < MIN_RAM_BYTES -> "运行内存不足（需 4GB 及以上）"
            else -> null
        }

    companion object {
        /** Android 10（API 29）。 */
        const val MIN_SDK = 29

        /** 4 GB。 */
        const val MIN_RAM_BYTES = 4L * 1024 * 1024 * 1024
    }
}
