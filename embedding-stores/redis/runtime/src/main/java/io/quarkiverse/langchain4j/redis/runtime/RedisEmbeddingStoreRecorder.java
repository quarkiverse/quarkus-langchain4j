package io.quarkiverse.langchain4j.redis.runtime;

import java.util.Collections;
import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RedisEmbeddingStoreRecorder {
    private final RuntimeValue<RedisEmbeddingStoreConfig> runtimeConfig;

    public RedisEmbeddingStoreRecorder(RuntimeValue<RedisEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<RedisEmbeddingStore>, RedisEmbeddingStore> embeddingStoreFunction(
            String clientName) {
        return new Function<>() {
            @Override
            public RedisEmbeddingStore apply(SyntheticCreationalContext<RedisEmbeddingStore> context) {
                RedisEmbeddingStore.Builder builder = new RedisEmbeddingStore.Builder();
                ReactiveRedisDataSource dataSource;
                if (clientName == null) {
                    dataSource = context.getInjectedReference(ReactiveRedisDataSource.class, new Default.Literal());
                } else {
                    dataSource = context.getInjectedReference(ReactiveRedisDataSource.class,
                            new RedisClientName.Literal(clientName));
                }
                builder.dataSource(dataSource);

                RedisSchema schema = new RedisSchema.Builder()
                        .indexName(runtimeConfig.getValue().indexName())
                        .prefix(runtimeConfig.getValue().prefix())
                        .vectorFieldName(runtimeConfig.getValue().vectorFieldName())
                        .scalarFieldName(runtimeConfig.getValue().scalarFieldName())
                        .numericMetadataFields(runtimeConfig.getValue().numericMetadataFields().orElse(Collections.emptyList()))
                        .textualMetadataFields(runtimeConfig.getValue().textualMetadataFields().orElse(Collections.emptyList()))
                        .vectorAlgorithm(runtimeConfig.getValue().vectorAlgorithm())
                        .dimension(runtimeConfig.getValue().dimension())
                        .metricType(runtimeConfig.getValue().distanceMetric())
                        .build();
                builder.schema(schema);

                return builder.build();
            }
        };
    }
}
