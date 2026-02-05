package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType

/**
 * 楼梯处理器
 *
 * @author sky
 */
class StairsHandler : BlockTypeHandler {

    override val priority = -1000

    override fun canHandle(material: Material): Boolean {
        return material.name.endsWith("STAIRS")
    }

    override fun getPathType(block: Block): PathType {
        return PathType.WALKABLE
    }
}
