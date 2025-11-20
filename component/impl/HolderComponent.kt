package taboolib.module.nms.component.impl

import net.minecraft.core.Holder
import net.minecraft.resources.MinecraftKey
import net.minecraft.sounds.SoundEffect
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.component.BaseComponentNBT
import java.util.*
import kotlin.jvm.optionals.getOrDefault

/**
 * TabooLib
 * taboolib.module.nms.impl.HolderComponent
 *
 * @author 晓劫
 * @since 2025/11/2 09:42
 */
class HolderComponent(val holder: Holder<*>) : BaseComponentNBT {

    override fun getTagData(): ItemTagData {
        val value = try {
            holder.value()
        } catch (_: NoSuchMethodError) {
            holder.invokeMethod<Any>("value")!!
        }
        return when (value) {
            is SoundEffect -> getSoundEffect(value)

            else -> error("Unsupported holder type: ${value.javaClass}")
        }
    }

    fun getSoundEffect(value: SoundEffect): ItemTagData {
        val loca = try {
            value.location
        } catch (_: NoSuchMethodError) {
            value.invokeMethod<MinecraftKey>("location")!!
        }
        val range = try {
            value.fixedRange
        } catch (_: NoSuchMethodError) {
            value.invokeMethod<Optional<Float>>("fixedRange")!!
        }.getOrDefault(0F)
        val location = "location" to ItemTagData(loca.toString())
        val fixed = "fixedRange" to ItemTagData(range)
        return ItemTag(mapOf(location, fixed))
    }

}