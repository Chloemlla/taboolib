package taboolib.expansion

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import taboolib.expansion.orm.AnalyzedClass

class CollectionTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var container: TestContainer

    @BeforeEach
    fun setup() {
        AnalyzedClass.cached.clear()
        dataSource = createTestDataSource()
        container = TestContainer(dataSource)
    }

    @AfterEach
    fun teardown() {
        container.close()
    }

    @Test
    fun `List - insert and query`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        val data = ListData("p1", "player1", listOf("tag1", "tag2", "tag3"))
        op.insert(listOf(data))
        val result = op.findOne(ListData::class.java, "p1")!!
        assertEquals("p1", result.id)
        assertEquals("player1", result.label)
        assertEquals(listOf("tag1", "tag2", "tag3"), result.tags)
    }

    @Test
    fun `List - preserves order`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        val data = ListData("p1", "test", listOf("c", "a", "b"))
        op.insert(listOf(data))
        val result = op.findOne(ListData::class.java, "p1")!!
        assertEquals(listOf("c", "a", "b"), result.tags)
    }

    @Test
    fun `Set - insert and query`() {
        container.new<SetData>()
        val op = container.get<SetData>()
        val data = SetData("s1", setOf("10", "20", "30"))
        op.insert(listOf(data))
        val result = op.findOne(SetData::class.java, "s1")!!
        assertEquals(setOf("10", "20", "30"), result.scores)
    }

    @Test
    fun `Map - insert and query`() {
        container.new<MapData>()
        val op = container.get<MapData>()
        val data = MapData("m1", mapOf("key1" to "val1", "key2" to "val2"))
        op.insert(listOf(data))
        val result = op.findOne(MapData::class.java, "m1")!!
        assertEquals(mapOf("key1" to "val1", "key2" to "val2"), result.props)
    }

    @Test
    fun `Mixed collections - insert and query`() {
        container.new<MixedCollectionData>()
        val op = container.get<MixedCollectionData>()
        val data = MixedCollectionData(
            "x1", "mixed",
            listOf("a", "b"),
            setOf("f1", "f2"),
            mapOf("k" to "v")
        )
        op.insert(listOf(data))
        val result = op.findOne(MixedCollectionData::class.java, "x1")!!
        assertEquals("mixed", result.name)
        assertEquals(listOf("a", "b"), result.tags)
        assertEquals(setOf("f1", "f2"), result.uniqueFlags)
        assertEquals(mapOf("k" to "v"), result.metadata)
    }

    @Test
    fun `Update syncs collection data`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        op.insert(listOf(ListData("p1", "v1", listOf("old1", "old2"))))
        // update with new tags
        op.update(ListData("p1", "v2", listOf("new1", "new2", "new3")))
        val result = op.findOne(ListData::class.java, "p1")!!
        assertEquals("v2", result.label)
        assertEquals(listOf("new1", "new2", "new3"), result.tags)
    }

    @Test
    fun `Delete cascades to collection tables`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        op.insert(listOf(ListData("p1", "v1", listOf("t1", "t2"))))
        op.delete(ListData::class.java, "p1")
        val result = op.findOne(ListData::class.java, "p1")
        assertEquals(null, result)
    }

    @Test
    fun `Empty collection`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        op.insert(listOf(ListData("p1", "empty", emptyList())))
        val result = op.findOne(ListData::class.java, "p1")!!
        assertEquals(emptyList<String>(), result.tags)
    }

    @Test
    fun `Multiple records with collections`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        op.insert(listOf(
            ListData("p1", "first", listOf("a", "b")),
            ListData("p2", "second", listOf("c", "d", "e"))
        ))
        val results = op.get(ListData::class.java) {}
        assertEquals(2, results.size)
        val r1 = results.first { it.id == "p1" }
        val r2 = results.first { it.id == "p2" }
        assertEquals(listOf("a", "b"), r1.tags)
        assertEquals(listOf("c", "d", "e"), r2.tags)
    }

    // === Accessor 测试 ===

    @Test
    fun `MapAccessor - put, get, containsKey, remove, size, clear`() {
        container.new<MapData>()
        val op = container.get<MapData>()
        op.insert(listOf(MapData("m1", mapOf("k1" to "v1"))))
        val map = op.mapAccessor("m1", "props")
        // get existing
        assertEquals("v1", map["k1"])
        assertEquals(1, map.size)
        assertTrue(map.containsKey("k1"))
        // put new
        map["k2"] = "v2"
        assertEquals("v2", map["k2"])
        assertEquals(2, map.size)
        // put overwrite
        map["k1"] = "v1_new"
        assertEquals("v1_new", map["k1"])
        assertEquals(2, map.size)
        // remove
        val removed = map.remove("k1")
        assertEquals("v1_new", removed)
        assertEquals(1, map.size)
        assertFalse(map.containsKey("k1"))
        // clear
        map.clear()
        assertEquals(0, map.size)
    }

    @Test
    fun `MapAccessor - entries iteration`() {
        container.new<MapData>()
        val op = container.get<MapData>()
        op.insert(listOf(MapData("m1", mapOf("a" to "1", "b" to "2"))))
        val map = op.mapAccessor("m1", "props")
        val entries = map.entries.associate { it.key to it.value }
        assertEquals(mapOf("a" to "1", "b" to "2"), entries)
    }

    @Test
    fun `ListAccessor - get, add, set, removeAt, size, contains`() {
        container.new<ListData>()
        val op = container.get<ListData>()
        op.insert(listOf(ListData("p1", "test", listOf("a", "b", "c"))))
        val list = op.listAccessor("p1", "tags")
        // get
        assertEquals("a", list[0])
        assertEquals("b", list[1])
        assertEquals("c", list[2])
        assertEquals(3, list.size)
        // contains
        assertTrue(list.contains("b"))
        assertFalse(list.contains("z"))
        // add at end
        list.add("d")
        assertEquals(4, list.size)
        assertEquals("d", list[3])
        // add at index
        list.add(1, "x")
        assertEquals(5, list.size)
        assertEquals("a", list[0])
        assertEquals("x", list[1])
        assertEquals("b", list[2])
        // set
        val old = list.set(0, "A")
        assertEquals("a", old)
        assertEquals("A", list[0])
        // removeAt
        val removedVal = list.removeAt(1) // remove "x"
        assertEquals("x", removedVal)
        assertEquals(4, list.size)
        assertEquals("A", list[0])
        assertEquals("b", list[1])
    }

    @Test
    fun `SetAccessor - add, contains, remove, size, clear, iterator`() {
        container.new<SetData>()
        val op = container.get<SetData>()
        op.insert(listOf(SetData("s1", setOf("10", "20"))))
        val set = op.setAccessor("s1", "scores")
        // initial state
        assertEquals(2, set.size)
        assertTrue(set.contains("10"))
        assertTrue(set.contains("20"))
        assertFalse(set.contains("30"))
        // add new
        assertTrue(set.add("30"))
        assertEquals(3, set.size)
        assertTrue(set.contains("30"))
        // add duplicate
        assertFalse(set.add("10"))
        assertEquals(3, set.size)
        // remove
        assertTrue(set.remove("20"))
        assertEquals(2, set.size)
        assertFalse(set.contains("20"))
        // remove non-existent
        assertFalse(set.remove("999"))
        // iterator
        val values = set.iterator().asSequence().toSet()
        assertEquals(setOf("10", "30"), values)
        // clear
        set.clear()
        assertEquals(0, set.size)
    }

    @Test
    fun `Accessor - verify database persistence`() {
        container.new<MapData>()
        val op = container.get<MapData>()
        op.insert(listOf(MapData("m1", emptyMap())))
        // write via accessor
        val map = op.mapAccessor("m1", "props")
        map["persistent_key"] = "persistent_val"
        // verify via full object read
        val result = op.findOne(MapData::class.java, "m1")!!
        assertEquals(mapOf("persistent_key" to "persistent_val"), result.props)
    }
}