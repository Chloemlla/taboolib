package taboolib.module.navigation

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.util.Vector
import taboolib.module.navigation.handlers.*
import java.util.*
import kotlin.math.ceil

/**
 * Navigation
 * ink.ptms.navigation.v2.PathTypeFactory
 *
 * @author sky
 * @since 2021/2/21 11:57 下午
 */
@Suppress("LiftReturnOrAssignment")
open class PathTypeFactory(val entity: NodeEntity) {

    val world = entity.location.world!!

    /**
     * 评估类型
     * 根据实体自身条件判断是否可以穿过该方块
     */
    open fun evaluateType(pathType: PathType): PathType {
        return when {
            pathType == PathType.DOOR_WOOD_CLOSED && entity.canOpenDoors && entity.canPassDoors -> PathType.WALKABLE_DOOR
            pathType == PathType.DOOR_OPEN && !entity.canPassDoors -> PathType.BLOCKED
            pathType == PathType.LEAVES -> PathType.BLOCKED
            else -> pathType
        }
    }

    /**
     * 获取方块类型
     * 主要目的是判断实体的碰撞箱是否允许通过该空间
     */
    open fun getTypeAsBoundingBox(x: Int, y: Int, z: Int): PathType {
        val cover = EnumSet.noneOf(PathType::class.java)
        val coverType = getTypeAsBoundingBox(x, y, z, cover)
        if (cover.contains(PathType.FENCE)) {
            return PathType.FENCE
        }
        var passable = PathType.BLOCKED
        cover.forEach {
            // 假设实体无法通过该方块
            // 则返回该方块类型
            if (entity.getPathfindingMalus(it) < 0.0f) {
                return it
            }
            // 当实体能够通过该方块
            // 则记录该方块
            if (entity.getPathfindingMalus(it) >= 0) {
                passable = it
            }
        }
        // 假设中心方块可通过，且附近无危险方块，怪物宽度小于等于 1 格
        // 则允许通过
        if (coverType == PathType.OPEN && entity.getPathfindingMalus(passable) == 0.0f && entity.width <= 1) {
            return PathType.OPEN
        } else {
            // 否则返回危险方块
            return passable
        }
    }

    /**
     * 获取方块类型
     * 主要目的是判断实体的碰撞箱是否允许通过该空间
     *
     * @param x x
     * @param y y
     * @param z z
     * @param cover 实体自身碰撞箱覆盖的所有方块类型
     */
    open fun getTypeAsBoundingBox(x: Int, y: Int, z: Int, cover: EnumSet<PathType>): PathType {
        var pathType: PathType? = null
        (0 until ceil(entity.width).toInt()).forEach { ox ->
            (0 until ceil(entity.height).toInt()).forEach { oy ->
                (0 until ceil(entity.depth).toInt()).forEach { oz ->
                    // 获取方块类型并评估
                    val type = evaluateType(getTypeAsWalkable(world, Vector(ox + x, oy + y, oz + z))).also {
                        cover.add(it)
                    }
                    // 如果是原点则作为方法的返回值
                    if (ox == 0 && oy == 0 && oz == 0) {
                        pathType = type
                    }
                }
            }
        }
        return pathType!!
    }

    /**
     * 获取方块类型
     * 主要目的为判断方块是否可行走及其行走代价
     *
     * 假设该方块的临近方块存在危险类型
     * 那么该方块也会被视为危险类型
     */
    open fun getTypeAsWalkable(world: World, position: Vector): PathType {
        var rawType = getRawType(world, position)
        if (rawType == PathType.OPEN) {
            val down = getRawType(world, position.down())
            // 下方是实体方块或可站立方块时，当前位置可站立
            if (down != PathType.OPEN && down != PathType.WATER && down != PathType.LAVA) {
                rawType = PathType.WALKABLE
            }
            // 继承下方危险类型
            rawType = when (down) {
                PathType.DAMAGE_FIRE -> PathType.DAMAGE_FIRE
                PathType.DAMAGE_CACTUS -> PathType.DAMAGE_CACTUS
                PathType.DAMAGE_OTHER -> PathType.DAMAGE_OTHER
                PathType.STICKY_HONEY -> PathType.STICKY_HONEY
                else -> rawType
            }
        }
        if (rawType == PathType.WALKABLE && entity.neighborCheck) {
            rawType = getTypeAsNeighbor(world, position, rawType)
        }
        return rawType
    }

    companion object {

        /** 邻居危险检测用缓存 */
        val dangerMaterials: EnumMap<Material, PathType> = EnumMap(Material::class.java)

        /** 处理器列表 */
        val handlers = mutableListOf<BlockTypeHandler>()

        /** Material → Handler 缓存 */
        val handlerCache = EnumMap<Material, BlockTypeHandler>(Material::class.java)

        fun registerHandler(handler: BlockTypeHandler) {
            handlers.add(handler)
            handlers.sortByDescending { it.priority }
            handlerCache.clear()
        }

        fun unregisterHandler(handler: BlockTypeHandler) {
            handlers.remove(handler)
            handlerCache.clear()
        }

        fun getHandler(material: Material): BlockTypeHandler {
            handlerCache[material]?.let { return it }
            for (handler in handlers) {
                if (handler.canHandle(material)) {
                    handlerCache[material] = handler
                    return handler
                }
            }
            error("No handler for $material")
        }

        init {
            // 邻居危险检测缓存
            for (mat in Material.values()) {
                val danger = classifyDanger(mat.name)
                if (danger != null) {
                    dangerMaterials[mat] = danger
                }
            }
            // 注册内置处理器
            registerHandler(TrapdoorHandler())
            registerHandler(DoorHandler())
            registerHandler(FenceGateHandler())
            registerHandler(CampfireHandler())
            registerHandler(SlabHandler())
            registerHandler(StairsHandler())
            registerHandler(LilyPadHandler())
            registerHandler(DefaultHandler())
        }

        private fun classifyDanger(name: String): PathType? {
            return when (name) {
                "CACTUS" -> PathType.DANGER_CACTUS
                "SWEET_BERRY_BUSH" -> PathType.DANGER_OTHER
                "FIRE", "MAGMA_BLOCK",
                "LAVA", "FLOWING_LAVA", "STATIONARY_LAVA" -> PathType.DANGER_FIRE
                "WATER", "FLOWING_WATER", "STATIONARY_WATER" -> PathType.WATER_BORDER
                else -> null
            }
        }

        /**
         * 获取方块类型
         * 主要目的是获取其临近的危险方块
         */
        fun getTypeAsNeighbor(world: World, position: Vector, pathType: PathType): PathType {
            (-1..1).forEach { ox ->
                (-1..1).forEach { oy ->
                    (-1..1).forEach { oz ->
                        if (ox != 0 || oz != 0) {
                            val block = world.getBlockAtIfLoaded(Vector(position.x + ox, position.y + oy, position.z + oz))
                            if (block != null) {
                                val danger = dangerMaterials[block.type]
                                if (danger != null) {
                                    return danger
                                }
                            }
                        }
                    }
                }
            }
            return pathType
        }

        /**
         * 获取单个方块的原始类型
         */
        fun getRawType(world: World, position: Vector): PathType {
            val block = world.getBlockAtIfLoaded(position) ?: return PathType.BLOCKED
            return getHandler(block.type).getPathType(block)
        }
    }
}
