package taboolib.platform

import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.PlayerDisconnectEvent
import taboolib.common.Inject
import taboolib.common.function.DebounceFunction
import taboolib.common.function.ThrottleFunction
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer

@Inject
@PlatformSide(Platform.BUNGEE)
internal object BungeeResources {

    init {
        ThrottleFunction.registerGlobalType(ProxiedPlayer::class.java)
        ThrottleFunction.registerGlobalType(ProxyPlayer::class.java)
        DebounceFunction.registerGlobalType(ProxiedPlayer::class.java)
        DebounceFunction.registerGlobalType(ProxyPlayer::class.java)
    }

    @SubscribeEvent
    fun onQuit(e: PlayerDisconnectEvent) {
        ThrottleFunction.allThrottleFunctions.forEach {
            when (it.keyType) {
                ProxiedPlayer::class.java -> it.removeKey(e.player)
                ProxyPlayer::class.java -> it.removeKey(adaptPlayer(e.player))
            }
        }
        DebounceFunction.allDebounceFunctions.forEach {
            when (it.keyType) {
                ProxiedPlayer::class.java -> it.removeKey(e.player)
                ProxyPlayer::class.java -> it.removeKey(adaptPlayer(e.player))
            }
        }
    }
}
