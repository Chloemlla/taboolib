package taboolib.expansion.ioc.visitor

import org.tabooproject.reflex.ReflexClass
import taboolib.common.Inject
import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.platform.Awake
import taboolib.common.platform.function.debug
import taboolib.expansion.ioc.annotation.Component
import taboolib.expansion.ioc.bean.BeanContainer

/**
 * ClassVisitor 集成，在 ENABLE 阶段扫描 @Component 类。
 * 容器初始化延迟到 ACTIVE 阶段，确保所有类扫描完毕。
 */
@Inject
@Awake
class ComponentVisitor : ClassVisitor(1) {

    override fun getLifeCycle(): LifeCycle = LifeCycle.ENABLE

    override fun visitStart(clazz: ReflexClass) {
        val javaClass = clazz.toClass()
        if (javaClass.isAnnotationPresent(Component::class.java)) {
            debug("[IoC] 扫描到 @Component: ${javaClass.simpleName}")
            BeanContainer.register(javaClass)
        }
    }
}

/**
 * 容器生命周期管理，独立 object 确保 @Awake 被正确发现。
 */
@Inject
object ComponentLifecycle {

    @Awake(LifeCycle.ACTIVE)
    fun init() {
        if (!BeanContainer.initialized && BeanContainer.definitions.isNotEmpty()) {
            debug("[IoC] ACTIVE 阶段，开始初始化容器，共 ${BeanContainer.definitions.size} 个 Bean")
            BeanContainer.initialize()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        if (BeanContainer.initialized) {
            debug("[IoC] DISABLE 阶段，关闭容器")
            BeanContainer.shutdown()
        }
    }
}
