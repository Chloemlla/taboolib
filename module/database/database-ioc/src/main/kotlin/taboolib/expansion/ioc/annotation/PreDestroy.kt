package taboolib.expansion.ioc.annotation

/**
 * Bean 销毁前回调。
 *
 * - SINGLETON：在 DISABLE 时调用
 * - PLAYER：在玩家退出时调用
 *
 * 标记的方法必须无参数。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreDestroy
