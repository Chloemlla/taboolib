package taboolib.expansion.ioc.annotation

/**
 * Bean 初始化后回调。
 *
 * 在 Bean 实例化 + 依赖注入完成后调用。
 * 标记的方法必须无参数。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostConstruct
