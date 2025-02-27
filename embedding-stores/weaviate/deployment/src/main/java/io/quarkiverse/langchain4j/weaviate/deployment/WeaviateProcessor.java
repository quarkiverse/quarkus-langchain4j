package io.quarkiverse.langchain4j.weaviate.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.weaviate.runtime.WeaviateRecorder;
import io.quarkiverse.langchain4j.weaviate.runtime.WeaviateRuntimeConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.weaviate.client.WeaviateClient;

class WeaviateProcessor {

    public static final DotName WEAVIATE_EMBEDDING_STORE = DotName.createSimple(WeaviateEmbeddingStore.class);
    public static final DotName WEAVIATE_CLIENT = DotName.createSimple(WeaviateClient.class);

    static final String FEATURE = "langchain4j-weaviate";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBeans(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            WeaviateRecorder recorder,
            WeaviateRuntimeConfig config,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(WEAVIATE_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .defaultBean()
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.weaviateStoreSupplier(config))
                .done());

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(WEAVIATE_CLIENT)
                .types(ClassType.create(WeaviateClient.class))
                .defaultBean()
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.weaviateClientSupplier(config))
                .done());

        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }
}
