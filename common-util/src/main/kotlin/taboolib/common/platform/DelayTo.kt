package taboolib.common.platform

import taboolib.common.LifeCycle

/**
 * 在特定生命周期时延迟加载（如果早于该生命周期则推迟到该生命周期时加载）
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class DelayTo(val value: LifeCycle = LifeCycle.CONST)