package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType
import taboolib.module.navigation.isIronDoor
import taboolib.module.navigation.isOpened

/**
 * 门处理器
 *
 * @author sky
 */
class DoorHandler : BlockTypeHandler {

    override val priority = -1000

    override fun canHandle(material: Material): Boolean {
        val name = material.name
        return (name.endsWith("DOOR") || name.endsWith("DOOR_BLOCK")) && !name.endsWith("TRAPDOOR")
    }

    override fun getPathType(block: Block): PathType {
        return if (block.isOpened()) {
            PathType.DOOR_OPEN
        } else if (block.isIronDoor()) {
            PathType.DOOR_IRON_CLOSED
        } else {
            PathType.DOOR_WOOD_CLOSED
        }
    }
}
