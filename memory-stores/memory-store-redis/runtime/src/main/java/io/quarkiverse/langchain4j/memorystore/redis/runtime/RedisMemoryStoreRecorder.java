package io.quarkiverse.langchain4j.memorystore.redis.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkiverse.langchain4j.memorystore.RedisChatMemoryStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RedisMemoryStoreRecorder {
    public Function<SyntheticCreationalContext<RedisChatMemoryStore>, RedisChatMemoryStore> chatMemoryStoreFunction(
            String clientName) {
        return new Function<>() {
            @Override
            public RedisChatMemoryStore apply(SyntheticCreationalContext<RedisChatMemoryStore> context) {
                RedisDataSource dataSource;
                if (clientName == null) {
                    dataSource = context.getInjectedReference(RedisDataSource.class, new Default.Literal());
                } else {
                    dataSource = context.getInjectedReference(RedisDataSource.class,
                            new RedisClientName.Literal(clientName));
                }
                return new RedisChatMemoryStore(dataSource);
            }
        };
    }
}
