package taboolib.module.nms.component.impl

import net.minecraft.world.item.alchemy.PotionContents
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.PotionContentsComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:54
 */
class PotionContentsComponent(val value: PotionContents): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}