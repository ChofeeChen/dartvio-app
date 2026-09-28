# 给 Mac 端 CodeBuddy 的提示词 · 第 3 轮：framework 输出 + 建 iOS 工程

> 约定：**MCB = Mac 端 CodeBuddy**，**WCB = Windows 端 CodeBuddy**。
> 本文件由 **WCB** 于 2026-09-28 编写（远端 HEAD `72eeb81`），用于通知 MCB 当前现状与本次任务。
>
> **用法**：把下面 `--- 提示词正文开始 ---` 到 `--- 提示词正文结束 ---` 之间**整段复制**给 MCB，
> 或让 MCB `git pull` 后直接读本文件执行。跨机上下文不共享，别指望它记得上一轮。
>
> 📌 既定流程：WCB 每完成一次任务，都会顺带产出下一轮提示词放进 `docs/handoff/`。
> 你（MCB）做完本轮后照旧回写到 `Mac端初始化_回写清单_V1.00.md` 末尾即可。

---

## 提示词正文开始

你是 **MCB**（Mac 端 CodeBuddy）。上一轮你的 iOS 编译验收通过了，WCB 已经批复，**M1 放行**，可以开工。

### 一、现状（2026-09-28，远端 HEAD `72eeb81`，两端已对齐）

1. WCB 的批复在 `docs/handoff/Mac端初始化_回写清单_V1.00.md` **§8**，逐条结论：
   | # | 结论 |
   | --- | --- |
   | M1 | ✅ **批准：你改、你验**。只加 framework 配置，不顺手改别的 |
   | M2 | ⏸ 暂不加 `-Xexpect-actual-classes`（同意你的倾向），等 Kotlin 转正或 expect/actual 用法扩展时再说 |
   | M3 | ✅ 已核对：Android `namespace` 与 `applicationId` **都是 `com.dartvio.app`**，与你定的一致 |
   | M4 | ✅ `ios/` 不进 `settings.gradle.kts`，对 Android 构建零影响 |
2. WCB 本轮顺带做完的两件事（都已推送）：
   - **W11**：提示词升 **`Mac端初始化_提示词_V1.01.md`** —— §4 那处「`NSDate()` / `NSUUID()` 构造被禁用、改用工厂方法」
     的预判**是反的**，已按你的实测重写（构造器可用，工厂方法反而不存在）。T7 同步升 **V1.05**。
   - **`.gitignore` 补了 iOS 规则**：`**/DerivedData/` `**/xcuserdata/` `**/*.xcuserstate`
     `**/.swiftpm/configuration/` `**/Pods/` `*.ipa` `*.dSYM.zip`。
     已用 `git check-ignore` 验证：这些会被忽略，而 `project.pbxproj` 与 `contents.xcworkspacedata` **不会被忽略**（工程主文件要入库）。
     **你不用再管忽略规则**，建完工程直接 `git add ios/`。
3. WCB 已读过你推的两份文档，无异议：
   - `docs/handoff/iOS首版页面对照表_V1.00.md`
   - `docs/handoff/iOS端MVP施工蓝图_V1.00.md`
   其中对照表 §1 里挂着的「MPR 那处 `String.format` 待 WCB 修」——**已修**（`872eb04`，改成纯 Kotlin，`round` + Double 中间量，653 例全绿）。
   蓝图里「统计/成就/排行榜不在本文件范围」与 WCB 的判断一致：D5 未做，iOS 首版统计页**先占位**。

### 二、你的任务

#### T1（先做）

```
git pull
git log --oneline -1      # 应显示 72eeb81
```

#### T2：给 `shared` 加 framework 输出（M1，已批准）

改 `android/shared/build.gradle.kts`，**只加这一段**（不要顺手改 Kotlin / AGP 版本或 `compilerOptions`）：

```kotlin
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
        baseName = "shared"
        isStatic = true
    }
}
```

DSL 细节以你那边能编过为准。`isStatic = true` 按你的方案（省掉 embed & sign，Swift 端直接链接）。

#### T3：验收

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

你自己在 §7.2 提醒过：**compile 只产 klib，真正的活儿在 link** —— 以这个任务为准。
真机那份 `linkDebugFrameworkIosArm64` 能跑就一并跑，跑不了不必勉强（模拟器够验 V1–V4）。

#### T4：建 `ios/` 工程 + 5 页 MVP

按你的 `iOS端MVP施工蓝图_V1.00.md` 施工（Phase 0–4，P1 Home / P2 X01 设置 / P3 X01 对局 / P4 Count Up / P5 练习结算），
验收项就是蓝图里的 V1–V4。WCB 只补三点：

1. ⚠️ **路径**：蓝图 §3 写的是 `/Users/chenfeng/Developer/dartvio-app/ios`。
   请确保 `ios/` 建在**仓库根**下（与 `android/`、`docs/` 同级），**不是** `android/ios/`。
   如果仓库 clone 在了别处，路径跟着改 —— 别把工程建到仓库外面去。
2. **版本号**：iOS 侧不要另立版本。唯一出处是 `android/app/build.gradle.kts` 顶部的 `appVersionCode`
   （当前 **18** → `CFBundleShortVersionString = 0.1.18`）。升版本由 WCB 统一改 Android 侧后再同步给你。
3. **证书**：真机调试先用免费 Apple ID（7 天证书）即可；付费开发者账号还没就绪，TestFlight 那条路暂时不通。
   V1–V4 用模拟器就能验完，**不必等账号**。

#### T5：回写 + push

- 位置：`docs/handoff/Mac端初始化_回写清单_V1.00.md` **末尾**（§8 之后）。
  文件末尾已预留 `_（第 3 轮：iOS framework 输出 + Xcode 工程的结果请追加于此）_`，**直接替换这一行**，不要改写 WCB 写的 §8。
- 必写：framework 配置最终代码、`linkDebugFrameworkIosSimulatorArm64` 结果（含产物体积与路径）、
  5 页 MVP 的完成情况（V1–V4 逐条）、遇到的坑、下一步建议。

### 三、边界：这些不要动

| 对象 | 原因 |
| --- | --- |
| `commonMain/` 下任何文件 | 口径是两端唯一真源；有问题回写给 WCB |
| `docs/prd/T7_*.md`、`Mac端初始化_提示词_V1.01.md` | PRD 与提示词串行改，归 WCB |
| `shared/build.gradle.kts` 里 framework 之外的配置 | 两端共用，只做本轮批准的这一处 |
| `.gitignore` | WCB 已补 iOS 规则，你不用动 |
| `:app` 里的 `domain/{stats,achievement,leaderboard}` | D5 由 WCB 排期，不阻塞你；统计页先占位 |
| Apple 证书 / `.p12` / `.mobileprovision` | 已在忽略清单，**不要**用 `-f` 强加 |

### 四、提交规范（与前两轮一致）

- 身份：`ChofeeChen <ChofeeChen@users.noreply.github.com>`，用 `git -c user.name=... -c user.email=...` 逐条传参。
- **代码改动与文档改动分开提交**（你这个习惯很好，继续保持）。
- 提交信息：英文前缀 + 中文正文，例如 `feat(shared): ...` / `feat(ios): ...` / `docs: ...`。
- 提交前自检（应为空）：
  ```
  git diff --cached --name-only | grep -E 'local\.properties|keystore|\.jks|邀请码|\.apk|\.p12|\.mobileprovision|DerivedData|xcuserdata'
  ```
- ⚠️ 443 不通就改走 SSH：`git remote set-url origin git@github.com:ChofeeChen/dartvio-app.git`（22 端口始终通）。

### 五、完成后回报

1. T2 的最终配置代码 + T3 的 link 结果
2. V1–V4 逐条是否达成（跑不起来的写清卡在哪）
3. `ios/` 的目录是否已进仓库（确认 `git ls-files ios/ | head`）
4. 下一步建议

**特别提醒**：T2 改完 push 后请**明确告诉 WCB** —— WCB 会 pull 并跑 `:app:testDebugUnitTest`（653 例）
确认 Android 侧没有被共用构建文件影响。在你 push 之前，WCB 不会碰 `shared/build.gradle.kts`。

## 提示词正文结束

---

## WCB 侧备注（不传给 MCB）

- 本轮的**硬验收点**是 T3 的 `linkDebugFrameworkIosSimulatorArm64`；T4 的 5 页 MVP 是长任务，
  允许 MCB 分多次提交推进，不必一轮做完。
- M1 之后 WCB 的待办：**pull → 跑 653 例回归**（确认 `shared/build.gradle.kts` 改动没影响 Android 侧）。
- WCB 侧排期（都不阻塞 iOS）：D5 解耦（`stats/achievement/leaderboard`）；版本号由 WCB 统一维护。
- WCB 在 Windows 上无法构建 `ios/`：pull 到本地后只当作文件存在，不要尝试编译。
