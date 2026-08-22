package taboolib.module.incision.bridge

import io.izzel.incision.bridge.IncisionBridge
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 验证多个隔离后端共享唯一 native owner 时，字节码必须按 delegate 注册顺序串联处理。
 * 后一个 transformer 接收前一个的输出，任何插件都不能覆盖或跳过另一个插件的织入。
 */
class IncisionBridgeNativeRoutingTest {

    @Test
    fun nativeDelegatesTransformSequentially() {
        IncisionBridge.registerNativeBackend(FirstBackend::class.java, true)
        IncisionBridge.registerNativeBackend(SecondBackend::class.java, false)
        try {
            val transformed = IncisionBridge.transformNative(null, "example/Target", byteArrayOf(1))
            assertArrayEquals(byteArrayOf(1, 2, 3), transformed)
            assertEquals("loadedClassCount:example/Target", IncisionBridge.invokeNative("loadedClassCount", arrayOf("example/Target")))
        } finally {
            IncisionBridge.unregisterNativeBackend(FirstBackend::class.java)
            IncisionBridge.unregisterNativeBackend(SecondBackend::class.java)
        }
    }

    object FirstBackend {
        @JvmStatic fun onSharedClassFileLoad(loader: ClassLoader?, name: String, bytes: ByteArray) = bytes + 2
        @JvmStatic fun sharedNativeInvoke(operation: String, args: Array<Any?>): Any? =
            if (operation == "dispose") null else "$operation:${args[0]}"
    }

    object SecondBackend {
        @JvmStatic fun onSharedClassFileLoad(loader: ClassLoader?, name: String, bytes: ByteArray) = bytes + 3
    }
}
