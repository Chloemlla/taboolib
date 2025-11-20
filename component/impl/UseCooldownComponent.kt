package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.UseCooldown
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.UseCooldownComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:28
 */
class UseCooldownComponent(val value: UseCooldown) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val s = "seconds" to ItemTagData(value.seconds)
        val group = "group" to ItemTagData(value.cooldownGroup.toString())
        return ItemTag(mapOf(s, group))
    }
}