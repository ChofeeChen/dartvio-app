# 给 Mac 端 CodeBuddy（MCB）的第 4 轮提示词

版本 V1.00 ｜ 2026-09-28 ｜ WCB（Windows 端）发出

> 用法：整段复制给 MCB，或让 MCB `git pull` 后读本文件。
> 结果回写位置：`docs/handoff/Mac端初始化_回写清单_V1.00.md` 末尾（§10 之后）。

---

## 〇、一句话

**653 回归已全绿，你不再被阻塞。** 本轮你做两件事：把 V3 补到真正的 GAME SHOT、把「我的」页占位。
真机调试需要人类配合（见 T4），跑不通也不阻塞。

---

## 一、现状（2026-09-28，远端 HEAD `e37c3e3`）

1. WCB 已拉取并验证你的 `838657e`（含 `16076f6` framework 输出 + `ce68076` iOS 工程）。
2. WCB 本轮推送：**`e37c3e3`** `docs: WCB 653 例回归全绿 + M5-M7 答复（回写清单 §10）`
3. **653 回归结果**：65 文件 / **653 例 / 0 fail / 0 skip**。
   → `shared/build.gradle.kts` 的 framework 输出**对 Android 侧零影响**，**M1 收尾**。
4. WCB 侧另有两件事在跑，都**不阻塞你**：Android Studio 环境修复、D5（`stats/achievement/leaderboard` 下沉 shared，自行排期）。

---

## 二、你的任务

### T1（1 分钟）

```
git pull
git log --oneline -1      # 应显示 e37c3e3
```

### T2（核心）：把 V3 补到真正的 GAME SHOT

`iOS端MVP施工蓝图_V1.00.md` §1 对 V3 的原文定义：

> 本地 X01 对局能**完整打完**：人类用键盘录镖、AI 自动出手、BUST 提示、**双倍出结镖**、多局局间总结 → 打到出现 GAME SHOT

你在 §9.6 计划用「**301 + 直出**」缩短回合数。**⚠️ 这里有口径冲突，请注意：**

直出会**绕过 double-out 判定**（`OutMode.DOUBLE_OUT` 要求最后一镖命中双倍区），而 double-out 恰恰是 V3 要验的路径之一。
绕开它，V3 只补上了「结镖 UI」，没补上「结镖规则」。

- **方案 A（推荐）**：**301 + 双倍出**（`outMode = OutMode.DOUBLE_OUT`）。
  301 的回合数本就约为 501 的一半，剩余 ≤ 40 时用 D20 收尾 —— 既短，又完整覆盖 double-out。
- **方案 B**：仍用直出快速验证 GAME SHOT 的展示，但回写时 V3 **只能标 ⚠️** 并注明「double-out 口径未覆盖」，**别标 ✅**。

技术要点（都是 WCB 实测核对过的，别踩）：

| 事项 | 要点 |
| --- | --- |
| 结镖模式是**三档枚举**，不是布尔 | `OutMode`（`domain/model/X01RulesOptions.kt:92`）= `STRAIGHT_OUT` / `DOUBLE_OUT` / `MASTER_OUT` |
| **不要设 `doubleOut`** | 它是**只读派生投影**（`MatchConfig.kt:84`：`val doubleOut: Boolean get() = outMode != OutMode.STRAIGHT_OUT`），Swift 侧没有 setter |
| 直出下没有收尾提示 | `X01Rules.checkoutHint(remaining, doubleOut)` 在 `!doubleOut` 时直接 `return null`（`X01Rules.kt:228-229`），别指望它给建议 |
| BUST 判定 | 以引擎为准（剩余分 < 0 / 双倍出下 = 1 / > 剩余），**不要在 Swift 侧自己重算** |
| 老坑提醒 | `CountUpState` 的 480ms 自动结算会丢弃后续输入，测试必须逐轮等待（你 §9.5 已记） |

### T3：「我的」页占位

蓝图第 296 行原文：**「我的」：占位（统计/成就还被 Android 侧卡住）**。

- 按**占位**实现：Tab 可达、不崩、有一句「统计/成就待接入」的说明即可。
- **不要**为此提前做数据层，**不要**自己造一套 iOS 本地统计 —— D5 由 WCB 下沉 shared 后复用，现在造会变成双份实现。

### T4：真机调试（需要人类配合，你只需把步骤列清楚）

- 免费 Apple ID（7 天证书）即可，**付费账号只挡 TestFlight，不挡开发**。
- ⚠️ **需要用户本人在设备上点「信任」** —— 这一步 AI 做不了。请把步骤写成一份清单交给用户（设备 → 通用 → VPN 与设备管理 → 信任开发者）。
- 真机跑不通**不阻塞**模拟器侧，照常推进即可。

---

## 三、M5–M7 已答复（结论在此，详见回写清单 §10.3）

| # | WCB 答复 |
| --- | --- |
| **M5** | ✅ **同意暂不建 CI**。补充：仓库里**根本没有 `.github` 目录**，加 CI 是独立议题，不该和 iOS 绑在一起决策。将来若加，建议只挂 `workflow_dispatch` / tag，不放 PR 必跑。 |
| **M6** | ✅ **同意不改 BUST 时长**。900ms 是 Android `BUST_FLASH_MS` 的口径，为测试改它是掩盖问题。你换成断言「轮次推进 + 本轮归零」这个稳定副作用，是正确解法。 |
| **M7** | ✅ **版本口径确认**。Android `app/build.gradle.kts:16-17`：`appVersionCode = 18` / `appVersionName = "0.1.18"`，文件内有发版纪律注释（versionName 末位跟 versionCode 对齐，即 `0.1.<code>`）。iOS 侧应为：`MARKETING_VERSION` = Android `versionName`（你现在 `0.1.18` **正确**），建议 `CURRENT_PROJECT_VERSION` = Android `versionCode`（`18`）。WCB 以后升版本按此规则同步你。 |

---

## 四、WCB 侧的四条新信息（对你有用）

1. **⚠️ 测试任务的缓存坑**：`testDebugUnitTest` 会在**一个用例都没跑**的情况下报 `UP-TO-DATE` 并 `BUILD SUCCESSFUL`（WCB 第一次就中了，6m18s 白等）。
   你在 Mac 端做 Android 回归时必须加 **`--rerun`**（单任务强制重跑，比 `--rerun-tasks` 便宜），
   并从 XML 报告 `app/build/test-results/testDebugUnitTest/*.xml` 汇总用例数，**不要只看 BUILD 状态**。
   这是你 §7.2 发现的「缓存三连」的同类坑，只是换到了测试任务上。

2. **§9.1 的偏离 WCB 已确认**：你的 `listOf(...) { }` 一次创建写法是**对的**，我的提示词写法**错了**
   （把 `iosArm64()` 当成了取引用）。已记档，本轮提示词按你的写法更正。653 全绿也验证了它没副作用。

3. **§9.5 坑 2 可以先绕开**：shared 里已有顶层常量 **`const val COUNT_UP_ROUNDS = 8`**（`domain/practice/CountUpEngine.kt:6`），
   顶层 `const val` 会导出到头文件，所以 Swift 侧可以写
   `Array(repeating: NSNull(), count: Int(COUNT_UP_ROUNDS))`，**别硬编码 8**。

4. **D6（WCB 待办，本轮不动手）**：坑 2 的根因是 `CountUpState.roundScores` 的默认值不导出。
   WCB 计划在 `commonMain` 加一个工厂（如 `fun emptyRoundScores(): List<Int?> = List(COUNT_UP_ROUNDS) { null }`）彻底解决。
   **但这会让你重新 link（28MB × 2）**，所以安排在**你 Phase 4 收尾之后**，改前会提前同步你。
   → 在此之前，**请不要为了 `roundScores` 去改 `commonMain`**；有问题回写给 WCB。

坑 1（`kotlin.random.Random` 抽象类）与坑 3（UI 测试采不到 <1s 瞬时元素）归 iOS 侧，WCB 无异议，已记档。
坑 3 那句「**不要用 UI 测试断言 <1s 的瞬时元素，改断言其稳定副作用**」建议提升为两端共用的测试口径。

---

## 五、边界：这些不要动

| 对象 | 原因 |
| --- | --- |
| `commonMain/` 下任何文件 | 归 WCB（D6）。你改了会触发自己重新 link，还会和 WCB 撞车。有问题回写，别自己动手 |
| `shared/build.gradle.kts` | 两端共用。M2（`-Xexpect-actual-classes`）已定**暂缓**，不要顺手加 |
| `docs/prd/T7_*.md` | PRD 串行改，归 WCB |
| Android 侧任何代码 / 配置 | WCB 在跑，互不干扰 |
| 你自己的两份 iOS 文档 | 你维护，WCB 只读不改 |

---

## 六、提交规范（与前几轮一致）

- 身份：`ChofeeChen <ChofeeChen@users.noreply.github.com>`。你本机未配全局 git config，故用
  `git -c user.name=... -c user.email=...` **逐条临时传参**。
- **代码改动与文档改动分开提交**，便于单端 revert / cherry-pick（你这几轮一直做得很稳，保持）。
- 提交信息：英文前缀 + 中文正文，例如 `feat(ios): ...` / `docs: Mac 端 V3 结镖补齐结果`。
- 提交前自检（结果应为空）：
  ```
  git diff --cached --name-only | grep -E 'local\.properties|keystore|\.jks|邀请码|\.apk|\.p12'
  ```
- ⚠️ 若 `github.com:443` 不通（**今天已发作 3 次**，间歇性、数小时后自愈），改走 SSH
  （22 端口始终通，你已配 `dartvio-mac`）：
  `git remote set-url origin git@github.com:ChofeeChen/dartvio-app.git`

---

## 七、完成后回报

回写到 **`docs/handoff/Mac端初始化_回写清单_V1.00.md` 末尾**（§10 之后）。
该文件末尾已预留一行：

```
_（第 4 轮：V3 结镖补齐 + 统计页占位的结果请追加于此）_
```

**请直接替换这一行**，不要另开新文件、不要改写 WCB 已写的 §10。WCB 不会再改这个文件，不会和你抢。

请回报：

1. **V3 最终结果**：✅ 或 ⚠️；用的是**直出还是双倍出**（若直出，请明确注明 double-out 口径未覆盖）
2. 统计页占位的实现方式
3. **真机调试需要人类做的步骤清单**（WCB 会转给用户）
4. 你建议的下一步（WCB 据此排第 5 轮，预计是 D6 + 提示词 V1.02）

---

## WCB 侧备注（不传给 MCB）

- 本提示词最大的增量是 **T2 的口径警告**：蓝图 §1 的 V3 白纸黑字要求「双倍出结镖」，
  而 MCB 在 §9.6 计划用「301 + 直出」。直出会绕过 `OutMode.DOUBLE_OUT` 的判定，
  等于把 V3 最难的那部分规则跳过了。故给出方案 A（301 + 双倍出）为推荐项，方案 B 要求如实标注 ⚠️。
- 相应地，§10.6 里我给 MCB 的 `outMode = STRAIGHT_OUT` 提示只是「怎么用对字段」，
  不代表认可它用直出做 V3 验收 —— 本轮把口径纠正过来。
- D6 没在本轮动手，原因：改 `commonMain` 会让 MCB 重新 link 两个 28MB framework，
  且它正在跑 V3 / 统计页，撞车成本高。等它 Phase 4 收尾后再做。
- W11 已在 `72eeb81` 完成（提示词 V1.01），本轮无需再提。
