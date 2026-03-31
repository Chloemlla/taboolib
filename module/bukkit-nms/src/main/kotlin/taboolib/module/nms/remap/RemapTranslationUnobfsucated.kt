package taboolib.module.nms.remap

/**
 * TabooLib
 * taboolib.module.nms.remap.RemapTranslationUnobfsucated
 *
 * @author mical
 * @since 2026/3/31 22:53
 */
class RemapTranslationUnobfuscated : RemapTranslation() {

    override fun map(internalName: String): String {
        // 非混淆版本服务端不进行任何转译
        return internalName
    }
}