package io.quarkiverse.langchain4j.infinispan.runtime;

import java.util.function.Function;

import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class InfinispanEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<InfinispanEmbeddingStore>, InfinispanEmbeddingStore> embeddingStoreFunction(
            InfinispanEmbeddingStoreConfig config, String clientName) {
        return new Function<>() {
            @Override
            public InfinispanEmbeddingStore apply(SyntheticCreationalContext<InfinispanEmbeddingStore> context) {
                RemoteCacheManager cacheManager;
                if (clientName == null) {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class);
                } else {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class,
                            new InfinispanClientName.Literal(clientName));
                }
                return new InfinispanEmbeddingStore(cacheManager, InfinispanEmbeddingStoreConfig.toStoreConfig(config));
            }
        };
    }
}
