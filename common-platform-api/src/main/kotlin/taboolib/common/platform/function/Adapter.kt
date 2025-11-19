package taboolib.common.platform.function

import taboolib.common.platform.PlatformFactory
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.service.PlatformAdapter
import taboolib.common.util.Location
import taboolib.common.util.unsafeLazy
import java.util.*

val adapterService by unsafeLazy { PlatformFactory.getService<PlatformAdapter>() }

/**
 * 获取控制台
 */
fun console(): ProxyCommandSender {
    return adapterService.console()
}

/**
 * 将平台实现转换为跨平台实现
 */
fun adaptCommandSender(any: Any): ProxyCommandSender {
    return adapterService.adaptCommandSender(any)
}

/**
 * 获取所有在线玩家
 */
fun onlinePlayers(): List<ProxyPlayer> {
    return adapterService.onlinePlayers()
}

/**
 * 将平台实现转换为跨平台实现
 */
fun adaptPlayer(any: Any): ProxyPlayer {
    return adapterService.adaptPlayer(any)
}

/**
 * 通过名称获取玩家
 */
fun getProxyPlayer(name: String): ProxyPlayer? {
    return onlinePlayers().firstOrNull { it.name.equals(name, true) }
}

/**
 * 通过 UUID 获取玩家
 */
fun getProxyPlayer(uuid: UUID): ProxyPlayer? {
    return onlinePlayers().firstOrNull { it.uniqueId == uuid }
}

/**
 * 将平台实现转换为跨平台实现
 */
fun adaptLocation(any: Any): Location {
    return adapterService.adaptLocation(any)
}

/**
 * 将跨平台实现转换为平台实现
 */
@Suppress("UNCHECKED_CAST")
fun <T> platformLocation(location: Location): T {
    return adapterService.platformLocation(location) as T
}

/**
 * 获取所有世界
 */
fun allWorlds(): List<String> {
    return adapterService.allWorlds()
}