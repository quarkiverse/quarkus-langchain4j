package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.chatscopes.passivation.InfinispanChatScopeStore;
import io.quarkiverse.langchain4j.chatscopes.passivation.InfinispanChatScopeStoreRecorder;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;
import io.quarkus.infinispan.client.runtime.InfinispanClientUtil;

/**
 * Quarkus build step processor that registers the Infinispan-backed {@link ChatScopeStore}.
 * Creates a synthetic CDI bean that persists chat scope state to an Infinispan cache,
 * allowing chat scopes to survive application restarts.
 */
public class InfinispanPassivationProcessor {

    public static final DotName INFINISPAN_CHAT_SCOPE_STORE = DotName.createSimple(InfinispanChatScopeStore.class);

    /**
     * Declares the Infinispan client required by this extension so that
     * the Infinispan client extension can start dev services if needed.
     */
    @BuildStep
    public InfinispanClientNameBuildItem requestInfinispanClient(InfinispanChatScopeStoreBuildTimeConfig config) {
        return new InfinispanClientNameBuildItem(
                config.clientName().orElse(InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME));
    }

    /**
     * Creates the {@link InfinispanChatScopeStore} synthetic bean at runtime,
     * wiring the configured Infinispan client and cache name.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createChatScopeStoreBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            InfinispanChatScopeStoreRecorder recorder,
            InfinispanChatScopeStoreBuildTimeConfig config) {
        String clientName = config.clientName().orElse(null);
        String cacheName = config.cacheName().orElse(null);
        AnnotationInstance infinispanClientQualifier;
        if (clientName == null) {
            infinispanClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            infinispanClientQualifier = AnnotationInstance.builder(InfinispanClientName.class)
                    .add("value", clientName)
                    .build();
        }
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(INFINISPAN_CHAT_SCOPE_STORE)
                .types(ClassType.create(ChatScopeStore.class))
                .setRuntimeInit()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)),
                        infinispanClientQualifier)
                .createWith(recorder.chatStoreFunction(clientName, cacheName,
                        config.cacheConfig().orElse(null), config.createCache()))
                .unremovable()
                .done());
    }
}
