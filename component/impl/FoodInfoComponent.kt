package taboolib.module.nms.component.impl

import net.minecraft.world.food.FoodInfo
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.FoodInfoComponentNBT
 *
 * @author 晓劫
 * @since 2025/11/2 09:08
 */
class FoodInfoComponent(private val value: FoodInfo) : BaseComponentNBT {

    override fun getTagData(): ItemTagData {
        val nutrition = "nutrition" to ItemTagData(value.nutrition)
        val canAlwaysEat = "canAlwaysEat" to ItemTagData(value.canAlwaysEat().toString())
        val saturation = "saturation" to ItemTagData(value.saturation)
        return ItemTag(mapOf(nutrition, canAlwaysEat, saturation))
    }

}