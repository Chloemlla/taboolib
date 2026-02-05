package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType
import taboolib.module.navigation.isTrapdoorOpen

/**
 * 活板门处理器
 *
 * @author sky
 */
class TrapdoorHandler : BlockTypeHandler {

    override val priority = -1000

    override fun canHandle(material: Material): Boolean {
        val name = material.name
        return name.endsWith("TRAPDOOR") || name.endsWith("TRAP_DOOR")
    }

    override fun getPathType(block: Block): PathType {
        val open = block.isTrapdoorOpen()
        return if (open) PathType.OPEN else PathType.WALKABLE
    }
}
