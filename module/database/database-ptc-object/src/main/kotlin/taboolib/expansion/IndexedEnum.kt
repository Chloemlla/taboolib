package taboolib.expansion

/**
 * 索引枚举接口
 *
 * 实现此接口的枚举类在数据库中将以 [index] 数值存储，而非枚举名称字符串。
 * 读取时自动通过 [index] 值匹配回对应的枚举常量。
 *
 * ```kotlin
 * enum class TypeEnum(override val index: Long, val desc: String) : IndexedEnum {
 *     TYPE1(1, "类型1"),
 *     TYPE2(2, "类型2"),
 *     TYPE3(3, "类型3"),
 * }
 * ```
 *
 * 数据库列类型：MySQL 使用 `BIGINT`，SQLite 使用 `INTEGER`。
 *
 * 在 WHERE 条件中传入枚举值时，自动使用 [index] 进行 SQL 参数拼接：
 * ```kotlin
 * homeMapper.findAll { "type_enum" eq TypeEnum.TYPE1 }
 * // 生成 SQL: SELECT * FROM ... WHERE `type_enum` = 1
 * ```
 */
interface IndexedEnum {

    /** 枚举在数据库中存储的数值索引 */
    val index: Long
}
