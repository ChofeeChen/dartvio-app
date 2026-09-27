package com.dartvio.app.platform

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUUID

/*
 * ⚠️ 本文件**只能在 macOS 上编译**（Kotlin/Native 需要 Xcode 工具链）。
 * Windows 上构建请只跑 Android 任务：`gradlew :app:testDebugUnitTest`。
 * 因此这里写下的 API 需在 Mac 首次编译时验证一遍（Foundation 的 Kotlin 绑定偶有命名差异）。
 */
actual object PlatformTime {

    actual fun nowMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    /**
     * 与 Android 端一样按**具体时刻**取偏移（而不是取「当前偏移」）：
     * 夏令时切换日前后，同一个时区的偏移并不相同。
     */
    actual fun zoneOffsetMillis(atMillis: Long): Long =
        NSTimeZone.systemTimeZone
            .secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0))
            .toLong() * 1000L
}

actual fun randomIdHex(): String = NSUUID().UUIDString.replace("-", "")
