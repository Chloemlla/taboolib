package taboolib.expansion.ioc.scope

import taboolib.expansion.AnalyzedClass
import taboolib.expansion.DataMapper
import taboolib.expansion.Page
import taboolib.expansion.ioc.persistence.PersistenceManager
import taboolib.module.database.ActionDelete
import taboolib.module.database.ActionUpdate
import taboolib.module.database.Filter
import java.lang.reflect.ParameterizedType
import java.util.UUID
import kotlin.reflect.KProperty1

/**
 * 玩家数据管理器基类，对标 SingletonManager 设计。
 *
 * 每个玩家拥有独立的 Manager 实例，持有该玩家的数据对象。
 * 子类直接编写业务方法，通过 [data] 访问数据，通过 [update] 保存。
 *
 * 使用方式：
 * ```
 * @Component(scope = BeanScope.PLAYER)
 * class CoinsManager : PlayerOperator<CoinsData>() {
 *
 *     fun addCoins(amount: Int) {
 *         data.coins += amount
 *         update(CoinsData::coins)  // 只更新 coins 字段
 *     }
 *
 *     fun reset() {
 *         data.coins = 0
 *         data.level = 1
 *         update(CoinsData::coins, CoinsData::level)  // 更新多个字段
 *     }
 *
 *     fun save() {
 *         update()  // 不传参数 = 全量更新
 *     }
 * }
 * ```
 */
open class PlayerOperator<T : PlayerData> {

    /** 当前玩家数据，由框架自动加载 */
    lateinit var data: T
        internal set

    /** 数据类型（通过反射获取泛型参数） */
    @Suppress("UNCHECKED_CAST")
    val dataType: Class<T> by lazy {
        (javaClass.genericSuperclass as ParameterizedType)
            .actualTypeArguments[0] as Class<T>
    }

    // ========== 生命周期钩子 ==========

    /** 数据加载完成后调用 */
    open fun onLoad() {}

    /** 定时保存 / 退出保存前调用 */
    open fun onSave() {}

    /** 玩家退出时调用（在 onSave 之后） */
    open fun onQuit() {}

    // ========== 持久化操作 ==========

    /** 全量保存当前数据到数据库 */
    fun update() {
        val mapper = getMapper() ?: return
        @Suppress("UNCHECKED_CAST")
        (mapper as DataMapper<Any>).insertOrUpdate(data)
    }

    /**
     * 选择性字段更新，只写入指定的字段。
     *
     * ```
     * data.coins += 100
     * update(CoinsData::coins)
     *
     * data.level = 10
     * data.exp = 0
     * update(CoinsData::level, CoinsData::exp)
     * ```
     */
    fun update(vararg properties: KProperty1<T, *>) {
        if (properties.isEmpty()) {
            update()
            return
        }
        val mapper = getMapper() ?: return
        val analyzed = AnalyzedClass.of(dataType)
        val primaryName = analyzed.primaryMemberName ?: return
        val primaryValue = analyzed.getPrimaryMemberValue(data as Any)
        mapper.rawUpdate {
            where(primaryName eq toDbValue(primaryValue))
            for (prop in properties) {
                val member = analyzed.members.firstOrNull { it.propertyName == prop.name } ?: continue
                val value = analyzed.getValue(data as Any, member)
                set(member.name, toDbValue(value))
            }
        }
    }

    // ========== 全局查询（不限于当前玩家） ==========

    /** 通过 ID 查询 */
    fun findById(id: Any): T? = getMapper()?.findById(id)

    /** 条件查询（多条） */
    fun list(filter: Filter.() -> Unit = {}): List<T> = getMapper()?.findAll(filter) ?: emptyList()

    /** 条件查询（单条） */
    fun one(filter: Filter.() -> Unit): T? = getMapper()?.findOne(filter)

    /** 计数 */
    fun count(filter: Filter.() -> Unit = {}): Long = getMapper()?.count(filter) ?: 0

    /** 排行榜（倒序） */
    fun top(field: String, limit: Int = 10, filter: Filter.() -> Unit = {}): List<T> =
        getMapper()?.sortDescending(field, limit, filter) ?: emptyList()

    /** 排行榜（正序） */
    fun bottom(field: String, limit: Int = 10, filter: Filter.() -> Unit = {}): List<T> =
        getMapper()?.sort(field, limit, filter) ?: emptyList()

    /** 分页查询 */
    fun page(page: Int, size: Int, filter: Filter.() -> Unit = {}): Page<T> =
        getMapper()?.findPage(page, size, filter) ?: Page(emptyList(), page, size, 0)

    /** 分页排序（倒序） */
    fun pageDesc(field: String, page: Int, size: Int, filter: Filter.() -> Unit = {}): Page<T> =
        getMapper()?.sortDescendingPage(field, page, size, filter) ?: Page(emptyList(), page, size, 0)

    /** 自定义更新 */
    fun rawUpdate(builder: ActionUpdate.() -> Unit): Int =
        getMapper()?.rawUpdate(builder) ?: 0

    /** 自定义删除 */
    fun rawDelete(builder: ActionDelete.() -> Unit): Int =
        getMapper()?.rawDelete(builder) ?: 0

    /** 事务 */
    fun <R> transaction(block: DataMapper<T>.() -> R): Result<R> =
        getMapper()?.transaction(block) ?: Result.failure(IllegalStateException("DataMapper not available"))

    /** 获取底层 DataMapper */
    fun getMapper(): DataMapper<T>? = PersistenceManager.getMapper(dataType)

    /** 将 Kotlin 值转换为数据库兼容值 */
    private fun toDbValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Enum<*> -> value.name
            is UUID -> value.toString()
            is Char -> value.code
            else -> value
        }
    }
}
