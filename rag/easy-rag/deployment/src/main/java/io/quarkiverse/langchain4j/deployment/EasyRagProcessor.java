package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkiverse.langchain4j.deployment.items.AutoCreateEmbeddingModelBuildItem;
import io.quarkiverse.langchain4j.deployment.items.InMemoryEmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.easyrag.EasyRagManualIngestion;
import io.quarkiverse.langchain4j.easyrag.runtime.EasyRagConfig;
import io.quarkiverse.langchain4j.easyrag.runtime.EasyRagRecorder;
import io.quarkiverse.langchain4j.easyrag.runtime.EasyRetrievalAugmentor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class EasyRagProcessor {

    public static final DotName IN_MEMORY_EMBEDDING_STORE = DotName.createSimple(InMemoryEmbeddingStore.class);

    static final String FEATURE = "langchain4j-easy-rag";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void registerManualIngestionTrigger(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(EasyRagManualIngestion.class)
                .build());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void ingest(EasyRagConfig config,
            EasyRagRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        recorder.ingest(config, beanContainer.getValue());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createInMemoryEmbeddingStoreIfNoOtherExists(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            List<EmbeddingStoreBuildItem> embeddingStores,
            EasyRagRecorder recorder,
            EasyRagConfig config,
            BuildProducer<InMemoryEmbeddingStoreBuildItem> inMemoryEmbeddingStoreBuildItemBuildProducer) {
        if (embeddingStores.isEmpty()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(IN_MEMORY_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ClassType.create(InMemoryEmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)),
                            ParameterizedType.create(InMemoryEmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .defaultBean()
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.inMemoryEmbeddingStoreSupplier(config))
                    .done());
            inMemoryEmbeddingStoreBuildItemBuildProducer.produce(new InMemoryEmbeddingStoreBuildItem());
        }

    }

    /**
     * If the application doesn't contain any retrieval augmentor, generate one with
     * some default settings.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createEasyRetrievalAugmentorIfNoOtherIsFound(
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            BuildProducer<AutoCreateEmbeddingModelBuildItem> autoCreateEmbeddingModelBuildItemBuildProducer,
            EasyRagRecorder recorder,
            EasyRagConfig config) {
        Type retrievalAugmentor = ClassType.create(RetrievalAugmentor.class);
        Type retrievalAugmentorSupplier = ParameterizedType.create(Supplier.class, retrievalAugmentor);
        for (BeanInfo bean : beans.getBeans()) {
            if (bean.getTypes().contains(retrievalAugmentor) || bean.getTypes().contains(retrievalAugmentorSupplier)) {
                return;
            }
        }
        autoCreateEmbeddingModelBuildItemBuildProducer.produce(new AutoCreateEmbeddingModelBuildItem());
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(DotName.createSimple(EasyRetrievalAugmentor.class.getName()))
                .types(ClassType.create(RetrievalAugmentor.class))
                .defaultBean()
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(EmbeddingStore.class))
                .addInjectionPoint(ClassType.create(EmbeddingModel.class))
                .createWith(recorder.easyRetrievalAugmentorFunction(config))
                .done());
    }
}
