package taboolib.expansion

import taboolib.module.database.asFormattedColumnName
import taboolib.module.database.setupQuoterForHost
import java.sql.Connection
import javax.sql.DataSource

/**
 * 基于数据库子表的 MutableMap 代理。
 *
 * 所有操作直接转化为对容器子表的 SQL 操作，不在内存中缓存数据。
 *
 * @param dataSource 数据源
 * @param info 容器子表信息
 * @param parentId 父记录 ID
 * @param sharedConnection 事务共享连接（可选）
 */
class DatabaseMap(
    private val dataSource: DataSource,
    private val info: CollectionTableInfo,
    private val parentId: Any,
    private val sharedConnection: Connection? = null
) : AbstractMutableMap<String, String?>() {

    private val tableName get() = info.tableName.asFormattedColumnName()
    private val fkColumn get() = info.fkColumnName.asFormattedColumnName()
    private val mapKeyCol get() = "map_key".asFormattedColumnName()
    private val mapValueCol get() = "map_value".asFormattedColumnName()

    private inline fun <T> withConn(block: (Connection) -> T): T {
        setupQuoterForHost(info.table.host)
        return if (sharedConnection != null) {
            block(sharedConnection)
        } else {
            dataSource.connection.use { block(it) }
        }
    }

    override val size: Int
        get() = withConn { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    override fun containsKey(key: String): Boolean {
        return withConn { conn ->
            conn.prepareStatement("SELECT 1 FROM $tableName WHERE $fkColumn = ? AND $mapKeyCol = ? LIMIT 1").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setString(2, key)
                stmt.executeQuery().use { it.next() }
            }
        }
    }

    override fun get(key: String): String? {
        return withConn { conn ->
            conn.prepareStatement("SELECT $mapValueCol FROM $tableName WHERE $fkColumn = ? AND $mapKeyCol = ? LIMIT 1").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setString(2, key)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
    }

    override fun put(key: String, value: String?): String? {
        val old = get(key)
        withConn { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE $fkColumn = ? AND $mapKeyCol = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setString(2, key)
                stmt.executeUpdate()
            }
            conn.prepareStatement("INSERT INTO $tableName ($fkColumn, $mapKeyCol, $mapValueCol) VALUES (?, ?, ?)").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setString(2, key)
                stmt.setString(3, value)
                stmt.executeUpdate()
            }
        }
        return old
    }

    override fun remove(key: String): String? {
        val old = get(key)
        if (old != null || containsKey(key)) {
            withConn { conn ->
                conn.prepareStatement("DELETE FROM $tableName WHERE $fkColumn = ? AND $mapKeyCol = ?").use { stmt ->
                    stmt.setObject(1, parentId)
                    stmt.setString(2, key)
                    stmt.executeUpdate()
                }
            }
        }
        return old
    }

    override fun clear() {
        withConn { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.executeUpdate()
            }
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<String, String?>>
        get() {
            val list = withConn { conn ->
                conn.prepareStatement("SELECT $mapKeyCol, $mapValueCol FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                    stmt.setObject(1, parentId)
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.getString(1) to rs.getString(2))
                            }
                        }
                    }
                }
            }
            return DatabaseMapEntrySet(list)
        }

    private inner class DatabaseMapEntrySet(
        pairs: List<Pair<String, String?>>
    ) : AbstractMutableSet<MutableMap.MutableEntry<String, String?>>() {

        private val snapshot = pairs.map { DatabaseMapEntry(it.first, it.second) }.toMutableList()

        override val size: Int get() = snapshot.size

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, String?>> {
            return object : MutableIterator<MutableMap.MutableEntry<String, String?>> {
                private val inner = snapshot.iterator()
                private var last: DatabaseMapEntry? = null
                override fun hasNext() = inner.hasNext()
                override fun next(): MutableMap.MutableEntry<String, String?> {
                    val e = inner.next()
                    last = e
                    return e
                }
                override fun remove() {
                    val e = last ?: throw IllegalStateException()
                    this@DatabaseMap.remove(e.key)
                    last = null
                }
            }
        }

        override fun add(element: MutableMap.MutableEntry<String, String?>): Boolean {
            put(element.key, element.value)
            return true
        }
    }

    private inner class DatabaseMapEntry(
        override val key: String,
        override var value: String?
    ) : MutableMap.MutableEntry<String, String?> {
        override fun setValue(newValue: String?): String? {
            val old = value
            value = newValue
            this@DatabaseMap.put(key, newValue)
            return old
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int = key.hashCode() xor (value?.hashCode() ?: 0)
    }
}

/**
 * 基于数据库子表的 MutableList 代理。
 *
 * 使用 `sort_order` 列维护元素顺序，所有操作直接转化为 SQL。
 *
 * @param dataSource 数据源
 * @param info 容器子表信息
 * @param parentId 父记录 ID
 * @param sharedConnection 事务共享连接（可选）
 */
class DatabaseList(
    private val dataSource: DataSource,
    private val info: CollectionTableInfo,
    private val parentId: Any,
    private val sharedConnection: Connection? = null
) : AbstractMutableList<String?>() {

    private val tableName get() = info.tableName.asFormattedColumnName()
    private val fkColumn get() = info.fkColumnName.asFormattedColumnName()
    private val valueCol get() = "value".asFormattedColumnName()
    private val sortOrderCol get() = "sort_order".asFormattedColumnName()

    private inline fun <T> withConn(block: (Connection) -> T): T {
        setupQuoterForHost(info.table.host)
        return if (sharedConnection != null) {
            block(sharedConnection)
        } else {
            dataSource.connection.use { block(it) }
        }
    }

    override val size: Int
        get() = withConn { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    override fun get(index: Int): String? {
        return withConn { conn ->
            conn.prepareStatement("SELECT $valueCol FROM $tableName WHERE $fkColumn = ? ORDER BY $sortOrderCol LIMIT 1 OFFSET ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setInt(2, index)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else throw IndexOutOfBoundsException("Index: $index, Size: $size")
                }
            }
        }
    }

    override fun set(index: Int, element: String?): String? {
        val old = get(index)
        withConn { conn ->
            val sortOrder = conn.prepareStatement(
                "SELECT $sortOrderCol FROM $tableName WHERE $fkColumn = ? ORDER BY $sortOrderCol LIMIT 1 OFFSET ?"
            ).use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setInt(2, index)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else throw IndexOutOfBoundsException("Index: $index")
                }
            }
            conn.prepareStatement("UPDATE $tableName SET $valueCol = ? WHERE $fkColumn = ? AND $sortOrderCol = ?").use { stmt ->
                stmt.setString(1, element)
                stmt.setObject(2, parentId)
                stmt.setInt(3, sortOrder)
                stmt.executeUpdate()
            }
        }
        return old
    }

    override fun add(index: Int, element: String?) {
        val currentSize = size
        if (index < 0 || index > currentSize) throw IndexOutOfBoundsException("Index: $index, Size: $currentSize")
        withConn { conn ->
            conn.prepareStatement("UPDATE $tableName SET $sortOrderCol = $sortOrderCol + 1 WHERE $fkColumn = ? AND $sortOrderCol >= ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setInt(2, index)
                stmt.executeUpdate()
            }
            conn.prepareStatement("INSERT INTO $tableName ($fkColumn, $valueCol, $sortOrderCol) VALUES (?, ?, ?)").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setString(2, element)
                stmt.setInt(3, index)
                stmt.executeUpdate()
            }
        }
    }

    override fun removeAt(index: Int): String? {
        val old = get(index)
        withConn { conn ->
            val sortOrder = conn.prepareStatement(
                "SELECT $sortOrderCol FROM $tableName WHERE $fkColumn = ? ORDER BY $sortOrderCol LIMIT 1 OFFSET ?"
            ).use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setInt(2, index)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else throw IndexOutOfBoundsException("Index: $index")
                }
            }
            conn.prepareStatement("DELETE FROM $tableName WHERE $fkColumn = ? AND $sortOrderCol = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setInt(2, sortOrder)
                stmt.executeUpdate()
            }
            conn.prepareStatement("UPDATE $tableName SET $sortOrderCol = $sortOrderCol - 1 WHERE $fkColumn = ? AND $sortOrderCol > ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setInt(2, sortOrder)
                stmt.executeUpdate()
            }
        }
        return old
    }

    override fun contains(element: String?): Boolean {
        return withConn { conn ->
            val sql = if (element == null) {
                "SELECT 1 FROM $tableName WHERE $fkColumn = ? AND $valueCol IS NULL LIMIT 1"
            } else {
                "SELECT 1 FROM $tableName WHERE $fkColumn = ? AND $valueCol = ? LIMIT 1"
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, parentId)
                if (element != null) stmt.setString(2, element)
                stmt.executeQuery().use { it.next() }
            }
        }
    }
}

/**
 * 基于数据库子表的 MutableSet 代理。
 *
 * 所有操作直接转化为对容器子表的 SQL 操作。
 * 唯一性保证通过 INSERT 前检查实现。
 *
 * @param dataSource 数据源
 * @param info 容器子表信息
 * @param parentId 父记录 ID
 * @param sharedConnection 事务共享连接（可选）
 */
class DatabaseSet(
    private val dataSource: DataSource,
    private val info: CollectionTableInfo,
    private val parentId: Any,
    private val sharedConnection: Connection? = null
) : AbstractMutableSet<String?>() {

    private val tableName get() = info.tableName.asFormattedColumnName()
    private val fkColumn get() = info.fkColumnName.asFormattedColumnName()
    private val valueCol get() = "value".asFormattedColumnName()

    private inline fun <T> withConn(block: (Connection) -> T): T {
        setupQuoterForHost(info.table.host)
        return if (sharedConnection != null) {
            block(sharedConnection)
        } else {
            dataSource.connection.use { block(it) }
        }
    }

    override val size: Int
        get() = withConn { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    override fun add(element: String?): Boolean {
        if (contains(element)) return false
        withConn { conn ->
            conn.prepareStatement("INSERT INTO $tableName ($fkColumn, $valueCol) VALUES (?, ?)").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.setString(2, element)
                stmt.executeUpdate()
            }
        }
        return true
    }

    override fun contains(element: String?): Boolean {
        return withConn { conn ->
            val sql = if (element == null) {
                "SELECT 1 FROM $tableName WHERE $fkColumn = ? AND $valueCol IS NULL LIMIT 1"
            } else {
                "SELECT 1 FROM $tableName WHERE $fkColumn = ? AND $valueCol = ? LIMIT 1"
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, parentId)
                if (element != null) stmt.setString(2, element)
                stmt.executeQuery().use { it.next() }
            }
        }
    }

    override fun remove(element: String?): Boolean {
        if (!contains(element)) return false
        withConn { conn ->
            val sql = if (element == null) {
                "DELETE FROM $tableName WHERE $fkColumn = ? AND $valueCol IS NULL"
            } else {
                "DELETE FROM $tableName WHERE $fkColumn = ? AND $valueCol = ?"
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, parentId)
                if (element != null) stmt.setString(2, element)
                stmt.executeUpdate()
            }
        }
        return true
    }

    override fun clear() {
        withConn { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.executeUpdate()
            }
        }
    }

    override fun iterator(): MutableIterator<String?> {
        val snapshot = withConn { conn ->
            conn.prepareStatement("SELECT $valueCol FROM $tableName WHERE $fkColumn = ?").use { stmt ->
                stmt.setObject(1, parentId)
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }
        return object : MutableIterator<String?> {
            private val inner = snapshot.iterator()
            private var last: String? = null
            private var hasLast = false
            override fun hasNext() = inner.hasNext()
            override fun next(): String? {
                val v = inner.next()
                last = v
                hasLast = true
                return v
            }
            override fun remove() {
                if (!hasLast) throw IllegalStateException()
                this@DatabaseSet.remove(last)
                hasLast = false
            }
        }
    }
}
