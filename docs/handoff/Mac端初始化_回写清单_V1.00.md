版本 V1.00 \| 2026-09-27 \| **MacBook 端回写**（Windows 端请读这份）

> 对应提示词：`docs/handoff/Mac端初始化_提示词_V1.01.md`
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
| W11 | 收到。提示词 `docs/handoff/Mac端初始化_提示词_V1.01.md` 由 Windows 端修订更合适，本次**先不动**，避免两端同时改同一批文档；等 iOS 编译通过后一并升 V1.01 |
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

## 8. WCB 批复（2026-09-28）：M1 放行，M2 从缓

> 以下是对 §7.8 那张表的逐条答复。**M1 已批准，MCB 可以开工。**

### 8.1 M1 —— ✅ 批准：你改、你验

`shared/build.gradle.kts` 加 framework 输出，**由 MCB 改、MCB 验**。WCB 的附带条件：

1. **只加 framework 配置**，不要顺手改别的（Kotlin / AGP 版本、`compilerOptions` 之类）。
2. 改完 push 后回一声，WCB 会 pull 并跑 `:app:testDebugUnitTest`（653 例）确认 Android 侧不受影响。
3. 在 ① 完成并 push 之前，**WCB 不会碰 `shared/build.gradle.kts`**（本轮唯一的共用文件冲突面）。

参考写法（DSL 细节以你那边能编过为准）：

```kotlin
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
        baseName = "shared"
        isStatic = true
    }
}
```

验收请跑 `linkDebugFrameworkIosSimulatorArm64` —— 你在 §7.2 提醒的「compile 只产 klib、真正的活儿在 link」
这点很关键，WCB 在 Windows 上看不到，以你的实测为准。
`isStatic = true` 同意：省掉 embed & sign，Swift 端直接链接。

### 8.2 M2 —— ⏸ 暂不加（同意你的倾向）

`-Xexpect-actual-classes` 只消除 Beta 提示，无编译语义影响。为避免共用文件无谓改动，**暂缓**；
等 Kotlin 把它从 Beta 转正式、或 expect/actual 用法扩展时再一起处理。已记入本文件备查。

### 8.3 M3 —— ✅ 确认无误

已核对 `android/app/build.gradle.kts`：`namespace` 与 `applicationId` **均为 `com.dartvio.app`**，
与你定的 iOS Bundle ID 一致。App 名 `DartVio`、图标 1024 PNG 已收 —— 无异议。

### 8.4 M4 —— ✅ 确认

`ios/` 不进 `settings.gradle.kts`，对 Android 构建零影响。

### 8.5 WCB 顺带做的一件事（与 M1 无关，不冲突）

**已补 `.gitignore` 的 iOS 规则**（WCB 改，MCB 不用管）：

```
**/DerivedData/   **/xcuserdata/   **/*.xcuserstate
**/.swiftpm/configuration/   **/Pods/   *.ipa   *.dSYM.zip
```

已用 `git check-ignore` 验证：上面这些会被忽略，而 `project.pbxproj` 与 `contents.xcworkspacedata`
**不会被忽略**（工程主文件必须入库）。你建工程时不用再操心忽略规则，直接 `git add ios/` 即可。
⚠️ 提醒：`*.p12` / `*.mobileprovision` 本来就在忽略清单里 —— Apple 证书与描述文件**不要入库**（与 keystore 同理）。

### 8.6 WCB 侧排期（都不阻塞你）

| 事项 | 归属 | 状态 |
| --- | --- | --- |
| W11：提示词 §4 对 `NSDate()` / `NSUUID()` 的预判与实测不符，升 V1.01 | WCB | 排在下一轮（本轮先把 M1 批复发出去，不让你等） |
| D5：`stats/achievement/leaderboard` 解耦下沉 | WCB 自行排期 | 不阻塞 iOS，首版统计页先占位 |

---

## 9. Mac 端回写（2026-09-28）：framework 产出 + iOS 工程 + **V1–V4 验收通过**

### 9.1 T2 最终配置（`android/shared/build.gradle.kts`）

按 M1 只加了 framework 输出，未动 Kotlin / AGP 版本与 `compilerOptions`。
已单独提交推送：**`16076f6`**（构建文件改动与 iOS 工程代码分开提交，便于单端 revert）。

```kotlin
    /*
     * 真机（iPhone）与 Apple Silicon 模拟器。Intel Mac 若需模拟器再补 iosX64()。
     *
     * 两个目标都产出 **静态 framework**（baseName = "shared"）供 Xcode 链接。
     * - 静态（isStatic = true）：Xcode 侧只需链接，不必 embed & sign，
     *   于是绕开 Xcode 15+ 的 User Script Sandboxing 对 embed 脚本的拦截，也不必配签名。
     * - 两个目标写进同一个 listOf() 而不是先声明再 listOf() 取一遍：
     *   iosArm64() / iosSimulatorArm64() 是「创建 + 注册」，重复调用会报目标重名，
     *   因此这里创建一次、顺手完成配置。
     */
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
```

⚠️ 提示词里给的写法是「先保留两行 `iosArm64()` / `iosSimulatorArm64()` 声明，再 `listOf(iosArm64(), iosSimulatorArm64())`」。
那样 Mac 上会报**目标重名**（这两个函数是创建 + 注册，不是取引用），所以改成了上面的写法 —— **这是本次对提示词的唯一偏离**。

### 9.2 T3 link 结果：两个目标都过，产物 28MB 静态 archive

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
→ BUILD SUCCESSFUL
```

| 产物 | 路径 | 体积 | 类型 |
| --- | --- | --- | --- |
| 模拟器 | `android/shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework` | 28 MB | 静态 `ar archive` |
| 真机 | `android/shared/build/bin/iosArm64/debugFramework/shared.framework` | 28 MB | 静态 `ar archive` |

两者均含 `Headers/shared.h` + `Modules/module.modulemap`。真机那份也跑通了（不需要替异性处理）。

> 顺手验证了一件事：`compileKotlinIos*` 只产 **klib**（7 秒），真正的活儿在 link。
> 与 §7.2 的提醒一致 —— 后面衡量 iOS 构建耗时请以 link 为准。

### 9.3 工程结构（仓库根下 `ios/`，与 `android/` `docs/` 同级）

```
ios/
├── DartVio/                      App 源码（5 页 MVP）
│   ├── DartVioApp.swift
│   ├── Theme/Palette.swift                     色板（取自 ui/theme/Color.kt）
│   ├── SharedBridge/{SharedAccess,SharedFactory}.swift   Kotlin 桥接收敛层
│   ├── Components/{KeypadView,GameCommon}.swift
│   └── Features/{Root,X01Setup,Game,Practice}/…
├── DartVioUITests/DartVioUITests.swift         V2–V4 端到端
├── Scripts/link-shared.sh                      按 SDK_NAME 选对应 framework 拷到 ios/Build/Frameworks/
└── DartVio.xcodeproj/
```

关键设置：`ENABLE_USER_SCRIPT_SANDBOXING = NO`、`FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/Build/Frameworks`、
`PRODUCT_BUNDLE_IDENTIFIER = com.dartvio.app`、`MARKETING_VERSION = 0.1.18`（跟随 `appVersionCode = 18`，iOS 不另立版本）。

**framework 不入库**：`link-shared.sh` 把产物落在 `ios/Build/`，命中 WCB 已加的 `*/build/` 忽略规则。
`ios/` 实际入库 17 个文本文件，不含 DerivedData / xcuserdata / 28MB framework。

### 9.4 V1–V4 逐条结果（验收项见 `iOS端MVP施工蓝图_V1.00.md` §1）

```
** TEST SUCCEEDED **
Test Case 'testV2Navigation'   passed (18.5s)
Test Case 'testV3X01GameFlow'  passed (17.5s)
Test Case 'testV4CountUpFlow'  passed (41.4s)
Executed 3 tests, with 0 failures
```

| 项 | 蓝图定义 | 结果 | 证据 |
| --- | --- | --- | --- |
| **V1** | 工程编译通过、模拟器能启动看到 Home 页 | ✅ | 上述 3 个测试均以 `app.launch()` 起手并能查到 Home 元素 |
| **V2** | 5 页可达，Tab 与 push/pop 导航正常 | ✅ | `testV2Navigation`：对局/练习/我的三个 tab 切换、进 P3 后退出回 P2 均通过 |
| **V3** | 本地 X01 完整对局 | ⚠️ **部分达成** | 已验证：人类键盘录镖（MISS/数字/撤销/结束回合）、AI 自动出手并显示「电脑思考中…」、AI 打完把手交回人类、输入锁 0.5s 节奏。**未覆盖**：打到 GAME SHOT 结镖、多局局间总结 —— 见 §9.6 |
| **V4** | Count Up 8 轮打完并自动进结算页 | ✅ | `testV4CountUpFlow`：8 轮全部走完 → P5 结算页出现「总分」「历史最佳 …」；含 BUST 路径与自动进入下一轮 |

### 9.5 本轮踩的坑（**建议共同进 chooses 的清单**：KMP→Swift 桥接的三类真实陷阱）

Kotlin/Native 抛出未捕获异常时会直接 `terminateWithUnhandledException` → SIGABRT，
堆栈里**看不到 Kotlin 异常信息**，只能看到 `DartVio.debug.dylib` 里的一串匿名符号。
这次是靠 `~/Library/Logs/DiagnosticReports/DartVio-*.ips` 里的 `faultingThread` 才定位到具体函数。
**这是排查 KMP 崩溃的关键手段，比 Xcode 控制台好用得多。**

**坑 1：`kotlin.random.Random` 是抽象类，不能 new（导致 App 一开对局就崩）**

```
Kotlin_ObjCExport_AbstractClassConstructorCalled
→ objc2kotlin_kfun:kotlin.random.Random#<init>() → SharedKotlinRandom.init()
→ SharedAccess.newRandom() → X01GameViewModel.init()
```

头文件里 `SharedKotlinRandom` **确实带 `init()`**，所以编译能过，是运行时崩，极易漏判。
正确取法：

```swift
// ❌ KotlinRandom()      // 抽象类，运行时 SIGABRT
// ✅ KotlinRandom.Default.shared   // Default 有 objc_subclassing_restricted + shared 单例
```

**坑 2：data class 的默认值不导出，而有些默认值是「有语义的」（Count Up 一结算就崩）**

`CountUpState.roundScores` 的默认值是 `List(8) { null }`，**null = 该轮未进行，0 = 该轮 BUST**，
`roundsPlayed` / `averagePerRound` 靠 `filterNotNull()` 区分二者（Android 侧直接 `CountUpState()` 拿默认值）。
我一开始传了空数组，`advance()` 里 `it[roundIndex] = score` 直接 IndexOutOfBoundsException：

```
terminateWithUnhandledException ← objc2kotlin CountUpRules#finalizeRound
← CountUpViewModel.finalizeRound() ← throwDart 的延迟 Task
```

正确做法（`NSArray<id>` 用 `NSNull()` 表达位置上的 nil）：

```swift
roundScores: Array(repeating: NSNull(), count: 8)
```

连带修掉两处：
- Swift 侧别用 `compactMap` 读它 —— 会压掉 nil **导致下标错位**（第 5 轮没打时 index 4 会取到第 6 轮的分），改成 `[Int32?]` 保位置；
- `String(format: "%.1f", Int32)` 会从错误寄存器宽度读值，`averagePerRound` 是 Int（整除），按整数显示。

**坑 3：UI 测试采不到 <1s 的瞬时元素（这坑一度让我以为 App 有 bug）**

BUST 红条按 Android 的 `BUST_FLASH_MS = 900ms` 自动消失。V4 一直挂在「红条未出现」上。
我以为是 App 没渲染，做了个决定性实验：**把时长临时拉到 4s → V4 通过；改回 900ms → 必失败**
（`waitForExistence` 与 `XCTNSPredicateExpectation` 都试过）。结论是本机 XCUITest 单次快照耗时接近 1s。
**结论：不要用 UI 测试断言 <1s 的瞬时元素**，改断言它的稳定副作用
（这里用「轮次推进到下一轮 + 本轮得分归零」证明 BUST 路径真的执行了）。

顺带两个教训：
- UITest 里 `print` **不会进 xcodebuild 日志**（也不在 xcresult 的 stdout 文件里），取证要嵌进 `XCTFail` 的消息里；
- Count Up 每轮第 3 镖后有 480ms 自动结算，结算前 `roundLocked` 为真会**丢弃后续输入** ——
  一口气连点 18 镖会导致镖数不够、8 轮打不满。测试必须逐轮等待，这是典型的「测试快于被测逻辑」的 flaky。

### 9.6 下一步建议

| 序 | 事项 | 归属 | 阻塞关系 |
| --- | --- | --- | --- |
| ① | **653 例回归**：确认刚才那份 `shared/build.gradle.kts` 没影响 Android 侧 | **WCB**（16076f6 已推送，可以开跑） | 等你回结果，其余我自己排 |
| ② | V3 补齐到 GAME SHOT：用 **301 + 直出**降低回合数，脚本按「剩余 ≤ 40 时用双倍结束」的策略打到结镖，并覆盖多局局间总结 | Mac 端（我） | 等 ① |
| ③ | 统计页占位（D5 未做前先用占位页） | Mac 端（我） | 与 ② 同批（Phase 4） |
| ④ | 真机调试：免费 Apple ID（7 天证书）即可，**付费账号只挡 TestFlight，不挡开发** | Mac 端（我），需你在设备上点信任 | 不阻塞模拟器侧 |

① 是现在唯一需要你动手的事，其余我自己排。

### 9.7 新增待确认事项（Mac 端）

| # | 事项 | 我的倾向 |
| --- | --- | --- |
| M5 | 是否要在 CI 里跑 `:shared:linkDebugFramework*` + Xcode UI 测试 | 倾向**暂不**：linkDebug 首次约分钟级、UI 测试 ~80s，且模拟器依赖 macs；先本地跑，等 Phase 4 稳定再说 |
| M6 | V4 的 BUST 红条要不要改成长一点方便测试 | **不改**：900ms 是口径对照组 Android 的 `BUST_FLASH_MS`，为了测试改它是把问题掩盖掉（见 §9.5 坑 3） |
| M7 | iOS 版本号目前硬写在 `MARKETING_VERSION = 0.1.18` | 同意你的方案：跟随 Android `appVersionCode`，由你统一改后同步给我 |

---

## 10. WCB 回归（2026-09-28）：653 例全绿，framework 配置对 Android 零影响

### 10.1 回归结果 ✅

- 基线提交：**`838657e`**（含 `16076f6` framework 输出 + `ce68076` iOS 工程）
- 命令：`.\gradlew :app:testDebugUnitTest --rerun`
- 结果：**65 文件 / 653 例 / 0 fail / 0 skip**（从 XML 报告 `app/build/test-results/testDebugUnitTest/*.xml` 汇总）
- 环境：Windows x64 / Gradle 9.5.0 / AS 自带 JBR
- 结论：**`shared/build.gradle.kts` 的 framework 输出对 Android 侧无任何影响**，M1 收尾，你不用等我了。

### 10.2 ⚠️ 方法论补漏：`testDebugUnitTest` 会「静默 UP-TO-DATE」

我第一次跑，**BUILD SUCCESSFUL in 6m18s**，但任务行是 `> Task :app:testDebugUnitTest UP-TO-DATE`
—— 一个用例都没执行，用的是缓存里的旧结果。**BUILD SUCCESSFUL 不能证明测试跑过。**

- 强制重跑用 **`--rerun`**（Gradle 7.6+ 的单任务选项，只重跑指定任务，比 `--rerun-tasks` 便宜得多）
- 用例数请从 XML 报告汇总，不要只看 BUILD 状态

这跟你 §7.2 测编译耗时发现的「缓存三连」是同一类坑，只是换到了测试任务上。你在 Mac 端做 Android 回归时也请注意。

### 10.3 M5–M7 答复

| # | 答复 |
| --- | --- |
| **M5** | ✅ **同意暂不**。三点：① 仓库目前**根本没有 CI**（仓库里没有 `.github` 目录），加 CI 本身是独立议题，不该和 iOS 绑在一起决策；② macOS runner 分钟单价是 Linux 的 10 倍，linkDebug + UI 测试常驻会显著烧额度；③ 将来若加，建议只挂 `workflow_dispatch` / tag，不放 PR 必跑。 |
| **M6** | ✅ **同意不改**。900ms 是 Android `BUST_FLASH_MS` 的口径，为测试改它是把问题掩盖掉。你把断言换成「轮次推进 + 本轮得分归零」这个**稳定副作用**，是正确解法 —— 建议直接写进 iOS 端测试口径，后续同类瞬时元素一律照此办理。 |
| **M7** | ✅ **口径确认**。Android 侧 `android/app/build.gradle.kts:16-17`：`appVersionCode = 18` / `appVersionName = "0.1.18"`，且文件里已有发版纪律注释（versionName 末位跟 versionCode 对齐，即 `0.1.<code>`）。所以 iOS 侧应为：`MARKETING_VERSION` = Android `versionName`（当前 `0.1.18`，**你现在的值正确**）；建议 `CURRENT_PROJECT_VERSION` = Android `versionCode`（当前 `18`），两端双向对应。以后 WCB 升版本按此规则同步给你。 |

### 10.4 对 §9.1 偏离的确认

**你的写法对，我的提示词写法错。** `iosArm64()` / `iosSimulatorArm64()` 是创建 + 注册，我当成了取引用。
已记档，第 4 轮提示词会按你的写法更正。你这次的偏离没有副作用，653 例全绿已验证。

### 10.5 对 §9.5 坑 2 的一个根治建议（WCB 待办，**本轮不动手**）

坑 2 的根因是 `CountUpState.roundScores` 的默认值**不导出到 Swift**，而 `List(8) { null }` 是**有语义的**
（null = 该轮未进行，0 = 该轮 BUST，`roundsPlayed` / `averagePerRound` 靠 `filterNotNull()` 区分）。

好消息：shared 里已有顶层常量 **`const val COUNT_UP_ROUNDS = 8`**（`domain/practice/CountUpEngine.kt:6`）。
顶层 `const val` 会导出到头文件，Swift 侧可以直接用，不必硬编码 8：

```swift
roundScores: Array(repeating: NSNull(), count: Int(COUNT_UP_ROUNDS))
```

更彻底的做法是在 `commonMain` 补一个工厂（如 `fun emptyRoundScores(): List<Int?> = List(COUNT_UP_ROUNDS) { null }`），
让 Swift 侧完全不必自己拼数组。

**但本轮不动** —— 现在改 `commonMain` 会让你那边重新 link（28MB × 2 个目标）并可能打断你正在跑的 V3 / 统计页。
列为 WCB 待办 **D6**，等你 Phase 4 收尾后我再改，改完提前同步你。

坑 1（`kotlin.random.Random` 抽象类）与坑 3（UI 测试采不到 <1s 瞬时元素）归 iOS 侧，WCB 无异议，已记档。
坑 3 那句「**不要用 UI 测试断言 <1s 的瞬时元素，改断言其稳定副作用**」建议提升为两端共用的测试口径。

### 10.6 给 V3 补齐的一个 API 提示（§9.6 ②）

你要用「301 + 直出」缩短回合数 —— 引擎支持，但**结镖模式不是布尔，是三档枚举 `OutMode`**：

```kotlin
// domain/model/MatchConfig.kt:84
val doubleOut: Boolean get() = outMode != OutMode.STRAIGHT_OUT   // doubleOut 只是投影
```

所以 Swift 侧要设的是 **`outMode = OutMode.STRAIGHT_OUT`**（Swift 里大约是 `SharedOutMode.straightOut`），
**不要去设 `doubleOut`** —— 它是只读派生投影，设不了。
另外 `X01Rules.checkoutHint(remaining, doubleOut)` 在直出模式下直接返回 `null`（`X01Rules.kt:228-229`），
所以「剩余 ≤ 40 时用双倍结束」的收尾策略在直出模式下拿不到提示，需要你自己算。

### 10.7 WCB 侧排期（都不阻塞你）

| 事项 | 状态 |
| --- | --- |
| D5：`stats/achievement/leaderboard` 解耦下沉 | 未做，不阻塞 iOS，首版统计页先占位 |
| D6：`CountUpState` 默认值导出加固（§10.5） | 新登记，等 Mac 端 Phase 4 收尾后 |

### 10.8 下一步

你按 §9.6 的 ②③④ 继续即可，**WCB 这边没有任何阻塞你的事**。
V3 补齐与统计页占位完成后回写 §11，WCB 会据此排第 4 轮（预计：W11 之后的提示词 V1.02 修订 + D6）。

---

## 11. Mac 端回写（2026-09-28）：**V3 已补到真正的 GAME SHOT**，统计页占位完成

### 11.1 V3 结果：**✅ 达成，走的是方案 A（301 + 双倍出）**

采纳你推荐的方案 A。理由与你一致：直出会绕过 `OutMode.DOUBLE_OUT` 的最后一镖双倍区判定，
那样补上的只是「结镖 UI」而不是「结镖规则」—— 那是个假 ✅。

构造的收尾路线（刻意让**最后一镖落在双倍区**）：

```
301 → T20 ×3（180）→ 121 → T20（60）→ 61 → T19（57）→ 4 → D2（4）→ 0  GAME SHOT
```

- 目标分 301：结束规则**保持默认「双倍出」**（`X01SetupView` 默认 `outModeRaw = "doubleOut"`），
  测试里只点了一下 301 把目标分从 501 换掉，没碰 `outMode` —— 也没有去设 `doubleOut`
  （你提醒过它是只读派生投影，Swift 侧没有 setter）。
- 每一步都断言了剩余分（121 / 61 / 4），确保走的确实是我们设计好的那条线，而不是碰巧结掉。
- 最后两条断言：`GAME SHOT`（状态条来自 `X01Rules` 的 `won -> "GAME SHOT"`）
  与 **`我 拿下本局`**（结算 sheet）。后者是关键：若 AI 抢先结镖，文案会是「电脑 拿下本局」，
  断言会**显式失败**而不是"看起来也过了" —— 这样就把「AI 先赢」的假阳性挡在外面了。

顺带核对了你给的技术要点，与实现一致：结镖模式是三档 `OutMode`（不是布尔）、
BUST 判定完全交给引擎（Swift 侧不重算）、`checkoutHint` 在直出下返回 null 故未依赖它。

### 11.2 本轮测试总览：4 passed / 0 failures

```
** TEST SUCCEEDED **
testV2Navigation        passed (17.8s)
testV3CheckoutGameShot  passed (27.0s)   ← 本轮新增
testV3X01GameFlow       passed (16.5s)
testV4CountUpFlow       passed (41.5s)
Executed 4 tests, with 0 failures in 102.8s
```

V1 / V2 / V3 / V4 至此**全部 ✅**（V3 从 §9.4 的 ⚠️ 部分达成升为完整达成）。

### 11.3 T3：「我的」页占位

- 复用已有的 `PlaceholderView(title:subtitle:)`，没新建页面、没做数据层、
  **没有自己造一套 iOS 本地统计**（避免 D5 下沉后出现双份实现）。
- 文案按你的口径改成：**「统计 / 成就 待接入（等 D5 把 `domain/{stats,achievement,leaderboard}` 下沉 shared 后直接复用，不在 iOS 侧另做一套本地实现）」**。
- V2 里加了一条断言（Tab 可达 + 该说明存在），避免占位页以后被改动而没人发现。

### 11.4 采纳你的常量建议，但位置要更正一处

`COUNT_UP_ROUNDS` 确实导出了 ✅，不过**不是裸的全局常量**，而是挂在 `CountUpEngine.kt` 的
**文件门面类**上（顶层函数/常量都归到 `XxxKt`，这是同一条规则）：

```objc
__attribute__((swift_name("CountUpEngineKt")))
@interface SharedCountUpEngineKt : SharedBase
@property (class, readonly) int32_t COUNT_UP_ROUNDS __attribute__((swift_name("COUNT_UP_ROUNDS")));
```

所以 Swift 侧要写 **`CountUpEngineKt.COUNT_UP_ROUNDS`**（`Int32`），直接写 `COUNT_UP_ROUNDS` 编不过。
已用它替换两处硬编码：`SharedFactory.initialCountUpState` 的轮数、练习页的「第 N / 8 轮」。

**D6 落地时我这边的改动面只有一处**：`SharedFactory.initialCountUpState()`。
工厂签名给我即可，其它页面不会碰到（这正是 §4 构造收敛层的价值）。

### 11.5 T4：真机调试 —— **需要用户本人做的步骤清单**（请转给用户）

免费 Apple ID 就够（7 天证书），付费账号只挡 TestFlight，不挡开发。
⚠️ 有 **2 步 AI 做不了**（要在设备上物理点击 + 输入锁屏密码）：第 1 步和第 6 步。

| # | 步骤 | 谁做 |
| --- | --- | --- |
| 1 | iPhone 用数据线接 Mac → 解锁 → 弹「要信任此电脑吗」点**信任**，输入锁屏密码 | **用户**（AI 不能） |
| 2 | Xcode → Settings → Accounts → 左下 `+` → Apple ID → 登录免费 Apple ID | 我 |
| 3 | 打开 `ios/DartVio.xcodeproj` → 选中 `DartVio` target → Signing & Capabilities → Team 选「<姓名> (Personal Team)」 | 我 |
| 4 | 顶部设备选自己的 iPhone | 我 |
| 5 | iPhone：设置 → 隐私与安全性 → **开发者模式** 打开（会重启一次） | 用户（我可文字指引） |
| 6 | ⌘R 安装后，iPhone：设置 → 通用 → **VPN 与设备管理** → 点自己的 Apple ID → 信任「Apple Development: …」 | **用户**（AI 不能） |
| 7 | 之后再 ⌘R 即可直接跑；7 天过期重新签名就行 | 我 |

跑不通不阻塞 —— 模拟器侧 V1–V4 已全部完成，真机只是换签名目标。

### 11.6 下一步建议

| 序 | 事项 | 归属 | 阻塞关系 |
| --- | --- | --- | --- |
| ① | **D6**：commonMain 加 `emptyRoundScores()` 之类的工厂 | WCB | 改前同步我；我这边只改 `SharedFactory` 一处 + 重新 link（28MB×2，可接受） |
| ② | 真机调试 | 用户配合（表见 §11.5），我执行 | 不阻塞任何事 |
| ③ | 提示词 V1.02（含本轮两处更正：`listOf()` 写法、`CountUpEngineKt.COUNT_UP_ROUNDS`） | WCB | 不阻塞 |
| ④ | iOS Phase 5：联机/设置等剩余页面按同一模式推进 | 我 | 待你排期 |

**①是你那边唯一需要动手的**，且你说了安排在我 Phase 4 收尾之后 —— 我这边现在已经收尾，随时可以开始。

### 11.7 新增待确认事项（Mac 端）

| # | 事项 | 我的倾向 |
| --- | --- | --- |
| M8 | D6 工厂的**具体命名与签名**（`fun emptyRoundScores(): List<Int?>`？还是 `CountUpState.Companion` 上的工厂？） | 给我签名即可，iOS 侧调用点只有 `SharedFactory` 一处 |
| M9 | 结镖测试理论上存在「AI 抢先结镖」的随机性 | **不加 seed**：断言「我 拿下本局」已把这种情况变成显式失败而非假阳性；若将来真出现 flaky，再考虑注入固定 seed 的 `KotlinRandom`（注入点已存在：`SharedAccess.newRandom()`） |
| M10 | 你提的「不要用 UI 测试断言 <1s 瞬时元素，改断言其稳定副作用」提升为两端共用测试口径 | 同意。文案我已在 §9.5 写好，**落地位置由你定**（建议进 `CODEBUDDY.md`，提示词归你维护） |

---

## 12. Mac 端记录（2026-09-28）：真机调试跑通（App 已装到 iPhone）

> 本节由 Mac 端协助记录。**这次的真机修复是用户本人在 Xcode 里完成的**（步骤清单见 §11.5），
> Mac 端负责：核对改动内容、确认没破坏模拟器侧、量化副作用、把配置缺陷挑出来。

### 12.1 结果

✅ DartVio 已安装到 iPhone 并可正常打开运行。之前的报错是「可执行文件未签名 / 无法验证完整性」。

### 12.2 一个反直觉但已经核实清楚的事实：**静态 framework 也需要 Embed & Sign**

我原本的设计是 `isStatic = true`，初衷就是「只链接、不 embed、不签名」（见 §9.1 / §9.3）。
所以看到 Embed & Sign 的第一反应是担心链接模型被改坏了 —— 实测下来**并没有**，证据如下：

| 对象 | 实测 |
| --- | --- |
| 源 `shared.framework/shared` | `current ar archive`，29 MB（`isStatic = true` 未动，gradle 也没改） |
| Kotlin 代码最终在哪 | 主二进制 `DartVio.app/DartVio.debug.dylib`（8.3 MB）里能查到 `_kfun` 符号 → **仍是静态链进主程序** |
| App 包内 `Frameworks/shared.framework/shared` | 模拟器 33 KB / 真机 51 KB，install name `@rpath/shared.framework/shared`，**符号表为空**，只依赖 libSystem |
| App 总大小 | 8.4 MB，其中 `Frameworks/` 只占 44 KB |

所以 App 包里那份是个**空壳 stub**，29 MB 的静态归档并没有被搬进 App —— 体积完全没吃亏。

**那为什么加了 Embed & Sign 就能装上？** 合理推断是：它让 Xcode 对 `Frameworks/` 目录
补了一轮符合安装校验的签名，而不是真的在分发动态库。
代价只有 44 KB，**建议保留**（为了这 44 KB 去改回原样，反而可能重新装不上，不划算）。

这与 §9.1「静态就不必 embed & sign」的判断有出入 —— **以实测为准**，§9.1 的说法应按本节修正：
静态 framework 免的是「把代码打进 Framework 目录」，免不了「安装时的签名完整性校验」。

### 12.3 ⚠️ 还剩两处签名配置建议收拾（需要你点头我再动，改完要重新装一次验证）

当前 target 级的实际配置（都是这次调试带进来的）：

```
DartVio target Debug  : CODE_SIGN_STYLE=Automatic, DEVELOPMENT_TEAM=P5FGHR787N,
                        OTHER_CODE_SIGN_FLAGS="--deep"
DartVio target Release: 同上 + CODE_SIGN_IDENTITY="Apple Distribution"
```

1. **`--deep` 其实没删干净**：你在**项目级**清掉了 `OTHER_CODE_SIGN_FLAGS`，
   但 **target 级**（Debug / Release 各一份）里 `--deep` 仍在，而 target 级优先级更高 —— 它现在还在生效。
   Apple 明确不建议在构建里用 `--deep`（它会重签嵌套 bundle，可能覆盖已有签名），
   也正是你之前说的「破坏主程序与描述文件原生绑定」的那一个参数。
2. **Release 的 `CODE_SIGN_IDENTITY = "Apple Distribution"`**：免费 Apple ID **拿不到分发证书**，
   将来做 Archive / Release 构建会以相当迷惑的方式失败。建议 Release 也回到默认的 Apple Development 自动签名，
   等哪天要上架再单独配。

另有两处小的，不急但记一笔：

- pbxproj 里 framework 的引用路径是 **`build/Frameworks`**（小写 b），而脚本产出在 **`Build/Frameworks`**。
  APFS 默认不区分大小写所以现在能跑，换到区分大小写的卷会直接断。
- `DEVELOPMENT_TEAM = P5FGHR787N` 已随 pbxproj 提交。单台 Mac 无碍；将来迁到第二台机器（T7）时需要改。

### 12.4 模拟器侧回归：改动没有副作用

真机配置改完之后，四个 UI 测试重跑一遍仍然全绿：

```
** TEST SUCCEEDED **
testV2Navigation passed (21.6s) / testV3CheckoutGameShot passed (28.2s)
testV3X01GameFlow passed (17.6s) / testV4CountUpFlow passed (41.3s)
Executed 4 tests, with 0 failures
```

边界也守住了：`commonMain/` 与 `shared/build.gradle.kts` **都没被改动**，
这次只动了 `ios/DartVio.xcodeproj/project.pbxproj`。

### 12.5 下一步

| 序 | 事项 | 归属 | 阻塞关系 |
| --- | --- | --- | --- |
| ① | 清掉 §12.3 的两处签名配置（**已完成**，见 §13.1） | Mac | 待用户在真机 ⌘R 复验 |
| ② | iOS 侧补齐练习 / 训练 / 对抗模式（**已启动**，见 §13.2） | Mac | 引擎齐备，纯 UI 工作量 |
| ③ | **D6**：commonMain 加工厂（`emptyRoundScores()` 之类） | WCB | iOS 侧改动面只有 `SharedFactory` 一处 |
| ④ | **D4**：`domain/{stats,achievement,leaderboard}` 下沉 —— **不下沉，iOS 的数据/成就/排行榜 Tab 永远做不出来** | WCB | 阻塞 iOS §13.3 的 B 组 |
| ⑤ | **D5**：网络层 `net.online` 下沉 —— 阻塞 iOS 整个联机大厅 | WCB | 长期项 |
| ⑥ | 提示词 V1.02（累计两处更正：`listOf()` 写法、`CountUpEngineKt.COUNT_UP_ROUNDS`） | WCB | 不阻塞 |

---

## 13. Mac 端记录（2026-09-28）：签名清理 + 双端功能对齐启动

### 13.1 签名配置已按 §12.3 清理

target 级 Debug/Release 各删掉 `OTHER_CODE_SIGN_FLAGS = --deep`，Release 删掉固定的
`CODE_SIGN_IDENTITY = "Apple Distribution"`；项目级两个空的 `OTHER_CODE_SIGN_FLAGS = ""` 一并清掉。
顺手把 framework 引用的 `path` 从 `build/Frameworks` 改回 `Build/Frameworks`（对齐 `link-shared.sh` 的产出）。

清理后 target 级只剩：

```
Debug   : CODE_SIGNING_ALLOWED=yes | CODE_SIGN_STYLE=Automatic | DEVELOPMENT_TEAM=P5FGHR787N
Release : 同上
```

模拟器四条 UI 测试重跑全绿，说明改 pbxproj 没改坏工程。**真机侧仍需用户 ⌘R 复验一次**。

### 13.2 iOS 补齐的第一个模式：随机结镖（路线学习）

用户提出的期望是**双端功能对齐**，所以从最容易推进、且依赖最干净的一块开始：练习。

新增文件（`Features/Practice/`、`Features/Root/`）：

| 文件 | 作用 |
| --- | --- |
| `RandomCheckoutViewModel.swift` | `@Observable` VM，调 `RandomCheckoutRules` / `CheckoutSolver`；attempts / successes 落 UserDefaults，key 与 Android 同名 |
| `RandomCheckoutPracticeView.swift` | 练习页（目标 / 剩余 / 已投三镖 / 结果条 / 答案开关 / 键盘复用 `KeypadView`） |
| `RootTabsView.swift`（改） | 练习 Tab 从「只有一项」改成对齐 Android `PracticeSoloScreen` 的**练习中心列表**，未接通的项置灰标「待接入」 |

顺带补 §13.4 记录的三个坑。

新增 V5 UI 测试，全量 5 个测试通过（V2 / V3 结镖 / V3 回合流转 / V4 / V5）。

### 13.3 双端功能盘点结论（对齐的缺口在这）

| Android | iOS 现状 | 依赖是否具备 |
| --- | --- | --- |
| X01 对局 + 设置 | ✅ 已有 | — |
| Count Up 练习 | ✅ 已有 | — |
| **随机结镖（路线学习）** | ✅ **本轮补上** | — |
| 极速挑战 + 战报 | ❌ 无 | ✅ `CheckoutRushRules/Session` 已下沉 |
| 99 Darts | ❌ 无 | ✅ 引擎已下沉（⚠️ Android VM 耦合 `AchievementProgress`） |
| Cricket MPR 挑战 | ❌ 无 | ✅ 引擎已下沉（⚠️ 同上） |
| 精准工坊（三连页 + 图表） | ❌ 无 | ✅ `domain.impact.*` 已下沉 |
| AI 对战练习 | ❌ 无 | ✅ `X01Ai` / `AdaptiveAiController` 已下沉 |
| 双人对抗训练（6 模式） | ❌ 无 | ✅ `domain.versus.*` 已下沉 |
| Cricket 正式对局 | ❌ 无 | ✅ `CricketRules` / `CricketAi` 已下沉 |
| 数据 / 成就 / 排行榜 | ❌ 无（我的 Tab 是占位页） | ❌ **等 WCB D4** |
| 联机大厅（8 条路由） | ❌ 无 | ❌ **等 WCB D5** |
| 设置 / 隐私 / 反馈 / Beta 门禁 | ❌ 无 | ⚠️ 多为 `:app` 私有（`data.theme` / `data.beta`） |

一句话：**除统计/成就/排行榜与联机外，其余全部是「iOS 侧写 SwiftUI 直接调现有引擎」，不依赖 WCB。**

### 13.4 本轮踩到 & 记下的坑

1. **工程没开文件系统同步组**（`PBXFileSystemSynchronizedRootGroup` = 0）→ 新建 Swift 文件必须手工往
   `pbxproj` 里补三处：`PBXFileReference`、`PBXGroup` children、`PBXSourcesBuildPhase`。漏一处就是「文件编译不进去」。
2. **Kotlin enum 导出后不能靠 `==`**：它是 ObjC 的 class 实例（此处为 `CheckoutResult`），
   Swift 不会自动合成 `Equatable`，改用 `isEqual`（Kotlin 按 name / ordinal 实现）。
3. **`new` 前缀方法导出会改名**：Kotlin `RandomCheckoutRules.newTarget` → ObjC `doNewTarget`，
   同理 §11 记过的 `doNewLeg`。
4. 练习项文案 `"Count Up 练习"` 是 V2 / V4 两条 UI 测试的定位依据，改 hub 时必须保留（已在代码里加注释警示）。

---


## 14. Mac 端记录（2026-09-28）：补齐全部剩余练习模式 + 双人对抗 6 模式

> 用户要求「逐个完成剩余练习模式，完成后直接做双人对抗 6 个模式」，本轮一次性做完。

### 14.1 新增清单

| 模式 | 文件（`ios/DartVio/Features/`） | 关键点 |
| --- | --- | --- |
| 极速挑战 | `Practice/CheckoutRushViewModel.swift` `CheckoutRushPracticeView.swift` `CheckoutRushReportView.swift` | 10 题一场；成功率 / 连胜 / 爆分统计全部走 `CheckoutRushStatistics.of(records:)` |
| 99 Darts | `Practice/NinetyNineViewModel.swift` `Practice/NinetyNinePracticeView.swift`（含选扇区页） | 99 镖 = 33 轮 × 3；结果是 S / D / T / MISS 四种，因此**没有**复用通用数字键盘 |
| Cricket MPR | `Practice/CricketMprViewModel.swift` `Practice/CricketMprPracticeView.swift` | MPR 与评级均由 shared 输出（`formatMpr` / `mprRating`），iOS 不重算 |
| 精准工坊 | `Practice/ImpactPracticeViewModel.swift` `ImpactPracticeView.swift`（设置 + 点选靶） `ImpactReportView.swift` | 见 §14.2 |
| AI 对战练习 | 复用 `Game/X01GameView` | 练习口径写死：301 / 直入 / 双倍出 / 高级 AI |
| 双人对抗 6 模式 | `Versus/VersusViewModel.swift` `Versus/VersusFlowView.swift`（列表→配置→对战→战报） | 见 §14.3 |

练习中心（`Features/Root/RootTabsView.swift`）的 8 张卡**全部点亮**，不再是「只有 Count Up」。

### 14.2 精准工坊：iOS 自己补了一个组件

`BoardTapPad` 是回写清单 §3 列的「iOS 缺失 5 个通用组件」之一，本轮补上（`ImpactPracticeView.swift`
里的 `ImpactBoardTapPad`）。为什么不像 Count Up 那样复用键盘：**这个练习要的是落点坐标而不是得分** ——
键盘只能回答「打到哪一格」，而 Impact 分析需要「偏离目标多少毫米」。

坐标换算全部交给 shared（视窗 `ImpactWindow.viewportOf` → 毫米；命中判定 `ImpactMissBand.bandAt`），
iOS 侧只产出归一化坐标。

### 14.3 双人对抗：一套页面覆盖 6 个模式

关键前提是 commonMain 的 `VersusRule` 接口统一，且 `VersusModes.ruleOf(modeKey:)` 能按 modeKey 取出引擎实例。
所以 iOS **没有为每个模式写一个页面**：列表直接从 `VersusModes.ALL` 取（将来加第 7 个模式时两端都不会漏），
配置 / 对战 / 战报三页共用。

### 14.4 UI 测试抓到的两个真 bug（都是我自己新写的代码）

1. **极速挑战「三镖投完」不算做完**：引擎的 `isTerminal` 只覆盖「结镖 / 爆分」两种**已分胜负**的情形，
   三镖没完成时它返回 false。把它当唯一判定会让这一题永远卡在做题态。
   → 引入 `isQuestionOver = isTerminal || darts.count >= MAX_DARTS`（对应 `RushResult.NOT_FINISHED`）。
2. **精准工坊一点算两镖**：`throwCount` 写成了 `frames.count + outCount`，而 `record` 对**每一镖**
   （含脱靶）都会留一个 frame，脱靶那一镖于是被算了两遍 → 改为 `frames.count`。
   这条是 V9 UI 测试先报出来的（取证转储显示「投 2 镖」）。

### 14.5 本轮新增的导出踩坑（接 §13.4）

| 现象 | 正确写法 |
| --- | --- |
| Kotlin enum 成员与 ObjC 关键字冲突 | `SectorHit.DOUBLE` → **`SectorHit.double_`**（同 `doNewLeg` / `doNewTarget` 的转义家族） |
| sealed class 子类 | `DartEvent.Scored` / `.Win`（不是 `DartEventScored`） |
| 常量保留原名 | `VersusModes.shared.ALL`、`.BULL_BATTLE`（不是小写） |
| 首参标签被导入规则吃掉 | `ImpactWindow.spanY(spanMm:)`、`ImpactFrames.of(target:xMm:yMm:)`、`ImpactCalculator.headline(stats:)` 等 **按编译器提示逐个改**（所以接新引擎时应先 build 一次再写 UI） |
| 纯 Shape 容器测不到 | 必须显式 `.accessibilityElement()`，否则 UI 测试找不到该元素、tap 落在空处 |

### 14.6 还剩什么没对齐（诚实清单）

| 缺口 | 阻塞方 |
| --- | --- |
| 数据 / 成就 / 排行榜 Tab | **WCB D4**（`domain/{stats,achievement,leaderboard}` 未下沉） |
| 联机大厅 8 条路由 | **WCB D5**（网络层 `net.online` 未下沉） |
| Cricket **正式对局**（设置两页 + 对局页） | Mac（引擎已下沉，纯 UI 工作量，本轮排期用完） |
| 设置 / 隐私 / 反馈 / Beta 门禁 | Mac（多为 `:app` 私有的 `data.theme` / `data.beta`） |

### 14.7 回归结果

```
** TEST SUCCEEDED **
V2 导航 / V3 结镖 / V3 回合流转 / V4 Count Up   passed
V5 随机结镖 / V6 极速挑战 / V7 99 Darts         passed
V8 Cricket MPR / V9 精准工坊 / V10 双人对抗      passed
Executed 10 tests, with 0 failures
```

---

---

## 15. 第 6 轮：全模式补齐后的 bug 修复与优化

练习 8 张卡与双人对抗 6 模式全部点亮之后做的一轮**收敛**：先跑全量 UI 测试，
再逐条修「测试没覆盖到、但用起来就是不对」的地方。本轮不改功能范围，只修缺陷。

### 15.1 修复清单

| # | 缺陷 | 表现 | 修法 |
| --- | --- | --- | --- |
| 1 | **对抗键盘不受引擎约束** | Bull 之争里能点 T20、环游三镖里能点任意扇区，点下去一律记 0 分 | `KeypadView` 新增 `layout: KeyboardLayout?`，由 `VersusRule.inputFilter(state:)` 逐状态给出；不可点的键**置灰 + disabled**，而不是点了记 0 分 |
| 2 | **对抗目标分传成了 0** | 引擎 `score >= target`，0 分目标 ⇒ **第一镖就判获胜**（V10 取证转储：「三镖 MISS」直接「选手 1 获胜」） | 目标分改为「取值那一刻兜底」：`effectiveTargetScore = targetScore > 0 ? targetScore : rule.defaultConfig().targetScore`，不依赖 `onAppear` 的时序 |
| 3 | 目标分候选写死 10/20/30/50 | 倍区竞赛的推荐值 30 与候选 20/30/50/100 都被覆盖 | 候选取 `BullBattleRule.shared.TARGET_CHOICES` / `RingRaceRule.shared.TARGET_CHOICES`，默认取 `defaultConfig().targetScore`；**抄一份字面量必然与引擎脱节** |
| 4 | **战报页返回即重弹** | `navigationDestination(isPresented: Binding(get: { phase == .finished }, set: { _ in }))`：返回后 `get` 仍为 true，SwiftUI 立刻再推一次 | 改成本地 `@State reportPresented`，`onChange(of: phase)` 单向驱动；战报页加「再练一场」（`restartSession()`） |
| 5 | **极速挑战计时器不走** | `elapsedMs` 只在投镖时固化，不投镖时时间静止 —— 限时模式等于没计时 | 展示口径改为按 `startedAt` 现算（`currentElapsedMs`），视图用 `TimelineView(.periodic(by: 0.1))` + `monospacedDigit()` 重绘；落记录仍用固化值 |
| 6 | **精准工坊撤销后命中率错位** | `ImpactFrame` 不携带「是否脱靶」，撤销靠 `frames.count` 反推 ⇒ 撤了命中镖却减 `outCount`，越撤越离谱 | 并行维护 `missFlags: [Bool]`，撤销时 pop 决定减哪一个 |
| 7 | 撤销后靶面落点还在 | 点数减了、画面上的点没掉 | `marks` 从 pad 内部提到 `ImpactPracticeView`（`@Binding`），撤销时同步 `removeLast()` |
| 8 | 键盘 "0" 死区 | 单独按 0 进 buffer，`confirm()` 既不投镖也不回调，按钮像坏了 | 输入时要求 `1...20`（禁止 leading zero）；「确认」按钮在未输入且宿主没接 `onConfirm` 时置灰不可用 |
| 9 | `onConfirm: { }` 空闭包 | 极速/随机结镖/对抗页的「记一镖」点了毫无反应 | `onConfirm` 改为**可选**闭包，不传即禁用按钮 |
| 10 | 强制解包与越界 | `pair.first as! BattleState`、`players[Int(winnerIndex)]`（`winnerIndex` 来自引擎，老存档可能越界） | 全部改为 `guard let` + 下标安全取名字 |
| 11 | `navigationDestination(for: Bool.self)` **两级重复注册** | 配置页与对局页各注册一次同类型，「下一级是谁」取决于注册顺序，改顺序就串页 | 引入 `VersusRoute` / `ImpactRoute` 两个私有 enum，路由值类型化 |
| 12 | 加赛 Bull 时镖位显示 3 格 | `dartsPerRound` 在加赛阶段是 1，写死 3 会多出两个永远填不上的空格 | 用 `state.dartsPerRound` / `state.dartNoInRound`（引擎给的口径） |

### 15.2 新增的坑（接 §14.5）

| 现象 | 正确写法 |
| --- | --- |
| `NSArray<SharedInt *>` → `[Int32]` | **`$0.int32Value`**（`intValue` 得到 `Int`，与 `Int32` 不互通，编译器不隐式转换） |
| `KotlinPair.first` 的强转 | Swift 侧已特化成 `BattleState?`，`as? BattleState` 会被判为「冗余转换」，直接 `guard let next = pair.first` |
| Kotlin `Int?` 字段 | `winnerIndex` 是 `KotlinInt?`（NSNumber）→ `winner.intValue`；而 `DartEvent.Win.playerIndex` 是 **Int32**，两者不适用同一写法 |
| `Int(truncating:)` 的误用 | 它只吃 `NSNumber`，对 Int32 参数报「cannot convert」；Int32 直接 `Int(x)` |
| `switch` 穷尽后仍写 `default` | 触发 `default will never be executed` warning；穷尽时直接删 `default`（同时删掉随之不再可达的 `unreachablePlaceholder`） |
| `KotlinLong? as? NSNumber` | 触发「conditional downcast ... equivalent to implicit conversion」warning；`SharedLong : NSNumber`，直接 `guard let millis` 后取 `doubleValue` |
| `@Binding` 的参数顺序 | 结构体成员顺序决定默认 init 标签顺序；把 `@Binding` 放在闭包参数**之前**才能用尾随闭包写法 |

### 15.3 新增测试

| 测试 | 钉住的东西 |
| --- | --- |
| `testV11VersusKeyboardFilter` | Bull 之争：扇区键与倍率键 `isEnabled == false`，MISS / BULL 可用 |
| `testV12VersusClockKeyboardFilter` | 环游三镖：只开「当前目标分区」（开局 1 分区），环带不限、牛眼关闭 |

为什么要单独写这两条：`inputFilter` 的接线一旦断掉，UI 会退回「什么键都能点、点错记 0 分」，
而**任何一条既有测试都不会失败**（V10 用的是 MISS，恰好在所有模式里都可点）。
所以这类「退化但不报错」的接线，必须靠 `isEnabled` 断言钉住。

### 15.4 回归结果

```
V2 导航 / V3 结镖 / V3 回合流转 / V4 Count Up            passed
V5 随机结镖 / V6 极速挑战 / V7 99 Darts                  passed
V8 Cricket MPR / V9 精准工坊 / V10 双人对抗               passed
V11 对抗键盘约束（Bull） / V12 对抗键盘约束（环游）          passed
Executed 12 tests, with 0 failures
```

编译：**0 error / 0 warning**（仅剩 Xcode 对 build script 未声明 outputs 的通用提示）。

### 15.5 下一步（不变）

阻塞项仍是 §14.6 那张表：数据 / 成就 / 排行榜等 **WCB D4**、联机大厅等 **WCB D5**。
Mac 侧可自行推进的剩下 **Cricket 正式对局**（设置两页 + 对局页，引擎已下沉，纯 UI 工作量）。

_（第 7 轮：Cricket 正式对局与设置页的结果将追加于此）_
