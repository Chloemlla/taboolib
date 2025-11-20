package taboolib.module.nms.component.impl

import net.minecraft.network.chat.IChatBaseComponent
import net.minecraft.world.item.AdventureModePredicate
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.AdventureModePredicateComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:30
 */
class AdventureModePredicateComponent(val value: AdventureModePredicate): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val tooltip = "tooltip" to ItemTagList(value.invokeMethod<List<IChatBaseComponent>>("tooltip")?.map { ItemTagData(it.string) } ?: listOf())
        return ItemTag(mapOf(tooltip))
    }
}