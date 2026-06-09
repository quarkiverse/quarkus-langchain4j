package io.quarkiverse.langchain4j.qdrant;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.qdrant.runtime.QdrantEmbeddingStore;
import io.quarkiverse.langchain4j.qdrant.runtime.QdrantRecorder;
import io.quarkiverse.qdrant.deployment.RequestedQdrantClientBuildItem;
import io.quarkiverse.qdrant.runtime.QdrantClient;
import io.quarkiverse.qdrant.runtime.QdrantClientName;
import io.quarkiverse.qdrant.runtime.QdrantConfig;
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
    public void requestQdrantClients(QdrantEmbeddingStoreBuildTimeConfig config,
            BuildProducer<RequestedQdrantClientBuildItem> producer) {
        producer.produce(new RequestedQdrantClientBuildItem(
                config.defaultConfig().clientName().orElse(QdrantConfig.DEFAULT_CLIENT_NAME)));

        for (Map.Entry<String, QdrantNamedStoreBuildTimeConfig> entry : config.namedConfig().entrySet()) {
            String clientName = entry.getValue().clientName().orElse(QdrantConfig.DEFAULT_CLIENT_NAME);
            if (!QdrantConfig.isDefaultClient(clientName)) {
                producer.produce(new RequestedQdrantClientBuildItem(clientName));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            QdrantRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            QdrantEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            String defaultClientName = buildTimeConfig.defaultConfig().clientName().orElse(null);
            AnnotationInstance defaultClientQualifier = resolveQdrantClientQualifier(defaultClientName);

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(QDRANT_EMBEDDING_STORE)
                    .types(ClassType.create(QdrantEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QdrantClient.class)),
                            defaultClientQualifier)
                    .createWith(recorder.qdrantStoreFunction(
                            defaultClientName,
                            buildTimeConfig.defaultConfig().collectionName(),
                            buildTimeConfig.defaultConfig().payloadTextKey()))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        Map<String, QdrantNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (Map.Entry<String, QdrantNamedStoreBuildTimeConfig> entry : namedStores.entrySet()) {
            String storeName = entry.getKey();
            String storeClientName = entry.getValue().clientName().orElse(null);

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();
            AnnotationInstance storeClientQualifier = resolveQdrantClientQualifier(storeClientName);

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(QDRANT_EMBEDDING_STORE)
                    .types(ClassType.create(QdrantEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QdrantClient.class)),
                            storeClientQualifier)
                    .createWith(recorder.qdrantStoreFunction(
                            storeClientName,
                            entry.getValue().collectionName(),
                            entry.getValue().payloadTextKey()))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    private AnnotationInstance resolveQdrantClientQualifier(String clientName) {
        if (clientName != null && !QdrantConfig.isDefaultClient(clientName)) {
            return AnnotationInstance.builder(QdrantClientName.class).add("value", clientName).build();
        }
        return AnnotationInstance.builder(Default.class).build();
    }
}
