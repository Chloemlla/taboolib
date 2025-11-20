package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.LodestoneTracker
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.LodestoneTrackerComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:55
 */
class LodestoneTrackerComponent(val value: LodestoneTracker): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}