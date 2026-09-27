# DartVio — 项目上下文（所有窗口共读）

> 这份文件是**项目级单一上下文入口**：IDE 窗口、独立 Agents 窗口、CLI 都会自动加载它。
> 任何"换个窗口又要重新交代一遍"的信息，都应该写在这里或 `.codebuddy/rules/` 下，而不是留在对话里。

## 1. 产品一句话

DartVio：面向飞镖爱好者的**移动端** App，本地 2 人比赛 / 在线对战 / AI 对手 / 练习模式 / 统计分析。
对标 Dartsmind 与「好镖」，差异化在自动计分（硬件 M8 / 手机视觉 M12）与在线对战。

## 2. 当前状态（2026-09-27）

| 项 | 值 |
| --- | --- |
| Android App | `versionCode = 18`、`versionName = 0.1.18`（唯一出处：`android/app/build.gradle.kts` 顶部两个 val） |
| 平台 | Android 已发 Beta；**iOS 待启动** |
| 技术栈 | Kotlin 2.2.10 + Jetpack Compose（BOM 2026.02.01）+ Room 2.7.2；Gradle 9.5 / AGP 9.3；minSdk 24 / targetSdk 37 |
| 自动化门禁 | `testDebugUnitTest` 65 文件 / 653 例 / 0 fail；`assembleBeta` 通过 |
| 后端 | 联机 = **自建后端**（蜂窝可达，VM `/opt/dartvio`）；匿名统计 = Supabase（本机 TLS 被重置，E2E 需真机/换网络） |

## 3. 目录地图

```
<仓库根>/
├─ CODEBUDDY.md                    本文件（单一上下文入口）
├─ .codebuddy/rules/               项目规则（自动加载，入库共享）
├─ .codebuddy/memory/              项目级记忆
├─ android/                        Android 工程（2026-09-27 由 App_DartVio_Android_CB_V0.1 改名）
│  └─ shared/                      ✅ KMP 共享模块（2026-09-27 建成，:app 已依赖）
│     ├─ commonMain/domain/{model,rules,versus,practice,room,credit,impact,vision,profile}   两端共用
│     ├─ commonMain/platform/      expect：PlatformTime / randomIdHex + 纯 Kotlin 日历
│     ├─ androidMain/ iOS Main/    actual：TimeZone+UUID / NSTimeZone+NSUUID
│     └─ ⚠️ domain/{stats,achievement,leaderboard} 暂留 android/app（依赖 Room，见 T7 §5.2 D5）
└─ ios/                            待建：Xcode 工程
├─ docs/prd/                       现行 PRD（20 份 .md，唯一现行版）
├─ docs/archive/                   归档：docx 原件、历史文档_待清理、_backup_*（不入库）
├─ DartVio APK发布包/              对外 APK + APK版本清单.md + 邀请码台账（APK 与邀请码不入库）
└─ tools/                          一次性脚本（docx2md.py 等）
```

## 4. 硬约束（做任何决定前先读）

1. **文档只留一份现行版**：在现有文件上原地升版本号，**禁止新建平行文档**。归档/删除文件前先问用户。
2. **APK 只归档到 `DartVio APK发布包`**，不得另建平行目录；版本/命名/台账规则看该目录下 `APK版本清单.md`。
3. **版本号唯一出处** = `app/build.gradle.kts` 的 `appVersionCode` / `appVersionName`；`versionName = 0.1.<versionCode>`，只增不减。
4. **重构取舍**：废弃能力**连代码一起删**，不搞"隐藏入口、保留代码"；交互形态优先对齐竞品（Dartsmind / n01 转播口径）。
5. **命令行构建**必须先设 `JAVA_HOME` 到 Android Studio 自带 JDK 25，否则工具链解析会失败。
6. **敏感文件不入库**：`keystore.properties`、`local.properties`、`keystore/*.jks`、`DartVio_邀请码台账_*.txt`、所有 `.apk`。仓库只放 `.example` 模板。
7. **双机分工**：Windows = Android 开发 + 出包（keystore 仅存 Windows）；MacBook = iOS 开发 + 出包（Apple 证书仅存 Mac）。两端都不把对方的签名材料拷过去。
8. **签名密钥是本产品唯一「丢了不可逆」的资产**（丢 = 无法更新已装应用，用户只能卸载重装）。不入库之外，必须做**加密备份 ≥ 2 份、1 份异地**，并做恢复演练；上架前启用 Google Play 应用签名把风险降为可恢复。详见 `docs/prd/T7_双机双平台迁移准备清单_V1.03.md` §8。

## 5. 双平台路线（D-PLATFORM-002，2026-09-27 确认）

- 共享核心：**Kotlin Multiplatform**（`shared` 模块承载 domain / rules / stats，纯 Kotlin 无 Android 依赖）
- Android：继续 Jetpack Compose，直接依赖 `shared`
- iOS：Swift + SwiftUI 原生 UI，通过 KMP framework 复用 `shared`
- 迁移顺序：先抽 `shared`（规则引擎与统计口径优先，它们有 650 例单测可当回归网），再起 iOS 工程

## 6. 上下文从哪来（两个窗口如何协同）

共享：磁盘文件 + 本文件 + `.codebuddy/rules/` + `.codebuddy/memory/` + 用户级 `~/.codebuddy/`（Skills / MCP）
**不共享**：对话历史、打开的文件、终端输出。

因此协同规则是：**结论落到文件里，不靠窗口之间传话**。实现完成后写「回写清单」文件，需求变更直接改 PRD 文件，另一个窗口下次启动自然会读到。
