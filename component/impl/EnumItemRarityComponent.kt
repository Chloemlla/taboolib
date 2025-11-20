package taboolib.module.nms.component.impl

import net.minecraft.world.item.EnumItemRarity
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.EnumItemRarityComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:30
 */
class EnumItemRarityComponent(val value: EnumItemRarity) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
       val name = "name" to ItemTagData(value.name)
        return ItemTag(mapOf(name))
    }
}