# iOS MVP 施工蓝图 V1.00

> 配套文件：`iOS首版页面对照表_V1.00.md`（页面清单与 shared 覆盖度）
> 本文回答的是**具体怎么写**：工程结构、Swift 与 Kotlin framework 的桥接方式、状态容器、5 个页面的字段与调用。
> 阶段：WCB 修完 commonMain（10 处 JVM-only API）后即可开工。

---

## 1. 目标与验收（本文件的完成标准）

| # | 验收项 | 判定 |
| --- | --- | --- |
| V1 | Xcode 工程编译通过，**模拟器里 App 能启动** | 看到 Home 页 |
| V2 | 5 个页面全部可达，Tab 与 push/pop 导航正常 | 手工点一遍无卡死 |
| V3 | 本地 X01 对局能**完整打完**：人类用键盘录镖、AI 自动出手、BUST 提示、双倍出结镖、多局计分局间总结 | 打到出现 GAME SHOT |
| V4 | Count Up 练习 **8 轮**打完并自动进入结算页 | 总分/平均/最高/180+ 显示正确 |

不在本文件范围：真机签名、上架、统计/成就/排行榜（被 Android `:app` 反向依赖卡住）、联机大厅（第二批）。

---

## 2. 为什么 MVP 只要 5 页

Android UI 是 **87 文件 / 29545 行**，批①满配约 22–25 个页面。先做 5 页的理由：
这 5 页覆盖了**两条主链路的每一个技术难点**（framework 调用、键盘输入、AI 异步编排、自动结算、结果页），
做完这 5 页，剩下的 17 页基本是「同样的模式换个规则引擎」的重复劳动，风险反而最低。

| 页 | 覆盖的技术点 |
| --- | --- |
| P1 Home | 导航壳、Tab ⼊口 |
| P2 X01 设置 | **构造 `MatchConfig`**（ObjC 全参构造的最难一例） |
| P3 X01 对局 | **键盘录镖 + 实时预览 + AI 异步编排 + BUST + 多局**（最难） |
| P4 Count Up | 另一种结算节奏（自动 480ms 进下一轮、BUST 闪 900ms） |
| P5 练习结算 | 结果页 + 最佳分本地存储 |

---

## 3. 工程结构（`/Users/chenfeng/Developer/dartvio-app/ios`）

```
ios/
├── DartVio.xcodeproj                     # 手工建（Phase 0）
├── DartVio/
│   ├── DartVioApp.swift                  # @main
│   ├── Theme/
│   │   ├── Palette.swift                 # 色板（§9）
│   │   └── Typography.swift              # 字号 / x01ScoreSize 规则
│   ├── SharedBridge/
│   │   ├── SharedFactory.swift           # MatchConfig / Player / Dart 构造器
│   │   ├── SharedAccess.swift            # object 单例访问封装（隔离 API 名称差异）
│   │   └── SharedFormat.swift            # 结镖/ILE 2 位小数等格式化（对齐 commonMain）
│   ├── Features/
│   │   ├── Root/RootTabsView.swift       # P1
│   │   ├── X01Setup/X01SetupView.swift   # P2
│   │   ├── Game/
│   │   │   ├── X01GameView.swift         # P3
│   │   │   └── X01GameViewModel.swift    # @Observable 状态容器
│   │   └── Practice/
│   │       ├── CountUpPracticeView.swift # P4
│   │       ├── CountUpResultView.swift   # P5
│   │       └── CountUpViewModel.swift
│   └── Components/
│       ├── KeypadView.swift              # 数字键盘（S/D/T + 1-9 + BULL + ⌫ + 确认）
│       ├── DartSlotView.swift            # 单镖槽位
│       ├── TurnDartsRow.swift            # 3 镖行 + 累计分
│       ├── X01PlayerCard.swift           # 玩家卡片（主分/上一回合删除线/本回合+N/胜局）
│       ├── ActiveTriangle.swift          # 当前席三角指示器
│       ├── StatusStrip.swift             # BUST / NO SCORE / MAX ROUNDS 提示条
│       └── GameTopBar.swift              # 标题 + 第 N 局 + 退出
```

---

## 4. Swift ↔ Kotlin 桥接：动手前必须知道的四件事

> **以下四条已于 2026-09-28 在 `/tmp/mcb-drill` 用真实 framework 的 ObjC header 实测确认**（Kotlin 2.2.10 / Xcode 27），不再是猜测。

Kotlin/Native 导出的是 **Objective-C 头文件**，Swift 直接吃，但有几个和 Kotlin 直觉不一样的地方。

**命名规则（先记住，后面全靠它）**：ObjC 类名 = `<framework 名> + <Kotlin 类名>`，
例：`framework baseName = "shared"` → ObjC 类 `SharedX01Rules`；但 header 里带了
`__attribute__((swift_name("X01Rules")))`，**Swift 里写回干净的 `X01Rules`**，前缀不出现在 Swift 代码里。

### 4.1 data class 的默认参数不会导出

`MatchConfig` 有 12 个字段且全带默认值，Kotlin 里写 `MatchConfig(targetScore = 501)`，
ObjC 侧**必须传满 12 个参数**（实测 `init witdRemaining:currentTurnDarts:isFinished:` 三个全必填）。
`X01LegState` / `CountUpState` / `Player` 同理。
→ 对策：**`SharedFactory` 集中构造**（§5），业务代码里不准出现多参数的裸 init。

### 4.2 `copy()` 变成了必填全参的 `doCopy()` —— 不要用

Kotlin data class 的 `copy` 确实导出了，但名字是 **`doCopy`**，且**默认参数同样丢失**，必须写满全部字段：

```swift
// X01LegState 有 12 个字段，于是这一句要写满 12 个实参，其中 11 个是为了“保持不变”而重复传自身
leg.doCopy(config: leg.config, players: leg.players, currentPlayerIndex: leg.currentPlayerIndex, /* … 8 more … */)
```

Android 的 `GameViewModel.applyX01Dart` 是靠 `leg.copy(currentTurnDarts = turnDarts)` 录镖的。
Swift 侧**不采用这条路线**。此外实测所有 `val` 属性在 header 里都是 `@property (readonly)`，本来也不该直接改。

→ 对策：**「不可变基准局 + Swift 侧回合数组」**。所有可变状态留在 Swift 侧，Kotlin 只负责纯函数计算：

```swift
@Observable
final class X01GameViewModel {
    private(set) var leg: X01LegState          // 只由 applyTurn 的返回值替换
    private(set) var turnDarts: [Dart] = []    // Swift 侧独占：本回合已录的镖
    private(set) var message: String?
    private(set) var inputLocked = false

    // 实时预览：不结算，问规则引擎“按现在这 3 镖算，剩多少”
    var previewedRemaining: Int {
        turnDarts.isEmpty ? Int(leg.currentPlayer.remaining)
                          : Int(SharedAccess.x01Rules.previewRemaining(leg, turnDarts))
    }
    // 实际 Swift 签名（带 label）：previewRemaining(state:darts:) / applyTurn(state:darts:)

    func throwDart(_ dart: Dart) { /* 见 §6.1 */ }
}
```

这条改动的副产品：**UI 层完全不需要 `previewRemaining` 之外的 ViewModel 状态**，比 Android 版更简单。

### 4.3 `object` 单例写法：**`X01Rules.shared`（已实测确认）**

`X01Rules` / `X01Ai` / `CountUpRules` 都是 Kotlin `object`，header 里是：

```objc
@property (class, readonly, getter=shared) DrillSharedDrillRules *shared __attribute__((swift_name("shared")));
```

→ Swift 侧 **`X01Rules.shared.applyTurn(...)`** ✅ 确认可用。

**顶层函数**（如 `CricketMprEngine.kt` 里的 `formatMpr`）导出为独立的 `XxxKt` 类：

```swift
CricketMprEngineKt.formatMpr(mpr:)     // 实测同构样例：DrillKt.drillFormatMpr(mpr:)
```

→ 对策：单例与顶层函数全部收敛到 **`SharedAccess`**（§5）一处。

### 4.4 返回值类型（已实测确认）

| Kotlin | Swift 侧形态 | 备注 |
| --- | --- | --- |
| `Pair<X01LegState, TurnOutcome>` | `pair.first` / `pair.second`，`@property (readonly)`，类型是 `Any?`，需 `as?` 强转 | ✅ |
| `List<Dart>`（入参） | `[Dart]` | Kotlin `List` → `NSArray<SharedDart *>` |
| `List<Dart>`（出参） | `[Any]`，需 `compactMap { $0 as? Dart }` | |
| `enum class`（如 `AiDifficulty`） | 静态属性形式：`AiDifficulty.beginner` / `.intermediate` / `.advanced` / `.pro` | ⚠️ 导出的是**类**不是 Swift enum，**不能 `switch`**，要用 `if / ==` 或自建 Swift enum 映射 |
| `String?` message | `String?` | |

---

## 5. 适配层设计

### SharedFactory（构造收敛）

```swift
enum SharedFactory {
    /// X01 配置：只暴露 UI 真正允许改的 6 项，其余走低 risk 默认值
    static func x01MatchConfig(
        targetScore: Int32,           // 301 / 501 / 701
        mode: MatchMode,              // casual / multiLeg
        legsToWin: Int32,             // 休闲=1（single leg），多局=3
        outMode: OutMode,             // straightOut / doubleOut / masterOut
        inMode: InMode,               // straightIn / doubleIn / masterIn
        smartAi: Bool
    ) -> MatchConfig { /* 内部补 bullMode=standard25_50 / maxRounds=0 / cricket 相关默认值 */ }

    static func dart(number: Int32, multiplier: Int32) -> Dart
    static func aiPlayer(id: String, difficulty: AiDifficulty) -> Player
    static func humanPlayer(id: String, name: String) -> Player
    static func newLeg(config: MatchConfig, players: [Player], legNumber: Int32) -> X01LegState
    static func initialCountUpState() -> CountUpState   // 8 轮 × nil
}
```

`CountUpState` 需要初值；ObjC 构造是 6 参全填，集中在这里一次写完。

### SharedAccess（API 名称隔离）

```swift
enum SharedAccess {
    static let x01Rules   = X01Rules.shared        // ← 名称待 header 校验，只改这里
    static let x01Ai      = X01Ai.shared
    static let countUp    = CountUpRules.shared
}
```

---

## 6. 状态容器

### 6.1 `X01GameViewModel`（P3 的核心）

对齐 Android `GameViewModel` 的编排语义，但状态模型按 §4.2 改过：

```swift
func throwDart(_ dart: Dart) {
    guard !leg.isFinished, turnDarts.count < 3, !inputLocked else { return }

    // 规则结算只有一处事实来源：applyTurn。人类录镖同样先“试算”判断是否已结束
    let darts = turnDarts + [dart]
    let (_, probe) = SharedAccess.x01Rules.applyTurn(leg, darts)   // 试算（不改 my leg）
    let turnOver = probe.won || probe.result == TurnResult.bust

    if !turnOver {
        turnDarts = darts          // 继续录，等待手动确认或投满 3 镖
        return
    }
    commit(darts: darts)           // 直接落账：胜 / BUST 立即结算
}

func commitTurn() {                // 用户按“确认/结束回合”
    guard !turnDarts.isEmpty else { return }
    commit(darts: turnDarts)
}

func undoLastDart() { guard !turnDarts.isEmpty else { return }; turnDarts.removeLast() }

private func commit(darts: [Dart]) {
    let (newLeg, outcome) = SharedAccess.x01Rules.applyTurn(leg, darts)
    leg = newLeg
    turnDarts = []
    message = outcome.message            // "BUST" / "GAME SHOT" / "MAX ROUNDS"
    inputLocked = true
    Task { @MainActor in
        try? await Task.sleep(nanoseconds: 500_000_000)   // 对齐 Android：每镖后锁 0.5s
        inputLocked = false
    }
    if outcome.won { handleLegWin(outcome) } else { scheduleAiTurnIfNeeded() }
}
```

**AI 编排**（对齐 Android `scheduleAiTurnIfNeeded`，用 `Task` 替代协程）：

```swift
private var aiTask: Task<Void, Never>?

func scheduleAiTurnIfNeeded() {
    guard aiTask == nil else { return }                    // 防重入
    guard leg.currentPlayer.type == PlayerType.ai else { return }
    aiTask = Task { @MainActor in
        while !Task.isCancelled {
            guard leg.currentPlayer.type == PlayerType.ai, !leg.isFinished else { break }
            let profile = AiProfile(difficulty: aiDifficulty(of: leg.currentPlayer))
            let remaining = leg.currentRemaining
            let darts = SharedAccess.x01Ai.generateTurn(
                remaining, profile, leg.config, leg.currentPlayer.hasOpened
            )
            for d in darts {
                await throwDartAsync(d)                    // 内含 Jet delay: profile.nextDelayMs(random)
                if Task.isCancelled { break }
                if turnDarts.isEmpty { break }             // 本镖已自动结算（BUST/获胜）→ 停手
            }
            if turnDarts.isEmpty { try? await Task.sleep(nanoseconds: UInt64(profile.nextDelayMs(random) / 2) * 1_000_000) }
            commitTurn()
            try? await Task.sleep(nanoseconds: 300_000_000)
        }
        aiTask = nil
    }
}
```

> ⚠️ `turnDarts.isEmpty` 是 AI 停手判据（同 Android 的 `turnDartsCount == 0`）。
> **这条必须与 §4.2 的“applyTurn 返回后才清空 turnDarts”配套** —— 提前清空会让 AI 少投镖。

多局：Android 用 `handleLegWin` 累加 `legsWon` → 达 `legsToWin` 出比赛结果，否则出**局间总结 sheet**，
用户点繼續后调 `X01Rules.newLeg(config:players:legNumber+1)` 开新局。iOS 侧用同一个 `legNumber` 递增。

### 6.2 `CountUpViewModel`（P4）

与 Android `CountUpViewModel` 完全同构，**两种结算节奏不要混用**：

| 事件 | Android | iOS |
| --- | --- | --- |
| 录满 3 镖 | 延迟 **480ms** 自动 `finalizeRound()` | `.task { sleep(480ms); finalizeRound() }` |
| 未投镖按确认 = BUST | `bust()`，红条闪 **900ms** 后进下一轮（记 0 分） | 同：先 `bust()`，`sleep(900ms)` 后清 `bustFlash` |
| 8 轮结束 | `finished = true` 出结算页 | `navigationDestination` 到 P5 |
| 最佳分 | SharedPreferences `dartvio_practice/countup_best_score` | **UserDefaults 同名 key**，保持可读一致 |

UI 显示：`state.totalScore` / `averagePerRound` / `maxRoundScore` / `count180` / `dartsThrown` /
`roundsPlayed` / `roundScores`（8 宫格）—— 全部是 `CountUpState` 自带的计算属性，iOS 侧不需要重算。

---

## 7. 五个页面的设计

### P1 · `RootTabsView`（首页 / 导航壳）

- `TabView` 三个 tab：**对局 / 练习 / 我的**
- 「对局」页：一张卡 → `X01SetupView`（P2）
- 「练习」页：MVP 只放一张卡 → Count Up（P4）；Android 的 6 项练习后续按同一模式加
- 「我的」：占位（统计/成就还被 Android 侧卡住）
- 导航：每个 tab 各自一个 `NavigationStack`

### P2 · `X01SetupView`

| 字段 | 取值 | 落到 `MatchConfig` |
| --- | --- | --- |
| 目标分 | 301 / 501 / 701（901/1101 留 P1 后续） | `targetScore` |
| 模式 | 休闲（单局）/ 多局（先胜 N 局） | `mode` + `legsToWin`（休闲=1，多局=3） |
| 结束规则 | 直出 / 双倍出 / 大师出 | `outMode`（默认双倍出） |
| 开局规则 | 直入 / 双倍入 / 大师入 | `inMode`（默认直入） |
|  opponents | 对手：AI + 难度（入门/进阶/高手/专业） | `players: [human, aiPlayer(difficulty:)]` |
| 智能难度 | 开关 | `smartAi`（默认开） |

「开始」→ `SharedFactory.newLeg(config:players:legNumber:)` → push P3，并立即 `scheduleAiTurnIfNeeded()`（若先手是 AI）。

### P3 · `X01GameView`

竖排（对齐 Android `X01GameScreen`）：

1. `GameTopBar`：赛制名 + 「第 N 局 · 比分」+ 退出
2. `X01PlayerCard` 行：2 张等宽卡，当前席上方 `ActiveTriangle`
   - 行1 头像 + 昵称
   - 行2 **主得分**（当前席用 `previewedRemaining`，非当前席用 `player.remaining`）
   - 行3 上一回合灰色删除线（`previousRemaining` + `lastTurnScore`）+ 本回合 `+N` 框（累计 `turnDarts` 得分）
   - 行4 「胜 N 局」
   - 字号：所有席位取最长位数统一（`x01ScoreSize`），避免同排两种字号
3. `TurnDartsRow`：3 个镖槽（`Dart.label()`）+ 本回合累计分
4. `StatusStrip`：`message != nil` 时出现（BUST / NO SCORE / MAX ROUNDS）
5. `KeypadView`：`modeSwitch` 槽位（MVP 不放棋盘，确认键占满底行）；
   满 3 镖且 buffer 空时确认键文案改「结束回合」
6. 局间总结 / 胜利：`.sheet` 呈现，含「再来一次」（同配置 `newLeg` 原地重开）

### P4 · `CountUpPracticeView`

- 顶部：轮次 1/8 + 累计总分
- 中部：当前轮 3 镖槽 + 本轮分；`bustFlash` 时红条「BUST · 本轮 0 分」
- 底部：**复用 `KeypadView`**，`currentRemaining = nil`（无剩余分概念）、不显示输入方式切换键
- 底部提示语：「提示：未投镖时按「确认」= Bust（本轮 0 分）；每轮 3 镖后自动进入下一轮。」
- 8 轮后自动 push P5

### P5 · `CountUpResultView`

总分 + 历史最佳 + NEW RECORD 标记 + 平均/最高/180+/完成镖数 + **8 宫格轮分表**；「再来一次」「返回」。

---

## 8. SwiftUI 组件 ↔ Android 对应物

| SwiftUI | Android | 备注 |
| --- | --- | --- |
| `KeypadView` | `components/Keypad.kt` → `DartKeypad` | 5 行 4 列：左列 S/D/T + BULL25 + BULL50，右三列 1-9 + 0/⌫，底行确认 |
| `BoardTapPad`（本期不做） | `components/BoardTapPad.kt` | 镖盘点录盘依赖 `BoardGeometry.dartAt`，MVP 不做，预留接口 |
| `X01PlayerCard` | `X01GameScreen` 内的卡 | 4 行布局同上 |
| `ActiveTriangle` | `GameCommon.kt` L131 | Canvas 三角 |
| `TurnDartsRow` / `DartSlot` | `GameCommon.kt` L152 / L193 | |
| `StatusStrip` | `GameCommon.kt` L233 | |
| `LastScoreText` | `GameCommon.kt` L260 | 删除线对照 |
| `GameTopBar` / `SeatDivider` | `GameCommon.kt` L92 / L295 | |

文案：Android 侧 `strings.xml` 只有 `app_name`，**其余全部硬编码在 Composable 里**。
iOS 侧文案直接从下列字面量迁移：`BUST` / `NO SCORE` / `GAME SHOT` / `MAX ROUNDS` / `第 N 局` / `胜 N 局` /
`结束回合` vs `确认` / `MISS` / `Count Up 练习` / `累计总分` / `历史最佳` / `BUST · 本轮 0 分` / `NEW RECORD` /
`总分` / `平均每轮` / `单轮最高` / `完成镖数` / `完成轮次` / `返回` / `再来一次` / `结算并查看结果` / `直接退出`。

---

## 9. 主题（取自 Android `ui/theme/Color.kt`，不要另编一套）

| 用途 | 色值 | 备注 |
| --- | --- | --- |
| 主色 Primary | `#EE9756` | 品牌橙（与图标 `#D45A2C` 同族） |
| PrimaryDark / Light | `#C97840` / `#F6C29A` | |
| PrimaryContainer | `#4A2E17` | 深色卡片底 |
| OnPrimary | `#1A1108` | |
| Secondary | `#4EC9C4` | 副色青 |
| Accent | `#E8C468` | 强调金（AI / 高亮） |
| Background / Surface / Variant / Elevated | `#121212` / `#1E1E1E` / `#2A2A2A` / `#333333` | 深色主（Android 是深色优先） |
| Divider | `#3D3D3D` | |
| Success / Warning / Error / Info | `#5BC236` / `#F5A623` / `#E5484D` / `#5B9BD5` | |

**MVP 只做深色主题**（对齐 Android 现状），浅色三色 `#F7F5F2` / `#FFFFFF` / `#EFEBE6` 先保留在色板文件里备用。

---

## 10. 里程碑

| Phase | 内容 | 产出 | 前置 |
| --- | --- | --- | --- |
| **0** | 建 Xcode 工程 + 嵌入 framework（`embedAndSignAppleFrameworkForXcode` + `ENABLE_USER_SCRIPT_SANDBOXYING=NO`）+ `import shared` 并打印 `X01Rules` 单例 | 空白页面跑起来 **+ 单例写法确认** | WCB 修完 commonMain、给 `shared/build.gradle.kts` 加 framework 输出 |
| **1** | Theme（色板/字号）+ `DartVioApp` + `RootTabsView` 三个空 tab | V1 达成 | Phase 0 |
| **2** | `SharedFactory` / `SharedAccess` + P2 设置页 + `SharedBridge` 单测式冒烟（构造一个 501 双倍出的 `MatchConfig` 并打印） | 桥接层可用 | Phase 1 |
| **3** | P3 对局页：`KeypadView`/卡片/`TurnDartsRow`/`X01GameViewModel`（含 AI 编排） | **V3 达成** | Phase 2 |
| **4** | P4 + P5：`CountUpViewModel` / 8 宫格结果页 / UserDefaults 最佳分 | **V4 达成** | Phase 3 |

Phase 3 是最重的一段，建议拆两次提交：① 键盘 + 录镖 + 预览（不含 AI）② AI 编排 + BUST/多局。

---

## 11. 待验证 / 已知坑清单

| # | 事项 | 处理 |
| --- | --- | --- |
| V-1 | `object` 单例在 Swift 里的访问形式 | ✅ **已确认：`X01Rules.shared`**（2026-09-28 实测 header） |
| V-2 | `enum` 在 Swift 里的 case 名 | ✅ **已确认**：静态属性形式 `.beginner` 等；**是类不是 Swift enum，不能 `switch`** |
| V-3 | `KotlinPair` 取值 | ✅ **已确认**：`pair.first` / `pair.second`，类型 `Any?`，需 `as?` 强转 |
| V-4 | `X01LegState` 属性是否可写 | ✅ **已确认：`readonly`** → §4.2 的不可变设计成立 |
| V-5 | 顶层函数是否被导出 | ✅ **已确认**：导出为 `XxxKt` 类（如 `DrillKt.drillFormatMpr(mpr:)`）→ real project 为 `CricketMprEngineKt.formatMpr(mpr:)` |
| V-6 | `Task.sleep` 精度对 AI 延迟手感的影响（Android 用 coroutine delay） | 手感差异可接受；若 AI 太快就加长 delay |
| V-7 | Count Up 的 `roundLocked` / `finished` 边界：`advance()` 在最后一轮会**停在最后一轮且 finished=true** | 严格照 `CountUpEngine.kt` L97-114 的语义实现，不要自己算 |
| V-8 | `MatchConfig` / `X01LegState` 的 ObjC init 参数顺序与全名 | Phase 2 对着真 header 补进 `SharedFactory`，不外溢到页面 |
| V-9 | `X01Ai.generateTurn` 有**两个重载**（profile 版 / difficulty 版，且 `random` 有默认值） | ObjC 可能生成两套 selector 或要求全参；Phase 2 一并确认，只走 profile 版并显式传 `random` |

---

_作者：MCB（Mac 端）。本文为施工蓝图，实施时按 Phase 顺序提交，每 Phase 结束回写一份结果到本文件末尾。_
