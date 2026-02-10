package taboolib.expansion

import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import taboolib.expansion.AnalyzedClassMember.Companion.resolveTableName
import taboolib.expansion.AnalyzedClassMember.Companion.toColumnName
import taboolib.module.database.ColumnBuilder
import taboolib.module.database.Host
import taboolib.module.database.Table
import java.util.concurrent.ConcurrentHashMap

abstract class Container<T : ColumnBuilder>(val host: Host<T>) {

    val map = ConcurrentHashMap<String, ContainerOperator>()
    val dataSource = host.createDataSource(autoRelease = false)

    /** class → 表名映射（用于 @LinkTable 关联查询） */
    val classTableMap = ConcurrentHashMap<Class<*>, String>()

    /** class → ContainerOperator 映射（用于 @LinkTable 级联保存） */
    val classOperatorMap = ConcurrentHashMap<Class<*>, ContainerOperator>()

    /** class → 容器关联表信息列表 */
    val collectionTableMap = ConcurrentHashMap<Class<*>, List<CollectionTableInfo>>()

    /** 创建表 */
    protected abstract fun createTableObject(type: AnalyzedClass, name: String): Table<*, *>

    /** 创建容器类型子表 */
    protected abstract fun createCollectionTableObject(
        parentType: AnalyzedClass,
        parentTableName: String,
        member: AnalyzedClassMember,
        childTableName: String
    ): Table<*, *>

    /** 创建表 */
    open fun createTable(type: AnalyzedClass, name: String) {
        // 先注册 @LinkTable 关联类的表（确保关联类的 operator 在主类之前就绑定）
        for (member in type.linkMembers) {
            val linkClass = member.linkTableClass ?: continue
            if (!classOperatorMap.containsKey(linkClass)) {
                val linkType = AnalyzedClass.of(linkClass)
                val linkName = linkClass.resolveTableName()
                createTable(linkType, linkName)
            }
        }
        classTableMap[type.clazz] = name
        // 创建容器类型子表
        val collectionInfos = mutableListOf<CollectionTableInfo>()
        if (type.hasCollectionMembers) {
            val primaryMember = type.primaryMember ?: error(
                "容器类型成员需要主类有 @Id 字段。Collection members require the parent class to have an @Id field."
            )
            for (member in type.collectionMembers) {
                val childTableName = "${name}_${member.name}"
                val fkColumnName = "parent_${primaryMember.name}"
                val childTable = createCollectionTableObject(type, name, member, childTableName)
                collectionInfos += CollectionTableInfo(childTableName, fkColumnName, member, childTable)
            }
        }
        if (collectionInfos.isNotEmpty()) {
            collectionTableMap[type.clazz] = collectionInfos
        }
        val operator = ContainerOperatorImpl(
            createTableObject(type, name), dataSource,
            classTableMap = classTableMap, classOperatorMap = classOperatorMap,
            collectionTableInfos = collectionInfos.ifEmpty { null }
        )
        map[name] = operator
        classOperatorMap[type.clazz] = operator
    }

    /** 初始化所有表 */
    open fun init() {
        map.forEach { it.value.table.createTable(dataSource) }
        // 创建容器子表
        collectionTableMap.values.forEach { infos ->
            infos.forEach { it.table.createTable(dataSource) }
        }
    }

    /** 获取路径 */
    open fun path(): String {
        return host.connectionUrl.toString()
    }

    /** 关闭连接 */
    open fun close() {
        dataSource.invokeMethod<Void>("close")
    }

    /** 获取控制器 */
    open fun operator(name: String): ContainerOperator {
        return map[name] ?: error("Table not found")
    }
}