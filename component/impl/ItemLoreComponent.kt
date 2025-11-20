package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.ItemLore
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.ItemLoreComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:28
 */
class ItemLoreComponent(val value: ItemLore) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val lines = try {
            value.lines
        } catch (_: NoSuchMethodError) {
            value.invokeMethod<List<Any>>("lines")!!
        }.map { itemTagToBukkitCopy(it) }

        return ItemTagList(lines)
    }
}