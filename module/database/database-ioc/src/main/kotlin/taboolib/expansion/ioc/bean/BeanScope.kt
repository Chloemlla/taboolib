package taboolib.expansion.ioc.bean

/**
 * Bean 作用域枚举。
 */
enum class BeanScope {

    /** 全局单例 */
    SINGLETON,

    /** 每次获取创建新实例 */
    PROTOTYPE,

    /** 每个玩家一个实例，绑定 join/quit */
    PLAYER
}
