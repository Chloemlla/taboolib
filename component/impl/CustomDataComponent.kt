package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.CustomData
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.CustomDataComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:29
 */
class CustomDataComponent(val value: CustomData) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return itemTagToBukkitCopy(value.copyTag())
    }
}