package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType
import taboolib.module.navigation.isBottomSlab

/**
 * 台阶处理器
 *
 * @author sky
 */
class SlabHandler : BlockTypeHandler {

    override val priority = -1000

    override fun canHandle(material: Material): Boolean {
        return material.name.endsWith("SLAB")
    }

    override fun getPathType(block: Block): PathType {
        return if (block.isBottomSlab()) PathType.WALKABLE else PathType.BLOCKED
    }
}
