package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.Tool
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.ToolComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:31
 */
class ToolComponent(val value: Tool): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val rules = "rules" to ItemTag(mapOf("speeds" to ItemTagList(value.rules.map { ItemTagData(it.speed.get()) })))
        val correctForDrops = "correctForDrops" to ItemTagList(value.rules.map { ItemTagData(it.correctForDrops.get().toString()) })
        val damagePerBlock = "damagePerBlock" to ItemTagData(value.damagePerBlock)
        val defaultMiningSpeed = "defaultMiningSpeed" to ItemTagData(value.defaultMiningSpeed)
        val canDestroyBlocksInCreative = "canDestroyBlocksInCreative" to ItemTagData(value.canDestroyBlocksInCreative.toString())
        return ItemTag(mapOf(rules, correctForDrops, damagePerBlock, defaultMiningSpeed, canDestroyBlocksInCreative))
    }
}