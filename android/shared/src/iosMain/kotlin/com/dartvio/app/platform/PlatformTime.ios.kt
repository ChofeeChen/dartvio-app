package com.dartvio.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTimeZoneCopySystem
import platform.CoreFoundation.CFTimeZoneGetSecondsFromGMT
import platform.Foundation.NSDate
import platform.Foundation.NSTimeIntervalSince1970
import platform.Foundation.NSUUID

/*
 * ⚠️ 本文件**只能在 macOS 上编译**（Kotlin/Native 需要 Xcode 工具链）。
 * Windows 上构建请只跑 Android 任务：`gradlew :app:testDebugUnitTest`。
 *
 * 2026-09-27 Mac 首次编译实测（Kotlin 2.2.10 / Xcode 27）：Foundation 绑定里下面三个 API
 * **并不存在**，编译器报 Unresolved reference：
 *   - `NSDate.timeIntervalSince1970`
 *   - `NSDate.dateWithTimeIntervalSince1970(...)`
 *   - `NSTimeZone.systemTimeZone`（`localTimeZone` / `defaultTimeZone` / `system` / `timeZoneWithName` 同样不可用）
 * 另外 `NSTimeZone()` 虽然能编译，但运行时 `name` 为 null —— 它不是「系统时区」，不能用。
 *
 * 实际可用的替代（均已编译通过，并编译成 macOS 可执行程序实测过数值）：
 *   - 当前时刻：`NSDate()`（对应 `+[NSDate date]`）→ `timeIntervalSinceReferenceDate` + `NSTimeIntervalSince1970`
 *   - 某时刻的时区偏移：`CFTimeZoneCopySystem()` + `CFTimeZoneGetSecondsFromGMT(tz, atTime)`（CoreFoundation）
 */
@OptIn(ExperimentalForeignApi::class)
actual object PlatformTime {

    actual fun nowMillis(): Long =
        ((NSDate().timeIntervalSinceReferenceDate + NSTimeIntervalSince1970) * 1000.0).toLong()

    /**
     * 与 Android 端一样按**具体时刻**取偏移（而不是取「当前偏移」）：
     * 夏令时切换日前后，同一个时区的偏移并不相同。
     *
     * CF 的绝对时间以「2001-01-01」为原点（秒），而入参是 epoch 毫秒，
     * 因此先减掉 [NSTimeIntervalSince1970]（978307200）再换算成秒。
     */
    actual fun zoneOffsetMillis(atMillis: Long): Long {
        val timeZone = CFTimeZoneCopySystem()
        return try {
            CFTimeZoneGetSecondsFromGMT(
                timeZone,
                atMillis / 1000.0 - NSTimeIntervalSince1970,
            ).toLong() * 1000L
        } finally {
            CFRelease(timeZone)
        }
    }
}

actual fun randomIdHex(): String = NSUUID().UUIDString.replace("-", "")
