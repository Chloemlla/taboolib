package taboolib.module.nms.remap

import taboolib.module.nms.MinecraftVersion

/**
 * 非混淆服务端（26.1+）的转译器
 *
 * 26.1 起 Minecraft 不再混淆，运行时类名即为 Mojang Deobf 名。
 * 但 NMSProxy 实现类（如 NMS19Impl）仍以 Spigot 类名编译（如 WorldServer、DataWatcher），
 * 需要通过 1.21.11 的 Paper 映射将 Spigot 类名转译为 Mojang 类名。
 * 方法名和字段名在 26.1+ 已与 Mojang Deobf 一致，无需额外转译。
 *
 * 注意：1.21.11 的映射表在 26.2+ 可能过时（如 EntityTypes 被拆分为独立的常量类），
 * 因此转译结果需要通过运行时类加载验证，失败时保留原名。
 *
 * @author mical
 * @since 2026/3/31 22:53
 */
class RemapTranslationUnobfuscated : RemapTranslation() {

    override fun translate(key: String): String {
        // obc
        // 非混淆服务端，只能处理 obc 的版本号了
        if (key.startsWith("org/bukkit/craftbukkit")) {
            // 若当前使用 Universal CraftBukkit 环境，则移除版本号
            return key.replace(obc1, if (MinecraftVersion.isUniversalCraftBukkit) obc3 else obc2)
        }
        // 将低版本版本化包名转为现代包路径（如 net/minecraft/server/v1_16_R1/X → net/minecraft/server/level/X），
        // 再统一通过 Paper 映射将 Spigot 类名转译为 Mojang 类名
        if (key.startsWith("net/minecraft/")) {
            val dotKey = key.replace('/', '.')
            // 先查 Paper 映射的完整类名（覆盖无版本号路径，如 WorldServer → ServerLevel）
            val mojangName = MinecraftVersion.paperMapping.classMapSpigotToMojang[dotKey]
            if (mojangName != null) {
                // 映射表可能因版本差异过时（如 1.21.11 EntityTypes → EntityType，但 26.2 中 EntityTypes 才是正确类名），
                // 转译结果必须通过运行时类加载验证，失败则回退到原名
                return resolveWithFallback(key, mojangName)
            }
            // 对含版本号的路径（net/minecraft/server/v1_...），通过短类名查找 Spigot 全名后再转 Mojang 名
            if (key.startsWith("net/minecraft/server/v1_")) {
                val simpleName = key.substringAfterLast('/')
                val spigotFullName = MinecraftVersion.paperMapping.classMapSpigotS2F[simpleName]
                if (spigotFullName != null) {
                    val mapped = MinecraftVersion.paperMapping.classMapSpigotToMojang[spigotFullName]
                    if (mapped != null) {
                        return resolveWithFallback(key, mapped)
                    }
                }
            }
        }
        return key
    }

    /**
     * 将映射后的 Mojang 类名转为 internal name 并验证运行时可用性。
     * 映射目标存在时返回转译结果，不存在但原名可用时保留原名，两者均不可用才返回转译结果。
     */
    fun resolveWithFallback(original: String, mojangName: String): String {
        val result = mojangName.replace('.', '/')
        if (hasRuntimeClass(mojangName)) {
            return result
        }
        if (hasRuntimeClass(original.replace('/', '.'))) {
            return original
        }
        return result
    }
}
