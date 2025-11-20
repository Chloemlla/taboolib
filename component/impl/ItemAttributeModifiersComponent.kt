package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.ItemAttributeModifiers
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.ItemAttributeModifiersComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:30
 */
class ItemAttributeModifiersComponent(val value: ItemAttributeModifiers) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val modifiers = try {
            value.modifiers
        } catch (_: NoSuchMethodError) {
            value.invokeMethod<List<ItemAttributeModifiers.c>>("modifiers")!!
        }

        val attribute = "attribute" to ItemTagList(modifiers.map {
            val amount = "amount" to ItemTagData(it.modifier.amount)
            val defaultValue = "defaultValue" to ItemTagData(it.attribute.value().defaultValue)
            val descriptionId = "id" to ItemTagData(it.attribute.value().descriptionId.substringAfterLast("."))
            ItemTag(mapOf(descriptionId, amount, defaultValue))
        })
        return ItemTag(mapOf(attribute))
    }
}