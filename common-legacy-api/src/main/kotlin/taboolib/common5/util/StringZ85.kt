package taboolib.common5.util

import java.nio.charset.StandardCharsets

/**
 * Z85 编码字母表
 */
private const val Z85_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-:+=^!/*?&<>()[]{}@%$#"

/**
 * Z85 解码映射表
 */
private val Z85_DECODER = IntArray(96).apply {
    for (i in Z85_ALPHABET.indices) {
        this[Z85_ALPHABET[i].code - 32] = i
    }
}

/**
 * 将字节数组编码为 Z85 字符串
 *
 * @param autoPad 当输入大小不是4的倍数时，是否自动填充。默认为 false
 * @return Z85 编码后的字符串
 * @throws IllegalArgumentException 当输入大小不是 4 的倍数且 autoPad 为 false 时抛出
 */
fun ByteArray.encodeZ85(autoPad: Boolean = false): String {
    // 如果开启自动填充且大小不是 4 的倍数，则进行填充
    if (autoPad && size % 4 != 0) {
        return encodeZ85Padded()
    }

    require(size % 4 == 0) { "输入大小必须是 4 的倍数，当前为 $size 字节。可以设置 autoPad=true 来自动填充" }

    val output = StringBuilder(size * 5 / 4)
    var index = 0
    while (index < size) {
        // 将 4 个字节组合成一个 32 位值
        var value = 0L
        for (i in 0..3) {
            value = value * 256 + (this[index++].toInt() and 0xFF)
        }
        // 将 32 位值转换为 5 个 Z85 字符
        var divisor = 85L * 85L * 85L * 85L
        for (i in 0..4) {
            output.append(Z85_ALPHABET[(value / divisor % 85).toInt()])
            divisor /= 85
        }
    }
    return output.toString()
}

/**
 * 将字符串编码为 Z85 字符串
 *
 * @param autoPad 当输入大小不是4的倍数时，是否自动填充。默认为 false
 * @return Z85 编码后的字符串
 * @throws IllegalArgumentException 当输入大小不是 4 的倍数且 autoPad 为 false 时抛出
 */
fun String.encodeZ85(autoPad: Boolean = false): String {
    return toByteArray().encodeZ85(autoPad)
}

/**
 * 将字节数组编码为 Z85 字符串（自动填充）
 * 自动将输入填充至 4 的倍数
 *
 * @return Z85 编码后的字符串
 */
fun ByteArray.encodeZ85Padded(): String {
    val padding = (4 - size % 4) % 4
    if (padding == 0) return encodeZ85()
    val padded = ByteArray(size + padding)
    System.arraycopy(this, 0, padded, 0, size)
    return padded.encodeZ85()
}

/**
 * 将字符串编码为 Z85 字符串（自动填充）
 * 自动将输入填充至4的倍数
 *
 * @return Z85 编码后的字符串
 */
fun String.encodeZ85Padded(): String {
    return toByteArray().encodeZ85Padded()
}

/**
 * 将 Z85 字符串解码为字节数组
 *
 * @return 解码后的字节数组
 * @throws IllegalArgumentException 当输入长度不是 5 的倍数或包含非法字符时抛出
 */
fun String.decodeZ85(): ByteArray {
    require(length % 5 == 0) { "输入长度必须是 5 的倍数，当前为 $length 个字符" }

    val output = ByteArray(length * 4 / 5)
    var outputIndex = 0
    var index = 0

    while (index < length) {
        // 将 5 个 Z85 字符转换为 32 位值
        var value = 0L
        for (i in 0..4) {
            val code = this[index++].code - 32
            require(code in 0..95 && Z85_DECODER[code] != -1) {
                "非法的 Z85 字符: ${this[index - 1]}"
            }
            value = value * 85 + Z85_DECODER[code]
        }
        // 将 32 位值拆分为 4 个字节
        var divisor = 256L * 256L * 256L
        for (i in 0..3) {
            output[outputIndex++] = (value / divisor % 256).toByte()
            divisor /= 256
        }
    }
    return output
}

/**
 * 将 Z85 字符串解码为字节数组并移除填充
 *
 * @param originalSize 原始数据的大小（移除填充后的大小）
 * @return 解码后的字节数组
 * @throws IllegalArgumentException 当原始大小大于解码后的大小时抛出
 */
fun String.decodeZ85Padded(originalSize: Int): ByteArray {
    val decoded = decodeZ85()
    if (originalSize == decoded.size) return decoded
    require(originalSize <= decoded.size) { "原始大小不能大于解码后的大小" }
    return decoded.copyOf(originalSize)
}

/**
 * 将 Z85 字符串解码为字符串（UTF-8编码）
 *
 * @return 解码后的字符串
 */
fun String.decodeZ85ToString(): String {
    return String(decodeZ85(), StandardCharsets.UTF_8)
}

/**
 * 将 Z85 字符串解码为字符串（UTF-8编码）
 *
 * @param originalSize 原始数据的大小（移除填充后的大小）
 * @return 解码后的字符串
 */
fun String.decodeZ85ToString(originalSize: Int): String {
    return String(decodeZ85Padded(originalSize), StandardCharsets.UTF_8)
}