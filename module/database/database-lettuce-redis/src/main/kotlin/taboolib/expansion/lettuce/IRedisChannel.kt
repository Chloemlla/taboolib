package taboolib.expansion.lettuce

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import java.util.concurrent.CompletableFuture

interface IRedisChannel {
    
    // 同步
    fun <T> useConnection(
        use: ((StatefulRedisConnection<String, String>) -> T)? = null,
        useCluster: ((StatefulRedisClusterConnection<String, String>) -> T)? = null,
    ): T?

    // 异步
    fun <T> useAsyncConnection(
        use: ((StatefulRedisConnection<String, String>) -> T)? = null,
        useCluster: ((StatefulRedisClusterConnection<String, String>) -> T)? = null,
    ): CompletableFuture<T?>
}