package io.quarkiverse.langchain4j.infinispan;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;
import io.quarkus.infinispan.client.runtime.InfinispanClientUtil;

public class InfinispanEmbeddingStoreProcessor {

    public static final DotName INFINISPAN_EMBEDDING_STORE = DotName.createSimple(InfinispanEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-infinispan";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void requestInfinispanClients(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<InfinispanClientNameBuildItem> producer,
            InfinispanEmbeddingStoreBuildTimeConfig config) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(SchemaAndMarshallerProducer.class));
        producer.produce(new InfinispanClientNameBuildItem(
                config.defaultConfig().clientName().orElse(InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME)));

        for (Map.Entry<String, InfinispanNamedStoreBuildTimeConfig> entry : config.namedConfig().entrySet()) {
            String clientName = entry.getValue().clientName().orElse(null);
            if (clientName != null && !NamedConfigUtil.isDefault(clientName)) {
                producer.produce(new InfinispanClientNameBuildItem(clientName));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            InfinispanEmbeddingStoreRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            InfinispanEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        String defaultClientName = buildTimeConfig.defaultConfig().clientName().orElse(null);

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            AnnotationInstance defaultClientQualifier = resolveInfinispanClientQualifier(defaultClientName);

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(INFINISPAN_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)),
                            defaultClientQualifier)
                    .createWith(recorder.embeddingStoreFunction(defaultClientName, NamedConfigUtil.DEFAULT_NAME))
                    .done());
            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        for (Map.Entry<String, InfinispanNamedStoreBuildTimeConfig> entry : buildTimeConfig.namedConfig().entrySet()) {
            String storeName = entry.getKey();
            InfinispanNamedStoreBuildTimeConfig storeBuildTimeConfig = entry.getValue();
            String storeClientName = storeBuildTimeConfig.clientName().orElse(null);

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();
            AnnotationInstance storeClientQualifier = resolveInfinispanClientQualifier(storeClientName);

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(INFINISPAN_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)),
                            storeClientQualifier)
                    .createWith(recorder.embeddingStoreFunction(storeClientName, storeName))
                    .done());
            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    private AnnotationInstance resolveInfinispanClientQualifier(String clientName) {
        if (clientName != null && !NamedConfigUtil.isDefault(clientName)) {
            return AnnotationInstance.builder(InfinispanClientName.class).add("value", clientName).build();
        }
        return AnnotationInstance.builder(Default.class).build();
    }
}
