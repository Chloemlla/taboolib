package taboolib.expansion.ioc.bean

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Bean 元数据定义。
 *
 * @param type Bean 的类型
 * @param scope Bean 的作用域
 * @param isDataClass 是否为独立的 PlayerData 数据类（需要持久化）
 * @param managedDataType 若 Bean 继承 PlayerOperator<T>，则为 T 的类型（Manager 模式）
 * @param dependencies @Resource 字段列表
 * @param postConstruct 初始化后回调方法
 * @param preDestroy 销毁前回调方法
 */
class BeanDefinition(
    val type: Class<*>,
    val scope: BeanScope,
    val isDataClass: Boolean,
    val managedDataType: Class<*>?,
    val dependencies: List<DependencyField>,
    val postConstruct: Method?,
    val preDestroy: Method?
)

/**
 * 依赖字段描述。
 */
class DependencyField(
    val field: Field,
    val fieldType: Class<*>,
    /** 泛型参数类型（用于 DataMapper<T> / PlayerOperator<T>） */
    val genericType: Class<*>?
)
