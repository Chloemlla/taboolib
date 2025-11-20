package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.Weapon
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.WeaponComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:31
 */
class WeaponComponent(val value: Weapon): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val itemDamagePerAttack = "itemDamagePerAttack" to ItemTagData(value.itemDamagePerAttack)
        val disableBlockingForSeconds = "disableBlockingForSeconds" to ItemTagData(value.disableBlockingForSeconds)
        return ItemTag(mapOf(itemDamagePerAttack, disableBlockingForSeconds))
    }
}