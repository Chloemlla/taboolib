package taboolib.module.kether

import org.tabooproject.reflex.ClassMethod
import org.tabooproject.reflex.Reflex.Companion.getProperty
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import org.tabooproject.reflex.ReflexClass
import org.tabooproject.reflex.Unknown
import taboolib.common.OpenContainer
import taboolib.common.platform.function.pluginId
import taboolib.common.util.supplierLazy
import taboolib.library.kether.ParsedAction
import taboolib.library.kether.Quest
import java.util.*

/**
 * TabooLib
 * taboolib.module.kether.RemoteQuest
 *
 * @author sky
 * @since 2021/8/11 11:33 上午
 */
@Suppress("UNCHECKED_CAST")
class RemoteQuest(val remote: OpenContainer, val source: Any) : Quest {

    override fun getId(): String {
        return source.invokeMethod("getId", remap = false)!!
    }

    override fun getBlock(label: String): Optional<Quest.Block> {
        val getBlock = getBlockMethod[source].invoke(source, label) as Optional<Any>
        return if (getBlock.isPresent) {
            Optional.of(RemoteBlock(remote, getBlock.get()))
        } else {
            Optional.empty()
        }
    }

    override fun getBlocks(): Map<String, Quest.Block> {
        return source.invokeMethod<Map<String, Any>>("getBlocks", remap = false)!!.map { it.key to RemoteBlock(remote, it.value) }.toMap()
    }

    override fun blockOf(action: ParsedAction<*>): Optional<Quest.Block> {
        // 远程创建 ParsedAction
        val remoteAction = remote.call(StandardChannel.REMOTE_CREATE_PARSED_ACTION, arrayOf(pluginId, action.action, action.properties))
        // 远程执行方法
        val blockOf = blockOfMethod[source].invoke(source, remoteAction.value) as Optional<Any>
        return if (blockOf.isPresent) {
            Optional.of(RemoteBlock(remote, blockOf.get()))
        } else {
            Optional.empty()
        }
    }

    class RemoteBlock(val remote: OpenContainer, val source: Any) : Quest.Block {

        override fun getLabel(): String {
            return source.invokeMethod("getLabel", remap = false)!!
        }

        override fun getActions(): MutableList<ParsedAction<*>> {
            return source.invokeMethod<List<Any>>("getActions", remap = false)!!
                .map { ParsedAction(RemoteQuestAction<Any>(remote, it.getProperty<Any>("action", remap = false)!!), it.getProperty<Map<String, Any>>("properties", remap = false)!!) }
                .toMutableList()
        }

        override fun indexOf(action: ParsedAction<*>): Int {
            // 远程创建 ParsedAction
            val remoteAction = remote.call(StandardChannel.REMOTE_CREATE_PARSED_ACTION, arrayOf(pluginId, action.action, action.properties))
            // 远程执行方法
            return indexOfMethod[source].invoke(source, remoteAction.value) as Int
        }

        override fun get(i: Int): Optional<ParsedAction<*>> {
            val action = getMethod[source].invoke(source, i) as Optional<Any>
            return if (action.isPresent) {
                val remoteAction = RemoteQuestAction<Any>(remote, action.get().getProperty<Any>("action", remap = false)!!)
                Optional.of(ParsedAction(remoteAction, action.get().getProperty<Map<String, Any>>("properties", remap = false)!!))
            } else {
                Optional.empty()
            }
        }
    }

    companion object {

        val getBlockMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("getBlock", remap = false, parameter = arrayOf(String::class.java))
        }

        val blockOfMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("blockOf", remap = false, parameter = arrayOf(Unknown::class.java))
        }

        val indexOfMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("indexOf", remap = false, parameter = arrayOf(Unknown::class.java))
        }

        val getMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("get", remap = false, parameter = arrayOf(Int::class.java))
        }
    }
}