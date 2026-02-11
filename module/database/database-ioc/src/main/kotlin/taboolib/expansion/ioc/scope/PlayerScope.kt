package taboolib.expansion.ioc.scope

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.Inject
import taboolib.common.platform.Schedule
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.debug
import taboolib.common.platform.function.warning
import taboolib.expansion.ioc.bean.BeanContainer
import taboolib.expansion.ioc.bean.BeanDefinition
import taboolib.expansion.ioc.bean.BeanScope
import taboolib.expansion.ioc.persistence.PersistenceManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家作用域管理。
 *
 * 管理 PLAYER 作用域 Bean 的生命周期：
 * - 玩家加入 → 加载数据 + 创建实例 → 注入依赖 → 生命周期回调
 * - 玩家退出 → 生命周期回调 → 保存数据 → 清理缓存
 * - 定时保存所有在线玩家的数据
 */
@Inject
object PlayerScope {

    /** 玩家 Bean 缓存: UUID → (类名 → 实例) */
    val playerBeans = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Any>>()

    /**
     * 获取玩家的 Bean 实例。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getBean(uuid: UUID, type: Class<T>): T? {
        return playerBeans[uuid]?.get(type.name) as? T
    }

    /**
     * 加载玩家的所有 PLAYER 作用域 Bean。
     */
    fun loadPlayer(uuid: UUID) {
        debug("[IoC] 加载玩家数据: $uuid")
        val beans = ConcurrentHashMap<String, Any>()
        val playerDefs = BeanContainer.definitions.values.filter { it.scope == BeanScope.PLAYER }

        // 1. 加载独立数据类 Bean（从 DB）
        for (def in playerDefs.filter { it.isDataClass }) {
            try {
                beans[def.type.name] = loadDataInstance(def.type, uuid)
            } catch (e: Exception) {
                warning("[IoC] 加载玩家数据类失败: ${def.type.name} - ${e.message}")
            }
        }

        // 2. 创建 Manager 类 Bean（PlayerOperator 子类）+ 绑定其管理的数据
        for (def in playerDefs.filter { it.managedDataType != null }) {
            try {
                val manager = def.type.getDeclaredConstructor().newInstance() as PlayerOperator<*>
                // 优先复用步骤 1 已加载的数据实例，避免同一数据类存在两个实例导致保存覆盖
                val dataInstance = beans[def.managedDataType!!.name] ?: loadDataInstance(def.managedDataType!!, uuid).also {
                    beans[def.managedDataType!!.name] = it
                }
                setOperatorData(manager, dataInstance)
                beans[def.type.name] = manager
            } catch (e: Exception) {
                warning("[IoC] 创建玩家 Manager 失败: ${def.type.name} - ${e.message}")
            }
        }

        // 3. 创建普通服务类 Bean（非数据类、非 Manager）
        for (def in playerDefs.filter { !it.isDataClass && it.managedDataType == null }) {
            try {
                beans[def.type.name] = def.type.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                warning("[IoC] 创建玩家服务类失败: ${def.type.name} - ${e.message}")
            }
        }

        playerBeans[uuid] = beans
        debug("[IoC] 玩家 $uuid 已创建 ${beans.size} 个 Bean: ${beans.keys.map { it.substringAfterLast('.') }}")

        // 4. 注入 @Resource 依赖
        for (def in playerDefs) {
            val instance = beans[def.type.name] ?: continue
            BeanContainer.injectDependencies(instance, def, beans)
        }

        // 5. 生命周期回调：Manager.onLoad() + @PostConstruct
        for (def in playerDefs) {
            val instance = beans[def.type.name] ?: continue
            try {
                if (instance is PlayerOperator<*>) {
                    instance.onLoad()
                }
                def.postConstruct?.invoke(instance)
            } catch (e: Exception) {
                warning("[IoC] 生命周期回调失败: ${def.type.name} - ${e.message}")
            }
        }
    }

    /**
     * 卸载玩家的所有 PLAYER 作用域 Bean。
     */
    fun unloadPlayer(uuid: UUID) {
        debug("[IoC] 卸载玩家数据: $uuid")
        val beans = playerBeans[uuid] ?: return
        val playerDefs = BeanContainer.definitions.values.filter { it.scope == BeanScope.PLAYER }

        // 1. 生命周期回调：Manager.onSave() + Manager.onQuit() + @PreDestroy
        for (def in playerDefs) {
            val instance = beans[def.type.name] ?: continue
            try {
                if (instance is PlayerOperator<*>) {
                    instance.onSave()
                    instance.onQuit()
                }
                def.preDestroy?.invoke(instance)
            } catch (e: Exception) {
                warning("[IoC] 生命周期回调失败: ${def.type.name} - ${e.message}")
            }
        }

        // 2. 保存数据
        savePlayerData(beans)

        // 3. 清理缓存
        playerBeans.remove(uuid)
    }

    /**
     * 保存玩家的所有持久化数据。
     */
    private fun savePlayerData(beans: Map<String, Any>) {
        val playerDefs = BeanContainer.definitions.values.filter { it.scope == BeanScope.PLAYER }
        // 收集被 Manager 管理的数据类型，避免重复保存
        val managedTypes = playerDefs.mapNotNull { it.managedDataType?.name }.toSet()
        for (def in playerDefs) {
            val instance = beans[def.type.name] ?: continue
            try {
                when {
                    // 独立数据类：跳过已被 Manager 管理的（Manager 会负责保存）
                    def.isDataClass -> {
                        if (def.type.name in managedTypes) continue
                        val mapper = PersistenceManager.getMapper(def.type) ?: continue
                        @Suppress("UNCHECKED_CAST")
                        (mapper as taboolib.expansion.DataMapper<Any>).insertOrUpdate(instance)
                    }
                    // Manager：保存其管理的数据
                    def.managedDataType != null && instance is PlayerOperator<*> -> {
                        val mapper = PersistenceManager.getMapper(def.managedDataType) ?: continue
                        @Suppress("UNCHECKED_CAST")
                        (mapper as taboolib.expansion.DataMapper<Any>).insertOrUpdate(instance.data)
                    }
                }
            } catch (e: Exception) {
                warning("[IoC] 保存玩家数据失败: ${def.type.name} - ${e.message}")
            }
        }
    }

    /**
     * 从数据库加载数据实例，不存在则创建新实例。
     */
    private fun loadDataInstance(type: Class<*>, uuid: UUID): Any {
        val mapper = PersistenceManager.getMapper(type)
        val existing = mapper?.findById(uuid.toString())
        val instance = existing ?: type.getDeclaredConstructor().newInstance()
        if (instance is PlayerData && instance.uuid == UUID(0, 0)) {
            instance.uuid = uuid
        }
        debug("[IoC] 加载数据 ${type.simpleName}: ${if (existing != null) "从数据库" else "新建实例"}")
        return instance
    }

    /**
     * 设置 PlayerOperator 的 data 字段。
     */
    @Suppress("UNCHECKED_CAST")
    private fun setOperatorData(operator: PlayerOperator<*>, data: Any) {
        val field = PlayerOperator::class.java.getDeclaredField("data")
        field.isAccessible = true
        field.set(operator, data)
    }

    /**
     * 定时保存所有在线玩家的数据。
     */
    @Schedule(period = 6000, async = true)
    fun autoSave() {
        if (!BeanContainer.initialized) return
        for ((_, beans) in playerBeans) {
            // 调用 Manager.onSave()
            for (def in BeanContainer.definitions.values.filter { it.scope == BeanScope.PLAYER }) {
                val instance = beans[def.type.name] ?: continue
                if (instance is PlayerOperator<*>) {
                    try { instance.onSave() } catch (_: Exception) {}
                }
            }
            savePlayerData(beans)
        }
    }

    @SubscribeEvent
    fun onPlayerJoin(e: PlayerJoinEvent) {
        if (!BeanContainer.initialized) return
        loadPlayer(e.player.uniqueId)
    }

    @SubscribeEvent
    fun onPlayerQuit(e: PlayerQuitEvent) {
        if (!BeanContainer.initialized) return
        unloadPlayer(e.player.uniqueId)
    }

    /**
     * 关闭：批量保存所有在线玩家 → 清理。
     */
    fun shutdown() {
        for ((_, beans) in playerBeans) {
            val playerDefs = BeanContainer.definitions.values.filter { it.scope == BeanScope.PLAYER }
            for (def in playerDefs) {
                val instance = beans[def.type.name] ?: continue
                try {
                    if (instance is PlayerOperator<*>) {
                        instance.onSave()
                        instance.onQuit()
                    }
                    def.preDestroy?.invoke(instance)
                } catch (_: Exception) {
                }
            }
            savePlayerData(beans)
        }
        playerBeans.clear()
    }

    /**
     * 容器初始化完成后，为所有已在线的玩家加载数据。
     * 解决插件热加载或 ACTIVE 阶段晚于玩家登录的情况。
     */
    fun loadOnlinePlayers() {
        val online = Bukkit.getOnlinePlayers()
        if (online.isNotEmpty()) {
            debug("[IoC] 为 ${online.size} 个在线玩家补充加载数据")
        }
        for (player in online) {
            if (!playerBeans.containsKey(player.uniqueId)) {
                loadPlayer(player.uniqueId)
            }
        }
    }
}
