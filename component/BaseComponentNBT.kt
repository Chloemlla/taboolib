package taboolib.module.nms.component

import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.IChatBaseComponent
import net.minecraft.resources.MinecraftKey
import net.minecraft.world.ChestLock
import net.minecraft.world.entity.animal.EntityTropicalFish
import net.minecraft.world.food.FoodInfo
import net.minecraft.world.item.AdventureModePredicate
import net.minecraft.world.item.EnumColor
import net.minecraft.world.item.EnumItemRarity
import net.minecraft.world.item.JukeboxPlayable
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.component.*
import net.minecraft.world.item.component.InstrumentComponent
import net.minecraft.world.item.enchantment.Enchantable
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.item.enchantment.Repairable
import net.minecraft.world.item.equipment.Equippable
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.level.block.entity.BannerPatternLayers
import net.minecraft.world.level.block.entity.EnumBannerPatternType
import net.minecraft.world.level.block.entity.PotDecorations
import net.minecraft.world.level.saveddata.maps.MapId
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.NMSItemTag
import taboolib.module.nms.component.impl.*
import java.lang.reflect.Modifier

/**
 * TabooLib
 * taboolib.module.nms.base.BaseComponentNBT
 *
 * @author 晓劫
 * @since 2025/11/2 09:05
 */
interface BaseComponentNBT {

    fun getTagData(): ItemTagData

    fun itemTagToBukkitCopy(nbtTag: Any): ItemTagData {
        return NMSItemTag.instance.itemTagToBukkitCopy(nbtTag)
    }

    companion object {

        val components by lazy {
            try {
                DataComponents::class.java.declaredFields.filter {
                    Modifier.isStatic(it.modifiers) && it.type == DataComponentType::class.java
                }.map { it.get(null) as DataComponentType<*> }
            } catch (_: ClassNotFoundException) {
                emptyList()
            }
        }

        @JvmStatic
        fun of(value: Any): BaseComponentNBT {
            return when (value) {
                is IChatBaseComponent -> ChatComponent(value)
                is ItemLore -> ItemLoreComponent(value)

                is ItemEnchantments -> ItemEnchantmentsComponent(value)

                is MinecraftKey -> MinecraftKeyComponent(value)

                is FoodInfo -> FoodInfoComponent(value)
                is UseCooldown -> UseCooldownComponent(value)
                is TooltipDisplay -> TooltipDisplayComponent(value)
                is CustomData -> CustomDataComponent(value)

                is MapId -> MapIdComponent(value)
                is CustomModelData -> CustomModelDataComponent(value)
                is EnumItemRarity -> EnumItemRarityComponent(value)
                is AdventureModePredicate -> AdventureModePredicateComponent(value)
                is ItemAttributeModifiers -> ItemAttributeModifiersComponent(value)
                is Consumable -> ConsumableComponent(value)
                is UseRemainder -> UseRemainderComponent(value)
                is DamageResistant -> DamageResistantComponent(value)
                is Tool -> ToolComponent(value)
                is Weapon -> WeaponComponent(value)
                is Enchantable -> EnchantableComponent(value)
                is Equippable -> EquippableComponent(value)
                is Repairable -> RepairableComponent(value)
                is DeathProtection -> DeathProtectionComponent(value)
                is BlocksAttacks -> BlocksAttacksComponent(value)
                is DyedItemColor -> DyedItemColorComponent(value)
                is MapItemColor -> MapItemColorComponent(value)
                is MapDecorations -> MapDecorationsComponent(value)
//            TODO("paper又干坏事,把SoundEffect改成了SoundEvent")
                is Holder<*> -> HolderComponent(value)
                is MapPostProcessing -> MapPostProcessingComponent(value)
                is ChargedProjectiles -> ChargedProjectilesComponent(value)
                is BundleContents -> BundleContentsComponent(value)
                is PotionContents -> PotionContentsComponent(value)
                is SuspiciousStewEffects -> SuspiciousStewEffectsComponent(value)
                is WritableBookContent -> WritableBookContentComponent(value)
                is WrittenBookContent -> WrittenBookContentComponent(value)
                is ArmorTrim -> ArmorTrimComponent(value)
                is DebugStickState -> DebugStickStateComponent(value)
                is InstrumentComponent -> taboolib.module.nms.component.impl.InstrumentComponent(value)
                is ProvidesTrimMaterial -> ProvidesTrimMaterialComponent(value)
                is OminousBottleAmplifier -> OminousBottleAmplifierComponent(value)
                is JukeboxPlayable -> JukeboxPlayableComponent(value)
                is EnumBannerPatternType -> EnumBannerPatternTypeComponent(value)
                is LodestoneTracker -> LodestoneTrackerComponent(value)
                is FireworkExplosion -> FireworkExplosionComponent(value)
                is Fireworks -> FireworksComponent(value)
                is ResolvableProfile -> ResolvableProfileComponent(value)
                is BannerPatternLayers -> BannerPatternLayersComponent(value)
                is EnumColor -> EnumColorComponent(value)
                is PotDecorations -> PotDecorationsComponent(value)
                is ItemContainerContents -> ItemContainerContentsComponent(value)
                is BlockItemStateProperties -> BlockItemStatePropertiesComponent(value)
                is Bees -> BeesComponent(value)
                is ChestLock -> ChestLockComponent(value)
                is SeededContainerLoot -> SeededContainerLootComponent(value)
                is EntityTropicalFish.Variant -> EntityTropicalFishVariantComponent(value)

                else -> error("Unsupported type: ${value::class.java}")
            }
        }

    }

}