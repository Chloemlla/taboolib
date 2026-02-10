package taboolib.expansion

/**
 * 分页查询结果
 *
 * @param content 当前页数据
 * @param page 当前页码（从 1 开始）
 * @param size 每页大小
 * @param total 总记录数
 */
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val total: Long
) {

    /** 总页数 */
    val totalPages: Int
        get() = if (size > 0) ((total + size - 1) / size).toInt() else 0

    /** 是否有下一页 */
    val hasNext: Boolean
        get() = page < totalPages

    /** 是否有上一页 */
    val hasPrevious: Boolean
        get() = page > 1
}
