package taboolib.expansion

import taboolib.common.reflect.getAnnotationIfPresent
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.ColumnTypePostgreSQL
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType

/**
 * TabooLib
 * taboolib.expansion.AnalyzedClassMember
 *
 * @author 坏黑
 * @since 2023/3/29 11:28
 */
class AnalyzedClassMember(private val root: Parameter, name: String, val isFinal: Boolean) {

    /** 名称 */
    val name = root.findAnnotation<Alias>()?.value ?: name.toColumnName()

    /** 属性名称 */
    val propertyName: String = name

    /** 返回类型 */
    val returnType: Class<*> = root.type

    /** 是否为 ID 键 */
    val isPrimary = root.findAnnotation<Id>() != null

    /** 是否建立索引 */
    val isKey = root.findAnnotation<Key>() != null

    /** 是否建立唯一索引 */
    val isUniqueKey = root.findAnnotation<UniqueKey>() != null

    /** 是否不为空 */
    val isNotNull = root.findAnnotation<NotNull>() != null

    /** 长度 */
    val length = root.findAnnotation<Length>()?.value ?: 64

    /** 自定义 SQL 列类型 */
    val columnTypeSQL: ColumnTypeSQL? = root.findAnnotation<ColumnType>()?.sql

    /** 自定义 SQLite 列类型 */
    val columnTypeSQLite: ColumnTypeSQLite? = root.findAnnotation<ColumnType>()?.sqlite

    /** 自定义 PostgreSQL 列类型 */
    val columnTypePostgreSQL: ColumnTypePostgreSQL? = root.findAnnotation<ColumnType>()?.postgresql

    /** 是否指定了自定义列类型 */
    val hasColumnType: Boolean = root.findAnnotation<ColumnType>() != null

    /** 是否为关联表引用 */
    val isLinkTable: Boolean = root.findAnnotation<LinkTable>() != null

    /** 关联表外键列名（下划线命名） */
    val linkTableColumn: String? = root.findAnnotation<LinkTable>()?.value?.toColumnName()

    /** 关联的 data class 类型 */
    val linkTableClass: Class<*>? = if (isLinkTable) returnType else null

    /** 泛型参数化类型 */
    val parameterizedType: ParameterizedType? = root.parameterizedType.let {
        it as? ParameterizedType
    }

    /** 是否为 List 类型 */
    val isList: Boolean
        get() = List::class.java.isAssignableFrom(returnType)

    /** 是否为 Set 类型 */
    val isSet: Boolean
        get() = Set::class.java.isAssignableFrom(returnType)

    /** 是否为 Map 类型 */
    val isMap: Boolean
        get() = Map::class.java.isAssignableFrom(returnType)

    /** 是否为容器类型（List/Set/Map） */
    val isCollection: Boolean
        get() = isList || isSet || isMap

    /** 容器元素类型（List<T>/Set<T> 的 T，Map<K,V> 的 V） */
    val collectionElementType: Class<*>? = when {
        parameterizedType != null && (List::class.java.isAssignableFrom(returnType) || Set::class.java.isAssignableFrom(returnType)) -> {
            val arg = parameterizedType.actualTypeArguments.firstOrNull()
            arg as? Class<*> ?: if (arg is ParameterizedType) arg.rawType as? Class<*> else null
        }
        parameterizedType != null && Map::class.java.isAssignableFrom(returnType) -> {
            val args = parameterizedType.actualTypeArguments
            val arg = args.getOrNull(1)
            arg as? Class<*> ?: if (arg is ParameterizedType) arg.rawType as? Class<*> else null
        }
        else -> null
    }

    /** Map 的 Key 类型 */
    val mapKeyType: Class<*>? = if (parameterizedType != null && Map::class.java.isAssignableFrom(returnType)) {
        val arg = parameterizedType.actualTypeArguments.firstOrNull()
        arg as? Class<*> ?: if (arg is ParameterizedType) arg.rawType as? Class<*> else null
    } else null

    /** 是否为基础类型（Boolean） */
    val isBoolean: Boolean
        get() = returnType == Boolean::class.java || returnType == Boolean::class.javaPrimitiveType

    /** 是否为基础类型（Byte） */
    val isByte: Boolean
        get() = returnType == Byte::class.java || returnType == Byte::class.javaPrimitiveType

    /** 是否为基础类型（Short） */
    val isShort: Boolean
        get() = returnType == Short::class.java || returnType == Short::class.javaPrimitiveType

    /** 是否为基础类型（ByteArray） */
    val isByteArray: Boolean
        get() = returnType == ByteArray::class.java || returnType == ByteArray::class.javaPrimitiveType

    /** 是否为基础类型（Int） */
    val isInt: Boolean
        get() = returnType == Int::class.java || returnType == Int::class.javaPrimitiveType

    /** 是否为基础类型（Long） */
    val isLong: Boolean
        get() = returnType == Long::class.java || returnType == Long::class.javaPrimitiveType

    /** 是否为基础类型（Float） */
    val isFloat: Boolean
        get() = returnType == Float::class.java || returnType == Float::class.javaPrimitiveType

    /** 是否为基础类型（Double） */
    val isDouble: Boolean
        get() = returnType == Double::class.java || returnType == Double::class.javaPrimitiveType

    /** 是否为基础类型（Char） */
    val isChar: Boolean
        get() = returnType == Char::class.java || returnType == Char::class.javaPrimitiveType

    /** 是否为字符串 */
    val isString: Boolean
        get() = returnType == String::class.java

    /** 是否为 UUID */
    val isUUID: Boolean
        get() = returnType == java.util.UUID::class.java

    /** 是否为枚举 */
    val isEnum: Boolean
        get() = Enum::class.java.isAssignableFrom(returnType)

    /** 是否为实现了 [IndexedEnum] 接口的枚举（数据库中以数值存储） */
    val isIndexedEnum: Boolean
        get() = isEnum && IndexedEnum::class.java.isAssignableFrom(returnType)

    /** 是否为自定义对象 */
    val isCustomObject: Boolean
        get() = !isLinkTable && !isCollection && !isBoolean && !isByte && !isShort && !isInt && !isLong && !isFloat && !isDouble && !isChar && !isString && !isEnum && !isUUID && !isByteArray

    /** 是否可以转换成字符串类型 */
    fun canConvertedString(): Boolean {
        return isString || (isEnum && !isIndexedEnum) || isUUID
    }

    /** 是否可以转化为数字类型 */
    fun canConvertedNumber(): Boolean {
        return canConvertedInteger() || canConvertedDecimal()
    }

    /** 是否可以转化为整数类型 */
    fun canConvertedInteger(): Boolean {
        return isBoolean || isByte || isShort || isInt || isLong || isChar || isIndexedEnum
    }

    /** 是否可以转化为小数类型 */
    fun canConvertedDecimal(): Boolean {
        return isFloat || isDouble
    }

    override fun toString(): String {
        return "$name(${returnType})"
    }

    companion object {

        /** 转换为数据库字段名称 */
        fun String.toColumnName(): String {
            return toCharArray().joinToString("") { if (it.isUpperCase()) "_${it.lowercase()}" else it.toString() }.trimStart('_')
        }

        /** 解析数据类的表名：优先使用 @TableName 注解值，否则将类名转为下划线命名 */
        fun Class<*>.resolveTableName(): String {
            return getAnnotation(TableName::class.java)?.value ?: simpleName.toColumnName()
        }

        /** 获取注解 */
        inline fun <reified T : Annotation> Parameter.findAnnotation(): T? {
            return getAnnotationIfPresent(T::class.java)
        }

        /** 判断是否为基础类型（可直接映射到数据库列的类型） */
        fun isPrimitiveOrBasicType(clazz: Class<*>): Boolean {
            return clazz == Boolean::class.java || clazz == Boolean::class.javaPrimitiveType
                || clazz == Byte::class.java || clazz == Byte::class.javaPrimitiveType
                || clazz == Short::class.java || clazz == Short::class.javaPrimitiveType
                || clazz == Int::class.java || clazz == Int::class.javaPrimitiveType
                || clazz == Long::class.java || clazz == Long::class.javaPrimitiveType
                || clazz == Float::class.java || clazz == Float::class.javaPrimitiveType
                || clazz == Double::class.java || clazz == Double::class.javaPrimitiveType
                || clazz == Char::class.java || clazz == Char::class.javaPrimitiveType
                || clazz == String::class.java
                || clazz == java.util.UUID::class.java
                || Enum::class.java.isAssignableFrom(clazz)
                || clazz == ByteArray::class.java
        }
    }
}
