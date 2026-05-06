package io.quarkiverse.langchain4j.redis.runtime;

import java.util.Collections;
import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class RedisEmbeddingStoreRecorder {
    private final RuntimeValue<RedisEmbeddingStoreConfig> runtimeConfig;

    public RedisEmbeddingStoreRecorder(RuntimeValue<RedisEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<RedisEmbeddingStore>, RedisEmbeddingStore> embeddingStoreFunction(
            String clientName, String storeName) {
        return new Function<>() {
            @Override
            public RedisEmbeddingStore apply(SyntheticCreationalContext<RedisEmbeddingStore> context) {
                RedisEmbeddingStore.Builder builder = new RedisEmbeddingStore.Builder();
                ReactiveRedisDataSource dataSource;
                if (clientName == null || NamedConfigUtil.isDefault(clientName)) {
                    dataSource = context.getInjectedReference(ReactiveRedisDataSource.class, new Default.Literal());
                } else {
                    dataSource = context.getInjectedReference(ReactiveRedisDataSource.class,
                            new RedisClientName.Literal(clientName));
                }
                builder.dataSource(dataSource);

                RedisStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                if (storeConfig.dimension().isEmpty()) {
                    throw new ConfigValidationException(createDimensionConfigProblems(storeName));
                }

                RedisSchema schema = new RedisSchema.Builder()
                        .indexName(storeConfig.indexName())
                        .prefix(storeConfig.prefix())
                        .vectorFieldName(storeConfig.vectorFieldName())
                        .scalarFieldName(storeConfig.scalarFieldName())
                        .numericMetadataFields(storeConfig.numericMetadataFields().orElse(Collections.emptyList()))
                        .textualMetadataFields(storeConfig.textualMetadataFields().orElse(Collections.emptyList()))
                        .vectorAlgorithm(storeConfig.vectorAlgorithm())
                        .dimension(storeConfig.dimension().get())
                        .metricType(storeConfig.distanceMetric())
                        .build();
                builder.schema(schema);

                return builder.build();
            }
        };
    }

    private RedisStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private ConfigValidationException.Problem[] createDimensionConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] { new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.redis%sdimension is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + "."))) };
    }
}
