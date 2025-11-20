package taboolib.module.nms.component.impl

import net.minecraft.world.level.block.entity.EnumBannerPatternType
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.EnumBannerPatternTypeComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:55
 */
class EnumBannerPatternTypeComponent(val value: EnumBannerPatternType): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}