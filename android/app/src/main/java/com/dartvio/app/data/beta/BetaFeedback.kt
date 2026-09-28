package com.dartvio.app.data.beta

import android.content.Context
import android.content.SharedPreferences

/**
 * **反馈闭环**的本地一半：这台机器「发出去过几条反馈」。
 *
 * 为什么做这个，而不做「你是第几位体验用户」：
 * 排队序号只能炫耀，第二次就没有增量了，而且会把内测变成抢位游戏 —— 人少的时候
 * 序号难看，人多的时候又毫无意义。**能让人继续提意见的是闭环**：我提了 → 有人听 →
 * 下一版里看得见。所以这里记两个数字：本机发出的条数 + 包里这份「已采纳」清单
 * （后者见 [BetaFeedbackAck]）。
 *
 * 边界（写死在这里，免得日后为了「数据好看」偷偷放宽）：
 * - **只记时间戳，不记内容**。反馈正文从来不经我们手 —— 用户点开系统分享之后
 *   发到哪里由他决定，我们不收，也**无从核实**；
 * - 计数在本地 SP 里，卸载即清空，不上报、不参与任何统计口径；
 * - 正因为无法核实，数字由**用户自己点一下「发出去了」**才 +1，绝不猜。
 */
object BetaFeedback {

    const val PREFS = "beta_feedback"

    /** 每条反馈一个时间戳（毫秒）。用 Set 而不是 Int 计数：能去重、也能撤销最后一条。 */
    private const val KEY_SENT = "sent_at_millis"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 已发出的时间戳集合（每次新读新集合：SharedPreferences 的 StringSet 不该就地改）。 */
    fun sentMillis(prefs: SharedPreferences): Set<Long> =
        prefs.getStringSet(KEY_SENT, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    /** 已明确发出去的条数。 */
    fun sentCount(prefs: SharedPreferences): Int = sentMillis(prefs).size

    /** 最近一次的时间戳；一次都没发过返回 null。 */
    fun lastSentAtMillis(prefs: SharedPreferences): Long? = sentMillis(prefs).maxOrNull()

    /**
     * 记一条，返回新的条数。
     *
     * 同一毫秒内的重复（手抖点两下）天然被 Set 吃掉 —— 这是给人看的进度感，
     * 不是审计凭据。
     */
    fun recordSent(
        prefs: SharedPreferences,
        atMillis: Long = System.currentTimeMillis(),
    ): Int {
        val updated = sentMillis(prefs) + atMillis
        write(prefs, updated)
        return updated.size
    }

    /**
     * 撤销最近一条，返回新的条数。
     *
     * 存在的理由：用户点了「写一条反馈」进去发现不好发、或者发出去发现是错的，
     * 这时数字必须能退回来。一个只会往上走的计数器，第二次就没有信用了。
     */
    fun undoLast(prefs: SharedPreferences): Int {
        val remaining = sentMillis(prefs).toMutableSet()
        val last = remaining.maxOrNull() ?: return 0
        remaining.remove(last)
        write(prefs, remaining)
        return remaining.size
    }

    private fun write(prefs: SharedPreferences, millis: Set<Long>) {
        // commit 而非 apply：这是低频写入，且写完立刻返回 summary 就要显示新数字，
        // 同步落盘换「退出再进来数字没变」这种说不清的 bug。
        prefs.edit().putStringSet(KEY_SENT, millis.map { it.toString() }.toSet()).commit()
    }

    /**
     * 反馈正文模板：9 个固定字段，与随包文档《Beta体验反馈-模板.md》第二节一致。
     *
     * 参数全部由调用方传进来（而不是这里读 Context）：
     * ① 这条字符串是最终给用户在微信里编辑的文案，**必须能在 JVM 单测里直接断言**；
     * ② 模板一旦与随包那份 `.md` 漂移，用户填回来的东西就没法直接进汇总表。
     */
    fun buildMessage(usageId: String?, versionName: String, dateText: String): String = buildString {
        appendLine("【DartVio 反馈】")
        appendLine("体验ID：${usageId?.takeIf { it.isNotBlank() } ?: "（未开启统计的包没有 ID，留空即可）"}")
        appendLine("版本号：$versionName")
        appendLine("日期：$dateText")
        appendLine()
        appendLine("1. 发生位置：（首页 / 大厅 / 创建比赛 / 房间等候 / 对战 / 精准工坊 / 数据 / 我的）")
        appendLine("2. 类型：（bug / 看不懂 / 不顺手 / 想要的功能）")
        appendLine("3. 严重程度：（阻断 / 影响体验 / 无所谓）")
        appendLine("4. 我原本想做的事：")
        appendLine("5. 实际发生了：")
        appendLine("6. 期望是：")
        appendLine("7. 能否复现：（每次 / 偶尔 / 只出现一次）")
        appendLine("8. 截图或录屏：（有 / 无）")
    }.trimEnd()
}

/**
 * **反馈闭环的另一半：哪些意见被采纳了**（随包发行，每版出货时往这里加）。
 *
 * 为什么写死在包里而不是做成在线接口：
 * 采纳清单是**版本号级别的事实**，联网接口意味着「你在飞机上看不到自己提的建议被采纳了」；
 * 而且这份清单本身就在回答「这版改了什么」，离线也该成立。代价是改一次要出一次包 ——
 * 但它本来就是跟版本绑定的，这个代价是对的。
 *
 * 诚实边界：这里记的是**全体 Beta 体验者**的采纳总数，不能拆成「其中你提的 1 条」——
 * 拆分需要把反馈正文回传给我们（我们刻意不收），所以界面上如实写「来自全体体验者」。
 */
object BetaFeedbackAck {

    /** 一条已被采纳的反馈：`shippedIn` = 它真正落地在哪个版本（用户能立刻去核对）。 */
    data class Adopted(
        val date: String,
        val shippedIn: String,
        val source: String,
        val what: String,
    )

    /**
     * 采纳清单，新加的放最上面。
     *
     * `source` 只写来源性质（真机反馈 / 合规建议 / 群反馈），**不写人名** ——
     * 包是可以反编译的，在上面给体验者署名不需要事先征得他同意。
     */
    val items: List<Adopted> = listOf(
        Adopted(
            date = "2026-09-28",
            shippedIn = "v0.1.20",
            source = "真机反馈",
            what = "修「一回合录满 3 镖后还能继续输分数」：键盘只在**本回合镖数未满**时接受「再加一支镖」的输入" +
                "（倍率 / 数字 / 牛眼 / MISS 全部置灰），此前第 4、5 镖能录进去但落库侧只收 3 镖，表现为「按了没反应」，" +
                "接着按确认又因缓冲里还留着未落定的数字而进不了「结束回合」，整页卡住。" +
                "**退格与确认键不受限制** —— 第 3 镖录错要能退回来改，满 3 镖后也必须能把回合交出去。" +
                "X01 与 Cricket 两套键盘同步生效，练习页一并受益",
        ),
        Adopted(
            date = "2026-09-28",
            shippedIn = "v0.1.20",
            source = "真机反馈",
            what = "修「两台真机上玩家卡片显示不一样」（vivo 上比例失调、溢出严重）：卡片里「已赢 N 局 · PPR x.x」" +
                "与「本局 PPR」几行此前没有单行约束，在字体放大 + 窄屏的机型上会**逐字换行**（一行变四行），" +
                "把整张卡片撑高并顶出屏幕；另一台手机同一版本却正常，于是看起来像两台手机 UI 不同。" +
                "现已全部锁死单行，放不下时裁尾而不是换行",
        ),
        Adopted(
            date = "2026-09-28",
            shippedIn = "v0.1.19",
            source = "真机反馈",
            what = "**对战页键盘加高 40%**（200dp → 280dp）：五排按键每排从约 36dp 提到约 50dp，" +
                "此前按键偏矮、容易按错。键盘仍常驻屏幕底部且高度不随回合变化，上方记分表自动吸收这部分增量",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.18",
            source = "真机反馈",
            what = "修「打完一局本地 X01，数据页却是空的」：本地开局默认**先赢 1 局**（打完一局 = 打完一场 = 落库），" +
                "多局赛制在本局获胜页明说「整场先赢 N 局 · 全部打完才计入统计」，" +
                "并把战绩落库改成**不可取消**（结算页直接退出不再丢整场），空态提示补上「打到整场结束」这一口径",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.18",
            source = "真机反馈",
            what = "立**全局 UI 约束**（写进设计系统规范 §14）：结果页内容必须可滚动、主操作钉在内容区之下并避开系统导航条、" +
                "顶部不垫固定 Spacer、同行按钮等宽同高 52dp、主数字 ≤56sp、卡片四件套与卡距/段距统一。" +
                "Count Up 与 Cricket MPR 结果页按新规则重写（顶部溢出、按钮过低已修）",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.18",
            source = "真机反馈",
            what = "「我的」页去掉「对局总数 / 已完成」两张答不上「所以呢」的数字卡，去掉与卡片同名的「设置」标题，" +
                "并把全页卡片的圆角 / 内边距 / 底色 / 边框与卡距段距收成同一组值",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.18",
            source = "真机反馈",
            what = "反馈页文案不再承诺「会写进下一个版本」，改为「逐条认真评估、有价值的排进后续版本」——" +
                "兑现不了的承诺会连累整份采纳清单的可信度",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.18",
            source = "真机反馈",
            what = "版本信息按主流 App 的放法收进**设置页底部「关于 DartVio」**（版本号 + Build + 构建类型 + 本版更新说明），" +
                "「我的」页底部只留一行可随手抄的小字",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.17",
            source = "真机反馈",
            what = "对局页改为五段固定布局（导航 / 玩家卡片 / 三镖 / 记分表 / 键盘）：段间距统一 8dp，" +
                "键盘常驻屏幕底部（约 200dp）且**只在轮到自己时可输入**，对手回合整块禁用；三镖槽位常驻不消失",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.17",
            source = "真机反馈",
            what = "记分表对齐 n01 转播口径：**一行 = 双方各投完一轮**，同一轮的得分并排对照；" +
                "镖数列从首行起为 0、3、6、9（本局累计的 3 的整数倍），首行写初始分（501）、得分首行空",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.17",
            source = "真机反馈",
            what = "数据页放弃「一卡一指标」，改为行式布局：指标名居左、数值居右、中间一根双色柱——" +
                "柱长即该项表现（越长越好），率类按 100%、最高收尾按 170 等参考满值折算，数量类按组内最大值归一；类别之间细分割线",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.16",
            source = "真机反馈",
            what = "对局页读数重排：规则信息（X01-501 / LEG / R / SI-DO）并入标题栏房间名右侧并去掉「对局中」，" +
                "删掉「等待 XX 投掷 / 上一回合」那张卡，记分表上移紧邻玩家卡片、去掉「回合记录」标题",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.16",
            source = "真机反馈",
            what = "玩家卡片分数下方显示**本局实时 PPR**；记分表三列数字统一字号（14sp）/ 默认不加粗 / 掺灰白，" +
                "≥100 分加粗，每一局以一行初始分（501）开头、局与局之间粗线分割",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.16",
            source = "真机反馈",
            what = "记分表改为「一人一轮一行」（镖数记本轮镖数），并在投掷进行中用占位行指出下一组数字会落在哪格",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.15",
            source = "真机反馈",
            what = "数据页新增「练习」口径：练习数据本来就不写对局表（此前标着「本地训练」却永远空白），" +
                "现在单独汇总 99 Darts / Count Up / MPR / 极速结镖 / 对抗练习 / 落点诊断的本机成绩",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.15",
            source = "真机反馈",
            what = "「BETA DEMO 部分功能为演示」说明卡从首页移到「我的」页：首页留给「开一局」，版本声明回到身份与设置那一层",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.15",
            source = "合规自查",
            what = "补上「我的 → 隐私声明」入口：政策正文承诺了「之后可查看本政策」，此前只有首启一次、之后无处可查；" +
                "该页可直接开关匿名统计",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.14",
            source = "真机反馈",
            what = "分清「房间名 / 玩家昵称 / 实力」：昵称改为只有一份真源（此前本机与别人看到的名字不一样），大厅卡加「房主：昵称」",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.14",
            source = "真机反馈",
            what = "等候页成员行与对局页玩家卡显示 PPR（加入者的 PPR 随事件下发），对局卡按 X01 惯例给出局数与 PPR",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.13",
            source = "真机反馈",
            what = "退出房间改为「30 秒宽限后再销毁」：此前房主返回留下已结束的同名卡、客人退出留下点进去没有下一步的死房间",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.13",
            source = "真机反馈",
            what = "对局页标出「（本机）」、加 LEG / R / 规则一行，观战视角在右上角挂灰眼睛",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.13",
            source = "真机反馈",
            what = "回合记录去掉「实时更新」、卡片换表格线、分数去粗体并降一档：同样高度多看近一半回合",
        ),
        Adopted(
            date = "2026-09-27",
            shippedIn = "v0.1.13",
            source = "真机反馈",
            what = "对抗练习开卡双倍环游 / 上海争霸 / 减半挑战；设置页改成选对手（四选一），「开始对抗」移到底部",
        ),
        Adopted(
            date = "2026-09-26",
            shippedIn = "v0.1.12",
            source = "群反馈",
            what = "增设「早期体验者」身份标识与反馈闭环：数量身份认同用徽章表达，不显示排队序号",
        ),
        Adopted(
            date = "2026-09-26",
            shippedIn = "v0.1.11",
            source = "真机反馈",
            what = "数据页加「示例」开关，趋势 / 分布 / 占比三类数据各用一种图，不再混着画",
        ),
        Adopted(
            date = "2026-09-26",
            shippedIn = "v0.1.11",
            source = "真机反馈",
            what = "大厅空态删去「官方擂台」两行没人看得懂的文案，只留一句「先去练几镖」",
        ),
        Adopted(
            date = "2026-09-26",
            shippedIn = "v0.1.11",
            source = "合规建议",
            what = "首启隐私说明改为可自主选择：不同意也照样用，只关掉匿名使用统计",
        ),
        Adopted(
            date = "2026-09-25",
            shippedIn = "v0.1.10",
            source = "真机反馈",
            what = "修复蜂窝网络下在线状态一直显示「连接中」（WebSocket 无超时被挂死）",
        ),
    )
}
