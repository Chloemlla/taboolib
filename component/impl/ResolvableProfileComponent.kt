package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.ResolvableProfile
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.ResolvableProfileComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:56
 */
class ResolvableProfileComponent(val value: ResolvableProfile): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}