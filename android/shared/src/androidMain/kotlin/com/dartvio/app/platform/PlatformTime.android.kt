package com.dartvio.app.platform

import java.util.TimeZone
import java.util.UUID

actual object PlatformTime {

    actual fun nowMillis(): Long = System.currentTimeMillis()

    /**
     * `TimeZone.getDefault().getOffset(millis)` 带时刻参数，因此夏令时切换日也正确。
     *
     * 刻意不用 `java.time`：minSdk 24/25 未开核心库脱糖时会**运行期** `NoClassDefFoundError`。
     */
    actual fun zoneOffsetMillis(atMillis: Long): Long =
        TimeZone.getDefault().getOffset(atMillis).toLong()
}

actual fun randomIdHex(): String = UUID.randomUUID().toString().replace("-", "")
