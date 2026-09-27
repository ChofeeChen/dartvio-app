package com.dartvio.app.platform

/**
 * 平台时钟 —— 跨端唯一的「时间入口」。
 *
 * ## 为什么需要它
 *
 * `domain/` 下沉到 KMP 的 `shared` 模块后，`java.time` / `java.util.Calendar` / `SimpleDateFormat`
 * 在 common 里全部不可用。但「现在是几点」「今天是几号」这类口径又必须按**设备本地时区**算 ——
 * 用户的心智是「昨天练过」，不是「UTC 的昨天」。
 *
 * 因此把平台差异收敛成**一个数字**：时区偏移（毫秒）。剩下的日历运算全部用纯 Kotlin 实现
 * （见 [CivilDateTime]），两端共用同一份代码，也共用同一批单测 —— 这正是抽 shared 的目的。
 *
 * ## 为什么不用 kotlinx-datetime
 *
 * 它在 Android 上底层依赖 `java.time`，而本项目 `minSdk = 24` 且**刻意未开启 core library
 * desugaring**（见 `android/README_DEV.md`）。引入它会反过来逼我们开脱糖，属于本模块要避开的依赖。
 *
 * ## 为什么暴露的是偏移而不是时区
 *
 * `java.util.TimeZone` 与 `NSTimeZone` 是两个世界的类型，跨 common 只能交换数字。
 * 传时刻参数是因为夏令时：同一个时区在不同日期的偏移并不相同。
 */
expect object PlatformTime {

    /** 当前时刻（epoch 毫秒）。 */
    fun nowMillis(): Long

    /**
     * [atMillis] 这一刻，设备本地时区相对 UTC 的偏移（毫秒）。
     *
     * 东八区 = `+28_800_000`。
     */
    fun zoneOffsetMillis(atMillis: Long): Long
}

/**
 * 32 位十六进制随机串（小写、无连字符）。
 *
 * 用来替代 `java.util.UUID`（common 里没有）：Android 端仍是 UUID，iOS 端是 NSUUID。
 * 调用方按需截断（如本地档案 ID 取前 16 位）。
 */
expect fun randomIdHex(): String
