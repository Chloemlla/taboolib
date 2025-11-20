package taboolib.module.nms.component.impl

import net.minecraft.network.chat.IChatBaseComponent
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.ChatComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:28
 */
class ChatComponent(val value: IChatBaseComponent) : BaseComponentNBT {

    override fun getTagData(): ItemTagData {
        return ItemTagData(value.string)
    }

}