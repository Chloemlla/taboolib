package taboolib.module.nms.component.impl

import net.minecraft.world.item.equipment.Equippable
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import kotlin.jvm.optionals.getOrDefault

/**
 * TabooLib
 * taboolib.module.nms.impl.EquippableComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:32
 */
class EquippableComponent(val value: Equippable): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val assetId = "assetId" to ItemTagData(value.assetId.get().location().toString())

        val slotType = "name" to ItemTagData(value.slot.type.name)
        val slotName = "slot" to ItemTagData(value.slot.name)
        val index = "index" to ItemTagData(value.slot.index)
        val id = "id" to ItemTagData(value.slot.id)
        val slot = "slot" to ItemTag(mapOf(slotType, slotName, index, id))

        val swappable = "swappable" to ItemTagData(value.swappable.toString())
        val dispensable = "dispensable" to ItemTagData(value.dispensable.toString())
        val canBeSheared = "canBeSheared" to ItemTagData(value.canBeSheared.toString())
        val damageOnHurt = "damageOnHurt" to ItemTagData(value.damageOnHurt.toString())
        val equipOnInteract = "equipOnInteract" to ItemTagData(value.equipOnInteract.toString())

        val soundLocation = "location" to ItemTagData(value.equipSound.value().location.toString())
        val soundRange = "fixedRange" to ItemTagData(value.equipSound.value().fixedRange.getOrDefault(0F))
        val equipSound = "equipSound" to ItemTag(mapOf(soundLocation, soundRange))
        return ItemTag(mapOf(assetId, slot, swappable, dispensable, canBeSheared, damageOnHurt, equipOnInteract, equipSound))
    }
}