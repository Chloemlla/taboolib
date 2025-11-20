package taboolib.module.nms.component.impl

import net.minecraft.world.level.saveddata.maps.MapId
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.MapIdComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:29
 */
class MapIdComponent(val value: MapId) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagData(value.key())
    }
}