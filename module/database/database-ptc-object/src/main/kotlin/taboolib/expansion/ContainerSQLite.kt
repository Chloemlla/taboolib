package taboolib.expansion

import taboolib.module.database.*
import java.io.File

class ContainerSQLite(file: File) : Container<SQLite>(HostSQLite(file)) {

    /** 表名 -> @Key 字段名列表 */
    private val keyColumns = mutableMapOf<String, List<String>>()

    override fun createTableObject(type: AnalyzedClass, name: String): Table<*, *> {
        // 记录 @Key 字段用于后续创建索引
        val keys = type.members.filter { it.isKey }.map { it.name }
        if (keys.isNotEmpty()) {
            keyColumns[name] = keys
        }
        return Table(name, host) {
            // 只有在没有 @Id 字段时才自动添加 id 主键
            if (!type.members.any { it.isPrimary }) {
                add { id() }
            }
            type.members.forEach { member ->
                // @LinkTable 成员：创建外键列而非展开关联类
                if (member.isLinkTable) {
                    val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
                    val linkedPrimary = linkedClass.primaryMember!!
                    val fkColumnName = member.linkTableColumn!!
                    add(fkColumnName) {
                        when {
                            linkedPrimary.hasColumnType -> type(linkedPrimary.columnTypeSQLite!!, linkedPrimary.length)
                            linkedPrimary.isIndexedEnum -> type(ColumnTypeSQLite.INTEGER)
                            linkedPrimary.isString || linkedPrimary.isEnum -> type(ColumnTypeSQLite.TEXT, linkedPrimary.length)
                            linkedPrimary.isUUID -> type(ColumnTypeSQLite.TEXT, 36)
                            linkedPrimary.canConvertedInteger() -> type(ColumnTypeSQLite.INTEGER)
                            linkedPrimary.canConvertedDecimal() -> type(ColumnTypeSQLite.REAL)
                            else -> type(ColumnTypeSQLite.TEXT)
                        }
                    }
                    return@forEach
                }
                when {
                    // 自定义列类型
                    member.hasColumnType -> add(member.name) {
                        type(member.columnTypeSQLite!!, member.length) { options(member) }
                    }
                    // IndexedEnum（数值存储）
                    member.isIndexedEnum -> add(member.name) {
                        type(ColumnTypeSQLite.INTEGER) { options(member) }
                    }
                    // 字符串
                    member.isString || member.isEnum -> add(member.name) {
                        type(ColumnTypeSQLite.TEXT, member.length) { options(member) }
                    }
                    // UUID
                    member.isUUID -> add(member.name) {
                        type(ColumnTypeSQLite.TEXT, 36) { options(member) }
                    }
                    // 整数
                    member.canConvertedInteger() -> add(member.name) {
                        type(ColumnTypeSQLite.INTEGER) { options(member) }
                    }
                    // 小数
                    member.canConvertedDecimal() -> add(member.name) {
                        type(ColumnTypeSQLite.REAL) { options(member) }
                    }
                    // 字节数组
                    member.isByteArray -> add(member.name) {
                        type(ColumnTypeSQLite.BLOB) { options(member) }
                    }

                    else -> {
                        val customType = CustomTypeFactory.getCustomTypeByClass(member.returnType) ?: error("Unsupported type: ${member.name} (${member.returnType})")
                        add(member.name) { type(customType.typeSQLite, customType.length) { options(member) } }
                    }
                }
            }
        }
    }

    override fun init() {
        super.init()
        // 为 @Key 字段创建 SQLite 索引
        if (keyColumns.isNotEmpty()) {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    keyColumns.forEach { (tableName, columns) ->
                        columns.forEach { col ->
                            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS `idx_${tableName}_${col}` ON `$tableName` (`$col`)")
                        }
                    }
                }
            }
        }
    }

    private fun ColumnSQLite.options(member: AnalyzedClassMember) {
        if (member.isPrimary) {
            // ⚠ SQLite 的 PRIMARY KEY 强制唯一，与 SQL（MySQL）的 KEY（普通索引，不唯一）行为不同。
            // 这导致 @Id + @Key 复合定位模式（同一 @Id 值多条记录）在 SQLite 下不可用。
            options(ColumnOptionSQLite.PRIMARY_KEY)
        }
        if (member.isUniqueKey) {
            options(ColumnOptionSQLite.UNIQUE)
        }
        if (member.isNotNull) {
            options(ColumnOptionSQLite.NOTNULL)
        }
    }
}
