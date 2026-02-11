package taboolib.expansion

import taboolib.common.platform.function.warning
import taboolib.module.database.*
import taboolib.module.database.asFormattedColumnName
import taboolib.module.database.setupQuoterForHost
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource

/**
 * ContainerOperator 的标准实现
 *
 * 支持两种模式：
 * 1. 普通模式：每次操作从 DataSource 获取新连接（默认）
 * 2. 事务模式：所有操作共享同一个 Connection
 *
 * @author 坏黑
 * @since 2023/3/29 13:29
 */
class ContainerOperatorImpl(
    override val table: Table<*, *>,
    override val dataSource: DataSource,
    private val sharedConnection: Connection? = null,
    private val classTableMap: Map<Class<*>, String>? = null,
    private val classOperatorMap: Map<Class<*>, ContainerOperator>? = null,
    private val collectionTableInfos: List<CollectionTableInfo>? = null
) : ContainerOperator() {

    /**
     * 创建事务模式的操作器（共享 Connection）
     */
    fun withConnection(connection: Connection): ContainerOperatorImpl {
        return ContainerOperatorImpl(table, dataSource, connection, classTableMap, classOperatorMap, collectionTableInfos)
    }

    /**
     * 是否为事务模式
     */
    val isTransactional: Boolean
        get() = sharedConnection != null

    /**
     * 设置标识符引用方言
     */
    private fun setupQuoter() {
        setupQuoterForHost(table.host)
    }

    override fun <T> getOne(type: Class<T>, filter: Filter.() -> Unit): T? {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                limit(1)
                where(filter)
            }
            return executeQuery(action) { rs ->
                if (rs.next()) readJoinResult<T>(typeClass, rs) else null
            }
        }
        val action = ActionSelect(table.name).apply {
            limit(1)
            where(filter)
        }
        val map = executeQuery(action) { rs -> readMap(typeClass, rs) }
        return resolveOneWithCollections(typeClass, map)
    }

    override fun <T> get(type: Class<T>, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                where(filter)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where(filter)
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> findOne(type: Class<T>, id: Any, filter: Filter.() -> Unit): T? {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val primaryName = typeClass.primaryMemberName ?: error("No primary id found.")
            val tableName = table.name
            val action = buildJoinSelect(typeClass) {
                where("$tableName.$primaryName".asFormattedColumnName() eq id.value())
                where(filter)
                limit(1)
            }
            return executeQuery(action) { rs ->
                if (rs.next()) readJoinResult<T>(typeClass, rs) else null
            }
        }
        val map = executeQuery(selectById(typeClass, id, filter) { limit(1) }) { rs -> readMap(typeClass, rs) }
        return resolveOneWithCollections(typeClass, map)
    }

    override fun <T> find(type: Class<T>, id: Any, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val primaryName = typeClass.primaryMemberName ?: error("No primary id found.")
            val tableName = table.name
            val action = buildJoinSelect(typeClass) {
                where("$tableName.$primaryName".asFormattedColumnName() eq id.value())
                where(filter)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val maps = executeQuery(selectById(typeClass, id, filter)) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> sort(type: Class<T>, row: String, limit: Int, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                where(filter)
                limit(limit)
                orderBy(row)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where(filter)
            limit(limit)
            orderBy(row)
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> sortDescending(type: Class<T>, row: String, limit: Int, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                where(filter)
                limit(limit)
                orderBy(row, Order.Type.DESC)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where(filter)
            limit(limit)
            orderBy(row, Order.Type.DESC)
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> getPage(type: Class<T>, page: Int, size: Int, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                where(filter)
                limit(size)
                offset((page - 1) * size)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where(filter)
            limit(size)
            offset((page - 1) * size)
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> sortPage(type: Class<T>, row: String, page: Int, size: Int, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                where(filter)
                orderBy(row)
                limit(size)
                offset((page - 1) * size)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where(filter)
            orderBy(row)
            limit(size)
            offset((page - 1) * size)
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> sortDescendingPage(type: Class<T>, row: String, page: Int, size: Int, filter: Filter.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) {
                where(filter)
                orderBy(row, Order.Type.DESC)
                limit(size)
                offset((page - 1) * size)
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where(filter)
            orderBy(row, Order.Type.DESC)
            limit(size)
            offset((page - 1) * size)
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> selectCursor(type: Class<T>, filter: Filter.() -> Unit): Cursor<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) { where(filter) }
            return openCursor(action) { rs -> readJoinResult<T>(typeClass, rs) }
        }
        val action = ActionSelect(table.name).apply { where(filter) }
        return openCursor(action) { rs -> typeClass.createInstance<T>(typeClass.read(rs)) }
    }

    override fun <T> sortCursor(type: Class<T>, row: String, filter: Filter.() -> Unit): Cursor<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) { where(filter); orderBy(row) }
            return openCursor(action) { rs -> readJoinResult<T>(typeClass, rs) }
        }
        val action = ActionSelect(table.name).apply { where(filter); orderBy(row) }
        return openCursor(action) { rs -> typeClass.createInstance<T>(typeClass.read(rs)) }
    }

    override fun <T> sortDescendingCursor(type: Class<T>, row: String, filter: Filter.() -> Unit): Cursor<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val action = buildJoinSelect(typeClass) { where(filter); orderBy(row, Order.Type.DESC) }
            return openCursor(action) { rs -> readJoinResult<T>(typeClass, rs) }
        }
        val action = ActionSelect(table.name).apply { where(filter); orderBy(row, Order.Type.DESC) }
        return openCursor(action) { rs -> typeClass.createInstance<T>(typeClass.read(rs)) }
    }

    override fun <T> has(type: Class<T>, id: Any, filter: Filter.() -> Unit): Boolean {
        return executeQuery(selectById(AnalyzedClass.of(type), id, filter) { limit(1) }) { it.next() }
    }

    override fun has(filter: Filter.() -> Unit): Boolean {
        val action = ActionSelect(table.name).apply {
            limit(1)
            where(filter)
        }
        return executeQuery(action) { it.next() }
    }

    override fun insert(dataList: List<Any>) {
        if (dataList.isEmpty()) return
        val typeClass = AnalyzedClass.of(dataList.first().javaClass)
        cascadeSaveLinkedObjects(typeClass, dataList)
        val columnNames = getColumnNames(typeClass)
        val action = ActionInsert(table.name, columnNames.toTypedArray()).apply {
            dataList.forEach { data ->
                values(getColumnValues(typeClass, data))
            }
        }
        executeUpdate(action)
        insertCollectionData(typeClass, dataList)
    }

    override fun insertAndGetKeys(dataList: List<Any>): List<Long> {
        if (dataList.isEmpty()) return emptyList()
        val typeClass = AnalyzedClass.of(dataList.first().javaClass)
        cascadeSaveLinkedObjects(typeClass, dataList)
        val columnNames = getColumnNames(typeClass)
        val action = ActionInsert(table.name, columnNames.toTypedArray()).apply {
            dataList.forEach { data ->
                values(getColumnValues(typeClass, data))
            }
        }
        // ⚠ SQLite 限制：批量插入时 generatedKeys 仅返回最后一条记录的自增 ID，
        // 而非所有记录的 ID 列表。因此在 SQLite 模式下，返回的 List 长度可能为 1 而非 dataList.size。
        // MySQL 不受此限制，会正确返回所有生成的 key。
        return withConnection { conn ->
            conn.executePrepared(action.query, action.elements, Statement.RETURN_GENERATED_KEYS) {
                executeUpdate()
                generatedKeys.use { rs ->
                    buildList { while (rs.next()) add(rs.getLong(1)) }
                }
            }
        }.also { insertCollectionData(typeClass, dataList) }
    }

    override fun update(data: Any, usePrimaryKey: Boolean, filter: Filter.() -> Unit) {
        val typeClass = AnalyzedClass.of(data::class.java)
        if (typeClass.members.none { !it.isFinal || it.isLinkTable }) {
            error("No mutable field found.")
        }
        cascadeSaveLinkedObjects(typeClass, listOf(data))
        val name = if (usePrimaryKey) typeClass.primaryMemberName ?: error("No primary id found.") else null
        val value = if (usePrimaryKey) typeClass.getPrimaryMemberValue(data) else null
        // check-then-act 包裹在事务中，防止并发双重 INSERT
        withTransaction { conn ->
            val existsAction = ActionSelect(table.name).apply {
                limit(1)
                if (name != null) where(name eq value?.value())
                where(filter)
            }
            val exists = executeQueryWith(conn, existsAction) { it.next() }
            if (exists) {
                val updateAction = ActionUpdate(table.name).apply {
                    if (name != null) where(name eq value?.value())
                    where(filter)
                    typeClass.members.filter { (!it.isFinal || it.isLinkTable) && !it.isCollection }.forEach { member ->
                        if (member.isLinkTable) {
                            val linkedObj = typeClass.getValue(data, member)
                            val fkValue = if (linkedObj != null) {
                                val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
                                linkedClass.getPrimaryMemberValue(linkedObj)?.value()
                            } else null
                            set(member.linkTableColumn!!, fkValue)
                        } else {
                            set(member.name, typeClass.getValue(data, member)?.value())
                        }
                    }
                }
                conn.executePrepared(updateAction.query, updateAction.elements, Statement.RETURN_GENERATED_KEYS) { executeUpdate() }
            } else {
                val insertTypeClass = AnalyzedClass.of(data.javaClass)
                val columnNames = getColumnNames(insertTypeClass)
                val insertAction = ActionInsert(table.name, columnNames.toTypedArray()).apply {
                    values(getColumnValues(insertTypeClass, data))
                }
                conn.executePrepared(insertAction.query, insertAction.elements, Statement.RETURN_GENERATED_KEYS) { executeUpdate() }
            }
        }
        syncCollectionData(typeClass, data)
    }

    override fun updateByKey(data: Any, usePrimaryKey: Boolean) {
        val typeClass = AnalyzedClass.of(data::class.java)
        if (typeClass.members.none { !it.isFinal || it.isLinkTable }) {
            error("No mutable field found.")
        }
        update(data, usePrimaryKey) {
            typeClass.members.filter { it.isKey }.forEach { member ->
                member.name eq typeClass.getValue(data, member)?.value()
            }
        }
    }

    override fun upsert(dataList: List<Any>) {
        if (dataList.isEmpty()) return
        val typeClass = AnalyzedClass.of(dataList.first().javaClass)
        if (typeClass.members.none { !it.isFinal || it.isLinkTable }) {
            error("No mutable field found.")
        }
        cascadeSaveLinkedObjects(typeClass, dataList)
        val primaryName = typeClass.primaryMemberName ?: error("No primary id found.")
        val keyMembers = typeClass.members.filter { it.isKey }
        val mutableMembers = typeClass.members.filter { (!it.isFinal || it.isLinkTable) && !it.isCollection }
        // 构建唯一标识：@Id + @Key 的值
        fun buildKey(data: Any): String {
            val id = typeClass.getPrimaryMemberValue(data)?.value().toString()
            val keys = keyMembers.joinToString("|") { typeClass.getValue(data, it)?.value().toString() }
            return "$id|$keys"
        }
        withTransaction { conn ->
            // 1. 批量查询存在的记录
            val existingKeys = hashSetOf<String>()
            if (keyMembers.isNotEmpty()) {
                // 有 @Key：需要精确匹配 @Id + @Key
                dataList.forEach { data ->
                    val checkAction = ActionSelect(table.name).apply {
                        limit(1)
                        where(primaryName eq typeClass.getPrimaryMemberValue(data)?.value())
                        keyMembers.forEach { member ->
                            where(member.name eq typeClass.getValue(data, member)?.value())
                        }
                    }
                    val exists = executeQueryWith(conn, checkAction) { it.next() }
                    if (exists) {
                        existingKeys += buildKey(data)
                    }
                }
            } else {
                // 无 @Key：只匹配 @Id，使用 IN 查询优化
                val ids = dataList.mapNotNull { typeClass.getPrimaryMemberValue(it)?.value() }.toTypedArray()
                val action = ActionSelect(table.name).apply {
                    where { primaryName inside ids }
                }
                executeQueryWith(conn, action) { rs ->
                    while (rs.next()) {
                        existingKeys += rs.getObject(primaryName)?.toString() ?: ""
                    }
                }
            }
            // 2. 分流：存在的更新，不存在的插入
            val toInsert = mutableListOf<Any>()
            val toUpdate = mutableListOf<Any>()
            dataList.forEach { data ->
                val key = if (keyMembers.isNotEmpty()) {
                    buildKey(data)
                } else {
                    typeClass.getPrimaryMemberValue(data)?.value().toString()
                }
                if (key in existingKeys) {
                    toUpdate += data
                } else {
                    toInsert += data
                }
            }
            // 3. 批量插入
            if (toInsert.isNotEmpty()) {
                val insertSql = buildInsertSql(typeClass)
                conn.prepareStatement(insertSql).use { stmt ->
                    toInsert.forEach { data ->
                        getColumnValues(typeClass, data).forEachIndexed { i, v ->
                            stmt.setObject(i + 1, v)
                        }
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            // 4. 批量更新
            if (toUpdate.isNotEmpty()) {
                val updateSql = buildUpdateSql(typeClass, primaryName, keyMembers)
                conn.prepareStatement(updateSql).use { stmt ->
                    toUpdate.forEach { data ->
                        var idx = 1
                        // SET 子句的值
                        mutableMembers.forEach { member ->
                            if (member.isLinkTable) {
                                val linkedObj = typeClass.getValue(data, member)
                                val fkValue = if (linkedObj != null) {
                                    val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
                                    linkedClass.getPrimaryMemberValue(linkedObj)?.value()
                                } else null
                                stmt.setObject(idx++, fkValue)
                            } else {
                                stmt.setObject(idx++, typeClass.getValue(data, member)?.value())
                            }
                        }
                        // WHERE 子句的值
                        stmt.setObject(idx++, typeClass.getPrimaryMemberValue(data)?.value())
                        keyMembers.forEach { member ->
                            stmt.setObject(idx++, typeClass.getValue(data, member)?.value())
                        }
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
        }
        dataList.forEach { data -> syncCollectionData(typeClass, data) }
    }

    /**
     * 构建 INSERT SQL
     */
    private fun buildInsertSql(typeClass: AnalyzedClass): String {
        val columnNames = getColumnNames(typeClass)
        val columns = columnNames.joinToString(", ") { it.asFormattedColumnName() }
        val placeholders = columnNames.joinToString(", ") { "?" }
        return "INSERT INTO ${table.name.asFormattedColumnName()} ($columns) VALUES ($placeholders)"
    }

    /**
     * 构建 UPDATE SQL
     */
    private fun buildUpdateSql(
        typeClass: AnalyzedClass,
        primaryName: String,
        keyMembers: List<AnalyzedClassMember>
    ): String {
        val mutableMembers = typeClass.members.filter { (!it.isFinal || it.isLinkTable) && !it.isCollection }
        val setClause = mutableMembers.joinToString(", ") { member ->
            if (member.isLinkTable) "${member.linkTableColumn!!.asFormattedColumnName()} = ?" else "${member.name.asFormattedColumnName()} = ?"
        }
        val whereClause = buildString {
            append("${primaryName.asFormattedColumnName()} = ?")
            keyMembers.forEach { append(" AND ${it.name.asFormattedColumnName()} = ?") }
        }
        return "UPDATE ${table.name.asFormattedColumnName()} SET $setClause WHERE $whereClause"
    }

    override fun <T> delete(type: Class<T>, id: Any, filter: Filter.() -> Unit) {
        val typeClass = AnalyzedClass.of(type)
        val name = typeClass.primaryMemberName ?: error("No primary id found.")
        deleteCollectionDataByFk(id)
        val action = ActionDelete(table.name).apply {
            where(name eq id.value())
            where(filter)
        }
        executeUpdate(action)
    }

    override fun deleteWhere(filter: Filter.() -> Unit) {
        val action = ActionDelete(table.name).apply {
            where(filter)
        }
        executeUpdate(action)
    }

    override fun count(filter: Filter.() -> Unit): Long {
        val action = ActionSelect(table.name).apply {
            rows("COUNT(*)")
            where(filter)
        }
        return executeQuery(action) { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }

    override fun <T> findByKey(type: Class<T>, data: Any, usePrimaryKey: Boolean): List<T> {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val tableName = table.name
            val action = buildJoinSelect(typeClass) {
                if (usePrimaryKey) {
                    val pName = typeClass.primaryMemberName ?: error("No primary id found.")
                    val pValue = typeClass.getPrimaryMemberValue(data)
                    where("$tableName.$pName".asFormattedColumnName() eq pValue?.value())
                }
                typeClass.members.filter { it.isKey }.forEach { member ->
                    where("$tableName.${member.name}".asFormattedColumnName() eq typeClass.getValue(data, member)?.value())
                }
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = selectByKey(typeClass, data, usePrimaryKey)
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> findOneByKey(type: Class<T>, data: Any, usePrimaryKey: Boolean): T? {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val tableName = table.name
            val action = buildJoinSelect(typeClass) {
                if (usePrimaryKey) {
                    val pName = typeClass.primaryMemberName ?: error("No primary id found.")
                    val pValue = typeClass.getPrimaryMemberValue(data)
                    where("$tableName.$pName".asFormattedColumnName() eq pValue?.value())
                }
                typeClass.members.filter { it.isKey }.forEach { member ->
                    where("$tableName.${member.name}".asFormattedColumnName() eq typeClass.getValue(data, member)?.value())
                }
                limit(1)
            }
            return executeQuery(action) { rs ->
                if (rs.next()) readJoinResult<T>(typeClass, rs) else null
            }
        }
        val action = selectByKey(typeClass, data, usePrimaryKey) { limit(1) }
        val map = executeQuery(action) { rs -> readMap(typeClass, rs) }
        return resolveOneWithCollections(typeClass, map)
    }

    override fun <T> hasByKey(type: Class<T>, data: Any, usePrimaryKey: Boolean): Boolean {
        val typeClass = AnalyzedClass.of(type)
        val action = selectByKey(typeClass, data, usePrimaryKey) { limit(1) }
        return executeQuery(action) { it.next() }
    }

    override fun deleteByKey(data: Any, usePrimaryKey: Boolean) {
        val typeClass = AnalyzedClass.of(data::class.java)
        val action = ActionDelete(table.name).apply {
            if (usePrimaryKey) {
                val name = typeClass.primaryMemberName ?: error("No primary id found.")
                val value = typeClass.getPrimaryMemberValue(data)
                where(name eq value?.value())
            }
            typeClass.members.filter { it.isKey }.forEach { member ->
                where(member.name eq typeClass.getValue(data, member)?.value())
            }
        }
        executeUpdate(action)
    }

    override fun <T> findByRowId(type: Class<T>, rowId: Long): T? {
        val typeClass = AnalyzedClass.of(type)
        if (typeClass.hasLinkMembers) {
            val tableName = table.name
            val action = buildJoinSelect(typeClass) {
                where("$tableName.id".asFormattedColumnName() eq rowId)
                limit(1)
            }
            return executeQuery(action) { rs ->
                if (rs.next()) readJoinResult<T>(typeClass, rs) else null
            }
        }
        val action = ActionSelect(table.name).apply {
            where("id" eq rowId)
            limit(1)
        }
        val map = executeQuery(action) { rs -> readMap(typeClass, rs) }
        return resolveOneWithCollections(typeClass, map)
    }

    override fun deleteByRowId(rowId: Long) {
        val action = ActionDelete(table.name).apply {
            where("id" eq rowId)
        }
        executeUpdate(action)
    }

    // === 批量操作 ===

    override fun <T> findByIds(type: Class<T>, ids: List<Any>): List<T> {
        if (ids.isEmpty()) return emptyList()
        val typeClass = AnalyzedClass.of(type)
        val primaryName = typeClass.primaryMemberName ?: error("No primary id found.")
        if (typeClass.hasLinkMembers) {
            val tableName = table.name
            val action = buildJoinSelect(typeClass) {
                where { "$tableName.$primaryName".asFormattedColumnName() inside ids.map { it.value() }.toTypedArray() }
            }
            return executeQuery(action) { rs ->
                buildList {
                    while (rs.next()) {
                        add(readJoinResult<T>(typeClass, rs))
                    }
                }
            }
        }
        val action = ActionSelect(table.name).apply {
            where { primaryName inside ids.map { it.value() }.toTypedArray() }
        }
        val maps = executeQuery(action) { rs -> readMaps(typeClass, rs) }
        return resolveWithCollections(typeClass, maps)
    }

    override fun <T> deleteByIds(type: Class<T>, ids: List<Any>) {
        if (ids.isEmpty()) return
        val typeClass = AnalyzedClass.of(type)
        val name = typeClass.primaryMemberName ?: error("No primary id found.")
        ids.forEach { deleteCollectionDataByFk(it) }
        val action = ActionDelete(table.name).apply {
            where { name inside ids.map { it.value() }.toTypedArray() }
        }
        executeUpdate(action)
    }

    override fun updateBatch(dataList: List<Any>) {
        if (dataList.isEmpty()) return
        val typeClass = AnalyzedClass.of(dataList.first().javaClass)
        val mutableMembers = typeClass.members.filter { (!it.isFinal || it.isLinkTable) && !it.isCollection }
        if (mutableMembers.isEmpty()) error("No mutable field found.")
        cascadeSaveLinkedObjects(typeClass, dataList)
        val primaryName = typeClass.primaryMemberName ?: error("No primary id found.")
        val keyMembers = typeClass.members.filter { it.isKey }
        val updateSql = buildUpdateSql(typeClass, primaryName, keyMembers)
        withTransaction { conn ->
            conn.prepareStatement(updateSql).use { stmt ->
                dataList.forEach { data ->
                    var idx = 1
                    mutableMembers.forEach { member ->
                        if (member.isLinkTable) {
                            val linkedObj = typeClass.getValue(data, member)
                            val fkValue = if (linkedObj != null) {
                                val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
                                linkedClass.getPrimaryMemberValue(linkedObj)?.value()
                            } else null
                            stmt.setObject(idx++, fkValue)
                        } else {
                            stmt.setObject(idx++, typeClass.getValue(data, member)?.value())
                        }
                    }
                    stmt.setObject(idx++, typeClass.getPrimaryMemberValue(data)?.value())
                    keyMembers.forEach { member ->
                        stmt.setObject(idx++, typeClass.getValue(data, member)?.value())
                    }
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
        dataList.forEach { data -> syncCollectionData(typeClass, data) }
    }

    // === 自定义 SQL ===

    override fun <R> select(action: ActionSelect, handler: (ResultSet) -> R): R {
        return executeQuery(action, handler)
    }

    override fun execute(action: Action): Int {
        return executeUpdate(action)
    }

    // === @LinkTable 级联保存 ===

    /**
     * 级联保存关联对象：存在则更新，不存在则插入（支持无限嵌套）
     *
     * 在主对象写入之前调用，确保关联表中的记录已就绪。
     * 深度优先递归：先保存最深层的关联对象，确保外键引用完整。
     * - 关联类有可变字段 → upsert（存在则更新，不存在则插入）
     * - 关联类全为 val → 仅在不存在时插入
     *
     * @param visited 祖先链集合，用于环检测（A→B→A 跳过）
     */
    private fun cascadeSaveLinkedObjects(
        typeClass: AnalyzedClass,
        dataList: List<Any>,
        visited: MutableSet<Class<*>> = mutableSetOf()
    ) {
        if (!typeClass.hasLinkMembers || classOperatorMap == null) return
        visited.add(typeClass.clazz)
        for (member in typeClass.linkMembers) {
            val linkClass = member.linkTableClass ?: continue
            if (linkClass in visited) continue
            val operator = classOperatorMap[linkClass] ?: continue
            val linkedObjects = dataList.mapNotNull { typeClass.getValue(it, member) }
            if (linkedObjects.isEmpty()) continue
            val linkedTypeClass = AnalyzedClass.of(linkClass)
            // 深度优先：先保存更深层的关联对象
            cascadeSaveLinkedObjects(linkedTypeClass, linkedObjects, visited)
            // 再保存当前层
            val hasMutableFields = linkedTypeClass.members.any { !it.isFinal }
            if (hasMutableFields) {
                operator.upsert(linkedObjects)
            } else {
                val primaryName = linkedTypeClass.primaryMemberName ?: continue
                linkedObjects.forEach { obj ->
                    val id = linkedTypeClass.getPrimaryMemberValue(obj) ?: return@forEach
                    if (!operator.has { primaryName eq id.value() }) {
                        operator.insert(listOf(obj))
                    }
                }
            }
        }
        visited.remove(typeClass.clazz)
    }

    // === @LinkTable 辅助方法 ===

    /**
     * 获取实际列名列表（@LinkTable 用 FK 列名）
     */
    private fun getColumnNames(typeClass: AnalyzedClass): List<String> {
        return typeClass.members.filter { !it.isCollection }.map { member ->
            if (member.isLinkTable) member.linkTableColumn!! else member.name
        }
    }

    /**
     * 获取实际列值列表（@LinkTable 提取 FK 值）
     */
    private fun getColumnValues(typeClass: AnalyzedClass, data: Any): List<Any?> {
        return typeClass.members.filter { !it.isCollection }.map { member ->
            if (member.isLinkTable) {
                val linkedObj = typeClass.getValue(data, member)
                if (linkedObj != null) {
                    val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
                    linkedClass.getPrimaryMemberValue(linkedObj)?.value()
                } else {
                    null
                }
            } else {
                typeClass.getValue(data, member)?.value()
            }
        }
    }

    /**
     * JOIN 树节点，描述一个 @LinkTable 关联的递归结构
     */
    private data class LinkJoinNode(
        val analyzedClass: AnalyzedClass,
        val tableAlias: String,
        val realTableName: String,
        val columnPrefix: String,
        val member: AnalyzedClassMember,
        val parentAlias: String,
        val children: List<LinkJoinNode>
    )

    /**
     * 递归构建 JOIN 树（支持无限嵌套）
     *
     * @param typeClass 当前类的分析结果
     * @param parentAlias 父表的别名（用于 ON 子句）
     * @param prefixChain 列别名前缀链，每深一层追加 `__link__{fk}__`
     * @param aliasCounter 单元素数组，全局递增的表别名计数器
     * @param visited 祖先链集合，用于环检测（A→B→A 跳过）
     */
    private fun buildLinkTree(
        typeClass: AnalyzedClass,
        parentAlias: String,
        prefixChain: String,
        aliasCounter: IntArray,
        visited: MutableSet<Class<*>>
    ): List<LinkJoinNode> {
        val nodes = mutableListOf<LinkJoinNode>()
        for (member in typeClass.linkMembers) {
            val linkClass = member.linkTableClass ?: continue
            if (linkClass in visited) continue
            val linkedAnalyzed = AnalyzedClass.of(linkClass)
            val realTableName = classTableMap?.get(linkClass) ?: error(
                "关联类 ${linkClass.simpleName} 未注册表名。请确保关联类的表已在容器中创建。"
            )
            val alias = "__t${aliasCounter[0]++}"
            val prefix = "${prefixChain}__link__${member.linkTableColumn}__"
            visited.add(linkClass)
            val children = buildLinkTree(linkedAnalyzed, alias, prefix, aliasCounter, visited)
            visited.remove(linkClass)
            nodes.add(LinkJoinNode(linkedAnalyzed, alias, realTableName, prefix, member, parentAlias, children))
        }
        return nodes
    }

    /**
     * 构建带 JOIN 的 ActionSelect（支持无限嵌套关联）
     */
    private fun buildJoinSelect(typeClass: AnalyzedClass, extra: ActionSelect.() -> Unit = {}): ActionSelect {
        setupQuoter()
        val tableName = table.name
        val aliasCounter = intArrayOf(0)
        val visited = mutableSetOf(typeClass.clazz)
        val linkTree = buildLinkTree(typeClass, tableName, "", aliasCounter, visited)
        return ActionSelect(tableName).apply {
            val selectRows = mutableListOf<String>()
            // 主表列
            typeClass.columnMembers.forEach { member ->
                selectRows += "$tableName.${member.name}".asFormattedColumnName() + " AS " + member.name.asFormattedColumnName()
            }
            // 递归展开 JOIN 树
            fun processNodes(nodes: List<LinkJoinNode>) {
                for (node in nodes) {
                    node.analyzedClass.columnMembers.forEach { linkedMember ->
                        selectRows += "${node.tableAlias}.${linkedMember.name}".asFormattedColumnName() + " AS " + "${node.columnPrefix}${linkedMember.name}".asFormattedColumnName()
                    }
                    leftJoin("${node.realTableName.asFormattedColumnName()} AS ${node.tableAlias.asFormattedColumnName()}") {
                        on("${node.parentAlias}.${node.member.linkTableColumn}".asFormattedColumnName() eq
                            pre("${node.tableAlias}.${node.analyzedClass.primaryMemberName}".asFormattedColumnName()))
                    }
                    processNodes(node.children)
                }
            }
            processNodes(linkTree)
            rows(*selectRows.toTypedArray())
            extra()
        }
    }

    /**
     * 从 JOIN ResultSet 读取完整对象
     */
    private fun <T> readJoinResult(typeClass: AnalyzedClass, rs: ResultSet): T {
        return typeClass.createInstance(typeClass.readWithLinks(rs))
    }

    /**
     * 构建基于 @Id 的查询
     */
    private fun selectById(
        typeClass: AnalyzedClass,
        id: Any,
        filter: Filter.() -> Unit,
        extra: ActionSelect.() -> Unit = {}
    ): ActionSelect {
        val name = typeClass.primaryMemberName ?: error("No primary id found.")
        return ActionSelect(table.name).apply {
            where(name eq id.value())
            where(filter)
            extra()
        }
    }

    /**
     * 构建基于 @Id + @Key 的查询
     */
    private fun selectByKey(
        typeClass: AnalyzedClass,
        data: Any,
        usePrimaryKey: Boolean,
        extra: ActionSelect.() -> Unit = {}
    ): ActionSelect {
        return ActionSelect(table.name).apply {
            if (usePrimaryKey) {
                val name = typeClass.primaryMemberName ?: error("No primary id found.")
                val value = typeClass.getPrimaryMemberValue(data)
                where(name eq value?.value())
            }
            typeClass.members.filter { it.isKey }.forEach { member ->
                where(member.name eq typeClass.getValue(data, member)?.value())
            }
            extra()
        }
    }

    /**
     * 执行查询
     */
    private fun <T> executeQuery(action: ActionSelect, handler: (ResultSet) -> T): T {
        return withConnection { conn -> executeQueryWith(conn, action, handler) }
    }

    /**
     * 执行更新
     */
    private fun executeUpdate(action: Action): Int {
        return withConnection { conn ->
            conn.executePrepared(action.query, action.elements, Statement.RETURN_GENERATED_KEYS) { executeUpdate() }
        }
    }

    /**
     * 获取连接并执行操作
     */
    private inline fun <T> withConnection(crossinline block: (Connection) -> T): T {
        setupQuoter()
        return if (sharedConnection != null) {
            block(sharedConnection)
        } else {
            dataSource.connection.use { block(it) }
        }
    }

    /**
     * 在事务中执行操作（用于批量写入）
     */
    private inline fun withTransaction(crossinline block: (Connection) -> Unit) {
        setupQuoter()
        if (sharedConnection != null) {
            // 已在事务中，直接执行
            block(sharedConnection)
        } else {
            dataSource.connection.use { conn ->
                val originalAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    block(conn)
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = originalAutoCommit
                }
            }
        }
    }

    /**
     * 使用指定连接执行查询
     */
    private fun <T> executeQueryWith(conn: Connection, action: ActionSelect, handler: (ResultSet) -> T): T {
        return conn.executePrepared(action.query, action.elements) { executeQuery().use(handler) }
    }

    /**
     * 打开游标查询（不关闭 PreparedStatement 和 ResultSet，由 Cursor 管理生命周期）
     * 必须在事务模式下调用。
     */
    private fun <T> openCursor(action: ActionSelect, mapper: (ResultSet) -> T): Cursor<T> {
        val conn = sharedConnection ?: error("游标查询必须在事务中使用")
        val stmt = try {
            conn.prepareStatement(
                action.query,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY
            ).also { s ->
                action.elements.forEachIndexed { i, v -> s.setObject(i + 1, convertParam(v)) }
            }
        } catch (ex: SQLException) {
            warning("Query: ${action.query}")
            warning("Parameters (${action.elements.size}): ${action.elements}")
            throw ex
        }
        val rs = try {
            stmt.executeQuery()
        } catch (ex: SQLException) {
            stmt.close()
            warning("Query: ${action.query}")
            warning("Parameters (${action.elements.size}): ${action.elements}")
            throw ex
        }
        return Cursor(stmt, rs, mapper)
    }

    /**
     * 执行 PreparedStatement 的通用包装，处理参数绑定和错误日志
     */
    private inline fun <T> Connection.executePrepared(
        query: String,
        elements: List<Any?>,
        flags: Int = 0,
        crossinline block: PreparedStatement.() -> T
    ): T {
        return try {
            (if (flags != 0) prepareStatement(query, flags) else prepareStatement(query)).use { stmt ->
                elements.forEachIndexed { i, v -> stmt.setObject(i + 1, convertParam(v)) }
                stmt.block()
            }
        } catch (ex: SQLException) {
            warning("Query: $query")
            warning("Parameters (${elements.size}): $elements")
            throw ex
        }
    }

    /**
     * 转换 SQL 参数值：将枚举类型转换为数据库可识别的值
     *
     * - [IndexedEnum] → [IndexedEnum.index]（数值）
     * - 普通 [Enum] → [Enum.name]（字符串）
     * - 其他类型原样返回
     */
    private fun convertParam(value: Any?): Any? {
        return when (value) {
            is IndexedEnum -> value.index
            is Enum<*> -> value.name
            else -> value
        }
    }

    // === 容器类型（List/Set/Map）两阶段读取辅助 ===

    /**
     * Phase 1：从 ResultSet 读取单条记录到 Map（在连接内执行）
     */
    private fun readMap(typeClass: AnalyzedClass, rs: ResultSet): MutableMap<String, Any?>? {
        return if (rs.next()) typeClass.read(rs).toMutableMap() else null
    }

    /**
     * Phase 1：从 ResultSet 读取所有记录到 Map 列表（在连接内执行）
     */
    private fun readMaps(typeClass: AnalyzedClass, rs: ResultSet): List<MutableMap<String, Any?>> {
        return buildList {
            while (rs.next()) {
                add(typeClass.read(rs).toMutableMap())
            }
        }
    }

    /**
     * Phase 2：将单个 Map 转换为实例，并加载容器数据（连接已释放）
     */
    private fun <T> resolveOneWithCollections(typeClass: AnalyzedClass, map: MutableMap<String, Any?>?): T? {
        if (map == null) return null
        if (typeClass.hasCollectionMembers && !collectionTableInfos.isNullOrEmpty()) {
            loadCollections(typeClass, listOf(map))
        }
        return typeClass.createInstance(map)
    }

    /**
     * Phase 2：将 Map 列表转换为实例列表，并批量加载容器数据（连接已释放）
     */
    private fun <T> resolveWithCollections(typeClass: AnalyzedClass, maps: List<MutableMap<String, Any?>>): List<T> {
        if (maps.isNotEmpty() && typeClass.hasCollectionMembers && !collectionTableInfos.isNullOrEmpty()) {
            loadCollections(typeClass, maps)
        }
        return maps.map { typeClass.createInstance(it) }
    }

    // === 容器类型（List/Set/Map）关联表操作 ===

    /**
     * 查找容器成员对应的 CollectionTableInfo
     */
    private fun findCollectionTableInfo(member: AnalyzedClassMember): CollectionTableInfo? {
        return collectionTableInfos?.firstOrNull { it.member === member }
    }

    /**
     * 插入容器数据（批量）
     */
    private fun insertCollectionData(typeClass: AnalyzedClass, dataList: List<Any>) {
        if (collectionTableInfos.isNullOrEmpty() || !typeClass.hasCollectionMembers) return
        dataList.forEach { data -> syncCollectionData(typeClass, data) }
    }

    /**
     * 同步单条记录的容器数据（先删后插）
     */
    private fun syncCollectionData(typeClass: AnalyzedClass, data: Any) {
        if (collectionTableInfos.isNullOrEmpty() || !typeClass.hasCollectionMembers) return
        val parentId = typeClass.getPrimaryMemberValue(data)?.value() ?: return
        typeClass.collectionMembers.forEach { member ->
            val info = findCollectionTableInfo(member) ?: return@forEach
            val collection = typeClass.getValue(data, member)
            deleteCollectionByFk(info, parentId)
            if (collection != null) {
                insertCollectionValues(info, member, parentId, collection)
            }
        }
    }

    /**
     * 删除容器子表中指定 FK 的所有数据
     */
    private fun deleteCollectionByFk(info: CollectionTableInfo, parentId: Any) {
        setupQuoter()
        val sql = "DELETE FROM ${info.tableName.asFormattedColumnName()} WHERE ${info.fkColumnName.asFormattedColumnName()} = ?"
        withConnection { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, convertParam(parentId))
                stmt.executeUpdate()
            }
        }
    }

    /**
     * 按 FK 值批量删除容器子表数据
     */
    private fun deleteCollectionDataByFk(parentId: Any) {
        if (collectionTableInfos.isNullOrEmpty()) return
        collectionTableInfos.forEach { info: CollectionTableInfo ->
            deleteCollectionByFk(info, parentId.value())
        }
    }

    /**
     * 插入容器值到子表
     */
    private fun insertCollectionValues(info: CollectionTableInfo, member: AnalyzedClassMember, parentId: Any, collection: Any) {
        setupQuoter()
        when {
            member.isMap -> {
                val map = collection as? Map<*, *> ?: return
                if (map.isEmpty()) return
                val sql = "INSERT INTO ${info.tableName.asFormattedColumnName()} (${info.fkColumnName.asFormattedColumnName()}, ${"map_key".asFormattedColumnName()}, ${"map_value".asFormattedColumnName()}) VALUES (?, ?, ?)"
                withConnection { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        map.forEach { (k, v) ->
                            stmt.setObject(1, convertParam(parentId))
                            stmt.setObject(2, k?.toString())
                            stmt.setObject(3, v?.toString())
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
            }
            member.isList -> {
                val list = collection as? List<*> ?: return
                if (list.isEmpty()) return
                val sql = "INSERT INTO ${info.tableName.asFormattedColumnName()} (${info.fkColumnName.asFormattedColumnName()}, ${"value".asFormattedColumnName()}, ${"sort_order".asFormattedColumnName()}) VALUES (?, ?, ?)"
                withConnection { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        list.forEachIndexed { index, v ->
                            stmt.setObject(1, convertParam(parentId))
                            stmt.setObject(2, v?.toString())
                            stmt.setObject(3, index)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
            }
            member.isSet -> {
                val set = collection as? Set<*> ?: return
                if (set.isEmpty()) return
                val sql = "INSERT INTO ${info.tableName.asFormattedColumnName()} (${info.fkColumnName.asFormattedColumnName()}, ${"value".asFormattedColumnName()}) VALUES (?, ?)"
                withConnection { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        set.forEach { v ->
                            stmt.setObject(1, convertParam(parentId))
                            stmt.setObject(2, v?.toString())
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
            }
        }
    }

    /**
     * 批量加载容器数据并注入到结果 map 中
     */
    private fun loadCollections(typeClass: AnalyzedClass, results: List<MutableMap<String, Any?>>) {
        if (collectionTableInfos.isNullOrEmpty() || !typeClass.hasCollectionMembers) return
        val primaryName = typeClass.primaryMemberName ?: return
        val ids = results.mapNotNull { it[primaryName] }
        if (ids.isEmpty()) return
        setupQuoter()
        typeClass.collectionMembers.forEach { member ->
            val info = findCollectionTableInfo(member) ?: return@forEach
            val collectionData = batchLoadCollection(info, member, ids)
            results.forEach { map ->
                val id = map[primaryName]
                map[member.name] = collectionData[id?.toString()] ?: emptyCollection(member)
            }
        }
    }

    /**
     * 批量查询容器子表数据
     */
    private fun batchLoadCollection(
        info: CollectionTableInfo,
        member: AnalyzedClassMember,
        ids: List<Any>
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val idArray = ids.map { convertParam(it)!! }.toTypedArray()
        val placeholders = idArray.joinToString(", ") { "?" }
        val orderClause = if (member.isList) " ORDER BY ${"sort_order".asFormattedColumnName()}" else ""
        val sql = "SELECT * FROM ${info.tableName.asFormattedColumnName()} WHERE ${info.fkColumnName.asFormattedColumnName()} IN ($placeholders)$orderClause"
        withConnection { conn ->
            conn.prepareStatement(sql).use { stmt ->
                idArray.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
                stmt.executeQuery().use { rs ->
                    when {
                        member.isMap -> {
                            while (rs.next()) {
                                val fk = rs.getObject(info.fkColumnName)?.toString() ?: continue
                                val key = rs.getString("map_key")
                                val value = rs.getString("map_value")
                                @Suppress("UNCHECKED_CAST")
                                val map = result.getOrPut(fk) { mutableMapOf<String, String?>() } as MutableMap<String, String?>
                                map[key] = value
                            }
                        }
                        member.isList -> {
                            while (rs.next()) {
                                val fk = rs.getObject(info.fkColumnName)?.toString() ?: continue
                                val value = rs.getString("value")
                                @Suppress("UNCHECKED_CAST")
                                val list = result.getOrPut(fk) { mutableListOf<String?>() } as MutableList<String?>
                                list.add(value)
                            }
                        }
                        member.isSet -> {
                            while (rs.next()) {
                                val fk = rs.getObject(info.fkColumnName)?.toString() ?: continue
                                val value = rs.getString("value")
                                @Suppress("UNCHECKED_CAST")
                                val set = result.getOrPut(fk) { mutableSetOf<String?>() } as MutableSet<String?>
                                set.add(value)
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * 创建空容器
     */
    private fun emptyCollection(member: AnalyzedClassMember): Any {
        return when {
            member.isList -> emptyList<Any>()
            member.isSet -> emptySet<Any>()
            member.isMap -> emptyMap<Any, Any>()
            else -> error("Not a collection type")
        }
    }

    // === 容器类型 Accessor 工厂方法 ===

    /**
     * 通过父记录 @Id 值获取 Map 类型字段的数据库代理
     *
     * @param id 父记录的 @Id 值
     * @param fieldName 容器字段名（支持属性名和列名匹配）
     * @return [DatabaseMap] 代理对象
     */
    fun mapAccessor(id: Any, fieldName: String): DatabaseMap {
        val info = resolveCollectionTableInfo(fieldName) { it.isMap }
        return DatabaseMap(dataSource, info, id.value(), sharedConnection)
    }

    /**
     * 通过 Filter 查找父记录，返回 Map 类型字段的数据库代理
     */
    fun mapAccessor(fieldName: String, filter: Filter.() -> Unit): DatabaseMap {
        val info = resolveCollectionTableInfo(fieldName) { it.isMap }
        val parentId = resolveParentId(filter)
        return DatabaseMap(dataSource, info, parentId, sharedConnection)
    }

    /**
     * 通过父记录 @Id 值获取 List 类型字段的数据库代理
     *
     * @param id 父记录的 @Id 值
     * @param fieldName 容器字段名（支持属性名和列名匹配）
     * @return [DatabaseList] 代理对象
     */
    fun listAccessor(id: Any, fieldName: String): DatabaseList {
        val info = resolveCollectionTableInfo(fieldName) { it.isList }
        return DatabaseList(dataSource, info, id.value(), sharedConnection)
    }

    /**
     * 通过 Filter 查找父记录，返回 List 类型字段的数据库代理
     */
    fun listAccessor(fieldName: String, filter: Filter.() -> Unit): DatabaseList {
        val info = resolveCollectionTableInfo(fieldName) { it.isList }
        val parentId = resolveParentId(filter)
        return DatabaseList(dataSource, info, parentId, sharedConnection)
    }

    /**
     * 通过父记录 @Id 值获取 Set 类型字段的数据库代理
     *
     * @param id 父记录的 @Id 值
     * @param fieldName 容器字段名（支持属性名和列名匹配）
     * @return [DatabaseSet] 代理对象
     */
    fun setAccessor(id: Any, fieldName: String): DatabaseSet {
        val info = resolveCollectionTableInfo(fieldName) { it.isSet }
        return DatabaseSet(dataSource, info, id.value(), sharedConnection)
    }

    /**
     * 通过 Filter 查找父记录，返回 Set 类型字段的数据库代理
     */
    fun setAccessor(fieldName: String, filter: Filter.() -> Unit): DatabaseSet {
        val info = resolveCollectionTableInfo(fieldName) { it.isSet }
        val parentId = resolveParentId(filter)
        return DatabaseSet(dataSource, info, parentId, sharedConnection)
    }

    /**
     * 根据字段名查找 CollectionTableInfo
     *
     * @param fieldName 字段名（匹配 propertyName 或 name）
     * @param typeCheck 容器类型检查（isMap/isList/isSet）
     */
    private fun resolveCollectionTableInfo(fieldName: String, typeCheck: (AnalyzedClassMember) -> Boolean): CollectionTableInfo {
        if (collectionTableInfos.isNullOrEmpty()) {
            error("No collection table info found. Ensure the class has collection members.")
        }
        return collectionTableInfos.firstOrNull { info ->
            (info.member.propertyName == fieldName || info.member.name == fieldName) && typeCheck(info.member)
        } ?: error("Collection field '$fieldName' not found or type mismatch.")
    }

    /**
     * 通过 Filter 查询父记录 @Id 值
     */
    private fun resolveParentId(filter: Filter.() -> Unit): Any {
        val action = ActionSelect(table.name).apply {
            limit(1)
            where(filter)
        }
        return executeQuery(action) { rs ->
            if (rs.next()) {
                val primaryName = collectionTableInfos?.firstOrNull()?.fkColumnName
                    ?.removePrefix("parent_")
                    ?: error("Cannot determine primary key column.")
                rs.getObject(primaryName) ?: error("Parent record primary key is null.")
            } else {
                throw NoSuchElementException("No parent record found matching the given filter.")
            }
        }
    }
}
