package com.dartvio.app.data.vision

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * 手机视觉计分的 app 级偏好（PRD M12）。
 *
 * 现状说明：App 目前没有集中的 Settings 存储（`smartAi` 随 `MatchConfig` 仅内存传递，
 * 已有的 SharedPreferences 均为功能私有）。M12 需要一个**跨对局持久化**的开关，
 * 故新建本 Store，命名与 [com.dartvio.app.data.achievement.PracticePrefsStore] 保持同构。
 */
class VisionSettings(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户是否启用手机视觉计分（默认关闭；需先完成一次性标定）。 */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** 低置信度时是否提示用户确认（与 M8 同名同义，默认开启）。 */
    var lowConfidencePrompt: Boolean
        get() = prefs.getBoolean(KEY_LOW_CONFIDENCE_PROMPT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LOW_CONFIDENCE_PROMPT, value).apply()
        }

    /** 标定反投影残差（mm）；未标定为 null。残差超阈值时应提示重新标定。 */
    var calibrationResidualMm: Double?
        get() = if (prefs.contains(KEY_RESIDUAL)) prefs.getFloat(KEY_RESIDUAL, 0f).toDouble() else null
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove(KEY_RESIDUAL) else e.putFloat(KEY_RESIDUAL, value.toFloat())
            e.apply()
        }

    /** 相机运行期权限是否已授予（M12 使用 `CAMERA`，见 AndroidManifest）。 */
    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val PREFS_NAME = "dartvio_vision"
        private const val KEY_ENABLED = "vision_enabled"
        private const val KEY_LOW_CONFIDENCE_PROMPT = "vision_low_confidence_prompt"
        private const val KEY_RESIDUAL = "vision_calibration_residual_mm"
    }
}
