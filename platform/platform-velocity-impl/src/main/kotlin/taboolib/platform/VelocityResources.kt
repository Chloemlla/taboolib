package taboolib.platform

import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import taboolib.common.Inject
import taboolib.common.function.DebounceFunction
import taboolib.common.function.ThrottleFunction
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.ProxyPlayer
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer

@Inject
@PlatformSide(Platform.VELOCITY)
internal object VelocityResources {

    init {
        ThrottleFunction.registerGlobalType(Player::class.java)
        ThrottleFunction.registerGlobalType(ProxyPlayer::class.java)
        DebounceFunction.registerGlobalType(Player::class.java)
        DebounceFunction.registerGlobalType(ProxyPlayer::class.java)
    }

    @SubscribeEvent
    fun onQuit(e: DisconnectEvent) {
        ThrottleFunction.allThrottleFunctions.forEach {
            when (it.keyType) {
                Player::class.java -> it.removeKey(e.player)
                ProxyPlayer::class.java -> it.removeKey(adaptPlayer(e.player))
            }
        }
        DebounceFunction.allDebounceFunctions.forEach {
            when (it.keyType) {
                Player::class.java -> it.removeKey(e.player)
                ProxyPlayer::class.java -> it.removeKey(adaptPlayer(e.player))
            }
        }
    }
}
