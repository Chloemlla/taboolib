package taboolib.expansion

import java.io.Closeable
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * 游标查询结果，逐行从数据库读取数据，避免大量数据导致内存溢出。
 *
 * **必须在事务中使用**，以保证数据库连接不被断开：
 * ```kotlin
 * homeMapper.transaction {
 *     selectCursor { "world" eq "overworld" }.use { cursor ->
 *         for (home in cursor) {
 *             // 逐条处理，内存中始终只有一条数据
 *         }
 *     }
 * }
 * ```
 *
 * 也可以使用 [forEach] 便捷方法，自动管理资源释放：
 * ```kotlin
 * homeMapper.transaction {
 *     selectCursor { "world" eq "overworld" }.forEach { home ->
 *         // 处理每条数据
 *     }
 * }
 * ```
 *
 * @param statement 底层 PreparedStatement，游标关闭时释放
 * @param resultSet 底层 ResultSet，逐行读取
 * @param mapper 将 ResultSet 当前行映射为 T 的函数
 */
class Cursor<T>(
    private val statement: PreparedStatement,
    private val resultSet: ResultSet,
    private val mapper: (ResultSet) -> T
) : Iterable<T>, Closeable {

    private var closed = false
    private var iteratorCreated = false

    override fun iterator(): Iterator<T> {
        check(!closed) { "Cursor 已关闭" }
        check(!iteratorCreated) { "Cursor 只能迭代一次" }
        iteratorCreated = true
        return CursorIterator()
    }

    override fun close() {
        if (!closed) {
            closed = true
            runCatching { resultSet.close() }
            runCatching { statement.close() }
        }
    }

    /**
     * 逐条处理游标数据，处理完毕后自动关闭游标。
     *
     * ```kotlin
     * selectCursor { "world" eq "overworld" }.forEach { home ->
     *     println(home)
     * }
     * ```
     */
    inline fun forEach(action: (T) -> Unit) {
        use { cursor ->
            for (item in cursor) {
                action(item)
            }
        }
    }

    private inner class CursorIterator : Iterator<T> {

        private var hasNextCalled = false
        private var hasNextResult = false

        override fun hasNext(): Boolean {
            if (!hasNextCalled) {
                hasNextResult = resultSet.next()
                hasNextCalled = true
                if (!hasNextResult) {
                    close()
                }
            }
            return hasNextResult
        }

        override fun next(): T {
            if (!hasNextCalled) {
                if (!resultSet.next()) {
                    close()
                    throw NoSuchElementException()
                }
            }
            hasNextCalled = false
            return mapper(resultSet)
        }
    }
}
