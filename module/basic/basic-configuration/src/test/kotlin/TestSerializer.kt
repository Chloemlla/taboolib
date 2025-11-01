import taboolib.module.configuration.Configuration
import java.util.*

fun main() {
    // 测试不使用注解的 Map 和 UUID 自动转换
    val test = Test()
    println("原始对象: $test")

    val serialized = Configuration.serialize(test)
    println("\n序列化结果:")
    println(serialized)

    println("\n序列化为 YAML 字符串:")
    println(serialized.toString())
}

data class Test(
    // 不再需要 @Conversion 注解，Map 会自动使用 MapConverter
    val mp: MutableMap<String, Int> = mutableMapOf("a" to 1, "b" to 2),
    // UUID 也会自动使用 UUIDConverter
    val id: UUID = UUID.randomUUID(),
    val name: String = "test"
)