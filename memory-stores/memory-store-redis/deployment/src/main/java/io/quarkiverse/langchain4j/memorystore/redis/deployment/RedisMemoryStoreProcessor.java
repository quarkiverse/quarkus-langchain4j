package io.quarkiverse.langchain4j.memorystore.redis.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.RedisChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.redis.runtime.RedisMemoryStoreRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.deployment.client.RequestedRedisClientBuildItem;
import io.quarkus.redis.runtime.client.config.RedisConfig;

class RedisMemoryStoreProcessor {

    public static final DotName REDIS_CHAT_MEMORY_STORE = DotName.createSimple(RedisChatMemoryStore.class);
    private static final String FEATURE = "langchain4j-memory-store-redis";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public RequestedRedisClientBuildItem requestRedisClient(RedisMemoryStoreBuildTimeConfig config) {
        return new RequestedRedisClientBuildItem(config.clientName().orElse(RedisConfig.DEFAULT_CLIENT_NAME));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createMemoryStoreBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            RedisMemoryStoreRecorder recorder,
            RedisMemoryStoreBuildTimeConfig buildTimeConfig) {
        String clientName = buildTimeConfig.clientName().orElse(null);
        AnnotationInstance redisClientQualifier;
        if (clientName == null) {
            redisClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            redisClientQualifier = AnnotationInstance.builder(RedisClientName.class)
                    .add("value", clientName)
                    .build();
        }
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(REDIS_CHAT_MEMORY_STORE)
                .types(ClassType.create(ChatMemoryStore.class))
                .setRuntimeInit()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RedisDataSource.class)),
                        redisClientQualifier)
                .createWith(recorder.chatMemoryStoreFunction(clientName))
                .done());
    }

}
