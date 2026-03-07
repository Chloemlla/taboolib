package taboolib.expansion.ioc.persistence

import taboolib.common.platform.function.warning
import taboolib.expansion.DataMapper
import taboolib.expansion.mapper.DataMapperImpl
import taboolib.expansion.PersistentContainer
import taboolib.expansion.db
import taboolib.expansion.persistentContainer
import java.util.concurrent.ConcurrentHashMap

/**
 * ptc-object 桥接层。
 *
 * 管理数据类的持久化容器和 DataMapper 实例。
 */
object PersistenceManager {

    /** 待注册的数据类 */
    val dataClasses = mutableListOf<Class<*>>()

    /** 共享容器实例 */
    var container: PersistentContainer? = null
        private set

    /** 类型 → Mapper 映射 */
    val mappers = ConcurrentHashMap<Class<*>, DataMapper<*>>()

    /**
     * 初始化持久化层。
     */
    fun initialize() {
        if (dataClasses.isEmpty()) return
        try {
            container = persistentContainer(db()) {
                for (clazz in dataClasses) {
                    new(clazz)
                }
            }
            // 为每个数据类创建 DataMapper
            for (clazz in dataClasses) {
                mappers[clazz] = DataMapperImpl(clazz, container!!, null)
            }
        } catch (e: Exception) {
            warning("[IoC] 持久化层初始化失败: ${e.message}")
        }
    }

    /**
     * 获取指定类型的 DataMapper。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMapper(type: Class<T>): DataMapper<T>? {
        return mappers[type] as? DataMapper<T>
    }

    /**
     * 关闭持久化层。
     */
    fun shutdown() {
        container?.close()
        container = null
        mappers.clear()
        dataClasses.clear()
    }
}
