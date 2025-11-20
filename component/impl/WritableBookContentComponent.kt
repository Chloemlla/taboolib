package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.WritableBookContent
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.WritableBookContentComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:54
 */
class WritableBookContentComponent(val value: WritableBookContent): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}