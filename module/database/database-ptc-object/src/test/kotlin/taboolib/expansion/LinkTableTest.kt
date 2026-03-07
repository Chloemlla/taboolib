package taboolib.expansion

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import taboolib.expansion.orm.AnalyzedClass

class LinkTableTest {

    private lateinit var ds: HikariDataSource
    private lateinit var container: TestContainer

    @BeforeEach
    fun setUp() {
        AnalyzedClass.cached.clear()
        ds = createTestDataSource()
        container = TestContainer(ds)
        // 先创建被关联的表
        container.new<MultiKeyData>()
        // 再创建含 @LinkTable 的表
        container.new<LinkedSimpleData>()
    }

    @AfterEach
    fun tearDown() {
        ds.close()
    }

    @Test
    fun `insert and find with linked object`() {
        val linked = MultiKeyData("mk1", "catA", "subA", 100)
        container.get<MultiKeyData>().insert(listOf(linked))

        val data = LinkedSimpleData("s1", 42, linked)
        container.get<LinkedSimpleData>().insert(listOf(data))

        val found = container.get<LinkedSimpleData>().findOne(LinkedSimpleData::class.java, "s1")
        assertNotNull(found)
        assertEquals("s1", found!!.id)
        assertEquals(42, found.value)
        assertNotNull(found.testData)
        assertEquals("mk1", found.testData!!.id)
        assertEquals("catA", found.testData!!.category)
        assertEquals("subA", found.testData!!.subCategory)
        assertEquals(100, found.testData!!.score)
    }

    @Test
    fun `find with null linked object (LEFT JOIN)`() {
        // 插入一条主表数据，FK 为 null
        val data = LinkedSimpleData("s2", 10, null)
        container.get<LinkedSimpleData>().insert(listOf(data))

        val found = container.get<LinkedSimpleData>().findOne(LinkedSimpleData::class.java, "s2")
        assertNotNull(found)
        assertEquals("s2", found!!.id)
        assertEquals(10, found.value)
        assertNull(found.testData)
    }

    @Test
    fun `get all with linked objects`() {
        val linked1 = MultiKeyData("mk1", "catA", "subA", 100)
        val linked2 = MultiKeyData("mk2", "catB", "subB", 200)
        container.get<MultiKeyData>().insert(listOf(linked1, linked2))

        container.get<LinkedSimpleData>().insert(listOf(
            LinkedSimpleData("s1", 1, linked1),
            LinkedSimpleData("s2", 2, linked2),
            LinkedSimpleData("s3", 3, null)
        ))

        val all = container.get<LinkedSimpleData>().get(LinkedSimpleData::class.java)
        assertEquals(3, all.size)

        val s1 = all.first { it.id == "s1" }
        assertNotNull(s1.testData)
        assertEquals("mk1", s1.testData!!.id)

        val s2 = all.first { it.id == "s2" }
        assertNotNull(s2.testData)
        assertEquals("mk2", s2.testData!!.id)

        val s3 = all.first { it.id == "s3" }
        assertNull(s3.testData)
    }

    @Test
    fun `findByIds with linked objects`() {
        val linked = MultiKeyData("mk1", "catA", "subA", 100)
        container.get<MultiKeyData>().insert(listOf(linked))

        container.get<LinkedSimpleData>().insert(listOf(
            LinkedSimpleData("s1", 1, linked),
            LinkedSimpleData("s2", 2, null)
        ))

        val results = container.get<LinkedSimpleData>().findByIds(LinkedSimpleData::class.java, listOf("s1", "s2"))
        assertEquals(2, results.size)
    }

    @Test
    fun `sort with linked objects`() {
        val linked = MultiKeyData("mk1", "catA", "subA", 100)
        container.get<MultiKeyData>().insert(listOf(linked))

        container.get<LinkedSimpleData>().insert(listOf(
            LinkedSimpleData("s1", 10, linked),
            LinkedSimpleData("s2", 20, null),
            LinkedSimpleData("s3", 5, linked)
        ))

        val sorted = container.get<LinkedSimpleData>().sortDescending(LinkedSimpleData::class.java, "value", 3)
        assertEquals(3, sorted.size)
        assertEquals("s2", sorted[0].id)
        assertEquals("s1", sorted[1].id)
        assertEquals("s3", sorted[2].id)
    }

    @Test
    fun `cascade save - auto insert linked object`() {
        // 不手动插入 MultiKeyData，直接插入带关联对象的 LinkedSimpleData
        val linked = MultiKeyData("mk_auto", "catX", "subX", 999)
        val data = LinkedSimpleData("s_auto", 77, linked)
        container.get<LinkedSimpleData>().insert(listOf(data))

        // 验证关联对象被自动插入到 multi_key_data 表
        val linkedFound = container.get<MultiKeyData>().findOne(MultiKeyData::class.java, "mk_auto")
        assertNotNull(linkedFound)
        assertEquals("catX", linkedFound!!.category)
        assertEquals(999, linkedFound.score)

        // 验证主对象能通过 JOIN 查到完整关联
        val found = container.get<LinkedSimpleData>().findOne(LinkedSimpleData::class.java, "s_auto")
        assertNotNull(found)
        assertNotNull(found!!.testData)
        assertEquals("mk_auto", found.testData!!.id)
        assertEquals(999, found.testData!!.score)
    }

    @Test
    fun `cascade save - auto update existing linked object`() {
        // 先手动插入一条 MultiKeyData
        val linked = MultiKeyData("mk_upd", "catOld", "subOld", 100)
        container.get<MultiKeyData>().insert(listOf(linked))

        // 用修改后的关联对象插入主表 → 应自动更新 multi_key_data
        val updatedLinked = MultiKeyData("mk_upd", "catOld", "subOld", 999)
        val data = LinkedSimpleData("s_upd", 50, updatedLinked)
        container.get<LinkedSimpleData>().insert(listOf(data))

        // 验证关联对象的 score 已被更新
        val linkedFound = container.get<MultiKeyData>().findOne(MultiKeyData::class.java, "mk_upd")
        assertNotNull(linkedFound)
        assertEquals(999, linkedFound!!.score)
    }

    // === 嵌套关联测试 ===

    @Test
    fun `nested - insert and find three levels`() {
        val nestedContainer = TestContainer(ds)
        nestedContainer.new<Level3Data>()
        nestedContainer.new<Level2Data>()
        nestedContainer.new<Level1Data>()

        val l3 = Level3Data("l3-1", "deep info")
        val l2 = Level2Data("l2-1", "middle", l3)
        val l1 = Level1Data("l1-1", "top", l2)

        nestedContainer.get<Level1Data>().insert(listOf(l1))

        val found = nestedContainer.get<Level1Data>().findOne(Level1Data::class.java, "l1-1")
        assertNotNull(found)
        assertEquals("l1-1", found!!.id)
        assertEquals("top", found.title)
        assertNotNull(found.level2)
        assertEquals("l2-1", found.level2!!.id)
        assertEquals("middle", found.level2!!.name)
        assertNotNull(found.level2!!.level3)
        assertEquals("l3-1", found.level2!!.level3!!.id)
        assertEquals("deep info", found.level2!!.level3!!.info)
    }

    @Test
    fun `nested - middle layer null`() {
        val nestedContainer = TestContainer(ds)
        nestedContainer.new<Level3Data>()
        nestedContainer.new<Level2Data>()
        nestedContainer.new<Level1Data>()

        val l1 = Level1Data("l1-null", "no child", null)
        nestedContainer.get<Level1Data>().insert(listOf(l1))

        val found = nestedContainer.get<Level1Data>().findOne(Level1Data::class.java, "l1-null")
        assertNotNull(found)
        assertEquals("l1-null", found!!.id)
        assertNull(found.level2)
    }

    @Test
    fun `nested - leaf layer null`() {
        val nestedContainer = TestContainer(ds)
        nestedContainer.new<Level3Data>()
        nestedContainer.new<Level2Data>()
        nestedContainer.new<Level1Data>()

        val l2 = Level2Data("l2-noleaf", "has no leaf", null)
        val l1 = Level1Data("l1-noleaf", "partial", l2)

        nestedContainer.get<Level1Data>().insert(listOf(l1))

        val found = nestedContainer.get<Level1Data>().findOne(Level1Data::class.java, "l1-noleaf")
        assertNotNull(found)
        assertNotNull(found!!.level2)
        assertEquals("l2-noleaf", found.level2!!.id)
        assertEquals("has no leaf", found.level2!!.name)
        assertNull(found.level2!!.level3)
    }

    @Test
    fun `nested - cascade save inserts all levels`() {
        val nestedContainer = TestContainer(ds)
        nestedContainer.new<Level3Data>()
        nestedContainer.new<Level2Data>()
        nestedContainer.new<Level1Data>()

        val l3 = Level3Data("l3-cas", "cascade deep")
        val l2 = Level2Data("l2-cas", "cascade mid", l3)
        val l1 = Level1Data("l1-cas", "cascade top", l2)

        // 只插入 Level1Data，Level2Data 和 Level3Data 应被级联保存
        nestedContainer.get<Level1Data>().insert(listOf(l1))

        // 验证 Level3Data 被自动插入
        val foundL3 = nestedContainer.get<Level3Data>().findOne(Level3Data::class.java, "l3-cas")
        assertNotNull(foundL3)
        assertEquals("cascade deep", foundL3!!.info)

        // 验证 Level2Data 被自动插入
        val foundL2 = nestedContainer.get<Level2Data>().findOne(Level2Data::class.java, "l2-cas")
        assertNotNull(foundL2)
        assertEquals("cascade mid", foundL2!!.name)

        // 验证完整的三层 JOIN 查询
        val foundL1 = nestedContainer.get<Level1Data>().findOne(Level1Data::class.java, "l1-cas")
        assertNotNull(foundL1)
        assertNotNull(foundL1!!.level2)
        assertNotNull(foundL1.level2!!.level3)
        assertEquals("l3-cas", foundL1.level2!!.level3!!.id)
    }

    @Test
    fun `nested - get all with mixed nesting`() {
        val nestedContainer = TestContainer(ds)
        nestedContainer.new<Level3Data>()
        nestedContainer.new<Level2Data>()
        nestedContainer.new<Level1Data>()

        val l3 = Level3Data("l3-mix", "deep")
        val l2Full = Level2Data("l2-full", "full chain", l3)
        val l2Partial = Level2Data("l2-part", "no leaf", null)

        nestedContainer.get<Level1Data>().insert(listOf(
            Level1Data("l1-a", "full", l2Full),
            Level1Data("l1-b", "partial", l2Partial),
            Level1Data("l1-c", "empty", null)
        ))

        val all = nestedContainer.get<Level1Data>().get(Level1Data::class.java)
        assertEquals(3, all.size)

        val a = all.first { it.id == "l1-a" }
        assertNotNull(a.level2)
        assertNotNull(a.level2!!.level3)
        assertEquals("l3-mix", a.level2!!.level3!!.id)

        val b = all.first { it.id == "l1-b" }
        assertNotNull(b.level2)
        assertNull(b.level2!!.level3)

        val c = all.first { it.id == "l1-c" }
        assertNull(c.level2)
    }

    @Test
    fun `nested - verify SQL and ResultSet columns`() {
        val nestedContainer = TestContainer(ds)
        nestedContainer.new<Level3Data>()
        nestedContainer.new<Level2Data>()
        nestedContainer.new<Level1Data>()

        val l3 = Level3Data("l3-sql", "deep")
        val l2 = Level2Data("l2-sql", "mid", l3)
        val l1 = Level1Data("l1-sql", "top", l2)

        nestedContainer.get<Level1Data>().insert(listOf(l1))

        // 直接用 raw SQL 验证数据和 JOIN
        ds.connection.use { conn ->
            // 验证 level3_data 表有数据
            conn.prepareStatement("SELECT * FROM level3_data WHERE id = ?").use { stmt ->
                stmt.setString(1, "l3-sql")
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next(), "level3_data should have l3-sql")
                    assertEquals("deep", rs.getString("info"))
                }
            }
            // 验证 level2_data 表有数据且 FK 正确
            conn.prepareStatement("SELECT * FROM level2_data WHERE id = ?").use { stmt ->
                stmt.setString(1, "l2-sql")
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next(), "level2_data should have l2-sql")
                    assertEquals("mid", rs.getString("name"))
                    assertEquals("l3-sql", rs.getString("level3_id"), "FK level3_id should be l3-sql")
                }
            }
            // 验证三层 JOIN SQL
            val joinSql = """
                SELECT `level1_data`.`id` AS `id`, `level1_data`.`title` AS `title`,
                       `__t0`.`id` AS `__link__level2_id__id`, `__t0`.`name` AS `__link__level2_id__name`,
                       `__t1`.`id` AS `__link__level2_id____link__level3_id__id`,
                       `__t1`.`info` AS `__link__level2_id____link__level3_id__info`
                FROM `level1_data`
                LEFT JOIN `level2_data` AS `__t0` ON `level1_data`.`level2_id` = `__t0`.`id`
                LEFT JOIN `level3_data` AS `__t1` ON `__t0`.`level3_id` = `__t1`.`id`
                WHERE `level1_data`.`id` = ?
            """.trimIndent()
            conn.prepareStatement(joinSql).use { stmt ->
                stmt.setString(1, "l1-sql")
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next(), "JOIN query should return a row")
                    // 验证所有列别名可读
                    assertEquals("l1-sql", rs.getObject("id"))
                    assertEquals("top", rs.getObject("title"))
                    assertEquals("l2-sql", rs.getObject("__link__level2_id__id"))
                    assertEquals("mid", rs.getObject("__link__level2_id__name"))
                    assertEquals("l3-sql", rs.getObject("__link__level2_id____link__level3_id__id"),
                        "Nested link column should be readable")
                    assertEquals("deep", rs.getObject("__link__level2_id____link__level3_id__info"),
                        "Nested link column should be readable")
                }
            }
        }
    }
}
