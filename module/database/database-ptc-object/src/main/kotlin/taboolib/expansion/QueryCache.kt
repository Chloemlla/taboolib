package taboolib.expansion

import java.util.concurrent.ConcurrentHashMap

/**
 * 数据缓存接口
 *
 * 用户可实现此接口来自定义缓存策略（如 Redis、Caffeine 等），
 * 然后通过 `cache(myCache)` 传入 mapper DSL。
 *
 * ```kotlin
 * // 使用自定义缓存
 * val homeTable by mapper<PlayerHome>(dbFile("data.db")) {
 *     cache(MyCaffeineCache())
 * }
 *
 * // 为 Bean Cache 和 Query Cache 分别指定不同实现
 * val homeTable by mapper<PlayerHome>(dbFile("data.db")) {
 *     cache(beanCache = MyRedisCache(), queryCache = MyCaffeineCache())
 * }
 * ```
 *
 * ### 自定义实现示例
 *
 * ```kotlin
 * class MyCaffeineCache : DataCache {
 *     private val caffeine = Caffeine.newBuilder().maximumSize(1000).build<String, Any?>()
 *     override fun get(key: String, loader: () -> Any?) = caffeine.get(key) { loader() }
 *     override fun put(key: String, value: Any?) = caffeine.put(key, value)
 *     override fun invalidate(key: String) = caffeine.invalidate(key)
 *     override fun invalidateByPrefix(prefix: String) {
 *         caffeine.asMap().keys.removeIf { it.startsWith(prefix) }
 *     }
 *     override fun invalidateAll() = caffeine.invalidateAll()
 * }
 * ```
 */
interface DataCache {

    /**
     * 从缓存中获取值，未命中时调用 loader 加载并写入缓存
     *
     * @param key 缓存键（由方法名+参数自动构建）
     * @param loader 缓存未命中时的加载函数
     * @return 缓存值或 loader 返回值
     */
    fun get(key: String, loader: () -> Any?): Any?

    /**
     * 直接写入缓存条目
     */
    fun put(key: String, value: Any?)

    /**
     * 使指定 key 的缓存失效
     */
    fun invalidate(key: String)

    /**
     * 使所有以指定前缀开头的缓存失效
     *
     * 例如 `invalidateByPrefix("findAll:")` 会清除所有 `findAll:*` 缓存
     */
    fun invalidateByPrefix(prefix: String)

    /**
     * 清除所有缓存条目
     */
    fun invalidateAll()
}

/**
 * 通用缓存区域配置
 */
class CacheConfig {
    var maximumSize: Int = 256
    var expireAfterWrite: Long = 0   // 秒，0 = 不过期
    var expireAfterAccess: Long = 0  // 秒，0 = 不过期
}

/**
 * 基于 ConcurrentHashMap 的内置缓存实现
 *
 * - 查询时：以方法名+参数构建 key，命中则返回缓存值
 * - 写入时：按前缀或精确 key 失效受影响的缓存
 * - 支持 TTL 过期和最大容量限制
 */
class DefaultCache(private val config: CacheConfig) : DataCache {

    private data class CacheEntry(val value: Any?, val writeTime: Long, var accessTime: Long)

    private val store = ConcurrentHashMap<String, CacheEntry>()

    override fun get(key: String, loader: () -> Any?): Any? {
        val entry = store[key]
        if (entry != null && !isExpired(entry)) {
            entry.accessTime = System.currentTimeMillis()
            return entry.value
        }
        val value = loader()
        put(key, value)
        return value
    }

    override fun put(key: String, value: Any?) {
        evictIfNeeded()
        val now = System.currentTimeMillis()
        store[key] = CacheEntry(value, now, now)
    }

    override fun invalidate(key: String) {
        store.remove(key)
    }

    override fun invalidateByPrefix(prefix: String) {
        store.keys.removeIf { it.startsWith(prefix) }
    }

    override fun invalidateAll() {
        store.clear()
    }

    private fun isExpired(entry: CacheEntry): Boolean {
        val now = System.currentTimeMillis()
        if (config.expireAfterWrite > 0 && now - entry.writeTime > config.expireAfterWrite * 1000) return true
        if (config.expireAfterAccess > 0 && now - entry.accessTime > config.expireAfterAccess * 1000) return true
        return false
    }

    private fun evictIfNeeded() {
        // 先清除过期条目
        store.entries.removeIf { isExpired(it.value) }
        // 超出容量则移除最旧的
        while (store.size >= config.maximumSize) {
            val oldest = store.entries.minByOrNull { it.value.accessTime } ?: break
            store.remove(oldest.key)
        }
    }
}

/**
 * L2 双层缓存配置
 *
 * - **beanCache**：按实体 ID 存储，细粒度失效（更新/删除只影响特定 ID）
 * - **queryCache**：按查询哈希存储，粗粒度失效（任何写操作清空整个 Query Cache）
 *
 * ```kotlin
 * val homeTable by mapper<PlayerHome>(dbFile("data.db")) {
 *     cache {
 *         beanCache { maximumSize = 10000; expireAfterAccess = 600 }
 *         queryCache { maximumSize = 1000; expireAfterAccess = 600 }
 *     }
 * }
 * ```
 */
class L2CacheConfig {
    val beanCache = CacheConfig().apply { maximumSize = 10000 }
    val queryCache = CacheConfig().apply { maximumSize = 1000 }
    fun beanCache(block: CacheConfig.() -> Unit) { beanCache.apply(block) }
    fun queryCache(block: CacheConfig.() -> Unit) { queryCache.apply(block) }
}

/**
 * L2 双层缓存
 * - **beanCache**：按实体 ID 存储，细粒度失效
 * - **queryCache**：按查询哈希存储，粗粒度失效
 */
class L2Cache(
    val beanCache: DataCache,
    val queryCache: DataCache
) {
    constructor(config: L2CacheConfig) : this(
        DefaultCache(config.beanCache),
        DefaultCache(config.queryCache)
    )

    fun invalidateAll() {
        beanCache.invalidateAll()
        queryCache.invalidateAll()
    }
}
