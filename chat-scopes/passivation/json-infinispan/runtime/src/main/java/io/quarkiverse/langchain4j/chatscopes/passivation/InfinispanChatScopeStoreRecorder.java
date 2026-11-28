package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus recorder that creates the {@link InfinispanChatScopeStore} bean at runtime,
 * wiring the Infinispan cache manager and cache configuration together.
 */
@Recorder
public class InfinispanChatScopeStoreRecorder {

    /**
     * Returns a factory function that creates an {@link InfinispanChatScopeStore}
     * using the specified Infinispan client and cache settings.
     *
     * @param clientName the name of the Infinispan client, or {@code null} for the default client
     * @param cacheName the name of the cache, or {@code null} to use the default
     * @param cacheConfig optional XML/JSON cache configuration, or {@code null} to use the default
     * @param createCache whether to create the cache if it does not exist
     */
    public Function<SyntheticCreationalContext<InfinispanChatScopeStore>, InfinispanChatScopeStore> chatStoreFunction(
            String clientName, String cacheName, String cacheConfig, boolean createCache) {
        return new Function<>() {
            @Override
            public InfinispanChatScopeStore apply(SyntheticCreationalContext<InfinispanChatScopeStore> context) {
                RemoteCacheManager cacheManager;
                if (clientName == null) {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class, new Default.Literal());
                } else {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class,
                            new InfinispanClientName.Literal(clientName));
                }
                return new InfinispanChatScopeStore(cacheManager, cacheName, cacheConfig, createCache);
            }
        };
    }
}
