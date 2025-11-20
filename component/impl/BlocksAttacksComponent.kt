package taboolib.module.nms.component.impl

import net.minecraft.sounds.SoundEffect
import net.minecraft.world.item.component.BlocksAttacks
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList
import kotlin.jvm.optionals.getOrDefault

/**
 * TabooLib
 * taboolib.module.nms.impl.BlocksAttacksComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:41
 */
class BlocksAttacksComponent(val value: BlocksAttacks) : BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val soundEffect = "soundEffect" to getSoundEffect(value.blockSound.get().value())
        val blockDelaySeconds = "blockDelaySeconds" to ItemTagData(value.blockDelaySeconds)
        val damageReductions = "damageReductions" to ItemTagList(value.damageReductions.map {
            val base = "base" to ItemTagData(it.base)
            val factor = "factor" to ItemTagData(it.factor)
            val horizontalBlockingAngle = "horizontalBlockingAngle" to ItemTagData(it.horizontalBlockingAngle)
            val type = "type" to ItemTagList(it.type.get().map {
                val values = it.value()
                val msgId = "msgId" to ItemTagData(values.msgId)
                val deathMessageType = "deathMessageType" to ItemTagData(values.deathMessageType.name)
                val effects = "effects" to ItemTagData(values.effects.name)
                val scaling = "scaling" to ItemTagData(values.scaling.name)
                val exhaustion = "exhaustion" to ItemTagData(values.exhaustion)
                ItemTag(mapOf(msgId, deathMessageType, effects, scaling, exhaustion))
            })
            ItemTag(mapOf(base, factor, horizontalBlockingAngle, type))
        })
        return ItemTag(mapOf(soundEffect, blockDelaySeconds, damageReductions))
    }

    fun getSoundEffect(value: SoundEffect): ItemTagData {
        val location = "location" to ItemTagData(value.location.toString())
        val fixed = "fixedRange" to ItemTagData(value.fixedRange.getOrDefault(0F))
        return ItemTag(mapOf(location, fixed))
    }
}