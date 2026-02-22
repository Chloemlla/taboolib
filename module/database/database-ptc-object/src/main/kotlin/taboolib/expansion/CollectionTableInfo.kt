package taboolib.expansion

import taboolib.expansion.orm.AnalyzedClassMember
import taboolib.module.database.Table

/**
 * 容器类型（List/Set/Map）关联表的元信息
 *
 * @param tableName 子表名称
 * @param fkColumnName 外键列名（引用主表的 @Id 列）
 * @param member 对应的容器成员
 * @param table 子表的 Table 对象
 */
data class CollectionTableInfo(
    val tableName: String,
    val fkColumnName: String,
    val member: AnalyzedClassMember,
    val table: Table<*, *>,
)
