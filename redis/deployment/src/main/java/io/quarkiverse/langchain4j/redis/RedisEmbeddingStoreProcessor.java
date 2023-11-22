package io.quarkiverse.langchain4j.redis;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.redis.runtime.RedisEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.redis.runtime.RedisEmbeddingStoreRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.deployment.client.RequestedRedisClientBuildItem;
import io.quarkus.redis.runtime.client.config.RedisConfig;

public class RedisEmbeddingStoreProcessor {

    public static final DotName REDIS_EMBEDDING_STORE = DotName.createSimple(RedisEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-redis";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public RequestedRedisClientBuildItem requestRedisClient(RedisEmbeddingStoreConfig config) {
        return new RequestedRedisClientBuildItem(config.clientName().orElse(RedisConfig.DEFAULT_CLIENT_NAME));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            RedisEmbeddingStoreRecorder recorder,
            RedisEmbeddingStoreConfig config) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(REDIS_EMBEDDING_STORE)
                .types(EmbeddingStore.class)
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(ReactiveRedisDataSource.class)))
                .createWith(recorder.embeddingStoreFunction(config))
                .done());

    }

}
