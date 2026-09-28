# iOS 首版页面对照表 V1.00

> 生成时间：2026-09-27，由 **MCB（Mac 端 CodeBuddy）** 产出。
> 数据全部来自本机实测读取，未修改任何代码（只读盘点，不与 WCB 正在进行的 commonMain 修改冲突）。
>
> 数据来源：
> - `android/app/src/main/java/com/dartvio/app/ui/navigation/DartVioNavHost.kt`（路由图，`Routes` 对象）
> - `android/app/src/main/java/com/dartvio/app/ui/navigation/DartVioBottomBar.kt`（一级 Tab）
> - `android/shared/src/commonMain/kotlin/com/dartvio/app/`（69 文件 / 9835 行，已下沉能力）
>
> 用途：iOS 端（SwiftUI）写页面时的施工图 + 判断哪些页现在就能做、哪些必须等 WCB 解耦。

---

## 0. 一句话结论

**shared 对「本地对局 + 练习」的支撑是 100% 完整的**；卡住的是 **统计 / 成就 / 排行榜（domain 还在 `:app`）** 与 **联机（网络层 `net/` 还在 `:app`）**。
→ iOS 首版应严格按提示词 §7 的**第一批**做，统计页先占位，联机整批推到第二批。

---

## 1. shared 已下沉能力清单（69 文件 / 9835 行）

| 包 | 文件数 | 内容 | iOS 可用性 |
| --- | --- | --- | --- |
| `domain/rules` | 6 | `X01Rules` `CricketRules` `X01Ai` `CricketAi` `AdaptiveAiController` `AiProfile` | ✅ 纯 Kotlin |
| `domain/model` | 12 | `X01State` `CricketState` `Dart` `MatchConfig` `Enums` `PlayerAvatar` `X01RulesOptions` `CricketTarget` `CricketVariant` `MatchSource` `DartSource` | ✅ |
| `domain/practice` | 7 | `CountUpEngine` `RandomCheckoutEngine` `NinetyNineEngine` `CricketMprEngine` `CheckoutSolver` `CheckoutRushRules` `CheckoutRushSession` | ✅（MPR 那处 `String.format` 待 WCB 修） |
| `domain/versus` | 10 | `VersusRule` + 6 模式（BullBattle / ClockTriple / DoublesClock / HalveIt / RingRace / Shanghai）+ `VersusDartCodec` `VersusReport` `VersusModes` | ✅ |
| `domain/impact` | 10 | `ImpactWindow` `HeatmapGrid` `ImpactFrame` `ImpactStats` `ImpactTrend` `ImpactValue` `ImpactFingerprint` `ImpactMissBand` `ImpactPrescription` `IntentTarget` | ✅ |
| `domain/vision` | 7 | `BoardGeometry` `BoardLayout` `BoardViewport` `DartRecognizer` `Homography` `HeatmapCandidateDecoder` `VisionCapability` | ✅ 但 iOS 首版**不做**视觉计分（提示词 §7：M12 仅留接口） |
| `domain/room` | 14 | `RoomRules` `RoomModels` `RoomLiveState` `RoomLobbyState` `RoomEvent` `RoomExpiry` `RoomIdentity` `RoomIds` `RoomJoinPolicy` `RoomMatch` `RoomSchedule` `MatchStats` `NicknameRules` `OfficialArena` | ⚠️ 口径已下沉，但**网络层 `net/` 仍在 `:app`** → 联机第二批 |
| `domain/profile` | 1 | `LocalProfile` | ✅ |
| `domain/credit` | 1 | `CreditScorer` | ✅ |
| `platform` | 2 | `PlatformTime`（expect/actual，iOS actual **已完成**）、`CivilDateTime` | ✅ |

**`commonMain` 刻意零第三方依赖**（`shared/build.gradle.kts` 注释：「共享核心保持零依赖，iOS 端才好直接吃」）
→ iOS 侧不需要为任何三方库找 iOS 版本，编成 framework 即可直接用。

**expect/actual 只有 2 个**（`PlatformTime` 对象 + `randomIdHex()`），iOS 侧**已全部实现完毕**（`PlatformTime.ios.kt`，含 `nowMillis` / `zoneOffsetMillis` / `randomIdHex`）。

---

## 2. Android 页面 → iOS 首版批次对照

### 一级 Tab（4 个，底部导航）

| 路由 | 名称 | shared 支撑 | iOS 首版 |
| --- | --- | --- | --- |
| `home` | 首页 | —（纯 UI） | ✅ 批① |
| `lobby` | 大厅 | `domain/room` ✅，但 `net/` ⛔ | ⛔ 批② |
| `stats` | 数据 | ⛔ `domain/stats` 未下沉 | ⛔ 占位（等 T7 D5） |
| `profile` | 我的 | `LocalProfile` ✅ | ✅ 批① |

### 二级页面

| 路由 | 页面 | Android 文件 | shared 支撑 | iOS 首版 |
| --- | --- | --- | --- | --- |
| `x01_setup` | X01 设置 | `X01SetupScreen` | `MatchConfig` `X01RulesOptions` ✅ | ✅ 批① |
| `x01_detail` | X01 详细设置 | `X01DetailSettingsScreen` | 同上 ✅ | ✅ 批① |
| `cricket_games` | Cricket 玩法选择 | `CricketGamesScreen` | `CricketVariant` `CricketTarget` ✅ | ✅ 批① |
| `cricket_settings` | Cricket 设置 | `CricketSettingsScreen` | ✅ | ✅ 批① |
| `game` | 对局页（X01 / Cricket） | `GameScreen` `X01GameScreen` `CricketGameScreen` `GameViewModel` | `X01Rules` `CricketRules` `X01State` `CricketState` `X01Ai` `CricketAi` ✅ | ✅ 批① **核心页** |
| （同上） | 胜利页 | `VictoryScreen` | ✅ | ✅ 批① |
| `practice` | 训练中心 | `PracticeModeScreen` | — | ✅ 批① |
| `practice/solo` | 单人训练 | `PracticeSoloScreen` | — | ✅ 批① |
| `practice/count_up` | Count Up | `CountUpScreen` + VM | `CountUpEngine` ✅ | ✅ 批① |
| `practice/checkout` | 结镖训练入口 | `CheckoutTrainingEntryScreen` | `CheckoutRushRules` ✅ | ✅ 批① |
| `practice/checkout_rush` | 极速挑战 | `CheckoutRushScreen` + VM | `CheckoutRushSession` ✅ | ✅ 批① |
| `practice/checkout_rush_report` | 极速挑战报告 | `CheckoutRushReportScreen` | ✅ | ✅ 批① |
| `practice/random_checkout` | 随机结镖（路线学习） | `RandomCheckoutScreen` + VM | `RandomCheckoutEngine` `CheckoutSolver` ✅ | ✅ 批① |
| `practice/ninety_nine` (+setup) | 99 Darts | `NinetyNineSetupScreen` `NinetyNineScreen` + VM | `NinetyNineEngine` ✅ | ✅ 批① |
| `practice/cricket_mpr` | Cricket MPR | `CricketMprScreen` + VM | `CricketMprEngine` ⚠️（含 `String.format`，WCB 正在修） | ✅ 批① |
| `practice/impact_setup` | 精准工坊·选目标 | `ImpactSetupScreen` | `impact.*` ✅ | ✅ 批① |
| `practice/impact_practice` | 精准工坊·逐镖点录 | `ImpactPracticeScreen` + VM | ✅ | ✅ 批① |
| `practice/impact_report` | 精准工坊·轮后报告 | `ImpactReportScreen` + VM | ✅ | ✅ 批① |
| `practice/ai` | AI 对战练习 | `MatchSetupScreen`(versusLocked) | ✅ | ✅ 批① |
| `practice/versus_list` | 双人对抗·模式列表 | `VersusListScreen` | `VersusModes` ✅ | ✅ 批① |
| `practice/versus_setup/{modeKey}` | 双人对抗·配置 | `VersusSetupScreen` | ✅ | ✅ 批① |
| `practice/versus` | 双人对抗·对局 | `VersusBattleScreen` | `VersusRule` + 6 模式 ✅ | ✅ 批① |
| `practice/versus_report` | 双人对抗·战报 | `VersusReportScreen` | `VersusReport` ✅ | ✅ 批① |
| `achievements` | 成就墙 | `AchievementScreen` | ⛔ `domain/achievement` 未下沉 | ⛔ 占位 |
| `leaderboard` | 排行榜 | `LeaderboardScreen` | ⛔ `domain/leaderboard` 未下沉 | ⛔ 占位 |
| `feedback` / `privacy` | 我的反馈 / 隐私声明 | `FeedbackScreen` `PrivacyPolicyScreen` | —（纯 UI/文案） | ✅ 批①（成本低） |
| `settings` / `appearance` / `theme-debug` | 设置 / 外观 / 主题调试 | `ThemeSettingsPage` `ThemeDebugScreen` | —（纯本地存储） | 🟡 批①可延后 |
| `lobby/*`（8 条路由） | 大厅 / 建房 / 等候 / 对局 / 观战 / 结算 / 联机 / 诊断 | `lobby/*` 共 11 文件 | `room.*` ✅，`net/` ⛔ | ⛔ 批② |
| `beta/*` | Beta 访问 / 过期 / 隐私同意 | `BetaAccessScreen` 等 | 依赖服务端 | ⛔ 批② 或不做 |

---

## 3. iOS 端需自建的部分（shared 不覆盖）

| 类别 | Android 对应 | iOS 需自己写 |
| --- | --- | --- |
| 主题 | `ui/theme/*`（7 文件：`Color` `Theme` `ThemePalette` `Type` `LayoutTokens` `DrawingColors` `ThemeDebugScreen`） | SwiftUI `Color` / `Font` 扩展 + 深浅色 |
| 通用组件 | `ui/components/*`（`Keypad` `BoardTapPad` `SetupComponents` `BarChart` `TrendLineChart`） | SwiftUI 重写 5 个组件 |
| 状态容器 | `GameViewModel` 等 AndroidX ViewModel | Swift `@Observable` / `ObservableObject` |
| 持久化 | Room / DataStore | iOS 侧待定（首版可先内存态，本地存档用 SwiftData 或文件） |
| 视觉计分 | `domain/vision` + 摄像头 | 提示词 §7：M12 仅留接口，**不做** |

---

## 4. 工程量估算

- Android UI 总计：**87 文件 / 29545 行**（含 theme 与 components）
- iOS 首版批①满配：约 **22–25 个页面** + 5 个通用组件 + 主题
- iOS 首版**最小可验证闭环**（满足「编译 + 看到 UI + 跳转点击」）：约 **5 个页面**（见 §5）

---

## 5. 建议的交付节奏（待用户确认）

| 档 | 内容 | 页数 | 目的 |
| --- | --- | --- | --- |
| **MVP（推荐先做）** | 首页 → X01 设置 → 对局页（含键盘 + 计分 + 胜利页）→ 返回；外加 1 个练习页（Count Up） | ~5 | 最快打通「Xcode 编译 → 模拟器跑起来 → 能跳转能点」 |
| 批①满配 | 上面 + Cricket 两设置页 + 其余 5 个单人练习 + 精准工坊 3 页 + 双人对抗 4 页 + 我的/隐私/反馈 | ~22 | 对齐 Android v0.1.17 的本地功能 |
| 批② | 大厅 / 联机 / 统计 / 成就 / 排行榜 | ~13 | 需等 WCB 完成 T7 D5 解耦与网络层下沉 |

---

## 6. 前置依赖与阻塞

| # | 阻塞 | 归属 | 说明 |
| --- | --- | --- | --- |
| D1 | `commonMain` 10 处 JVM-only API | **WCB**（进行中） | 见回写清单 §4.6 |
| D2 | `shared/build.gradle.kts` 加 `binaries.framework { baseName="shared"; isStatic=true }` | MCB（需先回写确认，提示词 §6） | 两端共用文件，改前需 WCB 点头 |
| D3 | 建 `ios/` Xcode 工程 + embed 脚本 | MCB | 提示词 §6：第 4 阶段过 + 回写确认后再开始 |
| D4 | `domain/{stats,achievement,leaderboard}` 下沉 | **WCB**（T7 §5.2 D5） | 决定 iOS 统计页何时能做 |
| D5 | 网络层 `net/` 下沉 | **WCB** | 决定联机大厅（批②） |

---

## 7. 待确认 → 已全部定案（见 §9）

| # | 事项 | 结论 |
| --- | --- | --- |
| C1 | iOS 最低版本 | ✅ **iOS 17+** |
| C2 | Bundle ID / App 名 / 图标 | ✅ `com.dartvio.app` / **DartVio** / 图标已产出（见 §9.2） |
| C3 | framework 集成方式 | ✅ 直接嵌入 + `ENABLE_USER_SCRIPT_SANDBOXING = NO`；备用 XCFramework（已验证可行） |
| C4 | 首版范围 | ✅ 先 **MVP（5 页）**，再批①满配 |
| C5 | iOS 侧持久化 | ✅ MVP 用内存态（`@Observable` 状态容器），存档后续再上 |
| C6 | 文档形式 | ✅ 独立文件（本文档） |

---

## 8. 工具链演练记录（/tmp/mcb-drill，2026-09-28，M2 完成）

演练工程位于 `/tmp/mcb-drill`（**仓库外**，不入 dartvio-app 仓库），最小 KMP 模块 + `binaries.framework { isStatic = true }`。

### 8.1 实测结果

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| Kotlin/Native **链接**（模拟器） | `linkDebugFrameworkIosSimulatorArm64` | ✅ **25s**（Kotlin 2.2.10 + Xcode 27 组合无兼容问题） |
| Kotlin/Native **链接**（真机） | `linkDebugFrameworkIosArm64` | ✅ |
| framework 产物结构 | — | ✅ `DrillShared.framework`（Headers / Modules / Info.plist 齐全，static ar archive） |
| XCFramework 装配（备用路径） | `xcodebuild -create-xcframework -framework …sim -framework …device` | ✅ 生成 `ios-arm64` + `ios-arm64-simulator` 双 slice |

### 8.2 两个重要发现

1. **XCFramework 的 Gradle DSL 在 Kotlin 2.2.10 里不存在**：
   `org.jetbrains.kotlin.gradle.plugin.mpp.XCFramework` → `Unresolved reference`。
   → 不要在真工程里用这个 DSL；备用路径直接用 `xcodebuild -create-xcframework` 命令行装配（已验证）。
2. 主路径任务 `embedAndSignAppleFrameworkForXcode` 在任务列表里存在 ✓。
   接入 Xcode 时工程须设 **`ENABLE_USER_SCRIPT_SANDBOXING = NO`**（Xcode 15+ 默认开启，会拦截
   Build Phase 里跑 Gradle 的脚本访问 `~/.gradle` 与网络）。

### 8.3 结论

**工具链无风险。** WCB 修完 commonMain 后，真工程产出 framework 只剩"跑一遍"的工作量，没有未知数。

---

## 9. 决策记录（用户已授权 MCB 按推荐执行）

### 9.1 各项决策

见 §7 表格。

### 9.2 图标产物（C2）

| 项 | 值 |
| --- | --- |
| 源素材 | `~/Downloads/DartVio-logo_005.png`（474×473、四角透明；另有矢量源 `android/app logo/DartVio-logo_002.ai`） |
| 产物 | `/Users/chenfeng/dartvio-assets/AppIcon-1024.png`（**1024×1024、无 alpha 通道（PNG color type 2）、sRGB、全出血**，App Store 合规） |
| Xcode 素材 | `/Users/chenfeng/dartvio-assets/AppIcon.appiconset/`（`AppIcon.png` + 单尺寸 universal `Contents.json`，建工程时直接拷入 `Assets.xcassets`） |
| 处理脚本 | `/tmp/mcb-drill/make_icon3.swift`（底色取样 #D45A2C → 填充 → aspect-fill → 手动构造无 alpha CGImage 写出） |

处理要点（为什么不是简单缩放）：源图只有 474px 且四角透明，直接缩放会在圆角外露白/黑；
脚本用 logo 自身底色填充画布再叠加源图，透明圆角无缝融入。

**可选改进**：源图放大 2.16 倍，细节略有软化。若希望更锐利，可从 Illustrator（仓库里
`android/app logo/DartVio-logo_002.ai` 是矢量源）导出 ≥1024px 的 PNG 后，
重跑 `/tmp/mcb-drill/make_icon3.swift` 一键再生成。

### 9.3 接下来的顺序

1. 等 WCB 完成 commonMain 10 处修复（D1）
2. MCB：真工程补跑两个 iOS 编译任务，阶段 4 收尾
3. MCB：回写确认后给 `shared/build.gradle.kts` 加 framework 输出（D2）
4. MCB：建 `ios/` Xcode 工程（D3）→ MVP 5 页（§5）→ 模拟器跑通「看到 UI + 跳转点击」
