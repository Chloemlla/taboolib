package taboolib.expansion.container

import taboolib.module.database.*
import java.io.File

class ContainerSQLite(file: File) : Container<SQLite>(HostSQLite(file)) {

    override val dialect: DatabaseDialect = SQLiteDialect
}
