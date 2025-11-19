package taboolib.common.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SupplierLazyTest {

    @Test
    fun `test basic supplier lazy initialization`() {
        var initCount = 0
        val lazy = supplierLazy<String, String> { context ->
            initCount++
            "Hello $context"
        }

        assertFalse(lazy.isInitialized())

        val result1 = lazy["World"]
        assertEquals("Hello World", result1)
        assertEquals(1, initCount)
        assertTrue(lazy.isInitialized())

        // Should return cached value, not reinitialize
        val result2 = lazy["Different"]
        assertEquals("Hello World", result2)
        assertEquals(1, initCount)
    }

    @Test
    fun `test supplier lazy with null value`() {
        val lazy = supplierLazy<String, String?> { null }

        assertFalse(lazy.isInitialized())
        val result = lazy["test"]
        assertNull(result)
        assertTrue(lazy.isInitialized())
    }

    @Test
    fun `test supplier lazy reset`() {
        var initCount = 0
        val lazy = supplierLazy<Int, String> { context ->
            initCount++
            "Count: $context"
        }

        lazy[1]
        assertEquals(1, initCount)
        assertTrue(lazy.isInitialized())

        lazy.reset()
        assertFalse(lazy.isInitialized())

        lazy[2]
        assertEquals(2, initCount)
        assertTrue(lazy.isInitialized())
    }

    @Test
    fun `test supplier lazy with type isolation disabled`() {
        var initCount = 0
        val lazy = supplierLazy<Any, String>(typeIsolation = false) { context ->
            initCount++
            "Value: ${context::class.simpleName}"
        }

        val result1 = lazy["String"]
        assertEquals("Value: String", result1)
        assertEquals(1, initCount)

        // Different type but should return cached value
        val result2 = lazy[123]
        assertEquals("Value: String", result2)
        assertEquals(1, initCount)
    }

    @Test
    fun `test supplier lazy with type isolation enabled`() {
        var initCount = 0
        val lazy = supplierLazy<Any, String>(typeIsolation = true) { context ->
            initCount++
            "Value: ${context::class.simpleName}-$context"
        }

        assertFalse(lazy.isInitialized())

        val result1 = lazy["String"]
        assertEquals("Value: String-String", result1)
        assertEquals(1, initCount)
        assertTrue(lazy.isInitialized())

        // Different type should reinitialize
        val result2 = lazy[123]
        assertEquals("Value: Int-123", result2)
        assertEquals(2, initCount)

        // Same type as first should return cached value
        val result3 = lazy["Different"]
        assertEquals("Value: String-String", result3)
        assertEquals(2, initCount)

        // Same type as second should return cached value
        val result4 = lazy[456]
        assertEquals("Value: Int-123", result4)
        assertEquals(2, initCount)
    }

    @Test
    fun `test supplier lazy with type isolation and null values`() {
        var initCount = 0
        val lazy = supplierLazy<Any, String?>(typeIsolation = true) { context ->
            initCount++
            when (context) {
                is String -> "String: $context"
                is Int -> null
                else -> "Other: $context"
            }
        }

        val result1 = lazy["test"]
        assertEquals("String: test", result1)
        assertEquals(1, initCount)

        val result2 = lazy[42]
        assertNull(result2)
        assertEquals(2, initCount)

        val result3 = lazy[99]
        assertNull(result3)
        assertEquals(2, initCount) // Should use cached null value
    }

    @Test
    fun `test supplier lazy with type isolation reset`() {
        var initCount = 0
        val lazy = supplierLazy<Any, String>(typeIsolation = true) { context ->
            initCount++
            "Init: $initCount"
        }

        lazy["String"]
        lazy[123]
        assertEquals(2, initCount)
        assertTrue(lazy.isInitialized())

        lazy.reset()
        assertFalse(lazy.isInitialized())

        lazy["NewString"]
        lazy[456]
        assertEquals(4, initCount)
    }

    @Test
    fun `test supplier lazy with custom classes`() {
        data class Person(val name: String)
        data class Animal(val species: String)

        val lazy = supplierLazy<Any, String>(typeIsolation = true) { context ->
            when (context) {
                is Person -> "Person: ${context.name}"
                is Animal -> "Animal: ${context.species}"
                else -> "Unknown"
            }
        }

        val result1 = lazy[Person("Alice")]
        assertEquals("Person: Alice", result1)

        val result2 = lazy[Animal("Dog")]
        assertEquals("Animal: Dog", result2)

        // Same type should return cached
        val result3 = lazy[Person("Bob")]
        assertEquals("Person: Alice", result3)

        val result4 = lazy[Animal("Cat")]
        assertEquals("Animal: Dog", result4)
    }

    @Test
    fun `test supplier lazy toString`() {
        val lazy1 = supplierLazy<String, Int> { it.length }
        assertTrue(lazy1.toString().contains("not initialized"))

        lazy1["test"]
        assertFalse(lazy1.toString().contains("not initialized"))

        val lazy2 = supplierLazy<String, Int>(typeIsolation = true) { it.length }
        assertTrue(lazy2.toString().contains("not initialized"))

        lazy2["test"]
        assertTrue(lazy2.toString().contains("typeIsolation"))
        assertTrue(lazy2.toString().contains("1 type(s)"))
    }

    @Test
    fun `test wrapped context basic isolation`() {
        var initCount = 0
        val lazy = supplierLazy<WrappedContext<Any, String>, String>(typeIsolation = true) { ctx ->
            initCount++
            "${ctx.context}:${ctx.extra}"
        }

        val c1: WrappedContext<Any, String> = WrappedContext("user" as Any, "a")
        val c2: WrappedContext<Any, String> = WrappedContext("user" as Any, "b")
        val c3: WrappedContext<Any, String> = WrappedContext(123 as Any, "x")

        // 同一个 context 类型（String），虽然 extra 不同，但因为按 context.class 做隔离，只初始化一次
        val r1 = lazy[c1]
        val r2 = lazy[c2]
        assertEquals("user:a", r1)
        assertEquals("user:a", r2)
        assertEquals(1, initCount)

        // 不同 context 类型（Int），会使用另一份缓存
        val r3 = lazy[c3]
        assertEquals("123:x", r3)
        assertEquals(2, initCount)
    }

    @Test
    fun `test wrapped context mixed with plain context`() {
        var initCount = 0
        val lazy = supplierLazy<Any, String>(typeIsolation = true) { ctx ->
            initCount++
            when (ctx) {
                is WrappedContext<*, *> -> "wrapped:${ctx.context}:${ctx.extra}"
                else -> "plain:$ctx"
            }
        }

        val plain: Any = "user"
        val wrapped1: Any = WrappedContext("user", "a")
        val wrapped2: Any = WrappedContext("user", "b")

        // 第一次：普通 String 上下文
        val rPlain1 = lazy[plain]
        assertEquals("plain:user", rPlain1)
        assertEquals(1, initCount)

        // 第二次：WrappedContext，同样的 context 类型 String，但因为 SupplierLazyWithTypeIsolationImpl
        // 对 WrappedContext 使用 context.context::class.java 作为 key，会与 plain 的 String 类型共用缓存
        val rWrapped1 = lazy[wrapped1]
        assertEquals("plain:user", rWrapped1)
        assertEquals(1, initCount)

        // 第三次：另一个 WrappedContext，仍然命中同一个 String 类型缓存
        val rWrapped2 = lazy[wrapped2]
        assertEquals("plain:user", rWrapped2)
        assertEquals(1, initCount)

        // 验证 Int 类型仍然是独立的缓存 key
        val rInt1 = lazy[123 as Any]
        assertEquals("plain:123", rInt1)
        assertEquals(2, initCount)
    }

    @Test
    fun `test wrapped context reset isolation map`() {
        var initCount = 0
        val lazy = supplierLazy<WrappedContext<Any, String>, String>(typeIsolation = true) { ctx ->
            initCount++
            "${ctx.context}:${ctx.extra}:$initCount"
        }

        val sCtx: WrappedContext<Any, String> = WrappedContext("user" as Any, "a")
        val iCtx: WrappedContext<Any, String> = WrappedContext(1 as Any, "x")

        val r1 = lazy[sCtx]
        val r2 = lazy[iCtx]
        assertEquals("user:a:1", r1)
        assertEquals("1:x:2", r2)
        assertEquals(2, initCount)

        lazy.reset()
        assertFalse(lazy.isInitialized())

        val r3 = lazy[sCtx]
        val r4 = lazy[iCtx]
        assertEquals("user:a:3", r3)
        assertEquals("1:x:4", r4)
        assertEquals(4, initCount)
    }
}