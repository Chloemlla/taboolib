package taboolib.expansion.orm

import taboolib.common.platform.function.warning
import taboolib.common.util.t
import taboolib.common5.*
import taboolib.expansion.BundleMapImpl
import taboolib.expansion.CustomTypeFactory
import taboolib.expansion.IndexedEnum
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 实体映射器：负责 ResultSet 读取和实例创建（Single Responsibility）
 *
 * 将原本位于 AnalyzedClass 中的 read/createInstance 行为提取到此处，
 * AnalyzedClass 保持为纯元数据容器。
 */
@Suppress("UNCHECKED_CAST")
class EntityMapper<T> private constructor(private val analyzedClass: AnalyzedClass) {

    /**
     * 读取数据（不含 @Ignore、@LinkTable 成员和容器成员）
     */
    fun read(result: ResultSet): Map<String, Any?> {
        val map = hashMapOf<String, Any?>()
        analyzedClass.members.forEach { member ->
            if (member.isIgnored || member.isLinkTable || member.isCollection) return@forEach
            val obj: Any? = result.getObject(member.name)
            if (obj == null) {
                map[member.name] = null
            } else {
                map[member.name] = readMemberValue(member, obj)
            }
        }
        return map
    }

    /**
     * 从带 JOIN 的 ResultSet 中读取数据（含 @LinkTable 成员，支持无限嵌套）
     */
    fun readWithLinks(result: ResultSet, prefix: String = ""): Map<String, Any?> {
        val map = hashMapOf<String, Any?>()
        analyzedClass.columnMembers.forEach { member ->
            val obj: Any? = result.getObject("${prefix}${member.name}")
            if (obj == null) {
                map[member.name] = null
            } else {
                map[member.name] = readMemberValue(member, obj)
            }
        }
        analyzedClass.linkMembers.forEach { member ->
            val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
            val childPrefix = "${prefix}__link__${member.linkTableColumn}__"
            val idColumn = "${childPrefix}${linkedClass.primaryMemberName}"
            val linkedIdValue: Any? = try {
                result.getObject(idColumn)
            } catch (ex: Exception) {
                warning("@LinkTable read failed: column '$idColumn' not found in ResultSet for ${analyzedClass.clazz.simpleName}.${member.propertyName} -> ${member.linkTableClass?.simpleName}")
                warning("  Cause: ${ex.message}")
                null
            }
            if (linkedIdValue == null) {
                map[member.name] = null
            } else {
                val linkedMapper = of<Any>(linkedClass.clazz as Class<Any>)
                val linkedMap = linkedMapper.readWithLinks(result, childPrefix)
                map[member.name] = linkedMapper.createInstance(linkedMap)
            }
        }
        return map
    }

    /**
     * 读取单个成员的值
     */
    private fun readMemberValue(member: AnalyzedClassMember, obj: Any): Any? {
        return when {
            member.isBoolean -> obj.cbool
            member.isByte -> obj.cbyte
            member.isShort -> obj.cshort
            member.isInt -> obj.cint
            member.isLong -> obj.clong
            member.isFloat -> obj.cfloat
            member.isDouble -> obj.cdouble
            member.isChar -> obj.cint.toChar()
            member.isString -> obj.toString()
            member.isUUID -> UUID.fromString(obj.toString())
            member.isIndexedEnum -> {
                val idx = obj.clong
                member.returnType.enumConstants.firstOrNull { (it as IndexedEnum).index == idx }
                    ?: error(
                        """
                        在 ${analyzedClass.clazz} 类中，成员 ${member.name} 的 IndexedEnum 索引值 "$obj" 不存在。
                        IndexedEnum index "$obj" not found for ${member.name} in ${analyzedClass.clazz}
                        """.t()
                    )
            }
            member.isEnum -> member.returnType.enumConstants.firstOrNull { (it as Enum<*>).name == obj.toString() }
                ?: error(
                    """
                    在 ${analyzedClass.clazz} 类中，成员 ${member.name} 的枚举值 "$obj" 不存在。
                    Enum value "$obj" not found for ${member.name} in ${analyzedClass.clazz}
                    """.t()
                )
            member.isByteArray -> obj.cByteArray
            member.isFlattenedCollection -> {
                val ct = CustomTypeFactory.getCustomTypeForCollection(member.returnType, member.collectionElementType!!)!!
                ct.deserialize(obj)
            }
            else -> {
                val customType = CustomTypeFactory.getCustomTypeByClass(member.returnType) ?: error(
                    """
                    在 ${analyzedClass.clazz} 类中，成员 ${member.name} 的类型 ${member.returnType} 不受支持。
                    Unsupported type ${member.returnType} for ${member.name} in ${analyzedClass.clazz}
                    """.t()
                )
                customType.deserialize(obj)
            }
        }
    }

    /**
     * 创建实例
     */
    fun createInstance(map: Map<String, Any?>): T {
        val clazz = analyzedClass.clazz
        val result = if (analyzedClass.wrapperFunction != null) {
            analyzedClass.wrapperFunction!!.invoke(analyzedClass.wrapperObjectInstance, BundleMapImpl(map)) ?: error(
                """
                无法创建 $clazz 实例。
                Failed to create instance for $clazz
                """.t()
            )
        } else if (analyzedClass.fieldScanMode) {
            val instance = analyzedClass.noArgConstructor!!.newInstance()
            analyzedClass.members.forEach { member ->
                if (member.isIgnored) return@forEach
                val field = analyzedClass.memberProperties[member.propertyName] ?: return@forEach
                field.isAccessible = true
                val value = map[member.name]
                if (value != null || !field.type.isPrimitive) {
                    field.set(instance, value)
                }
            }
            instance
        } else if (analyzedClass.hasIgnoredMembers && analyzedClass.kotlinDefaultConstructor != null) {
            var defaultMask = 0
            val args = analyzedClass.members.mapIndexed { index, member ->
                if (member.isIgnored) {
                    defaultMask = defaultMask or (1 shl index)
                    defaultValueForType(member)
                } else {
                    map[member.name]
                }
            }
            try {
                analyzedClass.kotlinDefaultConstructor!!.newInstance(*args.toTypedArray(), defaultMask, null)
            } catch (ex: Throwable) {
                val fallbackArgs = analyzedClass.members.map { member ->
                    if (member.isIgnored) defaultValueForType(member) else map[member.name]
                }
                try {
                    analyzedClass.primaryConstructor!!.newInstance(*fallbackArgs.toTypedArray())
                } catch (ex2: Throwable) {
                    error(
                        """
                        无法创建 $clazz 实例。($fallbackArgs, map=$map)
                        Failed to create instance for $clazz. ($fallbackArgs, map=$map)
                        """.t()
                    )
                }
            }
        } else if (analyzedClass.hasIgnoredMembers) {
            val args = analyzedClass.members.map { member ->
                if (member.isIgnored) defaultValueForType(member) else map[member.name]
            }
            try {
                analyzedClass.primaryConstructor!!.newInstance(*args.toTypedArray())
            } catch (ex: Throwable) {
                error(
                    """
                    无法创建 $clazz 实例。($args, map=$map)
                    Failed to create instance for $clazz. ($args, map=$map)
                    """.t()
                )
            }
        } else {
            val args = analyzedClass.members.map { map[it.name] }
            try {
                analyzedClass.primaryConstructor!!.newInstance(*args.toTypedArray())
            } catch (ex: Throwable) {
                error(
                    """
                    无法创建 $clazz 实例。($args, map=$map)
                    Failed to create instance for $clazz. ($args, map=$map)
                    """.t()
                )
            }
        }
        return result as T
    }

    /**
     * 为 @Ignore 字段生成类型安全的默认值
     */
    private fun defaultValueForType(member: AnalyzedClassMember): Any? {
        val type = member.returnType
        return when {
            List::class.java.isAssignableFrom(type) -> emptyList<Any>()
            Set::class.java.isAssignableFrom(type) -> emptySet<Any>()
            Map::class.java.isAssignableFrom(type) -> emptyMap<Any, Any>()
            type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
            type == Byte::class.javaPrimitiveType || type == Byte::class.java -> 0.toByte()
            type == Short::class.javaPrimitiveType || type == Short::class.java -> 0.toShort()
            type == Int::class.javaPrimitiveType || type == Int::class.java -> 0
            type == Long::class.javaPrimitiveType || type == Long::class.java -> 0L
            type == Float::class.javaPrimitiveType || type == Float::class.java -> 0.0f
            type == Double::class.javaPrimitiveType || type == Double::class.java -> 0.0
            type == Char::class.javaPrimitiveType || type == Char::class.java -> '\u0000'
            type == String::class.java -> ""
            else -> null
        }
    }

    companion object {
        private val cache = ConcurrentHashMap<Class<*>, EntityMapper<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T> of(clazz: Class<T>): EntityMapper<T> {
            return cache.getOrPut(clazz) { EntityMapper<T>(AnalyzedClass.of(clazz)) } as EntityMapper<T>
        }
    }
}
