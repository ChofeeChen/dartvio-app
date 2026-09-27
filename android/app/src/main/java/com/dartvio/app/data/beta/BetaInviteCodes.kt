package com.dartvio.app.data.beta

/**
 * Beta Demo 试用包的**邀请码白名单**（一人一码，共 100 个）。
 *
 * 格式：`DT-` 前缀 + 6 位「字母 + 数字」。字符集刻意**排除 I / O / 0 / 1**
 * 四个高混淆字符 —— 码要靠微信口头与文字传递，输错一位就会把朋友挡在激活页外。
 * 剩余 31 个字符，31^6 ≈ 8.9 亿种组合，盲猜概率可忽略。
 *
 * 边界（方案 A 的已知取舍）：这些码**明文编译进包里**，反编译可以全部读出。
 * 本地校验只防「没拿到码的人」，不防逆向；「码 → 人」的对账完全依赖分发者
 * 自己的台账（本包内不联网、不记录谁激活了哪个码）。
 */
object BetaInviteCodes {

    /**
     * 全部有效邀请码（随机抽样 100 个不重复，种子 20260919，升序排列便于对账）。
     * 生成规则与字符集见类注释；**新增码必须走同一条生成流程**，不要手写。
     */
    val all: List<String> = listOf(
        "DT-2EE89W",
        "DT-2XY3Z9",
        "DT-37NT4K",
        "DT-3GHW24",
        "DT-3KB6LB",
        "DT-3MZW7G",
        "DT-3XR2RM",
        "DT-3YGJ5T",
        "DT-4JREAN",
        "DT-4QZFPH",
        "DT-4V29P3",
        "DT-4YY8QL",
        "DT-5AK7M5",
        "DT-5KFA9X",
        "DT-5LJL7E",
        "DT-5YVLJA",
        "DT-6Y8HQ8",
        "DT-7F5FLX",
        "DT-7JXZ2N",
        "DT-7XT8SH",
        "DT-86JZYC",
        "DT-892UE6",
        "DT-989NFN",
        "DT-9AB8MV",
        "DT-9RJK3E",
        "DT-A93S6D",
        "DT-AQ6LA3",
        "DT-B7LGH6",
        "DT-BE5ZF4",
        "DT-BRXNS5",
        "DT-CLG5S8",
        "DT-DN9BB5",
        "DT-DQWSUX",
        "DT-EA9USG",
        "DT-EF6VM4",
        "DT-ESZ4DX",
        "DT-ETEXCT",
        "DT-F65H83",
        "DT-F66PQW",
        "DT-F6PM44",
        "DT-F84D8S",
        "DT-FA8YYX",
        "DT-FMSKBP",
        "DT-FSBHAH",
        "DT-FSD4BH",
        "DT-G96QDG",
        "DT-H9M7F2",
        "DT-HG9KFK",
        "DT-HS63UP",
        "DT-HZNE69",
        "DT-JF2C5F",
        "DT-JGPTWE",
        "DT-JKFL6T",
        "DT-JT7B2H",
        "DT-K6AQXT",
        "DT-KPJ6D8",
        "DT-L5L4UJ",
        "DT-LT734J",
        "DT-M8SDJN",
        "DT-MNYQXT",
        "DT-MRAN4V",
        "DT-MYLH2Z",
        "DT-NDQH5F",
        "DT-NZKLRD",
        "DT-PAAD8Q",
        "DT-PEKL5H",
        "DT-QMQTMW",
        "DT-QWLDL5",
        "DT-R8DETB",
        "DT-REEDB8",
        "DT-RKA26R",
        "DT-RPADYT",
        "DT-RZNN4P",
        "DT-SC7DNT",
        "DT-SNHMXS",
        "DT-SRW85N",
        "DT-SZP5Q3",
        "DT-SZUL2G",
        "DT-T36A98",
        "DT-T5TNAE",
        "DT-TEU6ZW",
        "DT-TGCVSG",
        "DT-TMS6GC",
        "DT-TTMTZL",
        "DT-UAU8Y9",
        "DT-UCA34V",
        "DT-UR5UCY",
        "DT-UZSYV8",
        "DT-V52ERL",
        "DT-VCZ7T4",
        "DT-WE9QGK",
        "DT-WL665P",
        "DT-WQPXTE",
        "DT-WVREK4",
        "DT-WXDJER",
        "DT-YX4RGH",
        "DT-ZLL6VA",
        "DT-ZQGB26",
        "DT-ZTCFBV",
        "DT-ZVTBYA",
    )

    /** 校验用集合（O(1) 查找），与 [all] 内容一致；单测断言两者 size 相等即无重复。 */
    val lookup: Set<String> = all.toSet()

    /** 格式正则：`DT-` 后接 6 位白名单字符集（不含 I / O / 0 / 1）。 */
    val FORMAT: Regex = Regex("^DT-[A-HJ-NP-Z2-9]{6}$")
}
