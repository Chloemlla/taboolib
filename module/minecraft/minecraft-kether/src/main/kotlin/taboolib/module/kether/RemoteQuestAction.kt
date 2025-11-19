package taboolib.module.kether

import org.tabooproject.reflex.ClassMethod
import org.tabooproject.reflex.ReflexClass
import org.tabooproject.reflex.Unknown
import taboolib.common.OpenContainer
import taboolib.common.platform.function.pluginId
import taboolib.common.util.supplierLazy
import taboolib.library.kether.QuestAction
import taboolib.library.kether.QuestContext
import java.util.concurrent.CompletableFuture

/**
 * TabooLib
 * taboolib.module.kether.RemoteQuestAction
 *
 * @author sky
 * @since 2021/8/10 3:51 下午
 */
@Suppress("UNCHECKED_CAST")
class RemoteQuestAction<T>(val remote: OpenContainer, val source: Any) : QuestAction<T>() {

    override fun process(frame: QuestContext.Frame): CompletableFuture<T> {
        val remoteFrame = remote.call(StandardChannel.REMOTE_CREATE_FLAME, arrayOf(pluginId, frame))
        return processMethod[source].invoke(source, remoteFrame.value) as CompletableFuture<T>
    }

    companion object {

        val processMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("process", remap = false, parameter = arrayOf(Unknown::class.java))
        }
    }
}