package taboolib.platform

import com.hypixel.hytale.event.EventRegistration
import com.hypixel.hytale.event.IBaseEvent
import taboolib.common.Inject
import taboolib.common.platform.Awake
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.PostOrder
import taboolib.common.platform.event.ProxyListener
import taboolib.common.platform.service.PlatformListener
import taboolib.common.util.unsafeLazy
import java.util.function.Consumer

/**
 * TabooLib
 * taboolib.platform.HytaleListener
 *
 * @author sky
 * @since 2024/1/1
 */
@Awake
@Inject
@PlatformSide(Platform.HYTALE)
class HytaleListener : PlatformListener {

    val plugin by unsafeLazy { HytalePlugin.getInstance() }

    override fun <T> registerListener(event: Class<T>, priority: EventPriority, ignoreCancelled: Boolean, func: (T) -> Unit): ProxyListener {
        error("Unsupported")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> registerListener(event: Class<T>, postOrder: PostOrder, func: (T) -> Unit): ProxyListener {
        val hytalePriority = com.hypixel.hytale.event.EventPriority.values()[postOrder.ordinal]
        val eventClass = event as Class<IBaseEvent<Void>>
        val consumer = Consumer<IBaseEvent<Void>> { e -> func(e as T) }
        val registration = plugin.eventRegistry.register(hytalePriority, eventClass, consumer) ?: error("Failed to register event listener for ${event.name}")
        return HytaleProxyListener(registration)
    }

    override fun unregisterListener(proxyListener: ProxyListener) {
        if (proxyListener is HytaleProxyListener) {
            proxyListener.registration.unregister()
        }
    }

    class HytaleProxyListener(val registration: EventRegistration<*, *>) : ProxyListener
}
