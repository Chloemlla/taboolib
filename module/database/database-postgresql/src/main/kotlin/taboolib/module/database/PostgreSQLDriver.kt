package taboolib.module.database

import taboolib.common.Inject
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency

@Inject
@RuntimeDependencies(
    RuntimeDependency(
        "!org.postgresql:postgresql:42.7.9",
        test = "!org.postgresql.Driver",
        transitive = false
    ),
)
object PostgreSQLDriver
