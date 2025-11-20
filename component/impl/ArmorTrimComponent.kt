package taboolib.module.nms.component.impl

import net.minecraft.world.item.equipment.trim.ArmorTrim
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.ArmorTrimComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:54
 */
class ArmorTrimComponent(val value: ArmorTrim) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val pattern = value.pattern.value().let {
            val decal = "decal" to ItemTagData(it.decal)
            val assetId = "assetId" to ItemTagData(it.assetId.toString())
            val description = "description" to ItemTagData(it.description.string)
            "pattern" to ItemTag(mapOf(decal, assetId, description))
        }

        val material = value.material.value()
        val descriptionMaterial = "description" to ItemTagData(material.description.string)
        val suffix = "suffix" to ItemTagData(material.assets.base.suffix)
        val assets = "assest" to ItemTagList(material.assets.overrides.map {
            val location = "location" to ItemTagData(it.key.location().toString())
            val suffixAssets = "suffix" to ItemTagData(it.value.suffix)
            ItemTag(mapOf(location, suffixAssets))
        })
        return ItemTag(mapOf(pattern, descriptionMaterial, suffix, assets))
    }
}