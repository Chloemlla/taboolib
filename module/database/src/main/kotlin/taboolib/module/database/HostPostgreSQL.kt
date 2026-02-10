package taboolib.module.database

import taboolib.library.configuration.ConfigurationSection

/**
 * PostgreSQL 数据库地址
 *
 * @author sky
 * @since 2024-01-01
 */
class HostPostgreSQL(
    val host: String,
    val port: String,
    val user: String,
    val password: String,
    val database: String,
    val schema: String = "public"
) : Host<PostgreSQL>() {

    val flags = arrayListOf<String>()

    val flagsURL: String
        get() = if (flags.isEmpty()) "" else "?${flags.joinToString("&")}"

    override val columnBuilder: ColumnBuilder
        get() = PostgreSQL()

    override val connectionUrl: String
        get() = "jdbc:postgresql://$host:$port/$database$flagsURL"

    override val connectionUrlSimple: String
        get() = "jdbc:postgresql://$host:$port/$database"

    override val driverClass: String
        get() = "org.postgresql.Driver"

    constructor(section: ConfigurationSection) : this(
        section.getString("host", "localhost")!!,
        section.getString("port", "5432")!!,
        section.getString("user", "postgres")!!,
        section.getString("password", "")!!,
        section.getString("database", "test")!!,
        section.getString("schema", "public")!!,
    )

    override fun toString(): String {
        return "HostPostgreSQL(host='$host', port='$port', user='$user', password='$password', database='$database', schema='$schema', connectionUrl='$connectionUrl')"
    }
}
