package io.quarkiverse.langchain4j.redis.runtime;

import java.util.Collections;
import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RedisEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<RedisEmbeddingStore>, RedisEmbeddingStore> embeddingStoreFunction(
            RedisEmbeddingStoreConfig config, String clientName) {
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
                        .indexName(config.indexName())
                        .prefix(config.prefix())
                        .vectorFieldName(config.vectorFieldName())
                        .scalarFieldName(config.scalarFieldName())
                        .metadataFields(config.metadataFields().orElse(Collections.emptyList()))
                        .vectorAlgorithm(config.vectorAlgorithm())
                        .dimension(config.dimension())
                        .metricType(config.distanceMetric())
                        .build();
                builder.schema(schema);

                return builder.build();
            }
        };
    }
}
