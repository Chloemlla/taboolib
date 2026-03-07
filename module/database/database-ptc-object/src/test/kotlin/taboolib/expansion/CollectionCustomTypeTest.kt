package taboolib.expansion

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import taboolib.expansion.orm.AnalyzedClass

class CollectionCustomTypeTest {

    private lateinit var container: TestContainer

    @BeforeEach
    fun setup() {
        container = TestContainer(createTestDataSource())
        // 手动注册集合 CustomType（测试环境不走 TabooLib 生命周期）
        CustomTypeFactory.registeredCollectionTypes.clear()
        CustomTypeFactory.registeredCollectionTypes
            .getOrPut(List::class.java) { java.util.concurrent.ConcurrentHashMap() }[ItemData::class.java] = ItemDataListType
        CustomTypeFactory.registeredCollectionTypes
            .getOrPut(Set::class.java) { java.util.concurrent.ConcurrentHashMap() }[ItemData::class.java] = ItemDataSetType
        CustomTypeFactory.registeredCollectionTypes
            .getOrPut(Map::class.java) { java.util.concurrent.ConcurrentHashMap() }[ItemData::class.java] = ItemDataMapType
        // 清除 AnalyzedClass 缓存，确保重新分析
        AnalyzedClass.cached.clear()
    }

    @AfterEach
    fun teardown() {
        container.close()
        CustomTypeFactory.registeredCollectionTypes.clear()
        CustomTypeFactory.registeredTypes.remove(ItemData::class.java)
        AnalyzedClass.cached.clear()
    }

    // === Feature 1: 扁平化集合（单列存储） ===

    @Test
    fun `flattened list - insert and read back`() {
        val op = container.new<FlattenedListData>()
        val items = listOf(ItemData("sword", 1), ItemData("shield", 2))
        op.insert(listOf(FlattenedListData("p1", "player1", items)))

        val result = op.findOne<FlattenedListData>(FlattenedListData::class.java, "p1")
        assertNotNull(result)
        assertEquals("p1", result!!.id)
        assertEquals("player1", result.label)
        assertEquals(items, result.items)
    }

    @Test
    fun `flattened set - insert and read back`() {
        val op = container.new<FlattenedSetData>()
        val items = setOf(ItemData("potion", 5), ItemData("arrow", 64))
        op.insert(listOf(FlattenedSetData("s1", items)))

        val result = op.findOne<FlattenedSetData>(FlattenedSetData::class.java, "s1")
        assertNotNull(result)
        assertEquals(items, result!!.items)
    }

    @Test
    fun `flattened map - insert and read back`() {
        val op = container.new<FlattenedMapData>()
        val items = mapOf("slot1" to ItemData("helmet", 1), "slot2" to ItemData("boots", 1))
        op.insert(listOf(FlattenedMapData("m1", items)))

        val result = op.findOne<FlattenedMapData>(FlattenedMapData::class.java, "m1")
        assertNotNull(result)
        assertEquals(items, result!!.items)
    }

    @Test
    fun `flattened list - update preserves data`() {
        val op = container.new<FlattenedListData>()
        val items1 = listOf(ItemData("sword", 1))
        op.insert(listOf(FlattenedListData("u1", "before", items1)))

        val items2 = listOf(ItemData("axe", 3), ItemData("bow", 1))
        op.update(FlattenedListData("u1", "after", items2))

        val result = op.findOne<FlattenedListData>(FlattenedListData::class.java, "u1")
        assertNotNull(result)
        assertEquals("after", result!!.label)
        assertEquals(items2, result.items)
    }

    @Test
    fun `flattened collection does not create sub-table`() {
        val analyzed = AnalyzedClass.of(FlattenedListData::class.java)
        // items 应该是 isFlattenedCollection=true, isCollection=false
        val itemsMember = analyzed.members.first { it.propertyName == "items" }
        assertTrue(itemsMember.isFlattenedCollection)
        assertFalse(itemsMember.isCollection)
        // collectionMembers 不应包含 items
        assertTrue(analyzed.collectionMembers.isEmpty())
        // columnMembers 应包含 items
        assertTrue(analyzed.columnMembers.any { it.propertyName == "items" })
    }

    @Test
    fun `flattened list - batch upsert`() {
        val op = container.new<FlattenedListData>()
        val data1 = FlattenedListData("b1", "first", listOf(ItemData("a", 1)))
        val data2 = FlattenedListData("b2", "second", listOf(ItemData("b", 2)))
        op.insert(listOf(data1, data2))

        // upsert: update b1, insert b3
        val updated1 = FlattenedListData("b1", "updated", listOf(ItemData("c", 3)))
        val new3 = FlattenedListData("b3", "third", listOf(ItemData("d", 4)))
        op.upsert(listOf(updated1, new3))

        val r1 = op.findOne<FlattenedListData>(FlattenedListData::class.java, "b1")
        assertEquals("updated", r1!!.label)
        assertEquals(listOf(ItemData("c", 3)), r1.items)

        val r3 = op.findOne<FlattenedListData>(FlattenedListData::class.java, "b3")
        assertEquals("third", r3!!.label)
        assertEquals(listOf(ItemData("d", 4)), r3.items)
    }

    // === Feature 2: 子表元素 CustomType 序列化/反序列化 ===

    @Test
    fun `sub-table element CustomType - list insert and read back`() {
        // 注册普通 CustomType（元素级别），移除集合 CustomType 以确保走子表
        CustomTypeFactory.registeredCollectionTypes.clear()
        CustomTypeFactory.registeredTypes[ItemData::class.java] = ItemDataType
        AnalyzedClass.cached.clear()

        val op = container.new<ElementCustomTypeListData>()
        val items = listOf(ItemData("gem", 10), ItemData("ore", 32))
        op.insert(listOf(ElementCustomTypeListData("e1", "test", items)))

        val result = op.findOne<ElementCustomTypeListData>(ElementCustomTypeListData::class.java, "e1")
        assertNotNull(result)
        assertEquals(2, result!!.items.size)
        assertEquals(ItemData("gem", 10), result.items[0])
        assertEquals(ItemData("ore", 32), result.items[1])
    }

    @Test
    fun `sub-table element CustomType - update and read back`() {
        CustomTypeFactory.registeredCollectionTypes.clear()
        CustomTypeFactory.registeredTypes[ItemData::class.java] = ItemDataType
        AnalyzedClass.cached.clear()

        val op = container.new<ElementCustomTypeListData>()
        op.insert(listOf(ElementCustomTypeListData("e2", "before", listOf(ItemData("old", 1)))))

        val updated = listOf(ItemData("new1", 5), ItemData("new2", 10))
        op.update(ElementCustomTypeListData("e2", "after", updated))

        val result = op.findOne<ElementCustomTypeListData>(ElementCustomTypeListData::class.java, "e2")
        assertNotNull(result)
        assertEquals(updated, result!!.items)
    }

    // === 混合测试 ===

    @Test
    fun `mixed flattened and sub-table collections`() {
        val op = container.new<MixedFlatAndSubTableData>()
        val flatItems = listOf(ItemData("flat1", 1), ItemData("flat2", 2))
        val tags = listOf("tag1", "tag2", "tag3")
        op.insert(listOf(MixedFlatAndSubTableData("mix1", flatItems, tags)))

        val result = op.findOne<MixedFlatAndSubTableData>(MixedFlatAndSubTableData::class.java, "mix1")
        assertNotNull(result)
        assertEquals(flatItems, result!!.flatItems)
        assertEquals(tags, result.tags)
    }

    @Test
    fun `mixed - analyzed class correctly identifies members`() {
        val analyzed = AnalyzedClass.of(MixedFlatAndSubTableData::class.java)
        val flatMember = analyzed.members.first { it.propertyName == "flatItems" }
        val tagsMember = analyzed.members.first { it.propertyName == "tags" }

        assertTrue(flatMember.isFlattenedCollection)
        assertFalse(flatMember.isCollection)

        assertFalse(tagsMember.isFlattenedCollection)
        assertTrue(tagsMember.isCollection)

        // columnMembers 包含 flatItems 但不包含 tags
        assertTrue(analyzed.columnMembers.any { it.propertyName == "flatItems" })
        assertFalse(analyzed.columnMembers.any { it.propertyName == "tags" })

        // collectionMembers 包含 tags 但不包含 flatItems
        assertTrue(analyzed.collectionMembers.any { it.propertyName == "tags" })
        assertFalse(analyzed.collectionMembers.any { it.propertyName == "flatItems" })
    }
}
