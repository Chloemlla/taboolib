package taboolib.module.kether

import org.tabooproject.reflex.ClassMethod
import org.tabooproject.reflex.ReflexClass
import taboolib.common.OpenContainer
import taboolib.common.OpenResult
import taboolib.common.util.supplierLazy

/**
 * TabooLib
 * taboolib.module.kether.RemoteScriptProperty
 *
 * @author sky
 * @since 2021/8/12 8:37 下午
 */
@Suppress("UNCHECKED_CAST")
class RemoteScriptProperty(val remote: OpenContainer, val source: Any, id: String) : ScriptProperty<Any>(id) {

    override fun read(instance: Any, key: String): OpenResult {
        return OpenResult.cast(readMethod[source].invoke(source, instance, key))
    }

    override fun write(instance: Any, key: String, value: Any?): OpenResult {
        return OpenResult.cast(writeMethod[source].invoke(source, instance, key, value))
    }

    companion object {

        val readMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("read", remap = false, parameter = arrayOf(Any::class.java, String::class.java))
        }

        val writeMethod = supplierLazy<Any, ClassMethod>(typeIsolation = true) {
            ReflexClass.of(it.javaClass).getMethodByTypes("write", remap = false, parameter = arrayOf(Any::class.java, String::class.java, Any::class.java))
        }
    }
}