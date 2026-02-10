package taboolib.module.database

/**
 * PostgreSQL 数据类型
 *
 * @author sky
 * @since 2024-01-01
 */
enum class ColumnTypePostgreSQL(val isRequired: Boolean = false) {

    /** 2 字节有符号整数，-32768 to 32767 */
    SMALLINT,

    /** 4 字节有符号整数，-2,147,483,648 to 2,147,483,647 */
    INTEGER,

    /** 8 字节有符号整数 */
    BIGINT,

    /** 2 字节自增整数 */
    SMALLSERIAL,

    /** 4 字节自增整数 */
    SERIAL,

    /** 8 字节自增整数 */
    BIGSERIAL,

    /** 4 字节单精度浮点 */
    REAL,

    /** 8 字节双精度浮点，SQL 输出为 DOUBLE PRECISION */
    DOUBLE_PRECISION,

    /** 精确小数 NUMERIC(precision, scale) */
    NUMERIC(true),

    /** 定长字符串 CHAR(n) */
    CHAR(true),

    /** 变长字符串 VARCHAR(n) */
    VARCHAR(true),

    /** 无限长度文本 */
    TEXT,

    /** 二进制数据 */
    BYTEA,

    /** 布尔类型 */
    BOOLEAN,

    /** UUID 类型（原生支持） */
    UUID,

    /** 不带时区的时间戳 */
    TIMESTAMP,

    /** 带时区的时间戳 */
    TIMESTAMPTZ,

    /** JSON 文本存储 */
    JSON,

    /** JSON 二进制存储（支持索引） */
    JSONB,

    /** 日期（无时间） */
    DATE,

    /** 时间（无日期） */
    TIME,

    /** 哨兵值：未显式指定 PostgreSQL 类型，由框架根据 Kotlin 类型自动推断 */
    _DEFAULT;

    /** 获取 SQL 类型名称，处理 DOUBLE_PRECISION → DOUBLE PRECISION 等含空格的类型 */
    val sqlName: String
        get() = name.replace('_', ' ')

    operator fun invoke(name: String, parameter1: Int = 0, parameter2: Int = 0, func: ColumnPostgreSQL.() -> Unit = {}): ColumnPostgreSQL {
        return ColumnPostgreSQL(this, name).also {
            it.parameter[0] = parameter1
            it.parameter[1] = parameter2
            func(it)
        }
    }
}
