package taboolib.module.nms.component.impl

import net.minecraft.world.item.component.DeathProtection
import net.minecraft.world.item.consume_effects.*
import taboolib.module.nms.component.BaseComponentNBT
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList

/**
 * TabooLib
 * taboolib.module.nms.impl.DeathProtectionComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:41
 */
class DeathProtectionComponent(val value: DeathProtection): BaseComponentNBT {
    override fun getTagData(): ItemTagData {
        val deaths = value.deathEffects.mapNotNull {
            when (it.type) {
                ConsumeEffect.a.APPLY_EFFECTS -> {
                    ItemTagList((it as ApplyStatusEffectsConsumeEffect).effects.map {
                        val amplifier = "amplifier" to ItemTagData(it.amplifier)
                        val duration = "duration" to ItemTagData(it.duration)
                        val descriptionId = "descriptionId" to ItemTagData(it.descriptionId)

                        val effect = it.effect.value()
                        val descriptionIdEffect = "descriptionId" to ItemTagData(effect.descriptionId)
                        val color = "color" to ItemTagData(effect.color)
                        val displayName = "displayName" to ItemTagData(effect.displayName.string)
                        val blendInDurationTicks = "blendInDurationTicks" to ItemTagData(effect.blendInDurationTicks)
                        val blendOutAdvanceTicks = "blendOutAdvanceTicks" to ItemTagData(effect.blendOutAdvanceTicks)
                        val blendOutDurationTicks = "blendOutDurationTicks" to ItemTagData(effect.blendOutDurationTicks)
                        val category = "category" to ItemTagData(effect.category.tooltipFormatting.name)
                        val effects = "effect" to ItemTag(
                            mapOf(
                                descriptionIdEffect, color, displayName, blendOutDurationTicks, blendOutAdvanceTicks, blendInDurationTicks, category
                            )
                        )


//                        val particleOptions = "particleOptions" to ItemTagData(it.particleOptions.type.toString())
                        ItemTag(mapOf(effects, amplifier, duration, descriptionId))
                    })
                }

                ConsumeEffect.a.REMOVE_EFFECTS -> {
                    ItemTagList((it as RemoveStatusEffectsConsumeEffect).effects.map {
                        val effect = it.value()
                        val descriptionId = "descriptionId" to ItemTagData(effect.descriptionId)
                        val color = "color" to ItemTagData(effect.color)
                        val displayName = "displayName" to ItemTagData(effect.displayName.string)
                        val blendInDurationTicks = "blendInDurationTicks" to ItemTagData(effect.blendInDurationTicks)
                        val blendOutAdvanceTicks = "blendOutAdvanceTicks" to ItemTagData(effect.blendOutAdvanceTicks)
                        val blendOutDurationTicks = "blendOutDurationTicks" to ItemTagData(effect.blendOutDurationTicks)
                        val category = "category" to ItemTagData(effect.category.tooltipFormatting.name)

                        ItemTag(
                            mapOf(
                                descriptionId, color, displayName, blendOutDurationTicks, blendOutAdvanceTicks, blendInDurationTicks, category
                            )
                        )
                    })
                }

                ConsumeEffect.a.PLAY_SOUND -> {
                    val sound = (it as PlaySoundConsumeEffect).sound.value()
                    val soundLocation = "location" to ItemTagData(sound.location.toString())
                    val soundFixedRange = "fixedRange" to ItemTagData(sound.fixedRange.get())
                    ItemTag(mapOf(soundFixedRange, soundLocation))

                }

                ConsumeEffect.a.TELEPORT_RANDOMLY -> ItemTagData((it as TeleportRandomlyConsumeEffect).diameter)

                else -> null
            }
        }
        return ItemTagList(deaths)
    }
}