package taboolib.expansion

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransactionPropagationTest {

    private lateinit var container: TestContainer

    @BeforeEach
    fun setUp() {
        AnalyzedClass.cached.clear()
        val ds = createTestDataSource()
        container = TestContainer(ds)
        container.new<SimpleData>("simple_data")
        container.new<AllValData>("all_val_data")
    }

    @AfterEach
    fun tearDown() {
        container.close()
    }

    @Test
    fun `nested transaction reuses outer connection`() {
        val result = container.transaction {
            val op = operator("simple_data")
            op.insert(listOf(SimpleData("a", 1, "alpha")))

            // 嵌套事务应复用外层连接
            val innerResult = container.transaction {
                val innerOp = operator("simple_data")
                innerOp.insert(listOf(SimpleData("b", 2, "beta")))
                "inner-done"
            }
            assertTrue(innerResult.isSuccess)
            assertEquals("inner-done", innerResult.getOrNull())
            "outer-done"
        }
        assertTrue(result.isSuccess)
        assertEquals("outer-done", result.getOrNull())
        // 两条数据都应该被提交
        val all = container.operator("simple_data").get(SimpleData::class.java)
        assertEquals(2, all.size)
    }

    @Test
    fun `outer rollback rolls back nested transaction data`() {
        val result = container.transaction {
            val op = operator("simple_data")
            op.insert(listOf(SimpleData("a", 1, "alpha")))

            container.transaction {
                val innerOp = operator("simple_data")
                innerOp.insert(listOf(SimpleData("b", 2, "beta")))
            }

            // 外层回滚 → 内外数据都应回滚
            rollback()
        }
        assertTrue(result.isFailure)
        val all = container.operator("simple_data").get(SimpleData::class.java)
        assertEquals(0, all.size)
    }

    @Test
    fun `inner exception propagates to outer and rolls back all`() {
        val result = container.transaction {
            val op = operator("simple_data")
            op.insert(listOf(SimpleData("a", 1, "alpha")))

            val innerResult = container.transaction {
                val innerOp = operator("simple_data")
                innerOp.insert(listOf(SimpleData("b", 2, "beta")))
                error("inner-boom")
            }
            // 内层异常被捕获为 Result.failure，外层可以选择重新抛出
            throw innerResult.exceptionOrNull()!!
        }
        assertTrue(result.isFailure)
        assertEquals("inner-boom", result.exceptionOrNull()!!.message)
        val all = container.operator("simple_data").get(SimpleData::class.java)
        assertEquals(0, all.size)
    }

    @Test
    fun `inner rollback does not commit or close outer connection`() {
        val result = container.transaction {
            val op = operator("simple_data")
            op.insert(listOf(SimpleData("a", 1, "alpha")))

            // 内层标记回滚，但外层不回滚
            val innerResult = container.transaction {
                operator("simple_data").insert(listOf(SimpleData("b", 2, "beta")))
                rollback()
            }
            assertTrue(innerResult.isFailure)

            // 外层继续操作，不受内层 rollback 标记影响
            op.insert(listOf(SimpleData("c", 3, "gamma")))
            "outer-done"
        }
        // 外层正常提交（内层 rollback 只影响内层 Result，不影响外层连接）
        assertTrue(result.isSuccess)
        val all = container.operator("simple_data").get(SimpleData::class.java)
        // a, b, c 都在同一连接上，外层 commit 全部提交
        assertEquals(3, all.size)
    }

    @Test
    fun `non-transactional operator participates in active transaction`() {
        val result = container.transaction {
            val txOp = operator("simple_data")
            txOp.insert(listOf(SimpleData("a", 1, "alpha")))

            // 直接使用非事务操作器，应通过 ThreadLocal 参与当前事务
            val plainOp = container.operator("all_val_data")
            plainOp.insert(listOf(AllValData("x", "fixed")))

            rollback()
        }
        assertTrue(result.isFailure)
        // 两张表的数据都应被回滚
        assertEquals(0, container.operator("simple_data").get(SimpleData::class.java).size)
        assertEquals(0, container.operator("all_val_data").get(AllValData::class.java).size)
    }

    @Test
    fun `nested cross-table transaction commits together`() {
        val result = container.transaction {
            operator("simple_data").insert(listOf(SimpleData("a", 1, "alpha")))

            container.transaction {
                operator("all_val_data").insert(listOf(AllValData("x", "fixed")))
            }

            "ok"
        }
        assertTrue(result.isSuccess)
        assertEquals(1, container.operator("simple_data").get(SimpleData::class.java).size)
        assertEquals(1, container.operator("all_val_data").get(AllValData::class.java).size)
    }

    @Test
    fun `ThreadLocal is cleaned up after transaction`() {
        container.transaction {
            operator("simple_data").insert(listOf(SimpleData("a", 1, "alpha")))
        }
        // 事务结束后 ThreadLocal 应为 null
        assertNull(TransactionContext.currentConnection.get())
    }

    @Test
    fun `ThreadLocal is cleaned up after failed transaction`() {
        container.transaction {
            operator("simple_data").insert(listOf(SimpleData("a", 1, "alpha")))
            error("boom")
        }
        assertNull(TransactionContext.currentConnection.get())
    }
}
