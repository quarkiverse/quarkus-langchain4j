package io.quarkiverse.langchain4j.lancedb.deployment;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.lance.namespace.model.CreateTableRequest;
import org.lance.namespace.model.DeleteFromTableRequest;
import org.lance.namespace.model.InsertIntoTableRequest;
import org.lance.namespace.model.QueryTableRequest;
import org.lance.namespace.model.QueryTableRequestColumns;
import org.lance.namespace.model.QueryTableRequestVector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.lancedb.LanceDbEmbeddingStore;
import io.quarkiverse.langchain4j.lancedb.runtime.LanceDbEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class LanceDbEmbeddingStoreProcessor {

    private static final DotName LANCEDB_EMBEDDING_STORE = DotName.createSimple(LanceDbEmbeddingStore.class);

    private static final String FEATURE = "langchain4j-lancedb";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("com.lancedb", "lancedb-core"));
        producer.produce(new IndexDependencyBuildItem("org.lance", "lance-core"));
    }

    @BuildStep
    void reflectiveClasses(BuildProducer<ReflectiveClassBuildItem> producer) {
        producer.produce(ReflectiveClassBuildItem.builder(RootAllocator.class,
                Float4Vector.class,
                VarCharVector.class,
                VarBinaryVector.class,
                FixedSizeListVector.class,
                VectorSchemaRoot.class,
                ArrowFileReader.class,
                ArrowStreamWriter.class).methods().fields().build());

        producer.produce(ReflectiveClassBuildItem.builder(CreateTableRequest.class,
                InsertIntoTableRequest.class,
                QueryTableRequest.class,
                QueryTableRequestColumns.class,
                QueryTableRequestVector.class,
                DeleteFromTableRequest.class).methods().fields().build());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            LanceDbEmbeddingStoreRecorder recorder,
            LanceDbEmbeddingStoreBuildTimeConfig buildTimeConfig,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer) {

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(LANCEDB_EMBEDDING_STORE)
                    .types(ClassType.create(LanceDbEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .createWith(recorder.embeddingStoreFunction(NamedConfigUtil.DEFAULT_NAME))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        Map<String, LanceDbNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (Map.Entry<String, LanceDbNamedStoreBuildTimeConfig> entry : namedStores.entrySet()) {
            String storeName = entry.getKey();

            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(LANCEDB_EMBEDDING_STORE)
                    .types(ClassType.create(LanceDbEmbeddingStore.class),
                            ClassType.create(EmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .createWith(recorder.embeddingStoreFunction(storeName))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }
}
