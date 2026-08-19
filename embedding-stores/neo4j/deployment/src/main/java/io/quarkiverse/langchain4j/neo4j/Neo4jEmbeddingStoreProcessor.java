package io.quarkiverse.langchain4j.neo4j;

import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.neo4j.driver.Driver;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.deployment.NamedConfigDiscovery;
import io.quarkiverse.langchain4j.neo4j.runtime.Neo4jEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.neo4j.runtime.Neo4jEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class Neo4jEmbeddingStoreProcessor {

    private static final String FEATURE = "langchain4j-neo4j";
    private static final String CONFIG_PREFIX = "quarkus.langchain4j.neo4j";
    private static final DotName NEO4J_EMBEDDING_STORE = DotName.createSimple(Neo4jEmbeddingStore.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            Neo4jEmbeddingStoreRecorder recorder,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            Neo4jEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(Driver.class));

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(NEO4J_EMBEDDING_STORE)
                    .types(
                            ClassType.create(EmbeddingStore.class),
                            ClassType.create(NEO4J_EMBEDDING_STORE),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .defaultBean()
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(Driver.class)))
                    .createWith(recorder.embeddingStoreFunction(NamedConfigUtil.DEFAULT_NAME))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        Map<String, Neo4jNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (String storeName : discoverStoreNames(buildTimeConfig)) {
            if (!namedStores.get(storeName).enabled()) {
                continue;
            }

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(NEO4J_EMBEDDING_STORE)
                    .types(
                            ClassType.create(EmbeddingStore.class),
                            ClassType.create(NEO4J_EMBEDDING_STORE),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .defaultBean()
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(Driver.class)))
                    .createWith(recorder.embeddingStoreFunction(storeName))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    /**
     * Named stores are only partially materialized by SmallRye Config, since the build-time config group holds a couple
     * of properties. Discovering them from the configured property names also covers stores configured at runtime only.
     */
    private static Set<String> discoverStoreNames(Neo4jEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        return NamedConfigDiscovery.discoverNames(CONFIG_PREFIX, buildTimeConfig.namedConfig().keySet(),
                Neo4jEmbeddingStoreBuildTimeConfig.class, Neo4jEmbeddingStoreConfig.class);
    }
}
