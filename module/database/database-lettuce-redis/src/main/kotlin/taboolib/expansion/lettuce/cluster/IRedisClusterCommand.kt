package taboolib.expansion.lettuce.cluster

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands
import io.lettuce.core.cluster.api.sync.RedisClusterCommands
import java.util.concurrent.CompletableFuture

interface IRedisClusterCommand {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useCommands(block: (RedisClusterCommands<String, String>) -> T): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useAsyncCommands(block: (RedisClusterAsyncCommands<String, String>) -> T): CompletableFuture<T?>

    /**
     * 使用反应式命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useReactiveCommands(block: (RedisClusterReactiveCommands<String, String>) -> T): CompletableFuture<T?>
}