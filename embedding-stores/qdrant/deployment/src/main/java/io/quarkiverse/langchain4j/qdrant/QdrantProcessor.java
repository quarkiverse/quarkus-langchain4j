package io.quarkiverse.langchain4j.qdrant;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.qdrant.runtime.QdrantRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class QdrantProcessor {

    public static final DotName QDRANT_EMBEDDING_STORE = DotName.createSimple(QdrantEmbeddingStore.class);
    public static final String FEATURE = "langchain4j-qdrant";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            QdrantRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(QDRANT_EMBEDDING_STORE)
                .types(
                        ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .defaultBean()
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.qdrantStoreSupplier())
                .done());

        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }

}
