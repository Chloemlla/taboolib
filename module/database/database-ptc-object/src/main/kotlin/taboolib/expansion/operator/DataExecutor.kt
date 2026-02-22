package taboolib.expansion.operator

import taboolib.module.database.Action
import taboolib.module.database.ActionSelect
import taboolib.module.database.Table
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * 数据执行器接口（internal）
 *
 * 将连接管理和 SQL 执行能力从 ContainerOperatorImpl 中抽象出来，
 * 供 LinkTableHandler 和 CollectionTableHandler 使用。
 */
internal interface DataExecutor {
    val dataSource: DataSource
    val table: Table<*, *>

    fun <T> withConnection(block: (Connection) -> T): T
    fun withTransaction(block: (Connection) -> Unit)
    fun <T> executeQuery(action: ActionSelect, handler: (ResultSet) -> T): T
    fun <T> executeQueryWith(conn: Connection, action: ActionSelect, handler: (ResultSet) -> T): T
    fun executeUpdate(action: Action): Int
    fun <T> executePrepared(conn: Connection, query: String, elements: List<Any?>, flags: Int = 0, block: PreparedStatement.() -> T): T
    fun convertParam(value: Any?): Any?
    fun setupQuoter()
}
