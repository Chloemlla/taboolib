import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import taboolib.common5.Demand
import taboolib.common5.Demand.Companion.toDemand

/**
 * Demand 类的单元测试
 * 
 * 测试 Demand 类的各种功能，包括：
 * - 解析命名空间
 * - 解析键值对
 * - 解析普通参数
 * - 解析标签
 * - 处理带引号的字符串
 * - 处理转义字符
 */
class DemandTest {

    @Test
    @DisplayName("测试基本解析功能")
    fun testBasicParsing() {
        val demand = Demand("command arg1 arg2 -key1 value1 -key2 value2 --tag1 --tag2")
        
        // 测试命名空间
        assertEquals("command", demand.namespace)
        
        // 测试普通参数
        assertEquals("arg1", demand.get(0))
        assertEquals("arg2", demand.get(1))
        assertNull(demand.get(2))  // 不存在的参数应返回 null
        
        // 测试键值对
        assertEquals("value1", demand.get("key1"))
        assertEquals("value2", demand.get("key2"))
        assertNull(demand.get("key3"))  // 不存在的键应返回 null
        
        // 测试标签
        assertTrue("tag1" in demand.tags)
        assertTrue("tag2" in demand.tags)
        assertFalse("tag3" in demand.tags)  // 不存在的标签
    }
    
    @Test
    @DisplayName("测试带引号的字符串解析")
    fun testQuotedStringParsing() {
        val demand = Demand("command arg1 \"arg with spaces\" -key1 \"value with spaces\"")
        
        // 测试带空格的键值对
        assertEquals("value with spaces", demand.get("key1"))
        
        // 测试带空格的普通参数
        assertEquals("arg1", demand.get(0))
        assertEquals("arg with spaces", demand.get(1))
    }
    
    @Test
    @DisplayName("测试转义字符处理")
    fun testEscapeCharacters() {
        val demand = Demand("command -key \"value with \\\"quotes\\\"\"")
        
        // 测试带转义引号的值
        assertEquals("value with \"quotes\"", demand.get("key"))
    }
    
    @Test
    @DisplayName("测试复杂场景")
    fun testComplexScenario() {
        val demand = Demand("command arg1 -key1 value1 --tag1 arg2 -key2 \"complex value with spaces\" --tag2 -key3 \"value with \\\"quotes\\\"\"")
        
        // 测试命名空间
        assertEquals("command", demand.namespace)
        
        // 测试普通参数
        assertEquals("arg1", demand.get(0))
        assertEquals("arg2", demand.get(1))
        
        // 测试键值对
        assertEquals("value1", demand.get("key1"))
        assertEquals("complex value with spaces", demand.get("key2"))
        assertEquals("value with \"quotes\"", demand.get("key3"))
        
        // 测试标签
        assertTrue("tag1" in demand.tags)
        assertTrue("tag2" in demand.tags)
        
        // 测试 get 方法的多键版本
        assertEquals("value1", demand.get(listOf("key1", "nonexistent")))
        assertEquals("default", demand.get(listOf("nonexistent1", "nonexistent2"), "default"))
    }
    
    @Test
    @DisplayName("测试边界情况")
    fun testEdgeCases() {
        // 空字符串
        val emptyDemand = Demand("")
        assertEquals("", emptyDemand.namespace)
        assertTrue(emptyDemand.args.isEmpty())
        assertTrue(emptyDemand.dataMap.isEmpty())
        assertTrue(emptyDemand.tags.isEmpty())
        
        // 只有命名空间
        val namespaceOnlyDemand = Demand("command")
        assertEquals("command", namespaceOnlyDemand.namespace)
        assertTrue(namespaceOnlyDemand.args.isEmpty())
        assertTrue(namespaceOnlyDemand.dataMap.isEmpty())
        assertTrue(namespaceOnlyDemand.tags.isEmpty())
        
        // 只有标签
        val tagsOnlyDemand = Demand("command --tag1 --tag2")
        assertEquals("command", tagsOnlyDemand.namespace)
        assertTrue(tagsOnlyDemand.args.isEmpty())
        assertTrue(tagsOnlyDemand.dataMap.isEmpty())
        assertEquals(2, tagsOnlyDemand.tags.size)
        
        // 未闭合的引号（这种情况下的行为取决于实现）
        val unclosedQuoteDemand = Demand("command -key \"unclosed quote")
        assertEquals("command", unclosedQuoteDemand.namespace)
        // 注意：这里的行为可能因实现而异，可能需要根据实际行为调整断言
    }
    
    @Test
    @DisplayName("测试 toDemand 扩展函数")
    fun testToDemandExtension() {
        val demand = "command arg1 -key value --tag".toDemand()
        
        assertEquals("command", demand.namespace)
        assertEquals("arg1", demand.get(0))
        assertEquals("value", demand.get("key"))
        assertTrue("tag" in demand.tags)
    }
    
    @Test
    @DisplayName("测试参数中包含未转义的连字符")
    fun testUnescapedHyphen() {
        // 测试参数中包含连字符
        val demandWithHyphenInArg = Demand("command arg-with-hyphen -key1 value1")
        assertEquals("arg-with-hyphen", demandWithHyphenInArg.get(0))
        
        // 测试键值中包含连字符
        val demandWithHyphenInValue = Demand("command -key1 value-with-hyphen")
        assertEquals("value-with-hyphen", demandWithHyphenInValue.get("key1"))
        
        // 测试引号内的连字符
        val demandWithHyphenInQuotes = Demand("command -key1 \"value with - hyphen\"")
        assertEquals("value with - hyphen", demandWithHyphenInQuotes.get("key1"))
        
        // 测试连字符开头的值（可能被误解为新的键）
        val demandWithHyphenAtStart = Demand("command -key1 -value-starts-with-hyphen")
        // 这种情况下，"-value-starts-with-hyphen" 会被解析为一个新的键，
        // 而 key1 没有值会被转换为普通参数，value-starts-with-hyphen 在最后也会被转换为普通参数
        assertEquals(2, demandWithHyphenAtStart.args.size)
        assertEquals("-key1", demandWithHyphenAtStart.get(0))
        assertEquals("-value-starts-with-hyphen", demandWithHyphenAtStart.get(1))
        assertTrue(demandWithHyphenAtStart.dataMap.isEmpty())

        // 测试使用引号包裹以连字符开头的值
        val demandWithQuotedHyphen = Demand("command -key1 \"-value-with-hyphen\"")
        assertEquals("-value-with-hyphen", demandWithQuotedHyphen.get("key1"))
    }

    @Test
    @DisplayName("测试没有值的key被转换为args")
    fun testKeyWithoutValueConvertedToArgs() {
        // 测试负数被转换为args
        val demandWithNegativeNumber = Demand("namespace -60")
        assertEquals("namespace", demandWithNegativeNumber.namespace)
        assertEquals(1, demandWithNegativeNumber.args.size)
        assertEquals("-60", demandWithNegativeNumber.get(0))
        assertTrue(demandWithNegativeNumber.dataMap.isEmpty())

        // 测试连续的key被转换为args
        val demandWithConsecutiveKeys = Demand("namespace -a -b")
        assertEquals("namespace", demandWithConsecutiveKeys.namespace)
        assertEquals(2, demandWithConsecutiveKeys.args.size)
        assertEquals("-a", demandWithConsecutiveKeys.get(0))
        assertEquals("-b", demandWithConsecutiveKeys.get(1))
        assertTrue(demandWithConsecutiveKeys.dataMap.isEmpty())

        // 测试混合情况
        val demandMixed = Demand("namespace arg1 -60 -key value")
        assertEquals("namespace", demandMixed.namespace)
        assertEquals(2, demandMixed.args.size)
        assertEquals("arg1", demandMixed.get(0))
        assertEquals("-60", demandMixed.get(1))
        assertEquals("value", demandMixed.get("key"))
    }

    @Test
    @DisplayName("性能测试：一万次复杂解析")
    fun testPerformanceWith10kIterations() {
        // 准备多个复杂的测试用例
        val testCases = listOf(
            "command arg1 arg2 -key1 value1 -key2 \"value with spaces\" --tag1 --tag2",
            "namespace arg1 -60 -key value --tag",
            "complex -a -b -c value3 \"quoted arg\" --tag1 --tag2 --tag3 arg4 arg5",
            "game player1 player2 -world \"My World\" -x 100 -y 64 -z -200 --force --silent",
            "cmd \"arg with spaces\" -key1 \"value1\" -key2 \"value2\" --tag arg2 -key3 value3"
        )

        val iterations = 10000
        val startTime = System.currentTimeMillis()

        // 执行一万次解析
        repeat(iterations) { i ->
            val testCase = testCases[i % testCases.size]
            val demand = Demand(testCase)

            // 验证基本解析正确性（每1000次验证一次避免影响性能）
            if (i % 1000 == 0) {
                assertNotNull(demand.namespace)
                assertTrue(demand.namespace.isNotEmpty() || testCase.isEmpty())
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations

        println("性能测试结果:")
        println("- 总迭代次数: $iterations")
        println("- 总耗时: ${duration}ms")
        println("- 平均每次解析耗时: ${String.format("%.4f", avgTime)}ms")
        println("- 每秒可处理: ${String.format("%.0f", 1000.0 / avgTime)} 次解析")

        // 性能断言：平均每次解析应该少于1ms（可以根据实际情况调整）
        assertTrue(avgTime < 1.0, "平均解析时间过长: ${avgTime}ms")
    }
}

/**
 * 简单的 main 函数测试，用于快速验证 Demand 类的功能
 */
fun main() {
    // 创建一个 Demand 实例并测试其功能
    val demand = Demand("command arg1 arg2 -key1 value1 -key2 \"value with spaces\" --tag1 --tag2")
    
    println("命名空间: ${demand.namespace}")
    println("参数列表: ${demand.args}")
    println("键值对: ${demand.dataMap}")
    println("标签: ${demand.tags}")
    
    println("\n获取特定参数:")
    println("arg[0]: ${demand.get(0)}")
    println("arg[1]: ${demand.get(1)}")
    println("arg[2]: ${demand.get(2, "默认值")}")
    
    println("\n获取特定键值:")
    println("key1: ${demand.get("key1")}")
    println("key2: ${demand.get("key2")}")
    println("key3: ${demand.get("key3", "默认值")}")
    
    println("\n测试多键获取:")
    println("key1 或 key3: ${demand.get(listOf("key1", "key3"))}")
    println("key3 或 key4 (带默认值): ${demand.get(listOf("key3", "key4"), "默认值")}")
    
    println("\n完整的 Demand 对象:")
    println(demand)
}