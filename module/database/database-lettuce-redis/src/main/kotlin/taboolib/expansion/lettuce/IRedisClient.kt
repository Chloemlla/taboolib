package taboolib.expansion.lettuce

import java.util.concurrent.CompletableFuture

interface IRedisClient {

    /**
     * 启动 Redis 客户端
     * @param autoRelease 关服是否自动释放
     * */
    fun start(autoRelease: Boolean = true): CompletableFuture<Void>
}