package com.dartvio.app.data.achievement

import android.content.SharedPreferences

/**
 * 纯 JVM 的内存 [SharedPreferences]，供 `data/` 层的持久化契约单测使用。
 *
 * **为什么需要它**：`SharedPreferences` 是**接口**，可以自行实现；而 `Context` 是抽象类，
 * 其方法在 unit test 里只有 `android.jar` 的 stub（一律抛异常），必须引入 Robolectric
 * 才能用。本项目的测试依赖只有 JUnit（见 `libs.versions.toml`），引入 Robolectric 既增加
 * 依赖体积、又受 SDK 版本支持范围限制（本项目 `compileSdk = 37`）。
 * 因此让被测对象依赖接口而非 `Context`（`AchievementStore.prefs(context)` 仍留给生产代码），
 * 就能在零新依赖、毫秒级的前提下覆盖真实的持久化语义。
 *
 * 刻意**忠实复刻 Android 的三条真实语义**，否则测试会给生产代码虚假的安全感：
 * 1. `apply()` / `commit()` 后立即可读（Android 的 `apply()` 会同步更新内存快照）；
 * 2. `getAll()` 返回**副本**，而 `getStringSet()` 返回**内部引用**（改了会污染存储）
 *    —— 这是 Android 自身的历史不一致，照搬才能测出「忘记复制」的回归；
 * 3. `put(key, null)` 等价于 `remove(key)`（Android 的 `commitToMemory` 就是这么做的）。
 */
class InMemorySharedPreferences : SharedPreferences {

    private val values = linkedMapOf<String, Any>()

    override fun getAll(): MutableMap<String, Any> = LinkedHashMap(values)

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    /**
     * 两阶段提交：编辑期间只累积改动，`apply()` / `commit()` 时一次性落到 [values]。
     *
     * 注意本类**不能**用 Kotlin 的 `apply { }` 作用域函数 —— 它会被解析成
     * `SharedPreferences.Editor.apply()` 成员方法（成员优先于扩展函数），
     * 返回 Unit 导致类型不匹配。因此每个 setter 都显式 `return this`。
     */
    private inner class Editor : SharedPreferences.Editor {

        private val written = linkedMapOf<String, Any>()
        private val removed = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            write(key, value)
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            // 存快照：对齐 Android —— 之后改动传入的 Set 不影响已提交内容
            write(key, values?.toMutableSet())
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            write(key, value)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            write(key, value)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            write(key, value)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            write(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removed += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            cleared = true
            return this
        }

        override fun commit(): Boolean {
            commitToMemory()
            return true
        }

        override fun apply() = commitToMemory()

        private fun write(key: String, value: Any?) {
            // Android 的 put(key, null) 走的是移除分支
            if (value == null) {
                removed += key
            } else {
                written[key] = value
            }
        }

        private fun commitToMemory() {
            if (cleared) values.clear()
            removed.forEach(values::remove)
            values.putAll(written)
        }
    }
}
