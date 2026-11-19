package io.quarkiverse.langchain4j.infinispan.runtime;

import java.util.function.Function;

import org.infinispan.client.hotrod.RemoteCacheManager;

import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class InfinispanEmbeddingStoreRecorder {
    private final RuntimeValue<InfinispanEmbeddingStoreConfig> runtimeConfig;

    public InfinispanEmbeddingStoreRecorder(RuntimeValue<InfinispanEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<InfinispanEmbeddingStore>, InfinispanEmbeddingStore> embeddingStoreFunction(
            String clientName, String storeName) {
        return new Function<>() {
            @Override
            public InfinispanEmbeddingStore apply(SyntheticCreationalContext<InfinispanEmbeddingStore> context) {
                InfinispanEmbeddingStore.Builder builder = new InfinispanEmbeddingStore.Builder();
                RemoteCacheManager cacheManager;
                if (clientName == null || NamedConfigUtil.isDefault(clientName)) {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class);
                } else {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class,
                            new InfinispanClientName.Literal(clientName));
                }
                builder.cacheManager(cacheManager);
                InfinispanStoreRuntimeConfig config = correspondingStoreConfig(storeName);
                if (config.dimension().isEmpty()) {
                    throw new ConfigValidationException(createDimensionConfigProblems(storeName));
                }
                builder.schema(new InfinispanSchema(config.cacheName(), config.dimension().get(),
                        config.distance(), config.similarity(), config.createCache(),
                        config.cacheConfig().orElse(null)));
                return builder.build();
            }
        };
    }

    private InfinispanStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private ConfigValidationException.Problem[] createDimensionConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] { new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.infinispan%sdimension is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + "."))) };
    }
}
