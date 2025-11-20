package taboolib.module.nms.component.impl

import net.minecraft.world.item.enchantment.Repairable
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.RepairableComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:41
 */
class RepairableComponent(val value: Repairable) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagList(value.items.map { ItemTagData(it.value().name.string) })
    }
}