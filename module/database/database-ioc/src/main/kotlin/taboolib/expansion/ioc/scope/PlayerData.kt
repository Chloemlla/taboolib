package taboolib.expansion.ioc.scope

import taboolib.expansion.Id
import java.util.UUID

/**
 * PLAYER 作用域数据类的抽象基类。
 *
 * 内置 @Id 绑定 UUID，子类只需关注业务字段。
 * 所有 PLAYER 作用域的数据类必须继承此类。
 *
 * 使用示例：
 * ```
 * @Component(scope = BeanScope.PLAYER)
 * class MyPlayerData : PlayerData() {
 *     var coins: Int = 0
 *     var level: Int = 1
 * }
 * ```
 */
abstract class PlayerData {

    /** 玩家 UUID，作为数据库主键，由框架自动设置 */
    @Id
    var uuid: UUID = UUID(0, 0)
        internal set
}
