package io.quarkiverse.langchain4j.memorystore.infinispan.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.InfinispanChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.infinispan.runtime.InfinispanMemoryStoreRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;
import io.quarkus.infinispan.client.runtime.InfinispanClientUtil;

class InfinispanMemoryStoreProcessor {

    public static final DotName INFINISPAN_CHAT_MEMORY_STORE = DotName.createSimple(InfinispanChatMemoryStore.class);
    private static final String FEATURE = "langchain4j-memory-store-infinispan";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public InfinispanClientNameBuildItem requestInfinispanClient(InfinispanMemoryStoreBuildTimeConfig config) {
        return new InfinispanClientNameBuildItem(
                config.clientName().orElse(InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createMemoryStoreBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            InfinispanMemoryStoreRecorder recorder,
            InfinispanMemoryStoreBuildTimeConfig buildTimeConfig) {
        String clientName = buildTimeConfig.clientName().orElse(null);
        String cacheName = buildTimeConfig.cacheName().orElse(null);
        AnnotationInstance infinispanClientQualifier;
        if (clientName == null) {
            infinispanClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            infinispanClientQualifier = AnnotationInstance.builder(InfinispanClientName.class)
                    .add("value", clientName)
                    .build();
        }
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(INFINISPAN_CHAT_MEMORY_STORE)
                .types(ClassType.create(ChatMemoryStore.class))
                .setRuntimeInit()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)),
                        infinispanClientQualifier)
                .createWith(recorder.chatMemoryStoreFunction(clientName, cacheName,
                        buildTimeConfig.cacheConfig().orElse(null), buildTimeConfig.createCache()))
                .done());
    }
}
