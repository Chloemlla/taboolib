package taboolib.module.nms.component.impl

import net.minecraft.core.Holder
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.component.BaseComponentNBT

/**
 * TabooLib
 * taboolib.module.nms.impl.ItemEnchantmentsComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:28
 */
class ItemEnchantmentsComponent(val value: ItemEnchantments) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        return try {
            value.keySet()
        } catch (_:NoSuchMethodError){
            value.invokeMethod<Set<Holder<Enchantment>>>("keySet")!!
        }.associateWith { it.value() }.let {
            ItemTag(it.map { it.key.registeredName to itemTagToBukkitCopy(it.value.weight) }.toMap())
        }
    }
}