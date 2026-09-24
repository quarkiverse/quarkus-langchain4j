package io.quarkiverse.langchain4j.opensearch.deployment;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.opensearch.runtime.OpenSearchRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class OpenSearchProcessor {

    public static final DotName OPENSEARCH_EMBEDDING_STORE = DotName.createSimple(OpenSearchEmbeddingStore.class);

    static final String FEATURE = "langchain4j-opensearch";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            OpenSearchRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            OpenSearchEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(OPENSEARCH_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ClassType.create(OpenSearchEmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .createWith(recorder.openSearchStoreFunction(NamedConfigUtil.DEFAULT_NAME))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        Map<String, OpenSearchNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (Map.Entry<String, OpenSearchNamedStoreBuildTimeConfig> entry : namedStores.entrySet()) {
            String storeName = entry.getKey();

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(OPENSEARCH_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ClassType.create(OpenSearchEmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .createWith(recorder.openSearchStoreFunction(storeName))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }
}
