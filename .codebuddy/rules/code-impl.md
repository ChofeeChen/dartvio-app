---
enabled: true
alwaysApply: true
priority: high
---

# 代码实现与协作规则

## 版本与发布

- 版本号唯一出处：`android/app/build.gradle.kts` 顶部的 `appVersionCode` / `appVersionName`。
  `versionName = 0.1.<versionCode>`，**只增不减**；改版本同时改注释里的变更说明。
- 对外包一律 `assembleBeta`（真签名 + 不可调试 + 匿名统计开），产物归档到 `2.1 DartVio APK发布包/`，
  命名 `DartVio_BetaDemo_v{version}_{特性}_{YYYYMMDD}.apk`，并维护 `DartVio_BetaDemo_latest.apk`。
- 每次发版在 `APK版本清单.md` 台账置顶插一行（版本/日期/变更/体积/SHA256/验证状态）。
- 命令行构建前先设 `JAVA_HOME` 到 Android Studio 自带 JDK 25，否则工具链解析失败。

## 改代码的默认姿态

- 废弃能力 = **入口与代码一并删除**，不保留兼容层、不"隐藏入口"；确需保留（如协议字段兼容老端）要单独写理由。
- 交互形态优先对齐竞品成熟做法（Dartsmind、n01 转播口径），不自创。
- 纯逻辑（规则、统计口径）放 `domain/`，保持**无 Android 依赖、可单测**——这是抽 KMP `shared` 模块的前提。
- 改逻辑必须带单测：新增规则/口径至少覆盖边界值；发版前 `testDebugUnitTest` 全绿。

## 跨窗口协作（多个窗口同时开同一项目）

- 对话上下文**不共享**。结论要落到文件，不靠窗口之间传话。
- 实现完成后产出「**回写清单**」文件（改了什么、与 PRD 的偏差、待真机确认项），交给 PRD 窗口更新文档。
- 同一个文件同一时刻只让一个窗口写；对端改完文件后，需显式让本窗口**重新读**该文件（它不会自动感知变化）。
- 提交前用 `git diff` 自查：不提交密钥、构建产物、`local.properties`、APK、邀请码。

## 双平台（D-PLATFORM-002）

- Windows 负责 Android 开发与出包；MacBook 负责 iOS。签名材料各自留在各自机器，不外传、不入库。
- `shared`（KMP）模块里不许出现 Android/iOS 专有 API；平台差异用 `expect/actual`。
- 两端共用的口径（规则、统计、协议字段）以 `shared` 中代码为唯一真源，PRD 只描述口径不写死实现。
