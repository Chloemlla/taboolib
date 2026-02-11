package taboolib.expansion

import org.tabooproject.reflex.Reflex.Companion.getProperty
import taboolib.common.platform.function.warning
import taboolib.common.util.t
import taboolib.common5.*
import java.lang.reflect.Parameter
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * TabooLib
 * taboolib.expansion.AnalyzedClass
 *
 * @author 坏黑
 * @since 2023/3/29 11:28
 */
@Suppress("UNCHECKED_CAST")
class AnalyzedClass private constructor(val clazz: Class<*>) {

    /** 主构造器（可能为 null，表示使用字段扫描模式） */
    private val primaryConstructor = clazz.declaredConstructors.firstOrNull { it.parameters.isNotEmpty() }

    /** 是否使用字段扫描模式（无带参构造器） */
    private val fieldScanMode = primaryConstructor == null

    /** 无参构造器（字段扫描模式使用） */
    private val noArgConstructor = if (fieldScanMode) {
        (clazz.declaredConstructors.firstOrNull { it.parameters.isEmpty() }
            ?: error(
                """
                未找到 $clazz 的构造器。
                No constructor found for $clazz
                """.t()
            )).also { it.isAccessible = true }
    } else null

    /** 成员列表（包含继承字段） */
    private val memberProperties = collectAllFields(clazz)

    private val mps = memberProperties.entries.toMutableList()

    /** 成员列表 */
    val members = if (fieldScanMode) {
        // 字段扫描模式：从所有非 static、非 synthetic 字段构建
        memberProperties.values.map { field ->
            val isFinal = field.modifiers and java.lang.reflect.Modifier.FINAL != 0
            AnalyzedClassMember.fromField(field, field.name, isFinal)
        }
    } else {
        primaryConstructor!!.parameters.map {
            val entry = mps.firstOrNull { e -> e.key == it.name }
                ?: mps.firstOrNull { e -> e.value.type == it.type }
                ?: error(
                    """
                    在 $clazz 类中，未找到成员 ${it.name}。
                    No member found for $it in $clazz
                    """.t()
                )
            mps.remove(entry)
            val final = entry.value.modifiers and 16 != 0
            AnalyzedClassMember.fromParameter(validation(it), entry.value.name, final)
        }
    }

    /** 主成员 */
    val primaryMember = members.firstOrNull { it.isPrimary }

    /** 主成员名称 */
    val primaryMemberName = primaryMember?.name

    /** 实际映射到列的成员（排除 @LinkTable 成员和容器成员） */
    val columnMembers = members.filter { !it.isLinkTable && !it.isCollection }

    /** @LinkTable 成员列表 */
    val linkMembers = members.filter { it.isLinkTable }

    /** 是否存在 @LinkTable 成员 */
    val hasLinkMembers = linkMembers.isNotEmpty()

    /** 容器类型成员列表（List/Set/Map） */
    val collectionMembers = members.filter { it.isCollection }

    /** 是否存在容器类型成员 */
    val hasCollectionMembers = collectionMembers.isNotEmpty()

    /** 反序列化所在伴生类实例 */
    val wrapperObjectInstance = runCatching { clazz.getProperty<Any>("Companion", isStatic = true) }.getOrNull()

    /** 反序列化方法 */
    val wrapperFunction = wrapperObjectInstance?.javaClass?.declaredMethods?.firstOrNull {
        it.parameters.size == 1 && BundleMap::class.java.isAssignableFrom(it.parameters[0].type)
    }

    init {
        val customs = members.filter { it.isCustomObject }
        if (customs.isNotEmpty()) {
            customs.forEach {
                if (CustomTypeFactory.getCustomTypeByClass(it.returnType) == null) {
                    error(
                        """
                            在 ${clazz.simpleName} 类中，成员 ${it.name} 的类型 ${it.returnType} 不受支持。
                            Unsupported type ${it.returnType} for ${it.name} in $clazz
                        """.t()
                    )
                }
            }
        }
        // 验证 @LinkTable 成员的关联类必须有 @Id 字段
        linkMembers.forEach { member ->
            val linkClass = member.linkTableClass!!
            val linkedClass = AnalyzedClass.of(linkClass)
            if (linkedClass.primaryMember == null) {
                error(
                    """
                        在 ${clazz.simpleName} 类中，@LinkTable 成员 ${member.propertyName} 的关联类 ${linkClass.simpleName} 没有 @Id 字段。
                        Linked class ${linkClass.simpleName} for @LinkTable member ${member.propertyName} in ${clazz.simpleName} has no @Id field.
                    """.t()
                )
            }
        }
        // 验证容器类型成员
        collectionMembers.forEach { member ->
            require(member.parameterizedType != null) {
                """
                    在 ${clazz.simpleName} 类中，容器类型成员 ${member.propertyName} 缺少泛型信息。
                    Collection member ${member.propertyName} in ${clazz.simpleName} is missing generic type information.
                """.t()
            }
        }
        if (members.count { it.isPrimary } > 1) {
            error(
                """
                    在 ${clazz.simpleName} 类中，主成员只能有一个，但找到了 ${members.count { it.isPrimary }} 个。
                    The primary member only supports one, but found ${members.count { it.isPrimary }}
                """.t()
            )
        }
        // 获取访问权限
        memberProperties.forEach { it.value.isAccessible = true }
    }

    /** 获取主成员值 */
    fun getPrimaryMemberValue(data: Any): Any? {
        val property = memberProperties[primaryMember?.propertyName.toString()] ?: error(
            """
                主成员 "$primaryMemberName" 在 $clazz 中未找到。
                Primary member "$primaryMemberName" not found in $clazz
            """.t()
        )
        return property.get(data)
    }

    /** 获取成员值 */
    fun getValue(data: Any, member: AnalyzedClassMember): Any? {
        val property = memberProperties[member.propertyName] ?: error(
            """
                成员 "${member.name}" 在 $clazz 中未找到。
                Member "${member.name}" not found in $clazz
            """.t()
        )
        return property.get(data)
    }

    /** 读取数据（不含 @LinkTable 成员和容器成员） */
    fun read(result: ResultSet): Map<String, Any?> {
        val map = hashMapOf<String, Any?>()
        members.forEach { member ->
            // 跳过 @LinkTable 成员和容器成员，它们没有对应的列
            if (member.isLinkTable || member.isCollection) return@forEach
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
     *
     * @param result JOIN 查询的 ResultSet
     * @param prefix 列别名前缀链，每深一层追加 `__link__{fk}__`（默认空字符串，向后兼容）
     */
    fun readWithLinks(result: ResultSet, prefix: String = ""): Map<String, Any?> {
        val map = hashMapOf<String, Any?>()
        // 读取本类的非 @LinkTable 列
        columnMembers.forEach { member ->
            val obj: Any? = result.getObject("${prefix}${member.name}")
            if (obj == null) {
                map[member.name] = null
            } else {
                map[member.name] = readMemberValue(member, obj)
            }
        }
        // 递归读取 @LinkTable 关联对象
        linkMembers.forEach { member ->
            val linkedClass = AnalyzedClass.of(member.linkTableClass!!)
            val childPrefix = "${prefix}__link__${member.linkTableColumn}__"
            val idColumn = "${childPrefix}${linkedClass.primaryMemberName}"
            val linkedIdValue: Any? = try {
                result.getObject(idColumn)
            } catch (ex: Exception) {
                warning("@LinkTable read failed: column '$idColumn' not found in ResultSet for ${clazz.simpleName}.${member.propertyName} -> ${member.linkTableClass?.simpleName}")
                warning("  Cause: ${ex.message}")
                null
            }
            if (linkedIdValue == null) {
                map[member.name] = null
            } else {
                // 递归：linkedClass 可能也有 linkMembers
                val linkedMap = linkedClass.readWithLinks(result, childPrefix)
                map[member.name] = linkedClass.createInstance(linkedMap)
            }
        }
        return map
    }

    /** 读取单个成员的值 */
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
                        在 $clazz 类中，成员 ${member.name} 的 IndexedEnum 索引值 "$obj" 不存在。
                        IndexedEnum index "$obj" not found for ${member.name} in $clazz
                        """.t()
                    )
            }
            member.isEnum -> member.returnType.enumConstants.firstOrNull { (it as Enum<*>).name == obj.toString() }
                ?: error(
                    """
                    在 $clazz 类中，成员 ${member.name} 的枚举值 "$obj" 不存在。
                    Enum value "$obj" not found for ${member.name} in $clazz
                    """.t()
                )
            member.isByteArray -> obj.cByteArray
            else -> {
                val customType = CustomTypeFactory.getCustomTypeByClass(member.returnType) ?: error(
                    """
                    在 $clazz 类中，成员 ${member.name} 的类型 ${member.returnType} 不受支持。
                    Unsupported type ${member.returnType} for ${member.name} in $clazz
                    """.t()
                )
                customType.deserialize(obj)
            }
        }
    }

    /** 创建实例 */
    @Suppress("UNCHECKED_CAST")
    fun <T> createInstance(map: Map<String, Any?>): T {
        val result = if (wrapperFunction != null) {
            wrapperFunction.invoke(wrapperObjectInstance, BundleMapImpl(map)) ?: error(
                """
                无法创建 $clazz 实例。
                Failed to create instance for $clazz
                """.t()
            )
        } else if (fieldScanMode) {
            // 字段扫描模式：无参构造 + 反射设值
            val instance = noArgConstructor!!.newInstance()
            members.forEach { member ->
                val field = memberProperties[member.propertyName] ?: return@forEach
                field.isAccessible = true
                val value = map[member.name]
                if (value != null || !field.type.isPrimitive) {
                    field.set(instance, value)
                }
            }
            instance
        } else {
            val args = members.map { map[it.name] }
            try {
                primaryConstructor!!.newInstance(*args.toTypedArray())
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

    /** 验证参数 */
    fun validation(parameter: Parameter): Parameter {
        // 可变参数
        if (parameter.isVarArgs) {
            error(
                """
                无法在 $parameter 上使用可变参数。
                Vararg parameters are not supported for $parameter
                """.t()
            )
        }
        return parameter
    }

    companion object {

        val cached = ConcurrentHashMap<Class<*>, AnalyzedClass>()

        fun of(clazz: Class<*>): AnalyzedClass {
            cached[clazz]?.let { return it }
            val instance = AnalyzedClass(clazz)
            return cached.putIfAbsent(clazz, instance) ?: instance
        }

        /** 收集类及其父类的所有实例字段（排除 static/synthetic） */
        private fun collectAllFields(clazz: Class<*>): Map<String, java.lang.reflect.Field> {
            val fields = LinkedHashMap<String, java.lang.reflect.Field>()
            var current: Class<*>? = clazz
            while (current != null && current != Any::class.java) {
                for (field in current.declaredFields) {
                    if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                    if (field.isSynthetic) continue
                    fields.putIfAbsent(field.name, field)
                }
                current = current.superclass
            }
            return fields
        }
    }
}