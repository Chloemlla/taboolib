package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.CustomModelData
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.CustomModelDataComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:30
 */
class CustomModelDataComponent(val value: CustomModelData) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val colors = "colors" to ItemTagList(value.colors.map { itemTagToBukkitCopy(it) })
        val floats = "floats" to ItemTagList(value.floats.map { itemTagToBukkitCopy(it) })
        val flags = "flags" to ItemTagList(value.flags.map { itemTagToBukkitCopy(it) })
        val strings = "string" to ItemTagList(value.strings.map { itemTagToBukkitCopy(it) })
        return ItemTag(mapOf(colors, floats, flags, strings))
    }
}