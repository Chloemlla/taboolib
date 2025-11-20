package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.MapDecorations
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.MapDecorationsComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:42
 */
class MapDecorationsComponent(val value: MapDecorations): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagList(value.decorations.map {
            val x = "x" to ItemTagData(it.value.x)
            val z = "z" to ItemTagData(it.value.z)
            val rotation = "rotation" to ItemTagData(it.value.rotation)
            val type = it.value.type.value()
            val assetId = "assetId" to ItemTagData(type.assetId.toString())
            val mapColor = "mapColor" to ItemTagData(type.mapColor)
            val types = "type" to ItemTag(mapOf(assetId, mapColor))

            ItemTag(mapOf(it.key to ItemTag(mapOf(x, z, rotation, types))))
        }
        )
    }
}