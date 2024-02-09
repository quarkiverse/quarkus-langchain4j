package io.quarkiverse.langchain4j.infinispan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;

public class InfinispanEmbeddingStoreProcessor {

    public static final DotName INFINISPAN_EMBEDDING_STORE = DotName.createSimple(InfinispanEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-infinispan";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public InfinispanClientNameBuildItem requestInfinispanClient(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStoreBuildTimeConfig config) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(SchemaAndMarshallerProducer.class));
        return new InfinispanClientNameBuildItem(config.clientName().orElse("<default>"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            InfinispanEmbeddingStoreRecorder recorder,
            InfinispanEmbeddingStoreConfig config,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            InfinispanEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        String clientName = buildTimeConfig.clientName().orElse(null);
        AnnotationInstance infinispanClientQualifier;
        if (clientName == null) {
            infinispanClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            infinispanClientQualifier = AnnotationInstance.builder(InfinispanClientName.class)
                    .add("value", clientName)
                    .build();
        }

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(INFINISPAN_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)),
                        infinispanClientQualifier)
                .createWith(recorder.embeddingStoreFunction(config, clientName))
                .done());
        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }

}
