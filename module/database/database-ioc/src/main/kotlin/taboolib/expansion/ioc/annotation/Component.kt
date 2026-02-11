package taboolib.expansion.ioc.annotation

import taboolib.expansion.ioc.bean.BeanScope

/**
 * 标记类为 IoC 管理的 Bean。
 *
 * @param scope Bean 的作用域，默认为 SINGLETON
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component(val scope: BeanScope = BeanScope.SINGLETON)
