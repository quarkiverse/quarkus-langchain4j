package io.quarkiverse.langchain4j.memorystore.infinispan.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkiverse.langchain4j.memorystore.InfinispanChatMemoryStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class InfinispanMemoryStoreRecorder {
    public Function<SyntheticCreationalContext<InfinispanChatMemoryStore>, InfinispanChatMemoryStore> chatMemoryStoreFunction(
            String clientName, String cacheName, String cacheConfig, boolean createCache) {
        return new Function<>() {
            @Override
            public InfinispanChatMemoryStore apply(SyntheticCreationalContext<InfinispanChatMemoryStore> context) {
                RemoteCacheManager cacheManager;
                if (clientName == null) {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class, new Default.Literal());
                } else {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class,
                            new InfinispanClientName.Literal(clientName));
                }
                return new InfinispanChatMemoryStore(cacheManager, cacheName, cacheConfig, createCache);
            }
        };
    }
}
