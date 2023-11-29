package io.quarkiverse.langchain4j.pinecone;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.pinecone.runtime.PineconeConfig;
import io.quarkiverse.langchain4j.pinecone.runtime.PineconeRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class PineconeProcessor {

    public static final DotName PINECONE_EMBEDDING_STORE = DotName.createSimple(PineconeEmbeddingStore.class);
    private static final String FEATURE = "langchain4j-pinecone";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            PineconeRecorder recorder,
            PineconeConfig config) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(PINECONE_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .defaultBean()
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class)
                .supplier(recorder.pineconeStoreSupplier(config))
                .done());
    }

}
