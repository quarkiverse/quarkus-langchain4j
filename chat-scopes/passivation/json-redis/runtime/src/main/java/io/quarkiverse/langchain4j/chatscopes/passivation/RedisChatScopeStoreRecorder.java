package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RedisChatScopeStoreRecorder {
    public Function<SyntheticCreationalContext<RedisChatScopeStore>, RedisChatScopeStore> chatStoreFunction(
            String clientName) {
        return new Function<>() {
            @Override
            public RedisChatScopeStore apply(SyntheticCreationalContext<RedisChatScopeStore> context) {
                RedisDataSource dataSource;
                if (clientName == null) {
                    dataSource = context.getInjectedReference(RedisDataSource.class, new Default.Literal());
                } else {
                    dataSource = context.getInjectedReference(RedisDataSource.class,
                            new RedisClientName.Literal(clientName));
                }
                return new RedisChatScopeStore(dataSource);
            }
        };
    }
}
