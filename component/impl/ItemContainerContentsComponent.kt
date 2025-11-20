package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.ItemContainerContents
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.ItemContainerContentsComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:56
 */
class ItemContainerContentsComponent(val value: ItemContainerContents): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}