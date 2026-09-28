版本 V1.01 \| 2026-09-28 \| 修订 §4「已知的坑」：原对 `NSDate()` / `NSUUID()` 构造被禁用的**预判与 Mac 端实测相反**；§4 / §6 补 2026-09-28 验收结果与 M1 批复

> 变更记录：V1.00（2026-09-27）初版。

> 使用方式：把本文件**整段复制**到 Mac 端 CodeBuddy 对话框，或 clone 后让它「读 `docs/handoff/Mac端初始化_提示词_V1.01.md` 并按此执行」。
> Windows 与 Mac 的对话上下文**不共享**，本文件是两边唯一的交接媒介，请严格按里面的路径与命令执行。

---

下面是给 Mac 端 AI 的提示词正文（从这条分隔线以下整段复制）：

---

# DartVio — MacBook 端环境初始化（请按阶段执行，每阶段结束报告结果后再进入下一阶段）

## 0. 背景与你的角色

DartVio 是飞镖计分 App，正在从「Android 单端」迁到「Android + iOS 双端」。
共享核心走 **Kotlin Multiplatform**：纯逻辑在 `android/shared/`（已建成，Windows 端 653 例单测全绿），
Android 端继续 Jetpack Compose，**iOS 端由你负责：Swift + SwiftUI 原生 UI，通过 KMP framework 复用 shared**。

分工（硬约束，不可协商）：
- **Windows = Android 开发 / 出包**，keystore 只留在 Windows；**你不要**碰 Android 签名材料，也不要在 Mac 打对外 Android 发布包。
- **Mac = iOS 开发 / 出包**，Apple 证书 / `.p12` / `.mobileprovision` 只留在 Mac，**绝不提交进仓库**（`.gitignore` 已排除，但别用 `-f` 强加）。
- 两端共用**同一套 versionCode**：唯一出处是 `android/app/build.gradle.kts` 顶部的 `appVersionCode`（当前 **18**，`versionName = 0.1.18`）。iOS 侧 `CFBundleShortVersionString = 0.1.<code>`。**不要**在 Mac 上另立版本号。

**第一步先做这件事**：读仓库根的 `CODEBUDDY.md`（项目单一上下文入口）和 `docs/prd/T7_双机双平台迁移准备清单_V1.06.md`，了解全局后再动手。

## 1. 拉代码

仓库是**私有仓库** `https://github.com/ChofeeChen/dartvio-app`（分支 `main`）。

```bash
# 推荐 SSH（机器丢了可单独撤销这把 key）
ssh-keygen -t ed25519 -C "dartvio-mac"
cat ~/.ssh/id_ed25519.pub      # 加到 GitHub → Settings → SSH and GPG keys
git clone git@github.com:ChofeeChen/dartvio-app.git
# 若 SSH 一时配不好，可先用 HTTPS + Personal Access Token（GitHub 不接受账号密码）

cd dartvio-app
cp android/local.properties.example android/local.properties   # 留空即可：联机与匿名统计会自动关闭
chmod +x android/gradlew
```

验收：`git log --oneline -3` 应看到 `Initial commit: DartVio Android v0.1.18 + KMP shared module` 等提交。

## 2. 环境体检

需要：**Xcode（含 Command Line Tools）+ Android Studio（提供 Gradle / JDK / Android SDK）+ JDK 17 以上**。

```bash
xcode-select --install
sudo xcodebuild -license accept
brew install kdoctor && kdoctor        # KMP 官方环境体检，按它的提示补齐
```

**JAVA_HOME 必须指向 Android Studio 自带的 JDK**（否则 Gradle 9.5 / AGP 9.3 工具链解析会失败）：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
java -version      # 17 或以上
```

（路径含空格，`export` 时务必带引号；建议写进 `~/.zshrc`。）

## 3. 先验证 Android 侧 —— 确认仓库是健康的

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest
```

验收标准：**65 个测试文件 / 653 例，0 fail**（Windows 端基线就是这个数）。
首次运行会下载 Gradle 与依赖（仓库已配阿里云镜像），可能要十几分钟。

- 若失败且**不是**环境问题：停下来回写，不要在 Mac 端自行改业务代码。
- 若只是 SDK 路径问题：确认 `~/Library/Android/sdk` 存在，或 `local.properties` 里补 `sdk.dir=...`（**这个文件不入库，别提交**）。

## 4. 关键一步：验证 iOS 目标能否编过

> ✅ **2026-09-28 已验证通过**（Mac 端 Xcode 27.0 / Kotlin 2.2.10 / Gradle 9.5.0）：
> `:shared:compileKotlinIosSimulatorArm64` 与 `:shared:compileKotlinIosArm64` **均 BUILD SUCCESSFUL**，0 error。
> 若你是首次在新机器上执行，本节仍是唯一有技术风险的环节；若只是复跑，"从未编译过"这个背景已不成立。

这是本次**唯一有技术风险**的环节。`shared` 的 iOS 目标需要 Xcode 工具链，Windows 上编不了，所以下面这段 Kotlin/Native 代码在 Windows 端**从未编译过**：

`android/shared/src/iosMain/kotlin/com/dartvio/app/platform/PlatformTime.ios.kt`

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64    # 模拟器（Apple Silicon）
./gradlew :shared:compileKotlinIosArm64             # 真机
```

首次编译 Kotlin/Native 会下载 konan 工具链，较慢，属正常。

**已知的坑（2026-09-28 已按 Mac 端实测校准，详见回写清单 §4.3 的 API 可用对照表）**：

> ⚠️ 下面第一条是 V1.00 写错的地方：当时预判「`NSDate()` / `NSUUID()` 构造被禁用、改用工厂方法」，
> 实测**正好相反** —— 构造器可用，工厂方法反而不存在。若你按旧版本修过，请以本节为准。

- `NSDate()` **可用**（就是 `+[NSDate date]`，即当前时刻）。反倒是 `NSDate.date()` /
  `NSDate.dateWithTimeIntervalSince1970(...)` **不存在** —— 工厂方法已被映射成构造器。
  取当前 epoch 毫秒：`((NSDate().timeIntervalSinceReferenceDate + NSTimeIntervalSince1970) * 1000.0).toLong()`
  （`NSTimeIntervalSince1970` = 978307200.0，Foundation 常量 ✅ 可用）。
- `NSUUID()` / `.UUIDString` **可用**，无需改。
- `NSTimeZone.systemTimeZone` / `localTimeZone` / `defaultTimeZone` / `timeZoneWithName` **全部不存在**。
  `NSTimeZone()` 虽能编译，但运行时 `name` 为 null —— 它不是系统时区，**不能用**。
- 取某时刻的时区偏移改用 CoreFoundation：
  `CFTimeZoneCopySystem()` + `CFTimeZoneGetSecondsFromGMT(tz, atMillis / 1000.0 - NSTimeIntervalSince1970)`，
  需要 `@OptIn(ExperimentalForeignApi::class)`，用完 `CFRelease(tz)`。
  仍要**按具体时刻**取（夏令时切换日前后偏移不同），**不要**改成「当前偏移」的写法——这是 Android 端刻意的口径，改了两端就不一致。

修的原则：**只动 `iosMain` 的实现，不要改 `commonMain` 的口径**。口径是两端共用的唯一真源，改了会破坏 Android 的 653 例单测。
如果你认为 `commonMain` 确实有问题，**停下来回写**，等 Windows 端确认后一起改。

补充：`shared/build.gradle.kts` 目前只声明了 `iosArm64()` 与 `iosSimulatorArm64()`。**如果你是 Intel Mac**，需要再补 `iosX64()`。

## 5. 回写（必做，否则 Windows 端看不到结果）

把结论落到文件，不要只在对话里说。新建 `docs/handoff/Mac端初始化_回写清单_V1.00.md`，内容包含：

1. 每阶段的**实际命令与结果**（通过 / 报错原文）
2. `PlatformTime.ios.kt` 是否改动：改了什么、为什么（贴最终代码）
3. 与预期的**偏差**（例如 Xcode / JDK 版本、需要补 `iosX64()`、依赖下载失败等）
4. 待 Windows 端确认的事项
5. 环境版本快照：`xcodebuild -version`、`java -version`、`kdoctor` 结论、Mac 机型（Apple Silicon / Intel）

然后提交并推送（提交身份用 `ChofeeChen <ChofeeChen@users.noreply.github.com>`）：

```bash
git add -A
git commit -m "docs: Mac 端初始化回写清单"
git push
```

## 6. 先**不要**做的事

- **不要**现在就建 iOS 工程 / 写 SwiftUI 界面 —— 等第 4 阶段 iOS 目标编过、回写被 Windows 端确认后再开始。
- **不要**改 `docs/prd/` 下的 PRD 内容（PRD 是串行资源，同一时刻只有一端改）。若有冲突，写进回写清单。
- **不要**新建平行文档。修订 PRD 一律在**原文件上原地升版本号**（修内容升 minor，结构或口径变更升 major）。
- **不要**改 `shared/src/commonMain` 的口径，也不要在 `domain/` 里引入 `android.*` / `androidx.*` / `java.time` / `java.util.Calendar`。
- 接入 Xcode 前需要给 `shared` 加 framework 输出配置（`binaries.framework { baseName = "shared"; isStatic = true }`），
  这会改 `shared/build.gradle.kts` —— 该文件 Windows 端也在用，**先写进回写清单，确认后再改**。
  ✅ **2026-09-28 WCB 已批准**（回写清单 §8.1）：由 Mac 端改、Mac 端验，跑 `linkDebugFrameworkIosSimulatorArm64`；
  约定改的时候**只加 framework 配置**，不顺手改 Kotlin / AGP 版本或 `compilerOptions`。

## 7. iOS 首版范围（背景，供你后续规划）

对齐 Android v0.1.17 功能集，**分两批**：① 本地对局 / 练习 / 统计 → ② 联机大厅。
**明确不做**：M10 社交（合规限制）、M8 硬件接入（P3）、M12 手机视觉计分（P2，仅留接口）。
注意：统计与成就相关的 `domain/{stats,achievement,leaderboard}` 目前还留在 `:app`（它们通过 Room 反向依赖 data 层，需先解耦才能下沉），
因此**统计口径暂时还不在 shared 里**——iOS 统计页要等 Windows 端完成解耦（T7 中的 D5）。
