package com.dartvio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dartvio.app.ui.navigation.DartVioRoot
import com.dartvio.app.ui.theme.DartVioTheme
import com.dartvio.app.data.theme.ThemeStore
import com.dartvio.app.ui.theme.LocalThemeStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dartvio.app.BuildConfig
import com.dartvio.app.data.beta.BetaAccess
import com.dartvio.app.data.beta.BetaConsent
import com.dartvio.app.data.beta.BetaInviteApi
import com.dartvio.app.ui.beta.BetaAccessScreen
import com.dartvio.app.ui.beta.BetaExpiryBanner
import com.dartvio.app.ui.beta.BetaExpiredScreen
import com.dartvio.app.ui.beta.PrivacyConsentScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val themeStore by lazy { ThemeStore(getSharedPreferences(ThemeStore.PREFS, MODE_PRIVATE)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全局深色：系统栏图标用浅色（亮色图标），适配深色背景。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val palette by themeStore.palette.collectAsState()
            // 自定义调色板跟随 FULL_ENTRIES（Beta Demo 包也要能换主题），
            // 判据不用 BuildConfig.DEBUG —— 见 build.gradle.kts 里 beta 变体的说明。
            val debugPalette = if (BuildConfig.FULL_ENTRIES) palette else null

            /*
             * 首启的三级顺序（顺序本身是有产品含义的，别随便调）：
             *
             * ① **隐私说明** —— 先说清会发生什么，再谈别的。
             *    任何一条「在他点头之前就发出去的数据」都会让后面所有数据不可信，
             *    包括反馈台账；所以这一步必须在任何网络请求之前。
             * ② **邀请码** —— 可选，可被跳过（2026-09-20 的定论：它是渠道标签，不是门禁）。
             * ③ **正常界面**（到期前一周在顶部挂一条提醒）。
             *
             * 到期（EXPIRED）则整个 App 换成到期页，且**不动任何本地数据**。
             */
            val now = System.currentTimeMillis()
            val betaPrefs = getSharedPreferences(BetaAccess.PREFS, MODE_PRIVATE)
            val consentPrefs = BetaConsent.prefs(this@MainActivity)

            var needConsent by remember {
                mutableStateOf(BuildConfig.TELEMETRY_ENABLED && BetaConsent.needsConsent(consentPrefs))
            }
            var showBetaIntro by remember {
                mutableStateOf(
                    BuildConfig.BETA_DEMO &&
                        !BetaAccess.hasDecided(betaPrefs) &&
                        BetaAccess.usable(now)
                )
            }

            // 离线放行的码要趁有网时补验一次：本地白名单看不出「这个码是不是已经在别处启用」，
            // 一直不补，渠道统计会被同一批码灌水。失败就留着下次再试（不重试、不打扰用户）。
            LaunchedEffect(Unit) {
                if (BuildConfig.BETA_DEMO && BetaAccess.isPending(betaPrefs)) {
                    withContext(Dispatchers.IO) { recheckInvite(betaPrefs) }
                }
            }

            CompositionLocalProvider(LocalThemeStore provides themeStore) {
            DartVioTheme(paletteOverride = debugPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!BetaAccess.usable(now)) {
                        BetaExpiredScreen()
                    } else if (needConsent) {
                        PrivacyConsentScreen(
                            onAgree = {
                                BetaConsent.save(consentPrefs, granted = true)
                                needConsent = false
                            },
                            onDecline = {
                                // 拒绝了也要按用户的选择执行：关掉匿名统计，功能一个不少。
                                BetaConsent.save(consentPrefs, granted = false)
                                needConsent = false
                            }
                        )
                    } else if (showBetaIntro) {
                        BetaAccessScreen(
                            onActivated = { showBetaIntro = false },
                            onSkip = {
                                // 跳过要落盘：否则下次启动还会再问一次，等于没跳过。
                                BetaAccess.markSkipped(betaPrefs)
                                showBetaIntro = false
                            }
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            if (BetaAccess.phase(now) == BetaAccess.Phase.EXPIRING) {
                                BetaExpiryBanner(BetaAccess.daysLeft(now))
                            }
                            DartVioRoot(Modifier.weight(1f))
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * 待补验的邀请码静默再问一次后台。
 *
 * 成功的情形：本地状态升级成「已验证」，与此前离线放行的结果一致，用户毫无感知。
 * 被后台拒绝（说明这个码确实已经在别处启用 / 已回收）：撤掉本地记录 ——
 * 留着一个假的激活态，等于把渠道统计和一个错误的人绑定在一起。
 * 后台依旧不通：什么都不做，等下次启动。
 */
private suspend fun recheckInvite(prefs: android.content.SharedPreferences) {
    if (!BetaInviteApi.available()) return
    val code = BetaAccess.activatedCode(prefs) ?: return
    when (val result = BetaInviteApi.verify(code, BetaAccess.installId(prefs))) {
        is BetaInviteApi.Result.Accepted -> BetaAccess.grantVerified(prefs, code)
        is BetaInviteApi.Result.Rejected -> BetaAccess.revoke(prefs)
        BetaInviteApi.Result.Unavailable -> Unit
    }
}
