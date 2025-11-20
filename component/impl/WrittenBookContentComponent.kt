package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.WrittenBookContent
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.WrittenBookContentComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:54
 */
class WrittenBookContentComponent(val value: WrittenBookContent): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}