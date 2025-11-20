package taboolib.module.nms.component.impl

import net.minecraft.world.ChestLock
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.ChestLockComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:57
 */
class ChestLockComponent(val value: ChestLock): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}