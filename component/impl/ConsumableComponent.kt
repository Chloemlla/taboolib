package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.Consumable
import taboolib.module.nms.ItemTag
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData
import kotlin.jvm.optionals.getOrNull

/**
 * TabooLib
 * taboolib.module.nms.impl.ConsumableComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:30
 */
class ConsumableComponent(val value: Consumable) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val soundRange = "fixedRange" to ItemTagData(value.sound.value().fixedRange.getOrNull() ?: 0F)
        val soundLocation = "location" to ItemTagData(value.sound.value().location.toString())
        val name = "registeredName" to ItemTagData(value.sound.registeredName)
        return ItemTag(mapOf(soundRange, soundLocation, name))
    }
}