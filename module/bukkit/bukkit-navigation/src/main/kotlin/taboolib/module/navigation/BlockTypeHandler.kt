package taboolib.module.navigation

import org.bukkit.Material
import org.bukkit.block.Block

/**
 * 方块类型处理器
 *
 * @author sky
 */
interface BlockTypeHandler {

    /**
     * 判断是否处理该材质（用于缓存构建）
     */
    fun canHandle(material: Material): Boolean

    /**
     * 获取方块的路径类型
     */
    fun getPathType(block: Block): PathType

    /**
     * 优先级，越高越先匹配
     */
    val priority: Int get() = 0
}
