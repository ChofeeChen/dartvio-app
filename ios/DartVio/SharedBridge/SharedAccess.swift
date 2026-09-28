import shared

/**
 * Kotlin `object` 单例与顶层函数的统一入口。
 *
 * 目的不是包一层语法糖，而是**把 API 名称收敛到唯一一处**：
 * KMP 导出的是 ObjC 头文件，单例写作 `X01Rules.shared`，顶层函数会落到 `XxxKt` 类上，
 * 将来若 Kotlin 侧改名或调整导出方式，只改这个文件，不污染五个页面。
 */
enum SharedAccess {
    static let x01Rules = X01Rules.shared
    static let x01Ai = X01Ai.shared
    static let countUpRules = CountUpRules.shared

    /** `new/checkout/single/round/undo/insert/remove/set/increment/decrement` 家族方法与 ObjC 的 init 家族冲突，
     *  导出时会被自动改名（加 `do` 前缀）：Kotlin 的 `newTarget` → Swift `doNewTarget`。 */
    static let randomCheckoutRules = RandomCheckoutRules.shared
    static let checkoutSolver = CheckoutSolver.shared

    /**
     * ⚠️ `kotlin.random.Random` 是**抽象类**，不能 `new`。
     *
     * 头文件里 `SharedKotlinRandom` 确实带 `- (instancetype)init`，所以编译能过，
     * 但运行时命中 `Kotlin_ObjCExport_AbstractClassConstructorCalled` → SIGABRT
     * （V4 UI 测试就是崩在这：`X01GameViewModel.init` → `newRandom()`）。
     * 抽象类的 `Default` 伴生对象有 `objc_subclassing_restricted` + `shared` 单例，
     * 正确取法是 `KotlinRandom.Default.shared`。
     */
    static func newRandom() -> KotlinRandom { KotlinRandom.Default.shared }
}
