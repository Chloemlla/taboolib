package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.Bees
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.BeesComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:57
 */
class BeesComponent(val value: Bees) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagList(value.bees.map {
            val entityData = "entityData" to BaseComponentNBT.of(it.entityData).getTagData()
            val ticksInHive = "ticksInHive" to ItemTagData(it.ticksInHive)
            val minTicksInHive = "minTicksInHive" to ItemTagData(it.minTicksInHive)
            ItemTag(mapOf(entityData, ticksInHive, minTicksInHive))
        }
        )
    }
}