package taboolib.expansion

import taboolib.expansion.AnalyzedClassMember.Companion.toColumnName
import taboolib.module.database.*
import java.sql.ResultSet

/**
 * DataMapper 的标准实现
 *
 * 封装 ContainerOperator，提供类型安全的 CRUD 操作。
 * 支持可选的 L2 双层缓存。
 *
 * ### L2 缓存架构
 *
 * - **Bean Cache**：按实体 ID 存储，细粒度失效（更新/删除只影响特定 ID）
 * - **Query Cache**：按查询哈希存储，粗粒度失效（任何写操作清空整个 Query Cache）
 *
 * ### 缓存失效策略
 *
 * | 操作类型 | Bean Cache | Query Cache |
 * |---------|-----------|-------------|
 * | 插入 | 不影响 | 全部清空 |
 * | 单条更新/删除 | 仅失效该 ID | 全部清空 |
 * | 批量/不确定范围 | 全部清空 | 全部清空 |
 *
 * @param type 数据类的 Class 对象
 * @param container 持久化容器
 * @param cache L2 双层缓存（可选）
 */
class DataMapperImpl<T>(
    private val type: Class<T>,
    private val container: PersistentContainer,
    private val cache: L2Cache?
) : DataMapper<T> {

    private val operator: ContainerOperator
        get() = container[type.simpleName.toColumnName()]

    private val analyzedClass by lazy { AnalyzedClass.of(type) }

    // === 插入 ===

    override fun insert(data: T) {
        operator.insert(listOf(data as Any))
        invalidateOnInsert()
    }

    override fun insertBatch(dataList: List<T>) {
        if (dataList.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        operator.insert(dataList as List<Any>)
        invalidateOnInsert()
    }

    override fun insertAndGetKey(data: T): Long {
        val keys = operator.insertAndGetKeys(listOf(data as Any))
        invalidateOnInsert()
        return keys.firstOrNull() ?: -1L
    }

    override fun insertBatchAndGetKeys(dataList: List<T>): List<Long> {
        if (dataList.isEmpty()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val keys = operator.insertAndGetKeys(dataList as List<Any>)
        invalidateOnInsert()
        return keys
    }

    // === 查询 ===

    override fun findById(id: Any, filter: Filter.() -> Unit): T? {
        return cachedBean("findById", id, filter) { operator.findOne(type, id, filter) }
    }

    override fun findAll(id: Any, filter: Filter.() -> Unit): List<T> {
        return cachedBean("findAllById", id, filter) { operator.find(type, id, filter) }
    }

    override fun findOne(filter: Filter.() -> Unit): T? {
        return cachedQuery("findOne", filter) { operator.getOne(type, filter) }
    }

    override fun findAll(filter: Filter.() -> Unit): List<T> {
        return cachedQuery("findAll", filter) { operator.get(type, filter) }
    }

    override fun findByIds(ids: List<Any>): List<T> {
        if (ids.isEmpty()) return emptyList()
        return cachedQuery("findByIds", ids) { operator.findByIds(type, ids) }
    }

    // === 基于 @Key 的查询 ===

    override fun findByKey(data: T): List<T> {
        return cachedQuery("findByKey", data) { operator.findByKey(type, data as Any) }
    }

    override fun findOneByKey(data: T): T? {
        return cachedQuery("findOneByKey", data) { operator.findOneByKey(type, data as Any) }
    }

    override fun existsByKey(data: T): Boolean {
        return cachedQuery("existsByKey", data) { operator.hasByKey(type, data as Any) }
    }

    override fun deleteByKey(data: T) {
        operator.deleteByKey(data as Any)
        invalidateOnMutation(data as Any)
    }

    // === 基于自增行 ID 的操作 ===

    override fun findByRowId(rowId: Long): T? {
        return cachedBean("findByRowId", rowId) { operator.findByRowId(type, rowId) }
    }

    override fun deleteByRowId(rowId: Long) {
        operator.deleteByRowId(rowId)
        invalidateOnRowIdMutation(rowId)
    }

    // === 排序 ===

    override fun sort(row: String, limit: Int, filter: Filter.() -> Unit): List<T> {
        return cachedQuery("sort", row, limit, filter) { operator.sort(type, row, limit, filter) }
    }

    override fun sortDescending(row: String, limit: Int, filter: Filter.() -> Unit): List<T> {
        return cachedQuery("sortDescending", row, limit, filter) { operator.sortDescending(type, row, limit, filter) }
    }

    // === 分页 ===

    override fun findPage(page: Int, size: Int, filter: Filter.() -> Unit): Page<T> {
        val content = cachedQuery("findPage", page, size, filter) { operator.getPage(type, page, size, filter) }
        val total = cachedQuery("count", filter) { operator.count(filter) }
        return Page(content, page, size, total)
    }

    override fun sortPage(row: String, page: Int, size: Int, filter: Filter.() -> Unit): Page<T> {
        val content = cachedQuery("sortPage", row, page, size, filter) { operator.sortPage(type, row, page, size, filter) }
        val total = cachedQuery("count", filter) { operator.count(filter) }
        return Page(content, page, size, total)
    }

    override fun sortDescendingPage(row: String, page: Int, size: Int, filter: Filter.() -> Unit): Page<T> {
        val content = cachedQuery("sortDescendingPage", row, page, size, filter) { operator.sortDescendingPage(type, row, page, size, filter) }
        val total = cachedQuery("count", filter) { operator.count(filter) }
        return Page(content, page, size, total)
    }

    // === 游标查询 ===

    override fun selectCursor(filter: Filter.() -> Unit): Cursor<T> {
        error("游标查询必须在 transaction {} 中使用")
    }

    override fun sortCursor(row: String, filter: Filter.() -> Unit): Cursor<T> {
        error("游标查询必须在 transaction {} 中使用")
    }

    override fun sortDescendingCursor(row: String, filter: Filter.() -> Unit): Cursor<T> {
        error("游标查询必须在 transaction {} 中使用")
    }

    // === 更新 ===

    override fun update(data: T, filter: Filter.() -> Unit) {
        operator.update(data as Any, true, filter)
        invalidateOnMutation(data as Any)
    }

    override fun updateByKey(data: T) {
        operator.updateByKey(data as Any)
        invalidateOnMutation(data as Any)
    }

    override fun insertOrUpdate(data: T, filter: Filter.() -> Unit) {
        operator.update(data as Any, true, filter)
        invalidateOnMutation(data as Any)
    }

    override fun upsertBatch(dataList: List<T>) {
        if (dataList.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        operator.upsert(dataList as List<Any>)
        invalidateAll()
    }

    override fun updateBatch(dataList: List<T>) {
        if (dataList.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        operator.updateBatch(dataList as List<Any>)
        invalidateAll()
    }

    // === 删除 ===

    override fun deleteById(id: Any, filter: Filter.() -> Unit) {
        operator.delete(type, id, filter)
        invalidateOnIdMutation(id)
    }

    override fun deleteWhere(filter: Filter.() -> Unit) {
        operator.deleteWhere(filter)
        invalidateAll()
    }

    override fun deleteByIds(ids: List<Any>) {
        if (ids.isEmpty()) return
        operator.deleteByIds(type, ids)
        invalidateAll()
    }

    // === 检查 ===

    override fun exists(id: Any, filter: Filter.() -> Unit): Boolean {
        return cachedBean("exists", id, filter) { operator.has(type, id, filter) }
    }

    override fun exists(filter: Filter.() -> Unit): Boolean {
        return cachedQuery("existsFilter", filter) { operator.has(filter) }
    }

    // === 计数 ===

    override fun count(filter: Filter.() -> Unit): Long {
        return cachedQuery("count", filter) { operator.count(filter) }
    }

    // === 事务 ===

    override fun <R> transaction(block: DataMapper<T>.() -> R): Result<R> {
        return container.transaction {
            val txOperator = operator(type.simpleName.toColumnName())
            val txMapper = TransactionalDataMapper(type, txOperator, cache, connection)
            txMapper.block()
        }
    }

    // === 自定义 SQL ===

    override val tableName: String
        get() = operator.table.name

    override fun query(builder: ActionSelect.() -> Unit): List<T> {
        val typeClass = AnalyzedClass.of(type)
        val action = ActionSelect(tableName).apply(builder)
        return operator.select(action) { rs ->
            buildList {
                while (rs.next()) { add(typeClass.createInstance<T>(typeClass.read(rs))) }
            }
        }
    }

    override fun queryOne(builder: ActionSelect.() -> Unit): T? {
        val typeClass = AnalyzedClass.of(type)
        val action = ActionSelect(tableName).apply { builder(); limit(1) }
        return operator.select(action) { rs ->
            if (rs.next()) typeClass.createInstance<T>(typeClass.read(rs)) else null
        }
    }

    override fun <R> rawQuery(builder: ActionSelect.() -> Unit, handler: (ResultSet) -> R): R {
        val action = ActionSelect(tableName).apply(builder)
        return operator.select(action, handler)
    }

    override fun rawUpdate(builder: ActionUpdate.() -> Unit): Int {
        val action = ActionUpdate(tableName).apply(builder)
        val result = operator.execute(action)
        invalidateAll()
        return result
    }

    override fun rawDelete(builder: ActionDelete.() -> Unit): Int {
        val action = ActionDelete(tableName).apply(builder)
        val result = operator.execute(action)
        invalidateAll()
        return result
    }

    override fun rawExecute(action: Action): Int {
        val result = operator.execute(action)
        invalidateAll()
        return result
    }

    // === 多表联查 ===

    override fun join(builder: JoinQuery.() -> Unit): JoinQuery {
        return container.join {
            from(tableName)
            builder()
        }
    }

    // === 生命周期 ===

    override fun close() = container.close()

    // === 缓存辅助 ===

    @Suppress("UNCHECKED_CAST")
    private fun <R> cachedBean(method: String, vararg args: Any?, query: () -> R): R {
        if (cache == null) return query()
        val key = buildCacheKey(method, *args)
        return cache.beanCache.get(key) { query() } as R
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> cachedQuery(method: String, vararg args: Any?, query: () -> R): R {
        if (cache == null) return query()
        val key = buildCacheKey(method, *args)
        return cache.queryCache.get(key) { query() } as R
    }

    private fun buildCacheKey(method: String, vararg args: Any?): String {
        return "$method:${args.joinToString(",") { it?.toString() ?: "null" }}"
    }

    // === L2 缓存失效策略 ===

    /** 插入后：仅清空 Query Cache */
    private fun invalidateOnInsert() {
        cache?.queryCache?.invalidateAll()
    }

    /** 单条更新/删除后（按 ID）：失效该 ID 的 Bean Cache + 清空 Query Cache */
    private fun invalidateOnIdMutation(id: Any) {
        if (cache == null) return
        cache.beanCache.invalidateByPrefix("findById:$id")
        cache.beanCache.invalidateByPrefix("findAllById:$id")
        cache.beanCache.invalidateByPrefix("exists:$id")
        cache.queryCache.invalidateAll()
    }

    /** 从数据对象提取 @Id 值，失效该 ID 的 Bean Cache + 清空 Query Cache */
    private fun invalidateOnMutation(data: Any) {
        if (cache == null) return
        val id = analyzedClass.getPrimaryMemberValue(data)
        if (id != null) {
            invalidateOnIdMutation(id)
        } else {
            invalidateAll()
        }
    }

    /** 失效指定行 ID 的 Bean Cache + 清空 Query Cache */
    private fun invalidateOnRowIdMutation(rowId: Long) {
        if (cache == null) return
        cache.beanCache.invalidate(buildCacheKey("findByRowId", rowId))
        cache.queryCache.invalidateAll()
    }

    /** 批量/不确定范围：全部清空 */
    private fun invalidateAll() {
        cache?.invalidateAll()
    }
}
