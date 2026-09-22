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
                return mojangName.replace('.', '/')
            }
            // 对含版本号的路径（net/minecraft/server/v1_...），通过短类名查找 Spigot 全名后再转 Mojang 名
            if (key.startsWith("net/minecraft/server/v1_")) {
                val simpleName = key.substringAfterLast('/')
                val spigotFullName = MinecraftVersion.paperMapping.classMapSpigotS2F[simpleName]
                if (spigotFullName != null) {
                    val mapped = MinecraftVersion.paperMapping.classMapSpigotToMojang[spigotFullName]
                    if (mapped != null) {
                        return mapped.replace('.', '/')
                    }
                }
            }
        }
        return key
    }
}
