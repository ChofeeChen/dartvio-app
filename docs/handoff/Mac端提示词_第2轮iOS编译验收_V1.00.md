# 给 Mac 端 CodeBuddy 的提示词 · 第 2 轮：iOS 编译验收

> 约定：**MCB = Mac 端 CodeBuddy**，**WCB = Windows 端 CodeBuddy**。
> 本文件由 **WCB** 于 2026-09-28 编写（远端 HEAD `100829a`），用于通知 MCB 当前现状与本次任务。
>
> **用法**：把下面 `--- 提示词正文开始 ---` 到 `--- 提示词正文结束 ---` 之间**整段复制**给 MCB，
> 或让 MCB `git pull` 后直接读本文件执行。跨机上下文不共享，别指望它记得上一轮。

---

## 提示词正文开始

你是 **MCB**（Mac 端 CodeBuddy）。WCB（Windows 端）已经把 `commonMain` 里阻塞 iOS 编译的
JVM-only API 全部清掉了，现在轮到你验收 iOS 编译。

### 一、现状（2026-09-28，远端 HEAD `100829a`，两端已对齐）

1. 仓库：`https://github.com/ChofeeChen/dartvio-app`（private，`main`）。你先 `git pull`。
2. WCB 刚推送两个提交：
   - `872eb04` `refactor(shared): commonMain 去除 JVM-only API，打通 iOS 编译`
   - `100829a` `docs: Windows 端回写 - commonMain 去除 JVM-only API，653 例全绿`
3. `872eb04` 改了 **9 个文件**（不是 10 —— `BoardGeometry.kt:76` 是 74 行的连锁报错，改完 74 行即自动消失），
   全部在 `android/shared/src/commonMain/kotlin/com/dartvio/app/` 下：
   - `Math.pow` / `Math.toRadians` / `Math.toDegrees` → `kotlin.math.pow` 或 `* PI / 180.0`（4 个文件）
   - `System.currentTimeMillis()` → `PlatformTime.nowMillis()`（3 个文件）
   - `String.format("%.2f", mpr)` → 纯 Kotlin 实现（1 个文件：`domain/practice/CricketMprEngine.kt`）
4. WCB 侧 Android 回归：65 文件 / **653 例 / 0 fail / 0 skip**。
   ⚠️ 你那边 skip 会是 **1**（`OnlineRoomFlowTest` 探活跳过），因为你的 `local.properties` 为空 →
   `OnlineConfig.isConfigured = false` → 按设计跳过。**这是设计如此，不是缺陷。**
   验收基线是 **653 例 / 0 fail**，skip 数两端不同属正常，不要为它去改配置。
5. `iosMain/.../PlatformTime.ios.kt` 你在 `baf9895` 里已用 CoreFoundation（`CFTimeZoneCopySystem` +
   `CFTimeZoneGetSecondsFromGMT`）修好。**WCB 接受这个方案，不会再动它**（回写清单 W13）。
6. iOS 工程目录**还不存在**（仓库里只有 `android/`、`docs/`、`tools/`）。**本轮不要创建**。

### 二、你的任务

#### T1（先做，1 分钟）

```
git pull
git log --oneline -1      # 应显示 100829a
```

#### T2（核心任务）：补跑两个 iOS 编译任务

```
cd android
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

注意：`gradlew` 在 **`android/` 目录**，不在仓库根。

- ✅ **两个都 BUILD SUCCESSFUL** → iOS 编译打通，记下耗时与 Kotlin / Xcode / Gradle 版本，进 T3。
- ❌ **还有错** → **不要自己改 `commonMain`**（WCB 刚改完，两端同时改同一批文件必然冲突）。
  按位置分责：
  - 错误在 `iosMain/` → 归你修，你直接改。
  - 错误在 `commonMain/` → 归 WCB 修，把**完整错误原文**（文件路径 + 行号 + `Unresolved reference` 的符号名）
    回写并发回 WCB。

#### T3：把结果追加到回写清单

追加到 `docs/handoff/Mac端初始化_回写清单_V1.00.md` **末尾**（§6 之后）。
该文件末尾已经预留了一行：

```
_（Mac 端补跑 iOS 编译的结果请追加于此）_
```

**请直接替换这一行**，不要另开新文件、不要改写 WCB 已写的 §6 内容。
WCB 不会再改这个文件，不会和你抢。

#### T4：确认两份文档已入库

- `docs/handoff/iOS首版页面对照表_V1.00.md`
- `docs/handoff/iOS端MVP施工蓝图_V1.00.md`

这两份在 `100829a` 里**都不存在**。如果还没提交，请先提交并推送 —— WCB 要按它们排期。
（WCB 不会改这两份文件的内容，只读取。）

#### T5（暂缓，本轮不要做）

给 `shared/build.gradle.kts` 加 iOS framework 输出
（`binaries.framework { baseName = "shared"; isStatic = true }`）。
这是**两端共用文件**，改动前必须先同步 WCB、等 WCB 回确认。本轮保持不动。

### 三、边界：这些不要动

| 对象 | 原因 |
| --- | --- |
| `commonMain/` 下任何文件 | WCB 刚改完；有问题回写给 WCB，别自己动手 |
| `docs/handoff/Mac端初始化_提示词_V1.00.md` | W11（提示词对 `NSDate()` / `NSUUID()` 的预判与实测不符）由 **WCB** 升 V1.01 修订 |
| `docs/prd/T7_*.md` | PRD 串行改，归 WCB |
| `shared/build.gradle.kts` | 两端共用，见 T5 |
| `:app` 里的 `domain/{stats,achievement,leaderboard}` | D5 解耦由 WCB 自行排期，**不阻塞 iOS**；iOS 首版统计页先占位 |
| 你新写的两份 iOS 文档的内容 | 你自己维护，WCB 不改 |

### 四、提交规范（与第 1 轮一致）

- 身份：`ChofeeChen <ChofeeChen@users.noreply.github.com>`。你本机未配全局 git config，
  所以用 `git -c user.name=... -c user.email=...` **逐条临时传参**。
- **代码改动与文档改动分开提交**，便于单端 revert / cherry-pick（你上一轮就是这么拆的，很好，继续保持）。
- 提交信息：英文前缀 + 中文正文，例如 `fix(shared): ...` / `docs: Mac 端 iOS 编译验收结果`。
- 提交前自检（结果应为空）：
  ```
  git diff --cached --name-only | grep -E 'local\.properties|keystore|\.jks|邀请码|\.apk'
  ```
- ⚠️ 若 `github.com:443` 不通（**两端都发生过**，间歇性阻断、数小时后自愈），改走 SSH：
  `git remote set-url origin git@github.com:ChofeeChen/dartvio-app.git`（22 端口始终通，你已配 `dartvio-mac`）。

### 五、完成后回报

请把以下信息写进回写清单，并告知 WCB：

1. 两个 iOS 编译任务的结果（SUCCESSFUL / 失败的完整原文）
2. Kotlin、Xcode、Gradle 版本 + 任务耗时
3. T4 两份文档是否已推送
4. 你建议的下一步（WCB 会据此排第 3 轮）

## 提示词正文结束

---

## WCB 侧备注（不传给 MCB）

- 本提示词的**唯一验收点**是 T2 的两个 iOS 编译任务。WCB 在 Windows 上无法自测 iOS 目标，只能靠 MCB 回传结果。
- W11（提示词 V1.00 的预判与实测不符）**本轮仍未修订**：刻意留到 iOS 编译通过之后，与 T7 一并升版，
  避免两端同时改同一批文档。
- 本轮不创建 iOS 工程：`iOS端MVP施工蓝图_V1.00.md` 还没进仓库，且 iOS 工程是大改动，
  等编译结果 + 蓝图到位后再单独排一轮。
