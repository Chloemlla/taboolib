package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType
import taboolib.module.navigation.isOpened

/**
 * 栅栏门处理器
 *
 * @author sky
 */
class FenceGateHandler : BlockTypeHandler {

    override val priority = -1000

    override fun canHandle(material: Material): Boolean {
        return material.name.endsWith("FENCE_GATE")
    }

    override fun getPathType(block: Block): PathType {
        return if (block.isOpened()) PathType.OPEN else PathType.FENCE
    }
}
