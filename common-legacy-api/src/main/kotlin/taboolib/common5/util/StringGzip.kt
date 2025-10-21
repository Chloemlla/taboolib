package taboolib.common5.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 将字节数组使用 GZIP 压缩
 *
 * @param bufferSize 缓冲区大小，默认为 1024 字节
 * @return 压缩后的字节数组
 */
fun ByteArray.gzip(bufferSize: Int = 1024): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { gzip ->
        gzip.write(this)
        gzip.finish()
    }
    return output.toByteArray()
}

/**
 * 将字符串使用 GZIP 压缩
 *
 * @param bufferSize 缓冲区大小，默认为 1024 字节
 * @return 压缩后的字节数组
 */
fun String.gzip(bufferSize: Int = 1024): ByteArray {
    return toByteArray(StandardCharsets.UTF_8).gzip(bufferSize)
}

/**
 * 将 GZIP 压缩的字节数组解压缩
 *
 * @param bufferSize 缓冲区大小，默认为 1024 字节
 * @return 解压缩后的字节数组
 */
fun ByteArray.ungzip(bufferSize: Int = 1024): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPInputStream(ByteArrayInputStream(this)).use { gzip ->
        val buffer = ByteArray(bufferSize)
        var len: Int
        while (gzip.read(buffer).also { len = it } != -1) {
            output.write(buffer, 0, len)
        }
    }
    return output.toByteArray()
}

/**
 * 将 GZIP 压缩的字节数组解压缩为字符串
 *
 * @param bufferSize 缓冲区大小，默认为 1024 字节
 * @return 解压缩后的字符串（UTF-8 编码）
 */
fun ByteArray.ungzipToString(bufferSize: Int = 1024): String {
    return String(ungzip(bufferSize), StandardCharsets.UTF_8)
}