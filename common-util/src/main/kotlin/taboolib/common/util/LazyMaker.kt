package taboolib.common.util

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * 声明一个线程不安全的延迟加载对象
 *
 * @param initializer 初始化函数
 */
fun <T> unsafeLazy(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)

/**
 * 声明一个允许重置的延迟加载对象
 *
 * @param groups 组
 * @param synchronized 是否线程安全
 * @param initializer 初始化函数
 */
fun <T> resettableLazy(vararg groups: String, synchronized: Boolean = false, initializer: () -> T): ResettableLazy<T> {
    return if (synchronized) {
        ResettableSynchronizedLazyImpl(*groups, initializer = initializer)
    } else {
        ResettableLazyImpl(*groups, initializer = initializer)
    }.also {
        ResettableLazy.defined.put(it, Unit)
    }
}

/**
 * 声明一个需要传入上下文对象才能初始化的延迟加载对象
 *
 * @param C 上下文类型
 * @param T 值类型
 * @param typeIsolation 是否进行类型隔离，如果为 true，传入不同类型的 context 时会重新初始化
 * @param initializer 初始化函数，接收上下文对象并返回值
 */
fun <C, T> supplierLazy(typeIsolation: Boolean = false, initializer: (C) -> T): SupplierLazy<C, T> {
    return if (typeIsolation) {
        SupplierLazyWithTypeIsolationImpl(initializer)
    } else {
        SupplierLazyImpl(initializer)
    }
}

/**
 * 包装上下文对象
 * 在 [SupplierLazy] 中使用时，当需要携带额外数据时使用
 */
data class WrappedContext<C: Any, E: Any>(val context: C, val extra: E)

/**
 * 需要传入上下文对象的延迟加载接口
 */
interface SupplierLazy<C, T> {

    /**
     * 传入上下文对象获取值
     *
     * @param context 上下文对象
     * @return 初始化后的值
     */
    operator fun get(context: C): T

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean

    /**
     * 重置状态
     */
    fun reset()
}

abstract class ResettableLazy<T>(vararg val groups: String) : Lazy<T> {

    abstract fun reset()

    companion object {

        val defined: Cache<ResettableLazy<*>, Unit> = CacheBuilder.newBuilder().weakKeys().build()

        fun reset(vararg groups: String) {
            if (groups.isEmpty() || groups.contains("*")) {
                defined.asMap().keys.forEach { it.reset() }
            } else {
                defined.asMap().keys.filter { lazy -> groups.any { lazy.groups.contains(it) } }.forEach { it.reset() }
            }
        }
    }
}

private class ResettableLazyImpl<T>(vararg groups: String, initializer: () -> T) : ResettableLazy<T>(*groups) {

    private var initializer: (() -> T)? = initializer

    private var localValue: Any? = UninitializedValue

    @Suppress("UNCHECKED_CAST")
    override val value: T
        get() {
            if (localValue === UninitializedValue) {
                localValue = initializer!!()
            }
            return localValue as T
        }

    override fun reset() {
        localValue = UninitializedValue
    }

    override fun isInitialized() = localValue !== UninitializedValue

    override fun toString() = if (isInitialized()) value.toString() else "Lazy(${groups.joinToString()}) value not initialized yet."
}

private class ResettableSynchronizedLazyImpl<T>(vararg groups: String, initializer: () -> T) : ResettableLazy<T>(*groups) {

    private var initializer: (() -> T)? = initializer

    @Volatile
    private var localValue: Any? = UninitializedValue

    private val lock = this

    @Suppress("UNCHECKED_CAST")
    override val value: T
        get() {
            val v1 = localValue
            if (v1 !== UninitializedValue) {
                return v1 as T
            }
            return synchronized(lock) {
                val v2 = localValue
                if (v2 !== UninitializedValue) {
                    v2 as T
                } else {
                    val typedValue = initializer!!()
                    localValue = typedValue
                    typedValue
                }
            }
        }

    override fun reset() {
        localValue = UninitializedValue
    }

    override fun isInitialized() = localValue !== UninitializedValue

    override fun toString() = if (isInitialized()) value.toString() else "Lazy(${groups.joinToString()}) value not initialized yet."
}

private class SupplierLazyImpl<C, T>(private val initializer: (C) -> T) : SupplierLazy<C, T> {

    private var localValue: Any? = UninitializedValue

    @Suppress("UNCHECKED_CAST")
    override fun get(context: C): T {
        if (localValue === UninitializedValue) {
            localValue = initializer(context)
        }
        return localValue as T
    }

    override fun isInitialized() = localValue !== UninitializedValue

    override fun reset() {
        localValue = UninitializedValue
    }

    override fun toString() = if (isInitialized()) localValue.toString() else "SupplierLazy value not initialized yet."
}

private class SupplierLazyWithTypeIsolationImpl<C, T>(private val initializer: (C) -> T) : SupplierLazy<C, T> {

    private val valueMap = ConcurrentHashMap<Class<*>, Optional<T>>()

    @Suppress("UNCHECKED_CAST")
    override fun get(context: C): T {
        val contextClass = if (context is WrappedContext<*, *>) context.context::class.java else context!!::class.java
        val optional = valueMap.computeIfAbsent(contextClass) {
            Optional.ofNullable(initializer(context)) as Optional<T>
        }
        return optional.orElse(null as T)
    }

    override fun isInitialized() = valueMap.isNotEmpty()

    override fun reset() {
        valueMap.clear()
    }

    override fun toString() = if (isInitialized()) "SupplierLazy(typeIsolation) with ${valueMap.size} type(s) initialized" else "SupplierLazy(typeIsolation) value not initialized yet."
}

internal object UninitializedValue