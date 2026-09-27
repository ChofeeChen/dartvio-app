plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties

/*
 * **版本号的唯一出处**（2026-09-26 确立的发布纪律，详见 `2.1 DartVio APK发布包/APK版本清单.md`）。
 *
 * 为什么把这两个数提到脚本最上面：它们同时被 `defaultConfig`（打进包内）、
 * APK 文件名（发布目录里的那个文件）、以及更新台账三处引用。各写一遍的版本号，
 * 迟早会在某一次发布里三者不一致 —— 而三者不一致时，用户报上来的版本号就没有意义了。
 */
val appVersionCode = 18
val appVersionName = "0.1.18"

/*
 * Beta 试用包的签名材料放在仓库根的 `keystore.properties`（密钥库本体在 `keystore/`）。
 * 换正式密钥时只改这一个文件即可，口令不进构建脚本。
 * 文件缺失时回退 debug 签名 —— 保证 clone 下来就能构建，不会因为缺密钥而卡住。
 */
val betaKeystorePropsFile = rootProject.file("keystore.properties")
val betaKeystoreProps = Properties().apply {
    if (betaKeystorePropsFile.exists()) {
        betaKeystorePropsFile.inputStream().use { load(it) }
    }
}

/*
 * 匿名使用统计的后端地址放在仓库根的 `local.properties`，**不进版本库**。
 * 与 `keystore.properties` 同一套做法：文件缺失时取空串，代码里靠空串判定
 * 「没配后端」并静默关闭上报 —— 保证 clone 下来就能构建，也保证没人能
 * 从仓库里翻出我的 anon key。
 *
 *   SUPABASE_URL=https://xxxx.supabase.co
 *   SUPABASE_ANON_KEY=eyJhbGci...
 *   dartvio.telemetry.debug=true   # 可选：让 debug 包也上报（默认关，避免开发数据污染）
 */
val telemetryPropsFile = rootProject.file("local.properties")
val telemetryProps = Properties().apply {
    if (telemetryPropsFile.exists()) {
        telemetryPropsFile.inputStream().use { load(it) }
    }
}
val telemetryUrl = (telemetryProps["SUPABASE_URL"] as String?).orEmpty()
val telemetryKey = (telemetryProps["SUPABASE_ANON_KEY"] as String?).orEmpty()
val telemetryOnDebug = (telemetryProps["dartvio.telemetry.debug"] as String?).orEmpty() == "true"

// 在线对战后端：**单独一份配置**，不再与统计共用。
// 2026-09-26：国内网络到 Supabase 的 TLS 握手被重置，联机改用自建后端（蜂窝可达）；
// 统计仍留在 Supabase。两边地址不同、钥匙也不同，各自换各自的，互不牵连。
// 没单独配时回退到统计那套，保证只配一份也能构建。
val onlineUrl = (telemetryProps["ONLINE_URL"] as String?)
    .takeUnless { it.isNullOrBlank() } ?: telemetryUrl
val onlineKey = (telemetryProps["ONLINE_ANON_KEY"] as String?)
    .takeUnless { it.isNullOrBlank() } ?: telemetryKey

// 全局启用 Compose Material3 实验性 API（TopAppBar / ModalBottomSheet 等），
// 避免每处调用都需要单独 @OptIn 标注。
kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

android {
    namespace = "com.dartvio.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
    applicationId = "com.dartvio.app"
    minSdk = 24
    targetSdk = 37
    // v14（2026-09-27）：★分清「房间名 / 玩家昵称 / 实力」三件事（真机反馈：概念混乱）——
    //   ①**昵称只有一份真源**：删掉 `PlayerProfileStore` 自己存的那份副本（此前「我的」页改名字
    //     只写档案，房间仍读旧副本 → 自己手机上叫「玩家xxxx」、别人看到的却是「茄子」）；
    //     「我的」页保存后统一走 `hydrateLocalUser` 同步。
    //   ②**大厅房间卡加「房主：昵称」**（此前只有房间名 + PPR，陌生人只能从房间名里猜是谁开的）；
    //     等候页顶栏把「这一局叫什么」与「谁开的」分成两行写。
    //   ③**PPR 打通到每个人**：加入者的 PPR 随 `member_joined` 事件下发（老端默认 0 读成「没有」，
    //     不显示 0.0 —— 那会被读成「他很菜」）；房主那份沿用索引行 `host_ppr`；
    //     等候页成员行与**对局页玩家卡**（X01 转播惯例：已赢局数 + PPR）都显示。
    //     新增 `RoomNamePprTest` 4 例钉住三者的分工与老端兼容。
    // v13（2026-09-27）：★联机房间退出治理 + 对局页读数改造 + 对抗练习三模式开卡 ——
    //   ①**退出治理**：任一方（房主或客人）退出房间后，房间**继续存活 30 秒**（误触返回还能
    //     点回来接着打），到期后置「已结束」并从大厅移除；此前的表现是
    //     「房主离开留下已结束的同名卡、客人离开留下等候中 1/2 人的死房间，点进去只有回合记录、
    //      没有任何下一步」。对局页的返回（含系统返回键）现在真的会通知服务端；
    //   ②**对局页**：去掉「实时更新」；本机玩家昵称右侧标注（本机）；玩家卡片上方加一行
    //     `LEG x · R x · SI-DO`（观战视角最右侧挂灰眼睛）；回合记录表头五个字段横向对齐、
    //     字号对齐大厅房间名、列居中，格内分数去粗体并降 20%，卡片换表格线、底色弱化
    //     （同样高度多放近一半回合），结镖轮得分绿色；
    //   ③**对抗练习**：双倍环游 / 上海争霸 / 减半挑战三张卡开卡（引擎早已有，缺的是入口），
    //     列表去掉「两人轮流投镖……」总起句与「热身 / 专项 / 压力」分组标题（那是内部分档，
    //     不是玩家语言），设置页把「玩家1 / 玩家2」输入框换成 X01 同款的
    //     「真人 vs 真人 / 真人 vs AI + 四选一对手」，「开始对抗」从列表末尾移到底部固定。
    //     新增 8 条规则单测（三个新模式的推进 / 收尾 / 秒杀 / 减半 + 注册表反查）。
    // v12（2026-09-26）：★反馈闭环 —— 放弃「第几位体验用户」这种排队序号（只能炫耀一次，且会把
    //   内测变成抢位游戏），改用两件事给早期体验者身份认同：
    //   ①「我的」页加「早期体验者」徽章（仅 beta 变体，正式版人人可得就不值钱了）；
    //   ②新增「我的 → 我的反馈」二级页 —— 左「你已提交 N 条」（由用户自己确认才 +1：
    //     我们不代发反馈、也收不到内容，所以**猜不得**），右「已采纳 M 条」+ 采纳清单
    //     （每条写明落地在哪一版，可当场核对）。正文沿用《Beta体验反馈-模板.md》的 9 个字段，
    //     开发系统分享面板，发到微信还是邮件由用户决定。
    // v11（2026-09-26）：Beta 分发合规三件套 ——
    //   ①**首启隐私说明**：冷启动先让用户对隐私政策做一次明确选择；选择（含拒绝）会被记住，
    //     政策版本升级后重新征求同意；「不同意」只关闭匿名使用统计，功能一个不少
    //     （`UsageReporter.reportOnce` 之前多一道同意门，未经同意一个包都不发）；
    //   ②**试用期**：到期后整页告知（不清任何本地数据），到期前 7 天顶部挂剩余天数提醒；
    //   ③**邀请码后台校验**：自建后端新增 `dartvio_invites` 台账 + `redeem_invite` RPC
    //     （发放规则留在服务端，客户端只能问不能改），后台批准后才放行；
    //     后台不通时才退回包内白名单并记「待补验」，下次联网静默补验。
    //   同时：数据页新增「示例」开关（趋势折线 / 分布柱状 / 占比条三种画法），
    //   大厅空态删去「官方擂台」那两行。
    // v10（2026-09-25）：★蜂窝在线「始终显示连接中」修复 ——
        //   ①Realtime 两条 WS 流加握手超时（10s）与建连超时：此前 TLS/Upgrade 被中间设备挂住时
        //     `webSocketSession` 永不返回，状态永远停在 CONNECTING，既不报错也不重连；
        //   ②新增 `OnlineRoomApi.ping()` + 云端探活循环，`OnlineRoomRepository.status` 不再直接照搬
        //     WS 状态，而是「WS 活 → 已连接；REST 通 → 可用·同步较慢（POLLING_ONLY）；都叫不应 → 重连中」；
        //     （联机入口页此前根本没有在跑的请求，那行状态纯粹是个初始值）
        // v9（2026-09-25）：比赛大厅按 Dartsmind 口径重构 —— 菜单行（创建比赛 + 筛选按钮）、
        //   房间卡片重排（房名/规则摘要 + 右上「加入比赛」/观战块 + 成员与空位格子）、可加入的房间置顶；
        //   大厅与等候室顶栏补状态栏避让，全局返回符号统一。
        // v8（2026-09-25）：★LAN 联机死锁修复 —— DartVioClient 的读循环此前在握手成功后才启动，
        //   hello 的回包无人分发，任何客户端（含房主自己的 UI client）都会 4s 超时循环十次进 FAILED。
        //   现读循环先于握手启动。新增 ClientHandshakeEndToEndTest 真端到端回归（JVM 起真服务）。
        // v7（2026-09-24）：图标素材换成 DartVio-logo_002（各密度前景/方形/圆形重新生成）。
        // v6（2026-09-24）：联机对局页六项整改 —— 玩家卡左右排布（大号白字当前分）、
        //   回合记录改双栏对照记分表、确认键预览与玩家卡实时扣减本回合已确认的镖
        //   （结镖回合靠它判断能否一镖收尾）、已投镖槽位不再变红、顶栏加状态栏避让、键盘缩小并与卡片对齐。
        // v5（2026-09-23）：App 图标替换为 DartVio logo（adaptive 前景 + 各密度位图，模板图标退役）。
        // v4（2026-09-23）：联机真机双机 bug 修复 —— Realtime 推送按现行 `postgres_changes`
        //   格式解析（旧代码只认已停推的 `insert`，导致房主端永远看不到客人加入），
        //   并给房间事件订阅补上 3 秒增量轮询兜底。
        // v3（2026-09-21）：训练中心 UI 逐页审计整改（对抗练习对局页重做 + 精准工坊图框锁比 + 战报按钮钉底）。
        // ★发版纪律：versionCode **必须递增**（朋友覆盖安装旧版的前提）；
        //   versionName 末位跟 versionCode 对齐（0.1.<code>），这样包内显示的版本、
        //   遥测的 app_version 字段、APK 文件名里的版本号三者永远对得上 ——
        //   收到「某个版本的反馈」时能立刻定位到具体构建。
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 匿名统计后端（空串 = 未配置 = 一个请求都不发）。变体无关，故放 defaultConfig。
        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
        buildConfigField("String", "TELEMETRY_ANON_KEY", "\"$telemetryKey\"")

        // 在线对战后端：与统计共用同一个 Supabase 项目，但**字段独立** ——
        // 将来只换其中一个后端时，两边不该被绑着一起改。
        // （2026-09-26 起联机走 ONLINE_URL 指向的自建后端，统计仍在 Supabase。）
        buildConfigField("String", "SUPABASE_URL", "\"$onlineUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$onlineKey\"")
    }

    signingConfigs {
        // 「beta」：对外试用包的签名。以 AGP 自带的 debug 配置打底，
        // 有 keystore.properties 就换成真密钥库（否则沿用 debug 签名）。
        create("beta") {
            initWith(getByName("debug"))
            if (betaKeystorePropsFile.exists()) {
                storeFile = rootProject.file(betaKeystoreProps["storeFile"] as String)
                storePassword = betaKeystoreProps["storePassword"] as String
                keyAlias = betaKeystoreProps["keyAlias"] as String
                keyPassword = betaKeystoreProps["keyPassword"] as String
            }
        }
    }

    testOptions {
        unitTests {
            // 让 android.jar 的 stub 方法返回默认值而不是抛 "not mocked"：
            // DartVioClient 的端到端握手测试要在 JVM 上跑真服务 + 真 client，
            // 而它内部用 android.util.Log 打点 —— 没有这项就会炸。
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        debug {
            // 开发包：入口全开（含主题调试面板），可调试。
            buildConfigField("boolean", "FULL_ENTRIES", "true")
            buildConfigField("boolean", "BETA_DEMO", "false")
            // 默认关：我每天开几十次 debug 包，全算进日活就把真数据淹了。
            // 要联调上报就在 local.properties 里开 dartvio.telemetry.debug=true。
            buildConfigField("boolean", "TELEMETRY_ENABLED", telemetryOnDebug.toString())
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField("boolean", "FULL_ENTRIES", "false")
            buildConfigField("boolean", "BETA_DEMO", "false")
            // 正式包不上报：正式版要有独立的、明说的隐私告知才配开统计。
            buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
            signingConfig = signingConfigs.getByName("beta")
        }

        /*
         * 对外试用的 **Beta Demo** 包（`./gradlew assembleBeta`）。
         *
         * 三件事与 debug 不同，每一件都有具体理由：
         *
         * 1. `isDebuggable = false` —— 可调试的包意味着拿到手机就能 `run-as` 导出
         *    数据库、挂调试器。发给别人的包不开这个口子。
         *    代价：`BuildConfig.DEBUG` 随之变成 false，而项目里有三处用它决定
         *    「是否显示调试入口」。所以入口开关改用独立的 `FULL_ENTRIES`，
         *    与「能不能调试」解耦 —— 这才做得到**入口全开但不可调试**。
         *
         * 2. 用 `beta` 签名配置（真密钥库）而不是 debug 签名 —— 朋友之后覆盖安装
         *    新版本时不会撞「签名不一致」。
         *
         * 3. 不开混淆/优化 —— 试用包要的是「崩了能看出崩在哪」，不为了体积引入
         *    未经验证的行为变化。
         */
        create("beta") {
            initWith(getByName("debug"))
            isDebuggable = false
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("beta")
            versionNameSuffix = "-beta-demo"
            buildConfigField("boolean", "FULL_ENTRIES", "true")
            buildConfigField("boolean", "BETA_DEMO", "true")
            // 只有对外试用的 beta 包默认上报 —— 我要的就是「朋友的使用情况」。
            buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // Ktor 的 jar 会带上若干与 Android 无关的元数据，不排除会触发 dex 合并冲突。
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

// Room 编译期生成 DAO / Database 实现，并把数据库 schema 导出为 JSON
// （对标 T1《数据库 Schema》交付物，产物位于 app/schemas/）。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // KMP 共享核心：domain / rules / stats（两端同一份口径）。
    // 2026-09-27 起 `com.dartvio.app.domain.*` 的源码位于 :shared，本模块只保留 UI / data / net。
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    // Compose BOM：统一约束所有 Compose 依赖版本，仅在此声明一次。
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Room：本地对局历史持久化（M9 第①期）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // M5 实时同步：主机端内嵌 Ktor server（权威节点）+ 客户端 WS 连接。
    // 说明：这是本项目第一次引入网络库；BL-001 硬约束要求房间状态「服务端权威 + 可广播」，
    // 内嵌 server 满足该约束，且客户端之间不直连（非 P2P）。
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Enforce the theme boundary. Transparent/Unspecified are rendering sentinels, not palette colors.
abstract class CheckThemeColorsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val themeSources: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val themeSourceRoot: DirectoryProperty

    @TaskAction
    fun checkColors() {
        val prohibited = Regex("""\bColor\s*\(|\bColor\.(?:White|Black|Red|Green|Blue|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta)\b|\b(?:parseColor|argb|rgb)\s*\(""")
        fun hasLiteral(source: String): Boolean = prohibited.containsMatchIn(
            source.replace(Regex("""/\*[\s\S]*?\*/|//[^\n]*"""), ""))
        // Positive/negative fixtures exercise the same detector as source scanning.
        check(hasLiteral("val sample = Color(0xFF123456)"))
        check(hasLiteral("val sample = Color.White"))
        check(hasLiteral("val sample = parseColor(value)"))
        check(hasLiteral("val gradient = listOf(Color.Red, Color.Blue)"))
        check(!hasLiteral("val sample = MaterialTheme.colorScheme.primary"))
        check(!hasLiteral("val sample = Color.Transparent"))
        val allowed = setOf("theme/Color.kt", "theme/Theme.kt")
        val violations = themeSources.files.filter { file ->
            val relative = file.relativeTo(themeSourceRoot.get().asFile).invariantSeparatorsPath
            relative !in allowed && hasLiteral(file.readText())
        }
        check(violations.isEmpty()) { "Hardcoded UI colors must move to Theme mappings: ${violations.joinToString()}" }
        logger.lifecycle("Theme color boundary passed (${themeSources.files.size} files; detector fixtures passed).")
    }
}
tasks.register<CheckThemeColorsTask>("checkThemeColors") {
    themeSources.from(fileTree("src/main/java/com/dartvio/app/ui") { include("**/*.kt") })
    themeSourceRoot.set(layout.projectDirectory.dir("src/main/java/com/dartvio/app/ui"))
}
tasks.matching { it.name == "lintDebug" }.configureEach { dependsOn("checkThemeColors") }

