package taboolib.module.nms.component.impl

import net.minecraft.resources.MinecraftKey
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.MinecraftKeyNBT
 *
 * @author 晓劫
 * @since 2025/11/2 09:27
 */
class MinecraftKeyComponent(val value:MinecraftKey) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return ItemTagData(value.toString())
    }
}