package taboolib.module.nms.component.impl

import net.minecraft.world.level.block.entity.BannerPatternLayers
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.BannerPatternLayersComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:56
 */
class BannerPatternLayersComponent(val value: BannerPatternLayers) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagList(value.layers.map {
            val pattern = it.pattern.value()
            val assetId = "assetId" to ItemTagData(pattern.assetId.toString())
            val translationKey = "translationKey" to ItemTagData(pattern.translationKey)
            val color = "color" to ItemTagData(it.color.name)
            ItemTag(mapOf(assetId, translationKey, color))
        })
    }
}