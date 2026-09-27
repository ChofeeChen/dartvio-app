package com.dartvio.app.data.beta

import android.app.Application
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Beta 试用包的**崩溃留痕**（只在 beta 变体安装，见 [install] 的调用处）。
 *
 * ## 为什么需要它
 *
 * 试用包是发给别人用的：朋友手机上一闪而退，我这边既看不到 logcat，也无从复现
 * 他那台设备上的现场。留一份「最近 5 次异常」在 SharedPreferences 里，
 * 他只要在 Beta 弹窗里截个图发回来，就够了 —— 比追问「你点了什么」有效得多。
 *
 * ## 三条刻意守住的边界
 *
 * 1. **不吞异常**：记完之后一定交还给原来的 handler，进程照常终止。
 *    它只是旁路记录，绝不能让一次崩溃变成「日志记了但 App 卡在半死状态」。
 * 2. **同步落盘**（`commit` 而非 `apply`）：崩溃后进程随时会被杀，
 *    异步写盘大概率来不及 —— 那种「查的时候永远是空的」记录等于没有。
 * 3. **只在本机**：不上传、不联网。它是给人看的现场，不是遥测。
 */
object BetaCrashLog {

    private const val PREFS = "beta_crash_log"
    private const val KEY = "entries"
    private const val MAX = 5

    /** 条目分隔符：一个**不会出现在日志文本里**的记号（日志只有时间 / 线程 / 异常 / 栈帧）。 */
    private const val SEP = "|~|"

    /**
     * 安装全局未捕获异常钩子（仅 beta 变体调用）。
     *
     * @param app 用于取 SharedPreferences。
     */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 追加一条记录，超出上限丢最旧的。 */
    fun append(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val frame = throwable.stackTrace.firstOrNull()
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }
            ?: "位置未知"
        val line = buildString {
            append(stamp)
            append(" · ").append(thread.name)
            append(" · ").append(throwable.javaClass.simpleName)
            append(": ").append(throwable.message?.take(120) ?: "无消息")
            append(" · ").append(frame)
        }
        val kept = (read(context) + line).takeLast(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, kept.joinToString(SEP))
            .commit()
    }

    /** 最近记录，旧的在前。 */
    fun read(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return raw.split(SEP).filter { it.isNotBlank() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()
    }
}
