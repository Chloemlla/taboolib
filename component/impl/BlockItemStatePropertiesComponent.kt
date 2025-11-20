package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.BlockItemStateProperties
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.BlockItemStatePropertiesComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:56
 */
class BlockItemStatePropertiesComponent(val value: BlockItemStateProperties): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagData(value.isEmpty)
    }
}