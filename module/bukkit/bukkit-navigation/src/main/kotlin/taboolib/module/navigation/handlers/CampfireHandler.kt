package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType
import taboolib.module.navigation.isCampfireLit

/**
 * 篝火处理器
 *
 * @author sky
 */
class CampfireHandler : BlockTypeHandler {

    override val priority = -1000

    override fun canHandle(material: Material): Boolean {
        val name = material.name
        return name == "CAMPFIRE" || name == "SOUL_CAMPFIRE"
    }

    override fun getPathType(block: Block): PathType {
        return if (block.isCampfireLit()) PathType.DAMAGE_FIRE else PathType.WALKABLE
    }
}
