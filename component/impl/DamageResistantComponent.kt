package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.DamageResistant
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.DamageResistantComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:31
 */
class DamageResistantComponent(val value: DamageResistant) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val location = "location" to ItemTagData(value.types.location.toString())
        val typesRegistry = "location" to ItemTagData(value.types.registry.location().toString())
        val typesLocation = "location" to ItemTagData(value.types.location.toString())
        return ItemTag(mapOf(location, typesLocation, typesRegistry))
    }
}