import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * KMP 共享核心（2026-09-27 建）。
 *
 * ## 为什么有这个模块
 *
 * 双平台路线定为「KMP 共享核心 + iOS 原生 UI」：规则判定、统计口径、成就这些**口径**必须两端
 * 一字不差，各写一遍迟早会漂移（用户会看到同一份战绩在两台手机上算成两个样）。
 * 因此把 `domain/` 整体下沉到这里，Android 继续 Compose、iOS 用 SwiftUI，但共用同一份口径。
 *
 * ## 硬约束
 *
 * commonMain **不得**引入 `android.*` / `androidx.*` / `java.time` / `java.util.Calendar`，
 * 也不得反向依赖 `com.dartvio.app.data.*`（Room 实体）—— 那是 Android 专属。
 * 平台差异只允许通过 `platform/` 下的 `expect/actual` 进入（目前只有「现在几点」与「随机 ID」两个）。
 *
 * ⚠️ `internal` 在跨模块后对 `:app` 不可见：domain 下沉时，被 UI / 单测用到的 internal 成员
 * 已统一改为 public，并在 KDoc 里注明「仍是内部实现」。
 * ⚠️ `com.dartvio.app.domain.{stats,achievement,leaderboard}` 暂留 `:app` ——
 * 它们通过 Room 的 `MatchWithPlayers` 反向依赖 data 层，需先解耦才能下沉（见 T7 §5.2 D5）。
 *
 * ## 为什么是 `com.android.kotlin.multiplatform.library`
 *
 * AGP 9.0 起 `com.android.library` 与 KMP 插件不兼容，官方改为这个插件。
 * 差异：没有 build 变体（单变体）、Android 配置写进 `kotlin { android { } }`、
 * 默认关闭测试与资源处理（本模块都不需要）。
 *
 * ## Windows 上的编译范围
 *
 * iOS 目标只能在 macOS 上编译（需要 Xcode）。Windows 只跑 Android 任务：`gradlew :app:testDebugUnitTest`。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.dartvio.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // 单测留在 :app（650 例原样跑，见 app/src/test），本模块不另开测试源集。
    }

    // 真机（iPhone）与 Apple Silicon 模拟器。Intel Mac 若需模拟器再补 iosX64()。
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // 刻意不加任何依赖：共享核心保持零依赖，iOS 端才好直接吃。
        }
    }
}
