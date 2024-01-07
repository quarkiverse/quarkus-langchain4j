package io.quarkiverse.langchain4j.opensearch.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.opensearch.client.opensearch.OpenSearchClient;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.opensearch.OpenSearchEmbeddingStore;
import io.quarkiverse.langchain4j.opensearch.runtime.OpenSearchEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.opensearch.runtime.OpenSearchEmbeddingStoreRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class Langchain4jOpensearchProcessor {

    public static final DotName OPENSEARCH_EMBEDDING_STORE = DotName.createSimple(OpenSearchEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-opensearch";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            OpenSearchEmbeddingStoreRecorder recorder,
            OpenSearchEmbeddingStoreConfig config,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {
        AnnotationInstance openSearchClientQualifier;
        openSearchClientQualifier = AnnotationInstance.builder(Default.class).build();

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(OPENSEARCH_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(OpenSearchClient.class)),
                        openSearchClientQualifier)
                .createWith(recorder.embeddingStoreFunction(config))
                .done());
        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }

}
