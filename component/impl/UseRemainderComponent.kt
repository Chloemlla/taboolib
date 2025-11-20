package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.UseRemainder
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.NMSItemTag

/**
 * TabooLib
 * taboolib.module.nms.impl.UseRemainderComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:30
 */
class UseRemainderComponent(val value: UseRemainder) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        // TODO("物品暂时不知道怎么跨类序列化")
        return ItemTag.fromJson(NMSItemTag.let {
            it.instance.toMinecraftJson(it.asBukkitCopy(value.convertInto))
        })
    }
}