package taboolib.module.nms.test

import org.bukkit.Bukkit
import taboolib.common.Test
import taboolib.common.platform.function.info
import taboolib.module.nms.MinecraftVersion
import taboolib.module.nms.NMSSign
import taboolib.module.nms.inputSign

/**
 * TabooLib
 * taboolib.test.nms_util.TestNMSSign
 *
 * @author 坏黑
 * @since 2024/9/8 00:56
 */
object TestNMSSign : Test() {

    override fun check(): List<Result> {
        val player = Bukkit.getOnlinePlayers().firstOrNull()
        return if (player != null) {
            listOf(
                sandbox("NMSSign:implementation") {
                    val expected = if (MinecraftVersion.isUnobfuscated) "NMSSignImpl26" else "NMSSignImpl"
                    check(NMSSign.instance.javaClass.simpleName == expected)
                },
                sandbox("NMSSign:inputSign()") {
                    player.inputSign(arrayOf("E2E")) {
                        info("输入 ${it.contentToString()}")
                        if (it.firstOrNull() == "E2E") {
                            info("[E2E-PROBE] SIGN_CALLBACK")
                        }
                    }
                },
            )
        } else {
            emptyList()
        }
    }
}
