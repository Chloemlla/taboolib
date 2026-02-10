package taboolib.module.database

/**
 * PostgreSQL 数据设置
 *
 * @author sky
 * @since 2024-01-01
 */
enum class ColumnOptionPostgreSQL(val query: String) {

    /** 非空 */
    NOTNULL("NOT NULL"),

    /** 主键 */
    PRIMARY_KEY("PRIMARY KEY"),

    /** 唯一约束 */
    UNIQUE("UNIQUE"),

    /**
     * 普通索引标记
     * PostgreSQL 不支持内联索引声明，此选项不在列定义中输出，
     * 而是在建表后通过 CREATE INDEX 语句创建。
     */
    KEY("");
}
