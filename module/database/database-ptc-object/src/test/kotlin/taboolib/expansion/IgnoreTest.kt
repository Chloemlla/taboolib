package taboolib.expansion

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IgnoreTest {

    private lateinit var container: TestContainer

    @BeforeEach
    fun setup() {
        container = TestContainer(createTestDataSource())
    }

    @AfterEach
    fun teardown() {
        container.close()
    }

    @Test
    fun `ignored field with default value - insert and read back`() {
        val op = container.new<IgnoreWithDefaultData>()
        val data = IgnoreWithDefaultData("p1", 100, "custom_name", 99)
        op.insert(listOf(data))

        val result = op.findOne<IgnoreWithDefaultData>(IgnoreWithDefaultData::class.java, "p1")
        assertNotNull(result)
        assertEquals("p1", result!!.id)
        assertEquals(100, result.value)
        // @Ignore 字段应使用 Kotlin 默认值，而非写入时的值
        assertEquals("default_name", result.cachedName)
        assertEquals(42, result.tempScore)
    }

    @Test
    fun `ignored collection fields use Kotlin defaults`() {
        val op = container.new<IgnoreCollectionData>()
        val data = IgnoreCollectionData("c1", "test", listOf("x", "y"), setOf("a", "b"), mapOf("foo" to "bar"))
        op.insert(listOf(data))

        val result = op.findOne<IgnoreCollectionData>(IgnoreCollectionData::class.java, "c1")
        assertNotNull(result)
        assertEquals("c1", result!!.id)
        assertEquals("test", result.label)
        // @Ignore 容器字段应使用 Kotlin 默认值
        assertEquals(listOf("a", "b"), result.tempTags)
        assertEquals(setOf("x"), result.tempSet)
        assertEquals(mapOf("k" to "v"), result.tempMap)
    }

    @Test
    fun `ignored nullable field defaults to null`() {
        val op = container.new<IgnoreNullableData>()
        val data = IgnoreNullableData("n1", 50, "not_null_value")
        op.insert(listOf(data))

        val result = op.findOne<IgnoreNullableData>(IgnoreNullableData::class.java, "n1")
        assertNotNull(result)
        assertEquals("n1", result!!.id)
        assertEquals(50, result.value)
        // @Ignore 可空字段应为 null（Kotlin 默认值）
        assertNull(result.nullable)
    }

    @Test
    fun `update does not touch ignored fields`() {
        val op = container.new<IgnoreWithDefaultData>()
        op.insert(listOf(IgnoreWithDefaultData("u1", 10)))
        // 更新 value
        op.update(IgnoreWithDefaultData("u1", 20, "changed", 999))
        val result = op.findOne<IgnoreWithDefaultData>(IgnoreWithDefaultData::class.java, "u1")
        assertNotNull(result)
        assertEquals(20, result!!.value)
        // @Ignore 字段仍然是 Kotlin 默认值
        assertEquals("default_name", result.cachedName)
        assertEquals(42, result.tempScore)
    }

    @Test
    fun `analyzed class members correctly identify ignored fields`() {
        val analyzed = AnalyzedClass.of(IgnoreWithDefaultData::class.java)
        // columnMembers 不应包含 @Ignore 字段
        val columnNames = analyzed.columnMembers.map { it.propertyName }
        assertTrue("id" in columnNames)
        assertTrue("value" in columnNames)
        assertFalse("cachedName" in columnNames)
        assertFalse("tempScore" in columnNames)
        // collectionMembers 不应包含 @Ignore 的容器字段
        val analyzedColl = AnalyzedClass.of(IgnoreCollectionData::class.java)
        assertTrue(analyzedColl.collectionMembers.isEmpty())
    }
}
