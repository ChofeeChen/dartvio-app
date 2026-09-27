# dartvio-app

DartVio Dual-platform app(Android+iOS)

飞镖计分 App：本地 2 人比赛、在线对战、AI 对手、练习模式、统计分析。
Android 已发 Beta（v0.1.17），iOS 待启动；两端共享核心走 Kotlin Multiplatform。

## 目录

| 目录 | 内容 |
| --- | --- |
| `android/` | Android 工程（Kotlin + Jetpack Compose，minSdk 24）；其下 `shared/` 是 KMP 共享模块 |
| `android/shared/` | ✅ KMP 共享核心：规则 / 计分 / 对战 / 练习 / 房间 / 模型（纯 Kotlin，两端共用） |
| `ios/` | 待建：Xcode 工程（Swift + SwiftUI） |
| `docs/prd/` | 现行 PRD（Markdown，唯一现行版） |
| `docs/archive/` | 归档材料，不入库 |
| `DartVio APK发布包/` | 对外 Beta 包与版本清单（APK 不入库） |

## 构建（Android）

命令行构建需先把 `JAVA_HOME` 指到 Android Studio 自带 JDK（JDK 25），否则工具链解析失败：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat testDebugUnitTest      # 单测：64 文件 / 650 例 / 0 fail
.\gradlew.bat assembleBeta           # 对外 Beta 包（真签名 + 不可调试）
```

`keystore.properties` / `local.properties` 不入库，参考同目录的 `.example` 模板填写。

## 版本号

唯一出处：`android/app/build.gradle.kts` 的 `appVersionCode` / `appVersionName`，`versionName = 0.1.<versionCode>`，只增不减。
iOS 共用同一套 versionCode（`CFBundleShortVersionString = 0.1.<code>`）。

## 双机分工

Windows = Android 开发与出包（签名密钥仅存 Windows）；MacBook = iOS 开发与出包（Apple 证书仅存 Mac）。
敏感文件与签名材料一律不入库，仓库只放 `.example` 模板。

## License

MIT — 见 [LICENSE](LICENSE)。
