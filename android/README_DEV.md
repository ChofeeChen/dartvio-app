# DartVio Android - 开发说明（阶段一）

## 当前状态

已从 Android Studio 空模板改造为 DartVio 工程，采用**选项2混合模式**：
- **代码开发**：全部在 CodeBuddy 内完成
- **构建运行**：在 Android Studio 内点击按钮完成

## 环境

| 项 | 值 |
|---|---|
| JDK | Studio 自带 JDK 25（工程 `gradle-daemon-jvm.properties` 已指定 toolchain=25） |
| Gradle | 9.5.0（wrapper 已配置） |
| AGP | 9.3.0 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-2.0.2（前缀必须与 Kotlin 严格一致） |
| Room | 2.7.2 |
| Compose BOM | 2026.02.01 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 |

> AGP 9 内置 Kotlin 与 KSP 的 `kotlin.sourceSets` DSL 冲突，`gradle.properties` 已设
> `android.disallowKotlinSourceSets=false`。Room schema 导出至 `app/schemas/`。

## 在 Android Studio 里运行

1. **打开工程**：`File > Open`，选择仓库根下的 `android` 目录（**不是**仓库根目录）。原目录名 `App_DartVio_Android_CB_V0.1` 已于 2026-09-27 改名。

## 双平台说明（2026-09-27）

- **共享核心 = Kotlin Multiplatform**：`shared/` 模块（本工程的子模块）已于 2026-09-27 建成，iOS 端（Swift + SwiftUI）直接复用。
- **已下沉**：`app/src/main/.../domain/{model,rules,versus,practice,room,credit,impact,vision,profile}` → `shared/src/commonMain`。
  **暂留 app**：`domain/{stats,achievement,leaderboard}` —— 它们通过 Room 的 `MatchWithPlayers` 反向依赖 data 层，
  需先解耦（T7 §5.2 的 D5）。
- **在 `shared/commonMain` 里禁止**：`android.*` / `androidx.*` / `Context` / Room（`com.dartvio.app.data.*`）/
  `java.time` / `java.util.Calendar` / `SimpleDateFormat` / `Locale` / `UUID`。
  时间与随机 ID 走 `com.dartvio.app.platform` 的 `expect/actual`。
- **注意 `internal` 跨模块不可见**：被 UI 或单测用到的成员要写 public（已在下沉时统一处理）。
- **不要**在 `domain/` 里用 `java.time`：minSdk 24/25 未开核心库脱糖会运行期 `NoClassDefFoundError`。
- iOS 工程由 Mac 端创建在 `ios/`，本工程不依赖它。
2. **等待 Sync**：首次会下载依赖，需要联网。
3. **选择设备**：连接真机（开启 USB 调试）或创建模拟器。
4. **点击运行**：绿色 ▶ 按钮，或 `Shift+F10`。

## 构建命令（如已配置命令行环境）

```bash
# Windows
gradlew.bat assembleDebug

# 产物路径
app/build/outputs/apk/debug/app-debug.apk
```

## 已实现功能（阶段一）

### 玩法
- **X01**：301 / 501 / 701 / 901 / 1101
- **Cricket**：15-20 + Bull 标记与得分

### 规则（PRD M2）
- X01 Bust 判定（超分、剩余1分、Double-Out 单倍收尾）
- Double-Out 收尾（含 Inner Bull 50 分）
- Cricket 标记（S=1/D=2/T=3，上限3）、关闭后得分、获胜判定
- 单镖即可获胜（不强制打满 3 镖）

### 对局（PRD M4 / 决策点2）
- 休闲模式（单局定胜负）
- 多局模式（1~10 局胜）
- 多局模式胜局统计与局间过渡

### 界面（PRD M7）
- 首页玩法选择
- 比赛设置（目标分、模式、局数、Double-Out、选手名）
- 对局计分板（X01 剩余分 / Cricket 标记网格）
- 计分键盘（S/D/T 三连按钮 + BULL + MISS）
- 当前回合镖预览、Bust/Game Shot 提示
- 比赛结束弹窗（再来一局 / 退出）

### 设计系统（PRD T1 / 决策点3）
- 主色 橙 `#EE9756`
- 辅助 青 `#4EC9C4`
- 强调 金 `#E8C468`
- 完整深/浅色主题、语义色（Success/Warning/Error/Checkout/GameShot）

### 数据持久化（第①期 / PRD M9 基础）
- Room 本地库 `match_records` + `match_players`，对局结束自动落库
- 只落**原始事实**（镖数、回合、得分、标记数等），指标读取时现算，
  口径调整无需数据库迁移
- 回合结算时累加，撤销（undo）天然安全
- 可信度分层：本地正式（多局全真人）/ 休闲·AI 对战 / 练习（M11，本期不落库）

### 统计与历史（第①期 / PRD M9）
- 首页「统计与历史」入口
- X01 Tab：PPR（总/正式）、胜率（总/正式）、最高收分、最长连胜、
  每局镖数、180 数、单回合最高分、Bust 率、收分成功率
- Cricket Tab：C1–C10（标记率、关闭率、三倍率、Bull 率、回合均分、
  平均关闭局数、得分效率、胜率、单回合最高分、首个关闭分区直方图）
- 历史 Tab：往局列表 + 对局详情弹窗（`observeMatches()` 实时刷新）

### AI 对手（第②期 / PRD M4 §6.1 / §6.1.1）
- 四档难度（入门/进阶/高手/专业），以 **PPR 区间 + 中值**定义：
  入门 25–40（中 32）/ 进阶 40–55（中 47）/ 高手 55–70（中 62）/ 专业 70–90（中 80）
- 每档带**结镖率、180 率、单镖延迟区间**；命中率由当前 PPR 现算
  （`AiDifficulty.hitChanceFor`，线性 25→0.20、90→0.80）
- **同级自适应**（`AdaptiveAiController`）：滚动最近 5 个真人回合的实测 PPR，
  `target = clamp(base + 0.5×(playerPpr − base))`，再按 `0.7/0.3` 平滑，**不跨档**
- **智能难度开关**（`MatchConfig.smartAi`，X01 详细设置页）；休闲模式自动关闭，恒为档位中值
- AI 每镖延迟按难度区间随机（0.5–2.5s），自适应随整场累积（跨局不重置）

## 单元测试

`app/src/test/java/com/dartvio/app/domain/rules/` 下覆盖：
- `X01RulesTest`：正常扣分、Bust 三种情形、Double-Out 收尾、Inner Bull 收尾、无 Double-Out、MISS
- `CricketRulesTest`：标记累加、关闭得分、对手关闭后不得分、Bull 标记、获胜判定、无效分数

在 Studio 中：右键 `test` 目录 > `Run 'Tests in ...'`

## 项目结构

```
app/src/main/java/com/dartvio/app/
├─ DartVioApp.kt                 # Application 容器（database / repository）
├─ MainActivity.kt
├─ data/
│  ├─ MatchRepository.kt         # 对局仓储（observeMatches / saveMatch）
│  └─ local/
│     ├─ DartVioDatabase.kt      # RoomDatabase 单例
│     ├─ MatchMapper.kt          # 领域模型 → Entity 映射
│     ├─ dao/MatchRecordDao.kt   # DAO（含 @Transaction 全量写入）
│     └─ entity/                 # MatchRecordEntity, MatchPlayerEntity
├─ domain/
│  ├─ model/                     # Dart, MatchConfig, X01State, CricketState, Enums
│  ├─ rules/                     # X01Rules, CricketRules（纯逻辑，可测试）
│  └─ stats/                     # StatsModels, MatchFacts, StatsCalculator
└─ ui/
   ├─ theme/                     # Color, Theme, Type（设计系统 Token）
   ├─ components/                # Keypad（计分键盘）
   ├─ home/                      # HomeScreen
   ├─ setup/                     # MatchSetupScreen
   ├─ game/                      # GameScreen, GameViewModel（结算累计 + 落库）
   ├─ stats/                     # StatsScreen, StatsViewModel
   └─ navigation/                # DartVioNavHost, Routes
app/schemas/                     # Room 导出的 schema JSON
```

## 阶段二规划（未实现）

- 更多玩法：Around the Clock / Shanghai / Halve It / Killer
- 后端接入（M5 实时同步、M6 在线大厅、M9 统计、M10 社交）
- 硬件自动计分（M8，依赖摄像头设备与 T4 协议）

## 注意事项

- 首次 Sync 若报依赖下载失败，检查网络/代理。
- 若 Studio 提示 JDK 版本，选择内置 JDK 25（`Settings > Build > Build Tools > Gradle > Gradle JDK`）。
- 本阶段**不含**后端与硬件；对局数据已本地持久化（Room），但不上传云端。
- 练习模式（M11）数据本期不落库；数据库版本 v1，`exportSchema=true`，
  schema 变更需提供 Migration（勿用 destructive migration）。
