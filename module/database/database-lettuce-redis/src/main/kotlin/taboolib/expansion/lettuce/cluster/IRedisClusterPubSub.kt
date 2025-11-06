package taboolib.expansion.lettuce.cluster

import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.cluster.pubsub.api.reactive.RedisClusterPubSubReactiveCommands
import io.lettuce.core.cluster.pubsub.api.sync.RedisClusterPubSubCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import taboolib.expansion.lettuce.IRedisPubSub

interface IRedisClusterPubSub: IRedisPubSub {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useClusterPubSubCommands(block: (RedisClusterPubSubCommands<String, String>) -> T): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useClusterPubSubAsyncCommands(block: (RedisClusterPubSubAsyncCommands<String, String>) -> T): T?

    /**
     * 使用反应式命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useClusterPubSubReactiveCommands(block: (RedisClusterPubSubReactiveCommands<String, String>) -> T): T?

    override fun <T> usePubSubCommands(block: (RedisPubSubCommands<String, String>) -> T): T? {
        return useClusterPubSubCommands {
            block(it)
        }
    }

    override fun <T> usePubSubAsyncCommands(block: (RedisPubSubAsyncCommands<String, String>) -> T): T? {
        return useClusterPubSubAsyncCommands {
            block(it)
        }
    }

    override fun <T> usePubSubReactiveCommands(block: (RedisPubSubReactiveCommands<String, String>) -> T): T? {
        return useClusterPubSubReactiveCommands {
            block(it)
        }
    }
}