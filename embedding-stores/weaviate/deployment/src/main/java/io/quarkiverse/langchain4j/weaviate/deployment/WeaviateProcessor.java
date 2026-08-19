package io.quarkiverse.langchain4j.weaviate.deployment;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.deployment.NamedConfigDiscovery;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.weaviate.runtime.WeaviateEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.weaviate.runtime.WeaviateRecorder;
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

    static final String CONFIG_PREFIX = "quarkus.langchain4j.weaviate";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBeans(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            WeaviateRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            WeaviateEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(WEAVIATE_EMBEDDING_STORE)
                    .types(ClassType.create(WeaviateEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .defaultBean()
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.weaviateStoreSupplier(NamedConfigUtil.DEFAULT_NAME))
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(WEAVIATE_CLIENT)
                    .types(ClassType.create(WeaviateClient.class))
                    .defaultBean()
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.weaviateClientSupplier(NamedConfigUtil.DEFAULT_NAME))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        for (String storeName : discoverStoreNames(buildTimeConfig)) {
            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(WEAVIATE_EMBEDDING_STORE)
                    .types(ClassType.create(WeaviateEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .supplier(recorder.weaviateStoreSupplier(storeName))
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(WEAVIATE_CLIENT)
                    .types(ClassType.create(WeaviateClient.class))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .supplier(recorder.weaviateClientSupplier(storeName))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    /**
     * Named stores are only partially materialized by SmallRye Config, since the build-time config group holds a single
     * property. Discovering them from the configured property names also covers stores configured at runtime only.
     */
    static Set<String> discoverStoreNames(WeaviateEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        return NamedConfigDiscovery.discoverNames(CONFIG_PREFIX, buildTimeConfig.namedConfig().keySet(),
                WeaviateEmbeddingStoreBuildTimeConfig.class, WeaviateEmbeddingStoreConfig.class);
    }
}
