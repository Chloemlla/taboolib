package taboolib.module.nms.component.impl

import net.minecraft.world.entity.animal.EntityTropicalFish
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.EntityTropicalFishVariantComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:57
 */
class EntityTropicalFishVariantComponent(val value: EntityTropicalFish.Variant): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}