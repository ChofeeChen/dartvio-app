版本 V1.00 \| 2026-09-27 \| **MacBook 端回写**（Windows 端请读这份）

> 对应提示词：`docs/handoff/Mac端初始化_提示词_V1.00.md`
> 规则：**每阶段做完即追加**，不在对话里传话；提交身份 `ChofeeChen <ChofeeChen@users.noreply.github.com>`。
> 本文件只记录「命令 + 结果 + 偏差」，不改任何 PRD，不改 `shared/src/commonMain` 口径。

---

# 环境快照（先行登记，第 2 阶段补齐）

| 项 | 值 |
| --- | --- |
| 仓库本地路径 | `/Users/chenfeng/Developer/dartvio-app`（IDE 工作区根为 `/Users/chenfeng/Developer`，仓库在其下一层，与提示词 `cd dartvio-app` 的相对路径一致） |
| Mac 机型 | ✅ **Apple Silicon**：`uname -m` = `arm64`、`sysctl hw.model` = `Mac17,3`、macOS **27.0**（Build 26A428）—— 与 T7 §E1 登记的「MacBook M5 / macOS 27 Golden Gate」一致 |
| Xcode | ✅ **Xcode 27.0**（Build 27A266a），`xcode-select -p` = `/Applications/Xcode.app/Contents/Developer`；许可证**已接受**（`IDEXcodeVersionForAgreedToGMLicense = 27.0`）；iOS 27.0 SDK + iOS Simulator SDK 齐备，模拟器可用（iPhone 18 Pro 等） |
| Android SDK | ✅ **已装** `~/Library/Android/sdk`：`platforms/android-37.0`、`build-tools/37.0.0`、`platform-tools`（37.0.1）；`local.properties` 已追加 `sdk.dir=`（该文件被 .gitignore 忽略） |
| JDK | ✅ **Android Studio 自带 openjdk 25.0.3**（`javac 25.0.3`）；`JAVA_HOME` 已写入 `~/.zprofile` |
| Android Studio | ✅ **2026.1.4.8**（内部版本 `AI-261.26222.65.2614.16379836`），位于 `/Applications/Android Studio.app` |
| Homebrew | ✅ **7.0.6**（走清华 TUNA 镜像，见 §2.7）；kdoctor 1.1.0、cocoapods 1.17.0、android-commandlinetools 已装 |
| Git 身份 | ⚠️ **本机未配置** `user.name` / `user.email`（`git config user.name` 输出为空）→ 第 5 阶段沿用 A8 做法，用 `git -c` 临时传参，**不改全局 git config** |

---

# 阶段 1：拉代码 —— ✅ 通过

## 1.1 实际执行的命令与输出

> 说明：仓库在本机**已经克隆好了**（不是本次新拉的），所以阶段 1 走的是「校验 + 补齐初始化动作」，没有重复 clone。

```bash
cd /Users/chenfeng/Developer/dartvio-app
git --no-pager log --oneline -3
git --no-pager remote -v
git --no-pager status --short
git rev-parse --abbrev-ref HEAD
```

输出：

```
e0e2c52 docs: add Mac handoff prompt; T7 V1.03
b782a2c docs: T7 V1.02 - repo initialized and pushed; sync references
4dd5c47 Merge remote Initial commit (README) into local history
---REMOTE---
origin	git@github.com:ChofeeChen/dartvio-app.git (fetch)
origin	git@github.com:ChofeeChen/dartvio-app.git (push)
---STATUS---
（空）
---BRANCH---
main
```

```bash
# 补做提示词里剩下的两个初始化动作
cp android/local.properties.example android/local.properties
chmod +x android/gradlew
ls -la android/gradlew android/local.properties
git check-ignore -v android/local.properties
git --no-pager status --short
```

输出：

```
-rwxr-xr-x@ 1 chenfeng  staff  8728 Sep 27 18:47 android/gradlew
-rw-r--r--@ 1 chenfeng  staff   489 Sep 27 18:56 android/local.properties
---IGNORE CHECK---
android/.gitignore:15:local.properties	android/local.properties
---STATUS---
 M android/gradlew
```

```bash
# 校验 SSH 凭据是否真的可用（提示词推荐 SSH 而非 HTTPS+PAT）
GIT_SSH_COMMAND="ssh -o BatchMode=yes -o ConnectTimeout=10" git ls-remote --heads origin
```

输出：

```
e0e2c52e8d2e9845411bd214a830c07c21ee632e	refs/heads/main
```

```bash
# 校验提示词验收标准里点名的那个提交是否在历史中
git --no-pager log --oneline | tail -10
git --no-pager log --oneline --all | grep -i "initial commit"
```

输出：

```
c77cc3b Initial commit: DartVio Android v0.1.18 + KMP shared module
658c9ea Initial commit
4dd5c47 Merge remote Initial commit (README) into local history
```

## 1.2 验收结论

| 验收项 | 结果 |
| --- | --- |
| 远端地址是 SSH 形式 `git@github.com:ChofeeChen/dartvio-app.git` | ✅ 是（提示词推荐的 SSH 方式，非 HTTPS） |
| SSH 凭据可用（免交互 `ls-remote` 成功） | ✅ 通过，`refs/heads/main` = `e0e2c52` |
| 本地 HEAD 与远端一致 | ✅ 本地 `e0e2c52` == 远端 `e0e2c52`，无超前/落后 |
| 工作区干净 | ✅ 除下面这条 chmod 外无改动 |
| `local.properties` 已就位且**不入库** | ✅ `git check-ignore` 命中 `android/.gitignore:15:local.properties` |
| `gradlew` 可执行 | ✅ 权限 `-rwxr-xr-x` |
| 历史中能看到 `Initial commit: DartVio Android v0.1.18 + KMP shared module` | ✅ `c77cc3b` 存在（在历史第 4 个，不在 `log -3` 里，因为之后又有 3 个 docs 提交） |

**阶段 1 结论：通过。** 代码已在位、凭据可用、初始化动作已补齐。

## 1.3 与提示词预期的偏差（共 4 条，均无阻塞）

1. **仓库是既有的，未重复 clone。** 本机 `/Users/chenfeng/Developer/dartvio-app` 已是完整克隆（含 `.git`），且 `git ls-remote` 证明与远端同步，因此跳过 `git clone`。提示词里 `ssh-keygen` 一步也跳过：本机已有 `~/.ssh/id_ed25519.pub`，且 `BatchMode` 免交互连通成功，说明该 key 已在 GitHub 登记。
2. **`local.properties.example` 的内容是 Supabase / 联机后端的占位模板**（`https://<your-project>.supabase.co` 等），提示词说「留空即可」。本机按提示词原样复制，未填真值 → 预期行为是**联机与匿名统计自动关闭**。若 Mac 端后续要跑涉及联机的用例，需要真值，但按分工这属于 Windows 侧资产，暂不处理。
3. **`chmod +x android/gradlew` 产生了一个未提交的改动**：git 把它记为 `M android/gradlew`（文件模式 `100644` → `100755`）。这是因为文件在 Windows 上提交时没有可执行位。**建议在第 5 阶段随回写清单一起提交**（无害，且能让 Mac/Linux 端直接 `./gradlew`），待 Windows 端确认。
4. **Android SDK 不在 `~/Library/Android/sdk`**（`ls` 报 No such file or directory）。提示词第 3 阶段说「若只是 SDK 路径问题，确认该目录存在，或在 `local.properties` 里补 `sdk.dir=...`」。这是**第 3 阶段 `./gradlew :app:testDebugUnitTest` 最可能的失败点**，提前登记。

## 1.4 待 Windows 端确认

| # | 事项 | 需要谁做什么 |
| --- | --- | --- |
| W1 | `gradlew` 可执行位（偏差 3）是否许可提交 | Windows 端确认「随回写清单一起提交 `M android/gradlew`」没问题；若 Windows 端的文件系统会给它加回 `100644`，则两端会来回抖，可改为不提交、各端自行 chmod |
| W2 | Mac 端是否需要 `local.properties` 真值 | 现阶段按「留空」执行（联机/统计关闭）。若第 3 阶段有单测依赖联机后端，再请示如何安全传值（不要走仓库） |

---

---

# 阶段 2：环境体检 —— ⚠️ 部分通过（Xcode 侧全绿，Java/Android 侧全空，第 3 阶段被阻塞）

## 2.1 实际执行的命令与输出

```bash
sw_vers; uname -m; sysctl -n hw.model
xcode-select -p; xcodebuild -version; xcodebuild -showsdks; xcrun simctl list devices available
defaults read /Library/Preferences/com.apple.dt.Xcode
ls /Applications; which brew; ls -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
which java; /usr/libexec/java_home -V; ls /Library/Java/JavaVirtualMachines
curl -s -o /dev/null -w "%{http_code}" -I https://mirrors.cloud.tencent.com/gradle/gradle-9.5.0-bin.zip
```

输出（原文）：

```
ProductName:  macOS     ProductVersion: 27.0     BuildVersion: 26A428
arm64
Mac17,3

/Applications/Xcode.app/Contents/Developer
Xcode 27.0
Build version 27A266a
iOS SDKs:          iOS 27.0            -sdk iphoneos27.0
iOS Simulator SDKs: Simulator - iOS 27.0  -sdk iphonesimulator27.0
== Devices == -- iOS 27.0 --
    iPhone 18 Pro (93B2448F-...) (Shutdown)   ... 等 11 台

{
    IDELastGMLicenseAgreedTo = EA2002;
    IDELastPTRLicenseAgreedTo = EA2002;
    IDEXcodeVersionForAgreedToGMLicense = "27.0";
    IDEXcodeVersionForAgreedToPTRLicense = "27.0";
}

/Applications: CodeBuddy CN.app / Doubao.app / ... / Xcode.app / iMovie.app / 元宝.app
（无 Android Studio、无 IntelliJ / Fleet）
brew not found
ls: /Applications/Android Studio.app/Contents/jbr/Contents/Home: No such file or directory

/usr/bin/java                       ← 只是 stub
The operation couldn't be completed. Unable to locate a Java Runtime.
Please visit http://www.java.com for information on installing Java.
/Library/Java/JavaVirtualMachines:  total 0   （空目录）

tencent-gradle:200
```

## 2.2 与提示词验收标准的对照

| 提示词要求 | 结果 |
| --- | --- |
| Xcode（含 Command Line Tools） | ✅ Xcode 27.0 + CLT 就位 |
| `sudo xcodebuild -license accept` | ✅ **无需执行**：`IDEXcodeVersionForAgreedTo*License = 27.0` 证明已接受（`xcrun simctl` 可用亦佐证） |
| Android Studio（提供 Gradle / JDK / Android SDK） | ❌ **未安装** |
| JDK 17 以上 | ❌ **一个 JDK 都没有**（`/usr/bin/java` 是未装 JDK 时的占位 stub） |
| `JAVA_HOME` 指向 AS 自带 JDK | ❌ **无法设置**：`/Applications/Android Studio.app/Contents/jbr/Contents/Home` 不存在 |
| `brew install kdoctor && kdoctor` | ❌ **无法执行**：brew 未安装 |

**阶段 2 结论：Xcode 侧（阶段 4 的前提）已满足；Java / Android 侧（阶段 3 的前提）完全缺失，阶段 3 现在跑不了。**

## 2.3 顺带探明的、对后续有用的事实

1. **机型是 Apple Silicon（`arm64`），不是 Intel。** → 提示词 §4 末尾说「如果你是 Intel Mac，需要再补 `iosX64()`」，**本机型不需要补**，`shared/build.gradle.kts` 现有的 `iosArm64()` + `iosSimulatorArm64()` 已覆盖真机与模拟器。
2. **网络与镜像可达性**：
   - Gradle 分发源 `https://mirrors.cloud.tencent.com/gradle/gradle-9.5.0-bin.zip` → **200**（提示词说"已配阿里云镜像"，实际 wrapper 配的是**腾讯云镜像**；`settings.gradle.kts` 的仓库才是阿里云 maven 镜像 + google()/mavenCentral() 兜底）
   - `maven.aliyun.com` / `dl.google.com` → 有 HTTP 响应（404 是目录无索引，属可达）
   - ⚠️ `curl https://github.com` 返回 **000（连接失败）**，但**阶段 1 的 SSH `git ls-remote` 成功** → HTTPS 到 github 疑似被代理/VPN 干扰（本机装有 iKuuuVPN）。若阶段 4 的 konan 工具链需从 github 取件，可能受阻；若失败则改用 JetBrains 源或关代理重试。
3. **版本档位**（来自 `gradle/libs.versions.toml` / wrapper）：Gradle **9.5.0**、AGP **9.3.0**、Kotlin **2.2.10** —— 与 CODEBUDDY.md §2 一致。据此 JDK 需 **17 以上**，按 CODEBUDDY §4.5 取 Android Studio 自带 **JDK 25**。

## 2.4 需要你决策/操作的安装项（阶段 3 的前置）

| # | 缺什么 | 建议 | 备注 |
| --- | --- | --- | --- |
| I1 | Homebrew | 装 brew 后一切从简 | 官方脚本需网络 + sudo |
| I2 | JDK 17+（建议 25） | ① 装 Android Studio 自带 jbr；② 或 `brew install openjdk@17`（需 `JAVA_HOME`） | CODEBUDDY §4.5 要求 JAVA_HOME 指向 AS 的 jbr |
| I3 | Android SDK（需 platform 37 等） | 装 Android Studio 时一并装；或仅装 command-line tools + `sdkmanager` | AGP 编译/单测必须有 SDK |
| I4 | kdoctor | `brew install kdoctor` | 体检工具，非必需；装了便于自检 |
| I5 | Android Studio 本体 | 约 1–2 GB，从 Google 下载 | 提示词 E4 指定；KMP 也需要它 |

> **我没有擅自安装**：这些动作需要 sudo / 越出工作区 / 大体积下载，按你的要求先停下来报告。

## 2.5 待 Windows 端确认

| # | 事项 |
| --- | --- |
| W3 | 确认**不需要**补 `iosX64()`（本机 arm64）✅ 已可自行判定，仅知会 |
| W4 | Mac 只装「JDK + Android command-line tools」而不装完整 Android Studio，是否被接受？—— 提示词 E4 要求装 AS，但本机 `/Applications` 干净、用户未必想要 AS GUI。若 Windows 端坚持 AS（KMP 插件/后续 Gradle 同步方便），则按 I5 装完整版 |

---

## 2.6 阻塞处理：sudo 需密码，改由用户手动安装（2026-09-27）

我这边确认了 sudo 不可用（非交互式 shell 无法代填密码）：

```bash
$ sudo -n true
sudo: a password is required
EXIT=1
```

`chenfeng` 属于 `admin` 组，但仍需输入密码。因此**Homebrew 与 Android Studio 由用户手动安装**（已与用户确认），我负责安装后的一切验证与免 sudo 部分。

交给用户的命令（标准路径，贴合提示词 E4）：

```bash
# ① 装 Homebrew（中途要一次开机密码，输密码时屏幕不显示字符，属正常）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# ② 把 brew 加进 PATH（Apple Silicon 是 /opt/homebrew）
eval "$(/opt/homebrew/bin/brew shellenv)"
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zshrc

# ③ 确认可用
brew --version

# ④ 装 Android Studio（约 1–2 GB，会再要一次密码，装进 /Applications）
brew install --cask android-studio

# ⑤ 装 KMP 官方体检工具（不需要密码）
brew install kdoctor
```

**我随后要自己做的免 sudo 部分**（用户装完就执行）：

1. 校验：`brew --version`、`ls /Applications/Android Studio.app`、`ls /Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java`
2. 设 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 并 `java -version`（期望 17+，CODEBUDDY §4.5 为 JDK 25）
3. 装 Android SDK 到 `~/Library/Android/sdk`（**不走 brew、不用 sudo**）：从 `dl.google.com` 取 commandlinetools zip，用 `sdkmanager` 安装项目所需的
   `platforms;android-37` / `build-tools;37.0.0` / `platform-tools`
   （依据：`app/build.gradle.kts` = `compileSdk release(37)`、`targetSdk 37`、`minSdk 24`；`shared/build.gradle.kts` = `compileSdk 37`、`minSdk 24`；`jvmTarget` 与 `sourceCompatibility` 均为 **17**）
4. 跑 `kdoctor`，结论贴进本文件
5. 重跑阶段 2 验收，通过后进入阶段 3

---

## 2.7 阻塞处理②：`github.com:443` 不通，改用清华 TUNA 镜像装 Homebrew（2026-09-27）

用户首次跑 Homebrew 安装脚本：sudo 成功、Command Line Tools 27.0 装好，但在
「Downloading and installing Homebrew」一步失败，报错原文：

```
fatal: unable to access 'https://github.com/Homebrew/brew/': Failed to connect to github.com port 443 after 75015 ms: Couldn't connect to server
Warning: Trying again in 2 seconds: ...（2s/4s/8s/16s 重试均失败）
Huab6622a#10
```

与阶段 2 实测 `curl https://github.com → 000` 一致：**本机 HTTPS 到 `github.com` 被阻断**
（装有 iKuuuVPN 但终端流量未走代理）。注意 `raw.githubusercontent.com` 是**通的**（200），
所以安装脚本本身能下载，只是克隆 `github.com/Homebrew/brew` 失败。

镜像连通性实测：

| 源 | 结果 |
| --- | --- |
| `mirrors.tuna.tsinghua.edu.cn/git/homebrew/brew.git` | ✅ **200** → 采用 |
| `mirrors.tuna.tsinghua.edu.cn/homebrew-bottles/api/formula.jws.json` | ✅ **200** → 采用 |
| `mirrors.ustc.edu.cn/git/homebrew/brew.git` | ❌ 404 |
| `github.com`（对照） | ❌ 000 |

处理方案：`export HOMEBREW_BREW_GIT_REMOTE / HOMEBREW_CORE_GIT_REMOTE / HOMEBREW_API_DOMAIN /
HOMEBREW_BOTTLE_DOMAIN` 指向 TUNA 后重跑官方安装脚本；`.zshrc` 里持久化 API/BOTTLE 两个变量，
保证后续 `brew install --cask android-studio`（实际下载走 `dl.google.com`）与 `brew install kdoctor`
不再依赖 github.com。

⚠️ **副作用登记**：安装脚本执行了 `xcode-select --switch /Library/Developer/CommandLineTools`，
把开发者目录从完整版 Xcode 切到了 CLT。**这会影响第 4 阶段的 Kotlin/Native 编译**，
需在 brew 装完后切回：`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`。

---

## 2.8 阶段 2 复检结果（2026-09-27）—— ✅ 通过

### 补齐的安装（免 sudo 部分由 Mac 端 AI 完成，需 sudo 部分由用户手动完成）

| 项 | 版本 / 位置 | 谁装 |
| --- | --- | --- |
| Homebrew | 7.0.6，`/opt/homebrew` | 用户（sudo，走 TUNA 镜像） |
| Android Studio | 2026.1.4.8（AI-261.26222.65.2614.16379836） | 用户（sudo，`brew install --cask android-studio`，1.5 GB） |
| JDK | openjdk **25.0.3**（AS 自带 jbr） | 随 AS |
| Android SDK | `platforms;android-37.0`、`build-tools;37.0.0`、`platform-tools`（37.0.1） | AI（`brew install --cask android-commandlinetools` → `sdkmanager --sdk_root=$HOME/Library/Android/sdk`） |
| kdoctor | 1.1.0 | 用户 |
| cocoapods | 1.17.0 + brew ruby 4.0.7 | AI（用户要求「一起装」） |

### 环境变量（已写入 `~/.zprofile`，用户级，不入库）

```bash
eval "$(/opt/homebrew/bin/brew shellenv zsh)"
export HOMEBREW_API_DOMAIN="https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles/api"
export HOMEBREW_BOTTLE_DOMAIN="https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles"
export HOMEBREW_BREW_GIT_REMOTE="https://mirrors.tuna.tsinghua.edu.cn/git/homebrew/brew.git"
export HOMEBREW_CORE_GIT_REMOTE="https://mirrors.tuna.tsinghua.edu.cn/git/homebrew/homebrew-core.git"
export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
```

`~/.zprofile` 而非 `~/.zshrc`：**以 Homebrew 安装脚本自己的提示为准**。

### kdoctor 最终输出（已去掉 ANSI 颜色码）

```
Environment diagnose (to see all details, use -v option):
[✓] Operation System
[✓] Java
  ! Android Studio (AI-261.26222.65.2614.16379836)
    Location: /Applications/Android Studio.app
    Bundled Java: openjdk 25.0.3 2026-04-21
    Kotlin Plugin: 261.26222.65.2614.16379836-AS
    Kotlin Multiplatform Mobile Plugin: not installed
[✓] Xcode
[✓] CocoaPods

Conclusion:
  ✓ Your operation system is ready for Kotlin Multiplatform Mobile Development!
```

（修复过程：① `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` 把 Xcode 从 CLT 切回完整版 → Xcode 由 ✖ 变 ✓；
② 添 `LC_ALL/LANG=en_US.UTF-8` → CocoaPods 由 ✖「requires UTF-8 encoding」变 ✓。
**`xcode-select` 那条是用户手动执行的**，因 sudo 需密码，AI 无法代填。）

### 阶段 2 验收对照

| 提示词要求 | 结果 |
| --- | --- |
| Xcode + CLT | ✅ Xcode 27.0，`xcode-select -p` = `/Applications/Xcode.app/Contents/Developer` |
| 接受 Xcode 许可 | ✅ 早已接受（`IDEXcodeVersionForAgreedToGMLicense = 27.0`） |
| Android Studio | ✅ 2026.1.4.8 |
| JDK 17+ | ✅ 25.0.3 |
| `JAVA_HOME` → AS jbr | ✅ 已持久化到 `~/.zprofile` |
| kdoctor | ✅ 结论「ready for Kotlin Multiplatform Mobile Development」 |

**唯一残留警告：Kotlin Multiplatform Mobile 插件未安装（kdoctor 记为 `!` 非 `✖`）。**
判定为**无害**：AS 2026.1 的 Kotlin 插件（261.26222.65.2614.16379836-AS）本身已含 KMP 支持，
那个独立插件（plugins.jetbrains.com/plugin/14936）是旧版入口。命令行构建完全不需要它。

### 待 Windows 端确认（新增）

| # | 事项 |
| --- | --- |
| W5 | Mac 端 SDK 装在 `~/Library/Android/sdk`，**未**用 Android Studio 默认路径以外的位置；`local.properties` 已写 `sdk.dir`（该文件不入库）。Windows 端不受影响 |
| W6 | KMM 插件警告按「无害」处理（见上），若 Windows 端坚持要在 AS 图形界面里装，请在 Mac 上手动：Android Studio → Settings → Plugins → 搜 "Kotlin Multiplatform Mobile" → Install |
| W7 | 本机 `github.com:443` 不通，Mac 端所有 GitHub 操作走 SSH（阶段 1 已验证 SSH 可用）；若将来 CI 或脚本依赖 HTTPS 拉 github，需要改走镜像或代理 |

---

# 阶段 3：验证 Android 侧 —— ✅ 通过（65 文件 / 653 例 / 0 fail）

> 先说结论：**与 Windows 端基线完全一致**。但首次运行失败了，原因是网络抖动，不是代码问题，详见 §3.2。

## 3.1 实际执行的命令

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export LC_ALL=en_US.UTF-8
nohup ./gradlew :app:testDebugUnitTest > /tmp/dartvio_stage3.log 2>&1 &   # 后台跑 + 轮询日志
```

（用后台 + 轮询而非前台等待，是为了避免长时间无输出被误判为卡死；构建**全程未中断**。）

## 3.2 首次运行：失败（BUILD FAILED in 10m 15s）—— 网络瞬时超时，非代码问题

完整报错原文（`/tmp/dartvio_stage3.log`）：

```
FAILURE: Build failed with an exception.

* What went wrong:
Configuration cache state could not be cached: field `__runtimeDependenciesNavigationFiles__` of task
`:app:processDebugNavigationResources` of type
`com.android.build.gradle.internal.tasks.ProcessNavigationXmlTask`:
error writing value of type 'org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection'
> Could not resolve all files for configuration ':app:debugRuntimeClasspath'.
   > Could not resolve androidx.core:core-ktx:1.16.0.
     Required by:
         project ':app'
      > Repository maven is disabled due to earlier error below:
         > Could not resolve androidx.navigation:navigation-common:2.7.7.
            > Could not get resource 'https://maven.aliyun.com/repository/google/androidx/navigation/navigation-common/2.7.7/navigation-common-2.7.7.pom'.
               > Could not GET 'https://maven.aliyun.com/repository/google/androidx/navigation/navigation-common/2.7.7/navigation-common-2.7.7.pom'.
                  > Read timed out
> There are 68 more failures with identical causes.

BUILD FAILED in 10m 15s
Configuration cache entry discarded due to serialization error.
```

**原因分析（先实测再下结论，未凭猜测改代码）**：对同一个失败 URL 逐个源实测：

| 源 | 结果 |
| --- | --- |
| `maven.aliyun.com/repository/google/...` | ✅ **200，0.21s** |
| `dl.google.com/dl/android/maven2/...` | ✅ 200，0.17s |
| `mirrors.cloud.tencent.com/maven/...` | ✅ 200，1.03s |
| `repo1.maven.org`（该构件不在中央库，404 属正常） | — |
| `mirrors.tuna.tsinghua.edu.cn/maven/...` | ❌ 404（无此路径） |

→ **镜像本身完全正常**，失败是下载高峰期的**瞬时读超时**；一旦超时，Gradle 会把整个仓库标记为 disabled，于是 68 个依赖连锁失败。
那条 `Configuration cache ... error writing value` 是**连锁反应**，不是独立故障：依赖没解析出来，配置缓存自然写不进去。

**修复（不动仓库内任何文件）**：新建**用户级** `~/.gradle/gradle.properties`（不在仓库里，Windows 端不受影响）：

```properties
systemProp.org.gradle.internal.http.connectionTimeout=180000
systemProp.org.gradle.internal.http.socketTimeout=180000
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
```

**没有**改 `android/settings.gradle.kts`（仓库里的仓库配置两端共用）**、也没有**改 `android/gradle.properties`（项目级，里面是 `org.gradle.jvmargs=-Xmx2048m`）。

## 3.3 第二次运行：BUILD SUCCESSFUL in 3m 52s

（首次运行已缓存 Gradle 9.5.0 与约 788 MB 依赖，所以第二次快很多。）

```
BUILD SUCCESSFUL in 3m 52s
35 actionable tasks: 35 executed
Configuration cache entry stored.
```

## 3.4 验收数字 —— 与 Windows 基线一致

```
$ cd android/app/build/test-results/testDebugUnitTest
XML 文件数:        65
tests:            653
failures:         0
errors:           0
skipped:          1
含 <failure>/<error> 的 XML:  0
```

| 验收项 | 期望 | 实际 |
| --- | --- | --- |
| 测试文件数 | 65 | ✅ **65** |
| 用例数 | 653 | ✅ **653** |
| 失败数 | 0 | ✅ **0** |

**阶段 3 结论：通过。仓库在 Mac 端是健康的，可以进入阶段 4。**

## 3.5 偏差与观察（3 条）

1. **1 个用例被跳过**：`TEST-com.dartvio.app.net.online.OnlineRoomFlowTest.xml`。
   推断与 `local.properties` 未填联机后端真值有关（按分工 Mac 端不持有这些值），属预期。**待 Windows 端确认基线是否同样是 1 个 skip**（Windows 端只记了「0 fail」，没记 skip 数）。
2. **JDK 25 警告**：`Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_24 JVM target`。
   Kotlin 2.2.10 尚不支持 JDK 25 字节码档位，回退到 24。项目显式设的是 `jvmTarget 17` / `sourceCompatibility 17`，**不影响结果**。
   之所以用 JDK 25 而非 17，是因为 CODEBUDDY.md §4.5 硬要求 `JAVA_HOME` 指向 Android Studio 自带 JDK。
3. **AGP 自动补装了 `build-tools;36.0.0`**：我在阶段 2 装的是 `37.0.0`，但 AGP 9.3 还要 36.0.0，Gradle 自动下载并接受了许可证。
   已落在 `~/Library/Android/sdk/build-tools/36.0.0`。

## 3.6 待 Windows 端确认（新增）

| # | 事项 |
| --- | --- |
| W8 | 基线里 **skipped 是否为 1**？Mac 端跳过了 `OnlineRoomFlowTest` 的 1 例，怀疑是联机后端未配置所致；若 Windows 端是 0，说明 Mac 端 `local.properties` 留空确实改变了行为，需判断要不要给 Mac 端真值 |
| W9 | Mac 端为抗抖动新增了用户级 `~/.gradle/gradle.properties`（放宽 HTTP 超时 + `-Xmx4g`）。**未改仓库内任何构建文件**；若 Windows 端也想更稳，可自行在本机加同样内容 |

---

# 阶段 4：验证 iOS 目标 —— ⚠️ **未通过**：`iosMain` 已修好并验证，`commonMain` 有 10 处 JVM-only API 待 Windows 端处理

> 按提示词 §4「只动 `iosMain` 的实现，不要改 `commonMain` 的口径……如果你认为 `commonMain` 确实有问题，**停下来回写**，等 Windows 端确认后一起改」——
> 我在此停下，未改 `commonMain` 任何一个字。

## 4.1 实际执行的命令

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

首次编译下载了 Kotlin/Native 工具链（`~/.konan` ≈ 997 MB，含 LLVM 19 / libffi），耗时正常。

## 4.2 首次编译：12 条错误，分两类

**A 类（iosMain，3 条，归 Mac 端修）：**

```
e: .../shared/src/iosMain/kotlin/com/dartvio/app/platform/PlatformTime.ios.kt:15:19 Unresolved reference 'timeIntervalSince1970'.
e: .../shared/src/iosMain/kotlin/com/dartvio/app/platform/PlatformTime.ios.kt:22:20 Unresolved reference 'systemTimeZone'.
e: .../shared/src/iosMain/kotlin/com/dartvio/app/platform/PlatformTime.ios.kt:23:43 Unresolved reference 'dateWithTimeIntervalSince1970'.
```

**B 类（commonMain，10 条，归 Windows 端改）：**

```
e: commonMain/.../impact/HeatmapGrid.kt:103:31      Unresolved reference 'Math'.
e: commonMain/.../impact/ImpactWindow.kt:45:17      Unresolved reference 'Math'.
e: commonMain/.../impact/IntentTarget.kt:45:19      Unresolved reference 'Math'.
e: commonMain/.../model/CricketState.kt:184:33      Unresolved reference 'System'.
e: commonMain/.../practice/CricketMprEngine.kt:142:44 Unresolved reference 'format'.
e: commonMain/.../room/RoomExpiry.kt:32:30          Unresolved reference 'System'.
e: commonMain/.../room/RoomModels.kt:89:27          Unresolved reference 'System'.
e: commonMain/.../vision/BoardGeometry.kt:74:19     Unresolved reference 'Math'.
e: commonMain/.../vision/BoardGeometry.kt:76:53     Unresolved reference 'div' for operator '/'.   ← 上面那条 Math 的连锁
e: commonMain/.../vision/BoardLayout.kt:43:19       Unresolved reference 'Math'.
```

> B 类的性质：**JVM-only API 漏进了 commonMain**。`java.lang.Math`、`java.lang.System` 在 JVM 上靠隐式 `java.lang.*` 导入能编译，
> 所以 `:app:testDebugUnitTest`（阶段 3，653 例全绿）**查不出来**；一到 Kotlin/Native 就全部暴露。
> 这与 T7 §5.2 里 D1「审计结论不完整」是同一类问题：**grep `android.*` 查不出隐式 `java.lang` 依赖**。

## 4.3 A 类修复：不靠猜，用编译器探针 + 真跑一遍来验证

提示词 §4 预判的坑（"`NSDate()` / `NSUUID()` 构造被禁用，改工厂方法"）**与实测不符**，实际是**成员缺失**：

| 写法 | 实测 | 备注 |
| --- | --- | --- |
| `NSDate()` | ✅ 可用 | 就是 `+[NSDate date]`，即当前时刻 |
| `NSDate().timeIntervalSince1970` | ❌ 不存在 | 实例属性里**没有**这个 |
| `NSDate().timeIntervalSinceReferenceDate` | ✅ 可用 | 以 2001-01-01 为原点 |
| `NSDate.date()` / `NSDate.dateWithTimeIntervalSince1970(...)` | ❌ 不存在 | 工厂方法已被映射成构造器 |
| `NSDate` 构造器全集 | `constructor()`、`constructor(timeIntervalSinceReferenceDate: Double)`、`constructor(coder: NSCoder)` | 由编译器候选列表给出 |
| `NSTimeIntervalSince1970`（Foundation 常量） | ✅ 可用 | = 978307200.0 |
| `NSTimeZone.systemTimeZone` / `localTimeZone` / `defaultTimeZone` / `system` / `timeZoneWithName` | ❌ **全部不存在** | 类级 API 全军覆没 |
| `NSTimeZone()` | ⚠️ 能编译但**运行时 `name` 为 null** | 不是系统时区，**不能用** |
| `NSUUID()` / `NSUUID.UUID()` / `.UUIDString` | ✅ 可用 | 无需改 |
| 类级访问本身是否正常 | ✅ 正常（`NSUUID.UUID()`、`NSBundle.mainBundle`、`NSCalendar.currentCalendar` 都能解析） | 说明不是"类级访问"的通用故障，是这几个 API 个案缺失 |
| `CFTimeZoneCopySystem()` + `CFTimeZoneGetSecondsFromGMT(tz, atTime)` | ✅ 可用（需 `@OptIn(ExperimentalForeignApi::class)`） | **最终采用** |

探针方法（全部在 `/tmp`，未碰仓库）：直接调用 `~/.konan/.../bin/kotlinc-native -target ios_simulator_arm64`
编译小文件，让编译器逐个判定；最后**编译成 macOS 可执行程序实跑**核对数值：

```
NSTimeIntervalSince1970 = 9.783072E8
epochMs   = 1790516390901        ← 对照 `date +%s` = 1790516390  ✅ 一致
offsetSec = 28800.0              ← 对照 `date +%z` = +0800       ✅ 一致（Asia/Shanghai）
NSTimeZone().name → NullPointerException   ← 证实 NSTimeZone() 不能用
```

## 4.4 改动内容：只改了 `iosMain` 这一个文件

`android/shared/src/iosMain/kotlin/com/dartvio/app/platform/PlatformTime.ios.kt` 最终代码：

```kotlin
package com.dartvio.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTimeZoneCopySystem
import platform.CoreFoundation.CFTimeZoneGetSecondsFromGMT
import platform.Foundation.NSDate
import platform.Foundation.NSTimeIntervalSince1970
import platform.Foundation.NSUUID

/*
 * ⚠️ 本文件**只能在 macOS 上编译**（Kotlin/Native 需要 Xcode 工具链）。
 * Windows 上构建请只跑 Android 任务：`gradlew :app:testDebugUnitTest`。
 *
 * 2026-09-27 Mac 首次编译实测（Kotlin 2.2.10 / Xcode 27）：Foundation 绑定里下面三个 API
 * **并不存在**，编译器报 Unresolved reference：
 *   - `NSDate.timeIntervalSince1970`
 *   - `NSDate.dateWithTimeIntervalSince1970(...)`
 *   - `NSTimeZone.systemTimeZone`（`localTimeZone` / `defaultTimeZone` / `system` / `timeZoneWithName` 同样不可用）
 * 另外 `NSTimeZone()` 虽然能编译，但运行时 `name` 为 null —— 它不是「系统时区」，不能用。
 *
 * 实际可用的替代（均已编译通过，并编译成 macOS 可执行程序实测过数值）：
 *   - 当前时刻：`NSDate()`（对应 `+[NSDate date]`）→ `timeIntervalSinceReferenceDate` + `NSTimeIntervalSince1970`
 *   - 某时刻的时区偏移：`CFTimeZoneCopySystem()` + `CFTimeZoneGetSecondsFromGMT(tz, atTime)`（CoreFoundation）
 */
@OptIn(ExperimentalForeignApi::class)
actual object PlatformTime {

    actual fun nowMillis(): Long =
        ((NSDate().timeIntervalSinceReferenceDate + NSTimeIntervalSince1970) * 1000.0).toLong()

    /**
     * 与 Android 端一样按**具体时刻**取偏移（而不是取「当前偏移」）：
     * 夏令时切换日前后，同一个时区的偏移并不相同。
     *
     * CF 的绝对时间以「2001-01-01」为原点（秒），而入参是 epoch 毫秒，
     * 因此先减掉 [NSTimeIntervalSince1970]（978307200）再换算成秒。
     */
    actual fun zoneOffsetMillis(atMillis: Long): Long {
        val timeZone = CFTimeZoneCopySystem()
        return try {
            CFTimeZoneGetSecondsFromGMT(
                timeZone,
                atMillis / 1000.0 - NSTimeIntervalSince1970,
            ).toLong() * 1000L
        } finally {
            CFRelease(timeZone)
        }
    }
}

actual fun randomIdHex(): String = NSUUID().UUIDString.replace("-", "")
```

**口径未变**：`nowMillis()` 仍是当前 epoch 毫秒；`zoneOffsetMillis(atMillis)` 仍是**按传入时刻**取偏移（不是当前偏移），
与 Android 端 `TimeZone.getOffset(atMillis)` 语义一致。改的只是"怎么拿到这两个数"，没改"拿什么"。

## 4.5 修复后重编译：iosMain 的 3 条错误已消失

```
$ ./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
> Task :shared:compileKotlinIosArm64 FAILED
> Task :shared:compileKotlinIosSimulatorArm64 FAILED
（错误清单只剩 B 类 commonMain 那 10 条，iosMain 0 条）
```

**即：`iosMain` 已通过；两个 iOS 目标仍因 `commonMain` 编译失败。**

## 4.6 给 Windows 端的 B 类问题清单（含现状代码与建议改法，我没有改）

| 文件:行 | 现状 | 建议 |
| --- | --- | --- |
| `impact/HeatmapGrid.kt:103` | `Math.pow(n.toDouble(), -0.2)` | `kotlin.math.pow`：`n.toDouble().pow(-0.2)` |
| `impact/ImpactWindow.kt:45` | `Math.toRadians(BoardGeometry.SECTOR_ANGLE_DEG / 2.0)` | Kotlin 无 `toRadians`：`x * PI / 180`（`kotlin.math.PI`） |
| `impact/IntentTarget.kt:45` | `Math.toRadians(index * BoardGeometry.SECTOR_ANGLE_DEG)` | 同上 |
| `vision/BoardGeometry.kt:74` | `Math.toDegrees(atan2(xMm, yMm))` | `atan2(...) * 180 / PI` |
| `vision/BoardGeometry.kt:76` | `((deg + SECTOR_ANGLE_DEG / 2.0) / SECTOR_ANGLE_DEG)` | **无需改**，是上一行 `Math` 的连锁错误，改完 74 即消失 |
| `vision/BoardLayout.kt:43` | `Math.toRadians(sectorIndex * BoardGeometry.SECTOR_ANGLE_DEG)` | 同 `toRadians` |
| `model/CricketState.kt:184` | `System.currentTimeMillis()` | `PlatformTime.nowMillis()`（commonMain 里已有这个 `expect`，`RoomSchedule.kt` 就在用） |
| `room/RoomExpiry.kt:32` | `System.currentTimeMillis()` | 同上 |
| `room/RoomModels.kt:89` | `System.currentTimeMillis()` | 同上 |
| `practice/CricketMprEngine.kt:142` | `String.format("%.2f", mpr)` | JVM-only。需自写两位小数格式化（如手工截断/四舍五入拼串） |

⚠️ 提醒：`toRadians` / `toDegrees` 在 `kotlin.math` 里**没有对应函数**，必须手写乘除；改完请务必让 `:app:testDebugUnitTest`
的 653 例**仍然全绿**（涉及角度的用例对这些常量很敏感，`SECTOR_ANGLE_DEG` 相关建议多核一遍）。

## 4.7 待 Windows 端确认（新增）

| # | 事项 |
| --- | --- |
| W10 | **阻塞项**：请按 §4.6 改完 `commonMain` 这 10 处后回我，Mac 端再跑一次 `:shared:compileKotlinIosSimulatorArm64` + `:shared:compileKotlinIosArm64` 收尾 |
| W11 | 提示词 §4 预判的坑（"`NSDate()` / `NSUUID()` 构造被禁用"）**与实际不符**，实际是成员缺失 + `NSTimeZone` 类级 API 全不可用。建议同步修订提示词 / T7，避免以后重复踩 |
| W12 | `commonMain` 里可能还有别的隐式 `java.lang.*` 依赖，本次只在 `Math` / `System` / `String.format` 上暴露。建议 Windows 端 grep 一遍 `Math\.` `System\.` `String.format` `Locale` `SimpleDateFormat` 再下沉 |
| W13 | 我在 `iosMain` 用了 CoreFoundation 的 `CFTimeZoneCopySystem` / `CFTimeZoneGetSecondsFromGMT` / `CFRelease`，需 `@OptIn(ExperimentalForeignApi::class)`。若 Windows 端更希望统一走 `expect/actual` 日历路线而不用 CF，请回我改 |

---

# 阶段 5：回写收尾与提交推送

## 5.1 本次 Mac 端产生的改动（共 3 项）

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `android/shared/src/iosMain/kotlin/com/dartvio/app/platform/PlatformTime.ios.kt` | **唯一的代码改动**。Foundation 三个 API 不存在 → 改用 `NSDate().timeIntervalSinceReferenceDate + NSTimeIntervalSince1970` 与 CoreFoundation 的 `CFTimeZoneCopySystem()` / `CFTimeZoneGetSecondsFromGMT`。详见 §4.4 |
| 2 | `android/gradlew` | 文件模式 `100644 → 100755`（补可执行位）。**内容零改动**，仅模式位。见 W1 |
| 3 | `docs/handoff/Mac端初始化_回写清单_V1.00.md` | 新增（本文件） |

**没有改动**：`commonMain`（一个字都没动）、`docs/prd/` 下任何 PRD、`shared/build.gradle.kts`（未加 framework 输出配置）、未建 `ios/` 工程、未写 SwiftUI。
`android/local.properties` 已确认被 `.gitignore` 排除，**未入库**。

## 5.2 环境版本快照（提示词 §5 第 5 项要求）

```
$ xcodebuild -version
Xcode 27.0
Build version 27A266a

$ java -version            （JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home）
openjdk version "25.0.3" 2026-04-21
OpenJDK Runtime Environment (build 25.0.3+-15898627-b508.16)
javac 25.0.3

$ kdoctor
[✓] Operation System
[✓] Java
[!] Android Studio (AI-261.26222.65.2614.16379836) — Kotlin Multiplatform Mobile Plugin: not installed（无害，见 §2.8）
[✓] Xcode
[✓] CocoaPods
Conclusion: ✓ Your operation system is ready for Kotlin Multiplatform Mobile Development!

机型：Apple Silicon（arm64 / Mac17,3 / macOS 27.0 Build 26A428）→ 不需要补 iosX64()

Android Studio：2026.1.4.8
Android SDK：~/Library/Android/sdk（platforms;android-37.0、build-tools;37.0.0、build-tools;36.0.0、platform-tools 37.0.1）
Gradle / AGP / Kotlin：9.5.0 / 9.3.0 / 2.2.10
```

## 5.3 四阶段结果总览

| 阶段 | 验收标准 | 结果 |
| --- | --- | --- |
| 1 拉代码 | SSH 可用、HEAD 对齐、local.properties 就位、gradlew 可执行 | ✅ 通过 |
| 2 环境体检 | Xcode / AS / JDK 17+ / JAVA_HOME / kdoctor | ✅ 通过（kdoctor 结论 ready） |
| 3 Android 单测 | 65 文件 / 653 例 / 0 fail | ✅ 通过（653 / 0 / 0，skip 1） |
| 4 iOS 编译 | `compileKotlinIosSimulatorArm64` + `compileKotlinIosArm64` | ⚠️ **iosMain ✅ / commonMain ⛔ 阻塞**（10 处 JVM-only API，见 §4.6） |

## 5.4 待 Windows 端确认事项汇总

| # | 事项 | 出处 |
| --- | --- | --- |
| W1 | `gradlew` 可执行位改动要不要收（担心两端模式位来回抖） | §1 |
| W2 | `local.properties` 真值：Mac 端留空，联机与匿名统计自动关闭，是否符合预期 | §1 |
| W5 | Mac 端 SDK 装在 `~/Library/Android/sdk` + `local.properties` 写了 `sdk.dir`（不入库），Windows 端不受影响 | §2.8 |
| W6 | KMM 插件警告按「无害」处理 | §2.8 |
| W7 | 本机 `github.com:443` 不通，Mac 端 GitHub 操作只能走 SSH | §2.8 |
| W8 | 基线 skipped 是否也是 1（Mac 端跳过 `OnlineRoomFlowTest` 1 例） | §3.6 |
| W9 | Mac 端新增用户级 `~/.gradle/gradle.properties`（放宽超时 + `-Xmx4g`），未改仓库构建文件 | §3.6 |
| W10 | **阻塞项**：按 §4.6 改完 `commonMain` 10 处后回我，Mac 端补跑两个 iOS 编译任务 | §4.7 |
| W11 | 提示词 §4 预判的坑（"NSDate()/NSUUID() 构造被禁用"）与实测不符，建议修订提示词 / T7 | §4.7 |
| W12 | `commonMain` 可能还有别的隐式 `java.lang.*` 依赖（建议 grep `Math\.` `System\.` `String.format` `Locale` `SimpleDateFormat`） | §4.7 |
| W13 | iosMain 用了 CoreFoundation 三个 CF 函数 + `@OptIn(ExperimentalForeignApi::class)`，是否接受 | §4.7 |

## 5.5 提交方式（按提示词 §5）

提交身份用 `ChofeeChen <ChofeeChen@users.noreply.github.com>`，本机未配全局 git config，
因此用 `git -c user.name=... -c user.email=...` **逐条临时传参**，不写全局配置。

> 偏差：提示词给的提交信息是 `docs: Mac 端初始化回写清单`，但本次还含一个代码改动（iosMain 修复）。
> 为了便于 Windows 端单独 revert / cherry-pick，我拆成了**两个提交**（而不是把代码塞进 docs 提交）：
> ① 代码改动（iosMain 修复 + gradlew 模式位）② 回写清单。

---

## 6. Windows 端回写（2026-09-28）：commonMain 10 处已清理

### 6.1 处理结果

- 提交 **`872eb04`**（`refactor(shared): commonMain 去除 JVM-only API，打通 iOS 编译`，已变基到 `14896cb` 之上）
- Android 回归：**65 文件 / 653 例 / 0 fail / 0 skip**（Windows 端本机实测，与 Mac 端 653/0 一致）
- 自查：commonMain 内已无任何 JVM-only API 残留（按含 `java.util` / `java.time` / `Locale` / `DecimalFormat` / `NumberFormat` 的宽口径复扫）

| 文件（`android/shared/src/commonMain/kotlin/com/dartvio/app/...`） | 改法 |
| --- | --- |
| `domain/impact/HeatmapGrid.kt` | `Math.pow(n, -0.2)` → `n.toDouble().pow(-0.2)` |
| `domain/impact/ImpactWindow.kt` | `Math.toRadians(...)` → `... * PI / 180.0` |
| `domain/impact/IntentTarget.kt` | 同上 |
| `domain/vision/BoardGeometry.kt` | `Math.toDegrees(atan2(...))` → `atan2(...) * 180.0 / PI` |
| `domain/vision/BoardLayout.kt` | `Math.toRadians(...)` → `... * PI / 180.0` |
| `domain/model/CricketState.kt` | `System.currentTimeMillis()` → `PlatformTime.nowMillis()` |
| `domain/room/RoomExpiry.kt` | 同上 |
| `domain/room/RoomModels.kt` | 同上 |
| `domain/practice/CricketMprEngine.kt` | `String.format("%.2f", mpr)` → 纯 Kotlin 实现（见 §6.2） |

**计数说明**：实际是 **9 个改动点**，不是 10 —— `BoardGeometry.kt:76` 是 74 行的连锁报错，改完 74 行即自动消失。

### 6.2 与 Mac 端建议改法的一处差异：`formatMpr`

按 Mac 端 2026-09-28 追加的约束实现（纯 Kotlin；不用 `Locale` / `DecimalFormat` / `NumberFormat`；不加 `expect/actual`；
用 `round` 而非截断；用 Double 中间量），另外**补了 NaN / Infinity / 负数的保护** —— MPR 理论上不会出现这些值，
但可以避免极端值被格式化成 `NaN.00`：

```kotlin
fun formatMpr(mpr: Float): String {
    if (mpr.isNaN() || mpr.isInfinite()) return "0.00"
    val negative = mpr < 0f
    val cents = round(abs(mpr.toDouble()) * 100.0).toLong()
    return "${if (negative) "-" else ""}${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
```

653 例全绿，说明没有用例与它冲突。

### 6.3 顺带修掉的一个文件损坏风险（与 iOS 无关，但必须记一笔）

`android/app logo/DartVio-logo_002.ai` 被 git 的 `text=auto` 误判成**文本**，`git add` 时正在被做 CRLF→LF 重写
（一次 add 就产生 3584 增 / 3624 删），会**损坏 AI 源文件**。已在 `.gitattributes` 显式声明
`*.ai` / `*.psd` / `*.eps` / `*.sketch` 为 `binary`，并以二进制重新入库（`DartVio-logo_005.png` 一并入库）。
⚠️ 提示：仓库里**历史版本**的 `.ai` 仍是被转换过的（无法靠新规则回溯修复），请以本机源文件为准。

### 6.4 W 项答复（§5.4 那张表）

| # | 答复 |
| --- | --- |
| W1 | ✅ 收下。Windows 端 `core.filemode = false`（git 在 Windows 的默认），模式位差异**不被跟踪**，不会两端来回抖 |
| W2 | ✅ 符合预期。Mac 端 `local.properties` 留空正是我们要的效果：联机与匿名统计自动关闭 |
| W5 | ✅ 不受影响。Windows 端的 SDK 路径来自自己的 `local.properties`（不入库） |
| W6 | ✅ 按无害处理，暂不动 |
| W7 | ⚠️ Windows 端 2026-09-27 晚间 `github.com:443` 也被阻断过数小时（22 通），现已恢复。**建议 Windows 端也配一把独立 SSH key 作备用** —— 但要用户手动跑 `ssh-keygen`（本机 shell 无法交互式生成，试了 4 种传参方式均失败） |
| W8 | ✅ 不是问题。Windows skip=**0**、Mac skip=1，差别是 `OnlineRoomFlowTest` 那 1 例：Mac 端 `local.properties` 为空 → `OnlineConfig.isConfigured = false` → 按设计探活跳过；Windows 端有真实配置且 Supabase 可达，所以真跑通了。**基线以 653 / 0 fail / 0 skip 为准** |
| W9 | ✅ 接受。用户级 `~/.gradle/gradle.properties` 不影响仓库 |
| W10 | ✅ 已完成，请 Mac 端补跑两个 iOS 编译任务 |
| W11 | 收到。提示词 `docs/handoff/Mac端初始化_提示词_V1.00.md` 由 Windows 端修订更合适，本次**先不动**，避免两端同时改同一批文档；等 iOS 编译通过后一并升 V1.01 |
| W12 | ✅ 已复扫，除清单那 10 处外无残留（与 Mac 端结论一致） |
| W13 | ✅ 接受 CoreFoundation 方案，不用改。`CFTimeZoneGetSecondsFromGMT` 仍按**具体时刻**取偏移，与 Android 端 `TimeZone.getOffset(atMillis)` 语义一致 |

### 6.5 给 Mac 端的下一步

1. `git pull` 后补跑：`./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`
2. 结果追加到本文件（§6 之后）
3. **两份新文档还没进仓库**：`docs/handoff/iOS首版页面对照表_V1.00.md`、`docs/handoff/iOS端MVP施工蓝图_V1.00.md`
   在当前 HEAD（`872eb04`）里都不存在。请 Mac 端确认是否已提交 —— 没推的话先推上来，Windows 端才能按它排期

---

## 7. Mac 端回写（2026-09-28）：iOS 编译验收 **通过**

### 7.1 结论

```
git pull → HEAD = aba70ee（两端对齐）
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64

> Task :shared:compileKotlinIosSimulatorArm64
> Task :shared:compileKotlinIosArm64
BUILD SUCCESSFUL
2 actionable tasks: 2 executed
e: 0 条     w: 4 行（同一 2 条警告 × 2 个目标，见 §7.4）
```

**✅ iOS 编译打通。** WCB 的 `872eb04`（9 文件，非 10 —— `BoardGeometry.kt:76` 是 74 行的连锁报错，已如预判自动消失）在 Mac 端验证有效。
`iosMain` 的 `PlatformTime.ios.kt`（`baf9895`，CoreFoundation 方案）同样编译通过，W13 按「接受」处理，未再改动。

### 7.2 关于耗时：我为什么测了四次

第一次跑出来 **6 秒**，这个数字我不敢直接写进回写清单 —— Kotlin/Native 编译近 1 万行不可能这么快。于是做了交叉验证：

| # | 方法 | 结果 | 判定 |
| --- | --- | --- | --- |
| 1 | 直接跑 | 6s | Gradle build cache 命中 |
| 2 | `--rerun-tasks` | 2s | 同上，仍命中 build cache |
| 3 | `--rerun-tasks --no-build-cache` | 1s | Kotlin 增量缓存命中 |
| 4 | `./gradlew --stop` + 手动删产物 + `--no-build-cache` | **7s** | ✅ **可信的冷启动耗时** |

第 4 次才真正绕过了三层缓存（Gradle build cache / Kotlin 增量缓存 / Kotlin daemon 内存缓存）。
所以**冷启动全量编译 = 7 秒**（两个目标合计），日常增量会更短。

> 顺带说明为什么这么快：这两个任务只产出 **klib**（前端 + IR 序列化），**不做 native codegen**。
> 真正的耗时在后面 `linkDebugFrameworkIos*`（链接成 framework），预计分钟级，届时另行记录。

### 7.3 环境版本快照（本次）

```
Xcode            27.0 (Build 27A266a)
Kotlin           2.2.10
Gradle           9.5.0
AGP              9.3.0
JDK              OpenJDK 25.0.3 (Android Studio JBR)
macOS            27.0 (Build 26A428) / Apple Silicon arm64
机型             Mac17,3（arm64，故不需要 iosX64()）
gradlew 位置     android/gradlew（不在仓库根）
remote           git@github.com:ChofeeChen/dartvio-app.git（22 端口通，本轮未触发 443 阻断）
```

### 7.4 产物校验（不只看 BUILD SUCCESSFUL）

```
android/shared/build/classes/kotlin/
├── iosSimulatorArm64/main/klib/shared/default/{manifest, ir/*.knf|knb|knd|knt}
└── iosArm64/main/klib/shared/default/{manifest, ir/*}
```

两个目标的 `manifest` 与全套 IR 文件均为**本次编译新生成**（已按时间戳核对，非历史残留）。

### 7.5 剩余警告（4 行，无害）

```
w: .../commonMain/.../platform/PlatformTime.kt:25:1
w: .../iosMain/.../platform/PlatformTime.ios.kt:27:1
   'expect'/'actual' classes ... are in Beta. Consider using the '-Xexpect-actual-classes' flag
```

同一个文件对，在两个目标各报一次 → 共 4 行。**不影响编译结果**。
若要消除，需在 `shared/build.gradle.kts` 加 `compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")` ——
属两端共用文件，**本次没动**（见 M2）。

### 7.6 两份 iOS 文档已入库

`docs/handoff/iOS首版页面对照表_V1.00.md`、`docs/handoff/iOS端MVP施工蓝图_V1.00.md`
此前是 Mac 端未跟踪文件，本次已提交推送（详见提交记录）。

### 7.7 我建议的下一步

| 序 | 事项 | 归属 | 阻塞关系 |
| --- | --- | --- | --- |
| ① | **`shared/build.gradle.kts` 加 `binaries.framework { baseName = "shared"; isStatic = true }`** | **需 WCB 先回确认**（两端共用文件） | 阻塞我建 iOS 工程 |
| ② | 建 `ios/` Xcode 工程 + embed framework 脚本（`ENABLE_USER_SCRIPT_SANDBOXING = NO`）+ Phase 0-4 | Mac 端（我） | 待① |
| ③ | W11：提示词 §4 对 `NSDate()` / `NSUUID()` 的预判与实测不符，升 V1.01 | WCB | 不阻塞 |
| ④ | D5：`domain/{stats,achievement,leaderboard}` 从 Room 反向依赖中解耦并下沉 | WCB 自行排期 | **不阻塞 iOS**（首版统计页先占位） |

**①是目前唯一的硬阻塞**，其余都能并行。我在等 WCB 回①的期间不做任何仓库改动。

### 7.8 新增待确认事项（Mac 端）

| # | 事项 | 我的倾向 |
| --- | --- | --- |
| M1 | `shared/build.gradle.kts` 的 framework 输出配置：**谁来改、改完谁验**。建议我改（iOS 相关），改完跑 `linkDebugFrameworkIosSimulatorArm64` 验证，WCB 只需回一句确认 | 我改、我验 |
| M2 | §7.5 的 `-Xexpect-actual-classes` 要不要加 | 倾向**不加**：只是 Beta 提示，加了会改共用构建文件；真要消除可以等②一起做 |
| M3 | iOS Bundle ID 定为 **`com.dartvio.app`**（与 Android `applicationId` 一致），App 名 **DartVio**，图标素材已收（1024 PNG） | 仅告知，已定 |
| M4 | `ios/` 目录不参与 Gradle 构建（不在 `settings.gradle.kts` 里），不会影响 Android 侧 | 仅告知 |

---

_（第 3 轮：iOS framework 输出 + Xcode 工程的结果将追加于此）_
