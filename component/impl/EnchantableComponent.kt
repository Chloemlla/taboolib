package taboolib.module.nms.component.impl

import net.minecraft.world.item.enchantment.Enchantable
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTagData

/**
 * TabooLib
 * taboolib.module.nms.impl.EnchantableComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:31
 */
class EnchantableComponent (val value: Enchantable): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        TODO("Not yet implemented")
    }
}