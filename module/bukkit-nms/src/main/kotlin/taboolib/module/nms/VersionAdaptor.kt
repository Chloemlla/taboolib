package taboolib.module.nms

/**
 * 版本适配分发器
 *
 * 消除级联 try-catch 的性能损耗。首次调用时遍历候选策略（try-catch），
 * 确定可用策略并锁定，后续直接调用缓存的策略。
 *
 * 用法（以 R 为函数类型实现零开销参数传递）：
 * ```kotlin
 * // 定义：R 为函数类型，策略 lambda 返回函数引用
 * val impl = versionAdaptor<(Player, Int, Int) -> Boolean>(
 *     { { p, cx, cz -> NMS20p.isChunkSent(p, cx, cz) } },
 *     { { p, cx, cz -> NMS19p.isChunkSent(p, cx, cz) } },
 *     { { p, cx, cz -> legacyCheck(p, cx, cz) } }
 * )
 *
 * // 使用：首次调用确定策略，后续零开销
 * fun isChunkVisible(player: Player, chunkX: Int, chunkZ: Int): Boolean {
 *     return impl()(player, chunkX, chunkZ)
 * }
 * ```
 *
 * @author sky
 */
class VersionAdaptor<R>(private val strategies: List<() -> R>) {

    @Volatile
    private var resolved: (() -> R)? = null

    /**
     * 执行分发
     *
     * 首次调用时遍历候选策略，找到第一个不抛异常的策略并缓存。
     * 后续调用直接使用缓存的策略，无 try-catch 开销。
     */
    operator fun invoke(): R {
        resolved?.let { return it() }
        synchronized(this) {
            resolved?.let { return it() }
            for (strategy in strategies) {
                try {
                    val result = strategy()
                    resolved = strategy
                    return result
                } catch (_: Throwable) {
                }
            }
        }
        error("No suitable version implementation found")
    }
}

/**
 * 创建版本适配分发器
 */
fun <R> versionAdaptor(vararg strategies: () -> R): VersionAdaptor<R> {
    return VersionAdaptor(strategies.toList())
}
