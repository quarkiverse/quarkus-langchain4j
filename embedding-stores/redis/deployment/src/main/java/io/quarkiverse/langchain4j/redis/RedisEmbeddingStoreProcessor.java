package io.quarkiverse.langchain4j.redis;

import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.deployment.NamedConfigDiscovery;
import io.quarkiverse.langchain4j.redis.runtime.RedisEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.redis.runtime.RedisEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
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

    private static final String CONFIG_PREFIX = "quarkus.langchain4j.redis";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void requestRedisClients(RedisEmbeddingStoreBuildTimeConfig config,
            BuildProducer<RequestedRedisClientBuildItem> producer) {
        producer.produce(new RequestedRedisClientBuildItem(
                config.defaultConfig().clientName().orElse(RedisConfig.DEFAULT_CLIENT_NAME)));

        Map<String, RedisNamedStoreBuildTimeConfig> namedStores = config.namedConfig();
        for (String storeName : discoverStoreNames(config)) {
            String clientName = namedStores.get(storeName).clientName().orElse(null);
            if (clientName != null && !NamedConfigUtil.isDefault(clientName)) {
                producer.produce(new RequestedRedisClientBuildItem(clientName));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            RedisEmbeddingStoreRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            RedisEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        String defaultClientName = buildTimeConfig.defaultConfig().clientName().orElse(null);
        AnnotationInstance defaultRedisClientQualifier = resolveRedisClientQualifier(defaultClientName);

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(REDIS_EMBEDDING_STORE)
                    .types(ClassType.create(RedisEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(ReactiveRedisDataSource.class)),
                            defaultRedisClientQualifier)
                    .createWith(recorder.embeddingStoreFunction(defaultClientName, NamedConfigUtil.DEFAULT_NAME))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        Map<String, RedisNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (String storeName : discoverStoreNames(buildTimeConfig)) {
            String storeClientName = namedStores.get(storeName).clientName().orElse(null);

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();
            AnnotationInstance storeRedisClientQualifier = resolveRedisClientQualifier(storeClientName);

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(REDIS_EMBEDDING_STORE)
                    .types(ClassType.create(RedisEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(ReactiveRedisDataSource.class)),
                            storeRedisClientQualifier)
                    .createWith(recorder.embeddingStoreFunction(storeClientName, storeName))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    /**
     * Named stores are only partially materialized by SmallRye Config, since the build-time config group holds a single
     * property. Discovering them from the configured property names also covers stores configured at runtime only.
     */
    private static Set<String> discoverStoreNames(RedisEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        return NamedConfigDiscovery.discoverNames(CONFIG_PREFIX, buildTimeConfig.namedConfig().keySet(),
                RedisEmbeddingStoreBuildTimeConfig.class, RedisEmbeddingStoreConfig.class);
    }

    private AnnotationInstance resolveRedisClientQualifier(String clientName) {
        if (clientName != null && !NamedConfigUtil.isDefault(clientName)) {
            return AnnotationInstance.builder(RedisClientName.class).add("value", clientName).build();
        }
        return AnnotationInstance.builder(Default.class).build();
    }
}
