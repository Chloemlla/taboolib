package taboolib.module.kether

import org.tabooproject.reflex.ClassMethod
import org.tabooproject.reflex.Reflex.Companion.getProperty
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import org.tabooproject.reflex.ReflexClass
import org.tabooproject.reflex.Unknown
import taboolib.common.OpenContainer
import taboolib.common.platform.function.pluginId
import taboolib.common.util.WrappedContext
import taboolib.common.util.orNull
import taboolib.common.util.supplierLazy
import taboolib.library.kether.*
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * TabooLib
 * taboolib.module.kether.RemoteQuestContext
 *
 * @author sky
 * @since 2021/8/11 12:04 上午
 */
@Suppress("UNCHECKED_CAST")
class RemoteQuestContext(val remote: OpenContainer, val source: Any) : ScriptContext(ScriptService, RemoteQuest(remote, source.invokeMethod("getQuest", remap = false)!!)) {

    override fun getService(): QuestService<ScriptContext> {
        error("remote context")
    }

    override fun setExitStatus(exitStatus: ExitStatus) {
        val status = remote.call(StandardChannel.REMOTE_CREATE_EXIT_STATUS, arrayOf(exitStatus.isRunning, exitStatus.isWaiting, exitStatus.startTime))
        setExitStatusMethod[source].invoke(source, status.value)
    }

    override fun getExitStatus(): Optional<ExitStatus> {
        val status = source.invokeMethod<Optional<Any>>("getExitStatus", remap = false)!!.orNull() ?: return Optional.empty()
        return Optional.of(ExitStatus(status.getProperty("running")!!, status.getProperty("waiting")!!, status.getProperty("startTime")!!))
    }

    override fun runActions(): CompletableFuture<Any> {
        return source.invokeMethod("runActions", remap = false)!!
    }

    override fun getExecutor(): QuestExecutor {
        return source.invokeMethod("getExecutor", remap = false)!!
    }

    override fun terminate() {
        source.invokeMethod<Void>("terminate", remap = false)
    }

    override fun rootFrame(): QuestContext.Frame {
        return RemoteFrame(remote, source.invokeMethod("rootFrame", remap = false)!!)
    }

    class RemoteFrame(val remote: OpenContainer, val source: Any) : QuestContext.Frame {

        val remoteQuestContext by lazy { RemoteQuestContext(remote, source.invokeMethod("context", remap = false)!!) }

        override fun close() {
            source.invokeMethod<Void>("close", remap = false)
        }

        override fun name(): String {
            return source.invokeMethod("name", remap = false)!!
        }

        override fun context(): QuestContext {
            return remoteQuestContext
        }

        override fun currentAction(): Optional<ParsedAction<*>> {
            val currentAction = source.invokeMethod<Optional<Any>>("currentAction", remap = false)!!
            return if (currentAction.isPresent) {
                val action = currentAction.get().getProperty<Any>("action")!!
                val properties = currentAction.get().getProperty<Map<String, Any>>("properties")!!
                Optional.of(ParsedAction(RemoteQuestAction<Any>(remote, action), properties))
            } else {
                Optional.empty()
            }
        }

        override fun children(): MutableList<QuestContext.Frame> {
            return source.invokeMethod<List<Any>>("children", remap = false)!!.map { RemoteFrame(remote, it) }.toMutableList()
        }

        override fun parent(): Optional<QuestContext.Frame> {
            val parent = source.invokeMethod<Optional<Any>>("parent", remap = false)!!
            return if (parent.isPresent) {
                Optional.of(RemoteFrame(remote, parent.get()))
            } else {
                Optional.empty()
            }
        }

        override fun setNext(action: ParsedAction<*>) {
            val remoteAction = remote.call(StandardChannel.REMOTE_CREATE_PARSED_ACTION, arrayOf(pluginId, action.action, action.properties)).value!!
            setNextActionMethod[WrappedContext(source, remoteAction)].invoke(source, remoteAction)
        }

        override fun setNext(block: Quest.Block) {
            context().quest.getBlock(block.label).ifPresent { setNextBlockMethod[WrappedContext(source, it)].invoke(source, it) }
        }

        override fun newFrame(name: String): QuestContext.Frame {
            return RemoteFrame(remote, newFrameStringMethod[WrappedContext(source, name)].invoke(source, name)!!)
        }

        override fun newFrame(action: ParsedAction<*>): QuestContext.Frame {
            val remoteAction = remote.call(StandardChannel.REMOTE_CREATE_PARSED_ACTION, arrayOf(pluginId, action.action, action.properties)).value!!
            return RemoteFrame(remote, newFrameActionMethod[WrappedContext(source, remoteAction)].invoke(source, remoteAction)!!)
        }

        override fun variables(): QuestContext.VarTable {
            return RemoteVarTable(remote, source.invokeMethod<Any>("variables", remap = false)!!)
        }

        override fun <T : AutoCloseable?> addClosable(closeable: T): T {
            return addClosableMethod[source].invoke(source, closeable) as T
        }

        override fun <T : Any?> run(): CompletableFuture<T> {
            return source.invokeMethod("run", remap = false)!!
        }

        override fun isDone(): Boolean {
            return source.invokeMethod("isDone", remap = false)!!
        }
    }

    class RemoteVarTable(val remote: OpenContainer, val source: Any) : QuestContext.VarTable {

        override fun <T> get(name: String): Optional<T>? {
            return getMethod[source].invoke(source, name) as? Optional<T>
        }

        override fun <T> getFuture(name: String): Optional<QuestFuture<T>>? {
            return getFutureMethod[source].invoke(source, name) as? Optional<QuestFuture<T>>
        }

        override fun set(name: String, value: Any?) {
            setValueMethod[source].invoke(source, name, value)
        }

        override fun <T> set(name: String, owner: ParsedAction<T>, future: CompletableFuture<T>) {
            val remoteAction = remote.call(StandardChannel.REMOTE_CREATE_PARSED_ACTION, arrayOf(pluginId, owner.action, owner.properties)).value
            setFutureMethod[source].invoke(source, name, remoteAction, future)
        }

        override fun remove(name: String) {
            removeMethod[source].invoke(source, name)
        }

        override fun clear() {
            source.invokeMethod<Void>("clear", remap = false)
        }

        override fun keys(): MutableSet<String> {
            return source.invokeMethod("keys", remap = false)!!
        }

        override fun values(): MutableCollection<MutableMap.MutableEntry<String, Any>> {
            return source.invokeMethod("values", remap = false)!!
        }

        override fun initialize(frame: QuestContext.Frame) {
            initializeMethod[source].invoke(source, remote.call(StandardChannel.REMOTE_CREATE_FLAME, arrayOf(pluginId, frame)).value)
        }

        override fun close() {
            source.invokeMethod<Void>("close", remap = false)
        }

        override fun parent(): QuestContext.VarTable {
            return RemoteVarTable(remote, source.invokeMethod("parent", remap = false)!!)
        }
    }

    companion object {

        // RemoteQuestContext methods
        val setExitStatusMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("setExitStatus", remap = false, parameter = arrayOf(Unknown::class.java))
        }

        // RemoteFrame methods
        val setNextActionMethod = supplierLazy<WrappedContext<Any, Any>, ClassMethod>(typeIsolation = true) { (source, action) ->
            ReflexClass.of(source.javaClass).getMethod("setNext", remap = false, parameter = arrayOf(action))
        }

        val setNextBlockMethod = supplierLazy<WrappedContext<Any, Any>, ClassMethod>(typeIsolation = true) { (source, block) ->
            ReflexClass.of(source.javaClass).getMethod("setNext", remap = false, parameter = arrayOf(block))
        }

        val newFrameStringMethod = supplierLazy<WrappedContext<Any, String>, ClassMethod>(typeIsolation = true) { (source, string) ->
            ReflexClass.of(source.javaClass).getMethod("newFrame", remap = false, parameter = arrayOf(string))
        }

        val newFrameActionMethod = supplierLazy<WrappedContext<Any, Any>, ClassMethod>(typeIsolation = true) { (source, action) ->
            ReflexClass.of(source.javaClass).getMethod("newFrame", remap = false, parameter = arrayOf(action))
        }

        val addClosableMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("addClosable", remap = false, parameter = arrayOf(AutoCloseable::class.java))
        }

        // RemoteVarTable methods
        val getMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("get", remap = false, parameter = arrayOf(String::class.java))
        }

        val getFutureMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("getFuture", remap = false, parameter = arrayOf(String::class.java))
        }

        val setValueMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("set", remap = false, parameter = arrayOf(String::class.java, Any::class.java))
        }

        val setFutureMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("set", remap = false, parameter = arrayOf(String::class.java, Any::class.java, CompletableFuture::class.java))
        }

        val removeMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("remove", remap = false, parameter = arrayOf(String::class.java))
        }

        val initializeMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("initialize", remap = false, parameter = arrayOf(Unknown::class.java))
        }
    }
}