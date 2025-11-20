package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.MapItemColor
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.MapItemColorComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:42
 */
class MapItemColorComponent(val value: MapItemColor): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}