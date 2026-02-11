package taboolib.expansion.ioc.bean

import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import taboolib.expansion.DataMapper
import taboolib.expansion.ioc.annotation.Component
import taboolib.expansion.ioc.annotation.PostConstruct
import taboolib.expansion.ioc.annotation.PreDestroy
import taboolib.expansion.ioc.annotation.Resource
import taboolib.expansion.ioc.persistence.PersistenceManager
import taboolib.expansion.ioc.scope.PlayerData
import taboolib.expansion.ioc.scope.PlayerOperator
import taboolib.expansion.ioc.scope.PlayerScope
import java.lang.reflect.ParameterizedType
import java.util.concurrent.ConcurrentHashMap

/**
 * IoC 核心容器。
 *
 * 负责 Bean 注册、依赖解析、实例管理和生命周期控制。
 */
object BeanContainer {

    /** 所有已注册的 BeanDefinition */
    val definitions = ConcurrentHashMap<Class<*>, BeanDefinition>()

    /** SINGLETON 实例缓存 */
    val singletons = ConcurrentHashMap<Class<*>, Any>()

    /** 是否已初始化 */
    @Volatile
    var initialized = false
        private set

    /**
     * 注册一个 @Component 类。
     */
    fun register(clazz: Class<*>) {
        val annotation = clazz.getAnnotation(Component::class.java) ?: return
        val scope = annotation.scope

        // 判断是否为独立数据类（直接继承 PlayerData）
        val isData = PlayerData::class.java.isAssignableFrom(clazz)
                && !PlayerOperator::class.java.isAssignableFrom(clazz)

        // 判断是否为 Manager（继承 PlayerOperator<T>），提取管理的数据类型
        val managedDataType = if (PlayerOperator::class.java.isAssignableFrom(clazz)) {
            extractSuperclassGenericType(clazz, PlayerOperator::class.java)
        } else null

        if (scope == BeanScope.PLAYER && isData && !PlayerData::class.java.isAssignableFrom(clazz)) {
            warning("[IoC] PLAYER 作用域的数据类 ${clazz.name} 必须继承 PlayerData")
            return
        }

        val deps = resolveDependencies(clazz)
        val postConstruct = clazz.declaredMethods.firstOrNull { it.isAnnotationPresent(PostConstruct::class.java) }
        val preDestroy = clazz.declaredMethods.firstOrNull { it.isAnnotationPresent(PreDestroy::class.java) }

        postConstruct?.isAccessible = true
        preDestroy?.isAccessible = true

        definitions[clazz] = BeanDefinition(clazz, scope, isData, managedDataType, deps, postConstruct, preDestroy)
        debug("[IoC] 注册 Bean: ${clazz.simpleName} (scope=$scope, isData=$isData, managedData=${managedDataType?.simpleName}, deps=${deps.size})")
    }

    /**
     * 初始化容器：持久化层 → 拓扑排序 → 实例化 SINGLETON → 注入依赖 → @PostConstruct。
     */
    fun initialize() {
        if (initialized) return
        initialized = true

        // 收集需要持久化的数据类：独立数据类 + Manager 管理的数据类（去重）
        val dataClasses = mutableSetOf<Class<*>>()
        for (def in definitions.values) {
            if (def.isDataClass) {
                dataClasses.add(def.type)
            }
            if (def.managedDataType != null) {
                dataClasses.add(def.managedDataType)
            }
        }
        if (dataClasses.isNotEmpty()) {
            debug("[IoC] 持久化数据类: ${dataClasses.map { it.simpleName }}")
            PersistenceManager.dataClasses.addAll(dataClasses)
            PersistenceManager.initialize()
        }

        // 拓扑排序 SINGLETON
        val singletonDefs = definitions.values.filter { it.scope == BeanScope.SINGLETON }
        val sorted = topologicalSort(singletonDefs)
        debug("[IoC] SINGLETON 初始化顺序: ${sorted.map { it.type.simpleName }}")

        // 二阶段初始化：先全部 new
        for (def in sorted) {
            singletons[def.type] = def.type.getDeclaredConstructor().newInstance()
        }

        // 再全部 inject + @PostConstruct
        for (def in sorted) {
            val instance = singletons[def.type] ?: continue
            injectDependencies(instance, def, null)
            def.postConstruct?.invoke(instance)
        }

        // 为已在线的玩家加载 PLAYER 作用域 Bean
        if (definitions.values.any { it.scope == BeanScope.PLAYER }) {
            PlayerScope.loadOnlinePlayers()
        }
        debug("[IoC] 容器初始化完成")
    }

    /**
     * 获取 Bean 实例。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getBean(type: Class<T>): T? {
        val def = definitions[type] ?: return null
        return when (def.scope) {
            BeanScope.SINGLETON -> singletons[type] as? T
            BeanScope.PROTOTYPE -> {
                val instance = type.getDeclaredConstructor().newInstance()
                injectDependencies(instance as Any, def, null)
                def.postConstruct?.invoke(instance)
                instance
            }
            BeanScope.PLAYER -> {
                warning("[IoC] 不能直接获取 PLAYER 作用域的 Bean，请使用 PlayerScope 或 Player.ioc()")
                null
            }
        }
    }

    /**
     * 关闭容器。
     */
    fun shutdown() {
        // 1. 关闭玩家作用域
        PlayerScope.shutdown()
        // 2. SINGLETON @PreDestroy
        for (def in definitions.values.filter { it.scope == BeanScope.SINGLETON }) {
            val instance = singletons[def.type] ?: continue
            try {
                def.preDestroy?.invoke(instance)
            } catch (e: Exception) {
                warning("[IoC] @PreDestroy 执行失败: ${def.type.name} - ${e.message}")
            }
        }
        // 3. 关闭持久化层
        PersistenceManager.shutdown()
        // 4. 清理
        singletons.clear()
        definitions.clear()
        initialized = false
    }

    /**
     * 为实例注入 @Resource 依赖。
     *
     * @param playerBeans 玩家作用域的 Bean 缓存（仅 PLAYER 作用域使用）
     */
    fun injectDependencies(instance: Any, def: BeanDefinition, playerBeans: Map<String, Any>?) {
        for (dep in def.dependencies) {
            val value = resolveDependencyValue(dep, def.scope, playerBeans)
            if (value != null) {
                dep.field.isAccessible = true
                dep.field.set(instance, value)
            }
        }
    }

    private fun resolveDependencyValue(dep: DependencyField, ownerScope: BeanScope, playerBeans: Map<String, Any>?): Any? {
        val fieldType = dep.fieldType

        // DataMapper<T> 注入
        if (DataMapper::class.java.isAssignableFrom(fieldType) && dep.genericType != null) {
            return PersistenceManager.getMapper(dep.genericType)
        }

        // 其他 @Component Bean
        val targetDef = definitions[fieldType]
        if (targetDef != null) {
            return when (targetDef.scope) {
                BeanScope.SINGLETON -> singletons[fieldType]
                BeanScope.PLAYER -> {
                    if (ownerScope == BeanScope.SINGLETON) {
                        warning("[IoC] SINGLETON Bean 不能注入 PLAYER 作用域的 Bean: ${fieldType.name}")
                        null
                    } else {
                        // 从同一玩家的 Bean 缓存中获取
                        playerBeans?.get(fieldType.name)
                    }
                }
                BeanScope.PROTOTYPE -> {
                    warning("[IoC] 不支持注入 PROTOTYPE 作用域的 Bean: ${fieldType.name}")
                    null
                }
            }
        }
        return null
    }

    private fun resolveDependencies(clazz: Class<*>): List<DependencyField> {
        val result = mutableListOf<DependencyField>()
        for (field in clazz.declaredFields) {
            if (!field.isAnnotationPresent(Resource::class.java)) continue
            val genericType = extractGenericType(field)
            result.add(DependencyField(field, field.type, genericType))
        }
        return result
    }

    private fun extractGenericType(field: java.lang.reflect.Field): Class<*>? {
        val type = field.genericType
        if (type is ParameterizedType) {
            val args = type.actualTypeArguments
            if (args.isNotEmpty() && args[0] is Class<*>) {
                return args[0] as Class<*>
            }
        }
        return null
    }

    /**
     * 拓扑排序 + 循环依赖检测。
     */
    private fun topologicalSort(defs: List<BeanDefinition>): List<BeanDefinition> {
        val defMap = defs.associateBy { it.type }
        val visited = mutableSetOf<Class<*>>()
        val visiting = mutableSetOf<Class<*>>()
        val sorted = mutableListOf<BeanDefinition>()

        fun visit(def: BeanDefinition) {
            if (def.type in visited) return
            if (def.type in visiting) {
                // 循环依赖 — 二阶段初始化可以处理，跳过
                return
            }
            visiting.add(def.type)
            for (dep in def.dependencies) {
                val depDef = defMap[dep.fieldType]
                if (depDef != null) {
                    visit(depDef)
                }
            }
            visiting.remove(def.type)
            visited.add(def.type)
            sorted.add(def)
        }

        for (def in defs) {
            visit(def)
        }
        return sorted
    }

    /**
     * 从子类中提取父类的泛型参数类型。
     * 例如 class MyManager : PlayerOperator<MyData>() → 返回 MyData::class.java
     */
    private fun extractSuperclassGenericType(clazz: Class<*>, targetSuperclass: Class<*>): Class<*>? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val genericSuper = current.genericSuperclass
            if (genericSuper is ParameterizedType && genericSuper.rawType == targetSuperclass) {
                val arg = genericSuper.actualTypeArguments.firstOrNull()
                if (arg is Class<*>) return arg
            }
            current = current.superclass
        }
        return null
    }
}
