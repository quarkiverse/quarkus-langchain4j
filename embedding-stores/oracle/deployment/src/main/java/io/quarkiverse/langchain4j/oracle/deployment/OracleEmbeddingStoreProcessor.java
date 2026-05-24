package io.quarkiverse.langchain4j.oracle.deployment;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.oracle.QuarkusOracleEmbeddingStore;
import io.quarkiverse.langchain4j.oracle.runtime.OracleEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;

class OracleEmbeddingStoreProcessor {

    private static final DotName QUARKUS_ORACLE_EMBEDDING_STORE = DotName.createSimple(QuarkusOracleEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-oracle";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-oracle"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            OracleEmbeddingStoreRecorder recorder,
            OracleEmbeddingStoreBuildTimeConfig buildTimeConfig,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {

        String defaultDatasourceName = buildTimeConfig.defaultConfig().datasource().orElse(null);
        AnnotationInstance defaultDatasourceQualifier = resolveDatasourceQualifier(defaultDatasourceName);

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(QUARKUS_ORACLE_EMBEDDING_STORE)
                    .types(ClassType.create(QuarkusOracleEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .createWith(recorder.embeddingStoreFunction(defaultDatasourceName, NamedConfigUtil.DEFAULT_NAME))
                    .addInjectionPoint(ClassType.create(DotName.createSimple(AgroalDataSource.class)),
                            defaultDatasourceQualifier)
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        Map<String, OracleNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (Map.Entry<String, OracleNamedStoreBuildTimeConfig> entry : namedStores.entrySet()) {
            String storeName = entry.getKey();
            OracleNamedStoreBuildTimeConfig storeBuildTimeConfig = entry.getValue();

            if (!storeBuildTimeConfig.enabled()) {
                continue;
            }

            String storeDatasourceName = storeBuildTimeConfig.datasource().orElse(null);

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName).build();
            AnnotationInstance storeDatasourceQualifier = resolveDatasourceQualifier(storeDatasourceName);

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(QUARKUS_ORACLE_EMBEDDING_STORE)
                    .types(ClassType.create(QuarkusOracleEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .createWith(recorder.embeddingStoreFunction(storeDatasourceName, storeName))
                    .addInjectionPoint(ClassType.create(DotName.createSimple(AgroalDataSource.class)),
                            storeDatasourceQualifier)
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    private AnnotationInstance resolveDatasourceQualifier(String datasourceName) {
        if (datasourceName != null && !NamedConfigUtil.isDefault(datasourceName)) {
            return AnnotationInstance.builder(DataSource.class).add("value", datasourceName).build();
        }
        return AnnotationInstance.builder(Default.class).build();
    }
}
