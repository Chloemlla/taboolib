package taboolib.expansion

import taboolib.module.database.*

class ContainerPostgreSQL(
    host: String,
    port: Int,
    user: String,
    password: String,
    database: String,
    schema: String = "public",
    flags: List<String> = emptyList(),
) : Container<PostgreSQL>(HostPostgreSQL(host, port.toString(), user, password, database, schema).also {
    it.flags.addAll(flags)
}) {

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
                // 跳过容器类型成员（它们存储在子表中）
                if (member.isCollection) return@forEach
                // @LinkTable 成员：创建外键列而非展开关联类
                if (member.isLinkTable) {
                    val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
                    val linkedPrimary = linkedClass.primaryMember!!
                    val fkColumnName = member.linkTableColumn!!
                    add(fkColumnName) {
                        when {
                            linkedPrimary.hasColumnType && linkedPrimary.columnTypePostgreSQL != ColumnTypePostgreSQL._DEFAULT -> {
                                val colType = linkedPrimary.columnTypePostgreSQL!!
                                if (colType.isRequired) type(colType, linkedPrimary.length)
                                else type(colType)
                            }
                            linkedPrimary.hasColumnType && linkedPrimary.columnTypePostgreSQL == ColumnTypePostgreSQL._DEFAULT -> {
                                val pgType = linkedPrimary.columnTypeSQL!!.toPostgreSQL()
                                if (pgType.isRequired) type(pgType, linkedPrimary.length)
                                else type(pgType)
                            }
                            linkedPrimary.isIndexedEnum -> type(ColumnTypePostgreSQL.BIGINT)
                            linkedPrimary.isString || linkedPrimary.isEnum -> {
                                if (linkedPrimary.length < 0) type(ColumnTypePostgreSQL.TEXT)
                                else type(ColumnTypePostgreSQL.VARCHAR, linkedPrimary.length)
                            }
                            linkedPrimary.isUUID -> type(ColumnTypePostgreSQL.UUID)
                            else -> type(linkedPrimary.type())
                        }
                    }
                    return@forEach
                }
                when {
                    // 自定义列类型（仅当显式指定了 postgresql 类型时）
                    member.hasColumnType && member.columnTypePostgreSQL != ColumnTypePostgreSQL._DEFAULT -> add(member.name) {
                        val colType = member.columnTypePostgreSQL!!
                        if (colType.isRequired) type(colType, member.length) { options(member) }
                        else type(colType) { options(member) }
                    }
                    // @ColumnType 指定了 SQL 类型但未指定 PostgreSQL 类型 → 从 SQL 类型推断
                    member.hasColumnType && member.columnTypePostgreSQL == ColumnTypePostgreSQL._DEFAULT -> add(member.name) {
                        val pgType = member.columnTypeSQL!!.toPostgreSQL()
                        if (pgType.isRequired) type(pgType, member.length) { options(member) }
                        else type(pgType) { options(member) }
                    }
                    // IndexedEnum（数值存储）
                    member.isIndexedEnum -> add(member.name) {
                        type(ColumnTypePostgreSQL.BIGINT) { options(member) }
                    }
                    // 字符串
                    member.isString || member.isEnum -> add(member.name) {
                        if (member.length < 0) {
                            type(ColumnTypePostgreSQL.TEXT) { options(member) }
                        } else {
                            type(ColumnTypePostgreSQL.VARCHAR, member.length) { options(member) }
                        }
                    }
                    // UUID（原生支持）
                    member.isUUID -> add(member.name) {
                        type(ColumnTypePostgreSQL.UUID) { options(member) }
                    }
                    // 字节数组
                    member.isByteArray -> add(member.name) {
                        type(ColumnTypePostgreSQL.BYTEA) { options(member) }
                    }
                    // 其他类型
                    else -> add(member.name) {
                        val customType = CustomTypeFactory.getCustomTypeByClass(member.returnType)
                        if (customType == null) {
                            type(member.type()) { options(member) }
                        } else {
                            if (customType.typePostgreSQL.isRequired) {
                                type(customType.typePostgreSQL, customType.lengthPostgreSQL) { options(member) }
                            } else {
                                type(customType.typePostgreSQL) { options(member) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun createCollectionTableObject(
        parentType: AnalyzedClass,
        parentTableName: String,
        member: AnalyzedClassMember,
        childTableName: String
    ): Table<*, *> {
        val primaryMember = parentType.primaryMember!!
        return Table(childTableName, host) {
            add { id() }
            // FK 列：引用主表的 @Id
            add("parent_${primaryMember.name}") {
                when {
                    primaryMember.hasColumnType && primaryMember.columnTypePostgreSQL != ColumnTypePostgreSQL._DEFAULT -> {
                        val colType = primaryMember.columnTypePostgreSQL!!
                        if (colType.isRequired) type(colType, primaryMember.length) else type(colType)
                    }
                    primaryMember.hasColumnType && primaryMember.columnTypePostgreSQL == ColumnTypePostgreSQL._DEFAULT -> {
                        val pgType = primaryMember.columnTypeSQL!!.toPostgreSQL()
                        if (pgType.isRequired) type(pgType, primaryMember.length) else type(pgType)
                    }
                    primaryMember.isIndexedEnum -> type(ColumnTypePostgreSQL.BIGINT)
                    primaryMember.isString || primaryMember.isEnum -> {
                        if (primaryMember.length < 0) type(ColumnTypePostgreSQL.TEXT)
                        else type(ColumnTypePostgreSQL.VARCHAR, primaryMember.length)
                    }
                    primaryMember.isUUID -> type(ColumnTypePostgreSQL.UUID)
                    else -> type(primaryMember.type())
                }
            }
            if (member.isMap) {
                add("map_key") { type(ColumnTypePostgreSQL.VARCHAR, 512) }
                add("map_value") { type(ColumnTypePostgreSQL.VARCHAR, 512) }
            } else {
                add("value") { type(ColumnTypePostgreSQL.VARCHAR, 512) }
                if (member.isList) {
                    add("sort_order") { type(ColumnTypePostgreSQL.INTEGER) }
                }
            }
        }
    }

    override fun init() {
        super.init()
        // 为 @Key 字段创建 PostgreSQL 索引
        if (keyColumns.isNotEmpty()) {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    keyColumns.forEach { (tableName, columns) ->
                        columns.forEach { col ->
                            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS \"idx_${tableName}_${col}\" ON \"$tableName\" (\"$col\")")
                        }
                    }
                }
            }
        }
    }

    private fun ColumnPostgreSQL.options(member: AnalyzedClassMember) {
        if (member.isKey || member.isPrimary) {
            options(ColumnOptionPostgreSQL.KEY)
        }
        if (member.isUniqueKey) {
            options(ColumnOptionPostgreSQL.UNIQUE)
        }
        if (member.isNotNull) {
            options(ColumnOptionPostgreSQL.NOTNULL)
        }
    }

    private fun AnalyzedClassMember.type(): ColumnTypePostgreSQL {
        return when {
            isBoolean -> ColumnTypePostgreSQL.BOOLEAN
            isByte -> ColumnTypePostgreSQL.SMALLINT
            isShort -> ColumnTypePostgreSQL.SMALLINT
            isInt -> ColumnTypePostgreSQL.INTEGER
            isLong -> ColumnTypePostgreSQL.BIGINT
            isFloat -> ColumnTypePostgreSQL.REAL
            isDouble -> ColumnTypePostgreSQL.DOUBLE_PRECISION
            isChar -> ColumnTypePostgreSQL.INTEGER
            isByteArray -> ColumnTypePostgreSQL.BYTEA
            else -> error("Unsupported type: $name (${returnType})")
        }
    }

    /** 将 MySQL 列类型映射为 PostgreSQL 等价类型 */
    private fun ColumnTypeSQL.toPostgreSQL(): ColumnTypePostgreSQL {
        return when (this) {
            ColumnTypeSQL.TINYINT -> ColumnTypePostgreSQL.SMALLINT
            ColumnTypeSQL.SMALLINT -> ColumnTypePostgreSQL.SMALLINT
            ColumnTypeSQL.MEDIUMINT -> ColumnTypePostgreSQL.INTEGER
            ColumnTypeSQL.INT -> ColumnTypePostgreSQL.INTEGER
            ColumnTypeSQL.BIGINT -> ColumnTypePostgreSQL.BIGINT
            ColumnTypeSQL.FLOAT -> ColumnTypePostgreSQL.REAL
            ColumnTypeSQL.DOUBLE -> ColumnTypePostgreSQL.DOUBLE_PRECISION
            ColumnTypeSQL.DECIMAL, ColumnTypeSQL.FIXED -> ColumnTypePostgreSQL.NUMERIC
            ColumnTypeSQL.BIT -> ColumnTypePostgreSQL.INTEGER
            ColumnTypeSQL.SERIAL -> ColumnTypePostgreSQL.BIGSERIAL
            ColumnTypeSQL.BOOL, ColumnTypeSQL.BOOLEAN -> ColumnTypePostgreSQL.BOOLEAN
            ColumnTypeSQL.CHAR -> ColumnTypePostgreSQL.CHAR
            ColumnTypeSQL.VARCHAR -> ColumnTypePostgreSQL.VARCHAR
            ColumnTypeSQL.TINYTEXT, ColumnTypeSQL.TEXT, ColumnTypeSQL.MEDIUMTEXT, ColumnTypeSQL.LONGTEXT -> ColumnTypePostgreSQL.TEXT
            ColumnTypeSQL.TINYBLOB, ColumnTypeSQL.BLOB, ColumnTypeSQL.MEDIUMBLOB, ColumnTypeSQL.LONGBLOB -> ColumnTypePostgreSQL.BYTEA
            ColumnTypeSQL.BINARY, ColumnTypeSQL.VARBINARY -> ColumnTypePostgreSQL.BYTEA
            ColumnTypeSQL.JSON -> ColumnTypePostgreSQL.JSON
            ColumnTypeSQL.DATE -> ColumnTypePostgreSQL.DATE
            ColumnTypeSQL.DATETIME, ColumnTypeSQL.TIMESTAMP -> ColumnTypePostgreSQL.TIMESTAMP
            ColumnTypeSQL.TIME -> ColumnTypePostgreSQL.TIME
            ColumnTypeSQL.YEAR -> ColumnTypePostgreSQL.SMALLINT
            else -> ColumnTypePostgreSQL.TEXT
        }
    }
}
