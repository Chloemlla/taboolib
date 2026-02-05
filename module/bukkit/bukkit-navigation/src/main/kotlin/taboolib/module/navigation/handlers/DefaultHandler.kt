package taboolib.module.navigation.handlers

import org.bukkit.Material
import org.bukkit.block.Block
import taboolib.module.navigation.BlockTypeHandler
import taboolib.module.navigation.PathType
import taboolib.module.navigation.isAirLegacy

/**
 * 默认方块类型处理器
 * 处理所有未被其他处理器匹配的方块
 *
 * @author sky
 */
class DefaultHandler : BlockTypeHandler {

    override val priority = Int.MIN_VALUE

    override fun canHandle(material: Material) = true

    override fun getPathType(block: Block): PathType {
        val mat = block.type
        val name = mat.name
        return when {
            mat.isAirLegacy() -> PathType.OPEN
            // 水
            name == "WATER" || name == "FLOWING_WATER" || name == "STATIONARY_WATER" -> PathType.WATER
            // 岩浆
            name == "LAVA" || name == "FLOWING_LAVA" || name == "STATIONARY_LAVA" -> PathType.LAVA
            // 燃烧物
            name == "FIRE" || name == "MAGMA_BLOCK" -> PathType.DAMAGE_FIRE
            // 仙人掌
            name == "CACTUS" -> PathType.DAMAGE_CACTUS
            // 浆果丛
            name == "SWEET_BERRY_BUSH" -> PathType.DAMAGE_OTHER
            // 蜂蜜块
            name == "HONEY_BLOCK" -> PathType.STICKY_HONEY
            // 可可豆
            name.endsWith("COCOA") -> PathType.COCOA
            // 树叶
            name.endsWith("LEAVES") || name.endsWith("LEAVES_2") -> PathType.LEAVES
            // 栅栏、石墙
            name.endsWith("FENCE") || name.endsWith("WALL") -> PathType.FENCE
            // 地毯等可穿过方块
            name == "CARPET"
                || name.endsWith("SAPLING")
                || name == "REDSTONE_WIRE"
                || (name.endsWith("GRASS") && !mat.isSolid)
                || name == "NETHER_WARTS"
                || name == "NETHER_STALK"
                || name == "DOUBLE_PLANT"
                || name.startsWith("FLOWER_POT")
                || name == "RED_ROSE"
                || name == "YELLOW_FLOWER"
                || name == "BEETROOT_BLOCK"
                || name.startsWith("DIODE_BLOCK")
                || name == "SUGAR_CANE_BLOCK" -> PathType.OPEN
            // 实体方块
            mat.isSolid -> PathType.BLOCKED
            // 其余
            else -> PathType.OPEN
        }
    }
}
