package taboolib.module.configuration

import com.electronwill.nightconfig.core.conversion.Converter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局 Converter 注册表
 *
 * 用于注册和获取类型转换器，避免在每个字段上都添加 @Conversion 注解
 */
object ConverterRegistry {

    private val converters = ConcurrentHashMap<Class<*>, Converter<*, *>>()

    init {
        // 注册默认的转换器
        register(Map::class.java, MapConverter())
        register(UUID::class.java, UUIDConverter())
    }

    /**
     * 注册一个转换器
     *
     * @param type 要转换的类型
     * @param converter 转换器实例
     */
    fun <T> register(type: Class<T>, converter: Converter<T, *>) {
        converters[type] = converter
    }

    /**
     * 获取指定类型的转换器
     *
     * @param type 类型
     * @return 转换器，如果没有注册则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getConverter(type: Class<*>): Converter<T, Any>? {
        // 精确匹配
        converters[type]?.let { return it as Converter<T, Any> }
        // 检查是否是接口或父类匹配
        for ((registeredType, converter) in converters) {
            if (registeredType.isAssignableFrom(type)) {
                return converter as Converter<T, Any>
            }
        }
        return null
    }

    /**
     * 检查是否有注册的转换器
     *
     * @param type 类型
     * @return 是否有转换器
     */
    fun hasConverter(type: Class<*>): Boolean {
        return getConverter<Any>(type) != null
    }
}

