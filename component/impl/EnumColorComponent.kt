package taboolib.module.nms.component.impl

import net.minecraft.world.item.EnumColor
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.EnumColorComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:56
 */
class EnumColorComponent(val value: EnumColor): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}