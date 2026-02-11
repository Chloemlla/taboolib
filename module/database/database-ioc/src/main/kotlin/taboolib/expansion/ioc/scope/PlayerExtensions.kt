package taboolib.expansion.ioc.scope

import org.bukkit.entity.Player

/**
 * 获取该玩家的 PLAYER 作用域 Bean 实例。
 */
inline fun <reified T : Any> Player.ioc(): T? {
    return PlayerScope.getBean(uniqueId, T::class.java)
}

/**
 * 获取该玩家的 PLAYER 作用域 Bean 并在 lambda 中操作。
 * Bean 不存在时不执行 lambda。
 */
inline fun <reified T : Any> Player.ioc(block: T.() -> Unit): T? {
    return ioc<T>()?.also { it.block() }
}

/**
 * 获取该玩家的 PLAYER 作用域 Bean 和数据，在 lambda 中同时操作。
 */
inline fun <reified S : Any, reified D : PlayerData> Player.ioc(block: (service: S, data: D) -> Unit) {
    val service = PlayerScope.getBean(uniqueId, S::class.java) ?: return
    val data = PlayerScope.getBean(uniqueId, D::class.java) ?: return
    block(service, data)
}
