package io.quarkiverse.langchain4j.redis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.redis.runtime.RedisEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.redis.runtime.RedisEmbeddingStoreRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.builder.Version;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.redis.client.RedisClientName;
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
    public RequestedRedisClientBuildItem requestRedisClient(RedisEmbeddingStoreBuildTimeConfig config) {
        return new RequestedRedisClientBuildItem(config.clientName().orElse(RedisConfig.DEFAULT_CLIENT_NAME));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            RedisEmbeddingStoreRecorder recorder,
            RedisEmbeddingStoreConfig config,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            RedisEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        String clientName = buildTimeConfig.clientName().orElse(null);
        AnnotationInstance redisClientQualifier;
        if (clientName == null) {
            redisClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            redisClientQualifier = AnnotationInstance.builder(RedisClientName.class)
                    .add("value", clientName)
                    .build();
        }

        // FIXME: after updating to Quarkus 3.15, this workaround can be removed
        String quarkusVersion = Version.getVersion();
        boolean workaround_specifyDialectAsParameter;
        if (quarkusVersion.split("\\.").length < 2) {
            // some custom version where we can't determine the minor version number
            workaround_specifyDialectAsParameter = false;
        } else {
            try {
                int minorVersion = Integer.parseInt(quarkusVersion.split("\\.")[1]);
                workaround_specifyDialectAsParameter = minorVersion < 13;
            } catch (NumberFormatException e) {
                workaround_specifyDialectAsParameter = false;
            }
        }

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(REDIS_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(ReactiveRedisDataSource.class)),
                        redisClientQualifier)
                .createWith(recorder.embeddingStoreFunction(config, clientName, workaround_specifyDialectAsParameter))
                .done());
        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }

}
