package taboolib.module.nms.component.impl

import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.component.TooltipDisplay
import org.tabooproject.reflex.Reflex.Companion.getProperty
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.TooltipDisplayComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:29
 */
class TooltipDisplayComponent(val value: TooltipDisplay) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val hideTool = try{
            value.hideTooltip()
        }catch (_:NoSuchMethodError){
            value.getProperty<Boolean>("hideTooltip")!!
        }

        val components =try{
            value.hiddenComponents
        }catch (_:NoSuchMethodError){
            value.getProperty<Set<DataComponentType<*>>>("hiddenComponents")!!
        }
        val hideTooltip = "hideTooltip" to ItemTagData(hideTool)
        val hiddenComponents = "hiddenComponents" to ItemTagList(components.map { itemTagToBukkitCopy(it) })
        return ItemTag(mapOf(hideTooltip, hiddenComponents))
    }
}