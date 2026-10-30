package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.chatscopes.passivation.RedisChatScopeStore;
import io.quarkiverse.langchain4j.chatscopes.passivation.RedisChatScopeStoreRecorder;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.deployment.client.RequestedRedisClientBuildItem;
import io.quarkus.redis.runtime.client.config.RedisConfig;

public class RedisPassivationProcessor {
    public static final DotName REDIS_JSON_CHAT_SCOPE_STORE = DotName.createSimple(RedisChatScopeStore.class);

    @BuildStep
    public RequestedRedisClientBuildItem requestRedisClient(RedisChatScopeStoreBuildTimeConfig config) {
        return new RequestedRedisClientBuildItem(config.clientName().orElse(RedisConfig.DEFAULT_CLIENT_NAME));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createMemoryStoreBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            RedisChatScopeStoreRecorder recorder,
            RedisChatScopeStoreBuildTimeConfig config) {
        String clientName = config.clientName().orElse(null);
        AnnotationInstance redisClientQualifier;
        if (clientName == null) {
            redisClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            redisClientQualifier = AnnotationInstance.builder(RedisClientName.class)
                    .add("value", clientName)
                    .build();
        }
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(REDIS_JSON_CHAT_SCOPE_STORE)
                .types(ClassType.create(ChatScopeStore.class))
                .setRuntimeInit()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RedisDataSource.class)),
                        redisClientQualifier)
                .createWith(recorder.chatStoreFunction(clientName))
                .unremovable()
                .done());
    }
}
