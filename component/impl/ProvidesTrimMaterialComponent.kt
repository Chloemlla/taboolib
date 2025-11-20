package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.ProvidesTrimMaterial
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.ProvidesTrimMaterialComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:55
 */
class ProvidesTrimMaterialComponent(val value: ProvidesTrimMaterial): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}