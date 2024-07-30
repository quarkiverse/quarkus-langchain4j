package io.quarkiverse.langchain4j.milvus;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.milvus.runtime.MilvusRecorder;
import io.quarkiverse.langchain4j.milvus.runtime.MilvusRuntimeConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class MilvusProcessor {

    public static final DotName MILVUS_EMBEDDING_STORE = DotName.createSimple(MilvusEmbeddingStore.class);
    public static final String FEATURE = "langchain4j-milvus";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            MilvusRecorder recorder,
            MilvusRuntimeConfig config,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(MILVUS_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .defaultBean()
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.milvusStoreSupplier(config))
                .done());
        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }

}
