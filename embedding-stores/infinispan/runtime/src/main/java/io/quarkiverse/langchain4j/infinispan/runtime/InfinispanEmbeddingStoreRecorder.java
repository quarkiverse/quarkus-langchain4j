package io.quarkiverse.langchain4j.infinispan.runtime;

import java.util.function.Function;

import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class InfinispanEmbeddingStoreRecorder {
    private final RuntimeValue<InfinispanEmbeddingStoreConfig> runtimeConfig;

    public InfinispanEmbeddingStoreRecorder(RuntimeValue<InfinispanEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<InfinispanEmbeddingStore>, InfinispanEmbeddingStore> embeddingStoreFunction(
            String clientName) {
        return new Function<>() {
            @Override
            public InfinispanEmbeddingStore apply(SyntheticCreationalContext<InfinispanEmbeddingStore> context) {
                InfinispanEmbeddingStore.Builder builder = new InfinispanEmbeddingStore.Builder();
                RemoteCacheManager cacheManager;
                if (clientName == null) {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class);
                } else {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class,
                            new InfinispanClientName.Literal(clientName));
                }
                builder.cacheManager(cacheManager);
                builder.schema(new InfinispanSchema(runtimeConfig.getValue().cacheName(), runtimeConfig.getValue().dimension(),
                        runtimeConfig.getValue().distance()));
                return builder.build();
            }
        };
    }
}
