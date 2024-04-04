package io.quarkiverse.langchain4j.pgvector.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import com.pgvector.PGvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.pgvector.QuarkusPgVectorEmbeddingStore;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreRecorder;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class PgVectorEmbeddingStoreProcessor {

    private static final DotName PG_VECTOR_EMBEDDING_STORE = DotName.createSimple(QuarkusPgVectorEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-pgvector";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("com.pgvector", "pgvector"));
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-pgvector"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            PgVectorEmbeddingStoreRecorder recorder,
            PgVectorEmbeddingStoreConfig config,
            PgVectorEmbeddingStoreBuildTimeConfig buildTimeConfig,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {

        AnnotationInstance datasourceQualifier = buildTimeConfig.datasource()
                .map(dn -> AnnotationInstance.builder(DataSource.class).add("value", dn).build())
                .orElse(AnnotationInstance.builder(Default.class).build());

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(PG_VECTOR_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class)
                .createWith(recorder.embeddingStoreFunction(config, buildTimeConfig.datasource().orElse(null)))
                .addInjectionPoint(ClassType.create(DotName.createSimple(AgroalDataSource.class)), datasourceQualifier)
                .done());

        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }

    @BuildStep
    public ReflectiveClassBuildItem reflectiveClass() {
        return ReflectiveClassBuildItem.builder(PGvector.class).build();
    }
}
