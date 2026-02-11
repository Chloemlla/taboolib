package taboolib.expansion.ioc.annotation

/**
 * 字段级依赖注入。
 *
 * 支持注入：
 * - 其他 @Component Bean（按类型匹配）
 * - DataMapper<T>（通过泛型参数 T 匹配）
 * - PlayerOperator<T>（通过泛型参数 T 匹配）
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Resource
