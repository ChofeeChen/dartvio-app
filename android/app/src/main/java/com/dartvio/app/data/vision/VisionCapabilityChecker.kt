package com.dartvio.app.data.vision

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dartvio.app.domain.vision.VisionCapability

/**
 * 读取设备真实条件并产出 [VisionCapability]（PRD M12 §4 能力 gate）。
 *
 * 纯 Android 框架实现，不依赖任何相机 / ML 库。
 */
object VisionCapabilityChecker {

    fun check(context: Context): VisionCapability {
        val memoryInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.getMemoryInfo(memoryInfo)

        return VisionCapability(
            sdkInt = Build.VERSION.SDK_INT,
            totalRamBytes = memoryInfo.totalMem,
            hasCamera = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        )
    }
}
