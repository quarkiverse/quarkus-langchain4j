package io.quarkiverse.langchain4j.chroma.deployment;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.chroma.runtime.ChromaEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.chroma.runtime.ChromaRecorder;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.deployment.NamedConfigDiscovery;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyReaderOverrideBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyWriterOverrideBuildItem;
import io.smallrye.config.Priorities;

class ChromaProcessor {

    public static final DotName CHROMA_EMBEDDING_STORE = DotName.createSimple(ChromaEmbeddingStore.class);

    static final String FEATURE = "langchain4j-chroma";

    static final String CONFIG_PREFIX = "quarkus.langchain4j.chroma";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            ChromaRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            ChromaEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHROMA_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ClassType.create(ChromaEmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .createWith(recorder.chromaStoreFunction(NamedConfigUtil.DEFAULT_NAME))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        for (String storeName : discoverStoreNames(buildTimeConfig)) {
            AnnotationInstance storeNameQualifier = AnnotationInstance.builder(EmbeddingStoreName.class)
                    .add("value", storeName)
                    .build();

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHROMA_EMBEDDING_STORE)
                    .types(ClassType.create(EmbeddingStore.class),
                            ClassType.create(ChromaEmbeddingStore.class),
                            ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addQualifier(storeNameQualifier)
                    .createWith(recorder.chromaStoreFunction(storeName))
                    .done());

            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    /**
     * Named stores are only partially materialized by SmallRye Config, since the build-time config group holds a single
     * property. Discovering them from the configured property names also covers stores configured at runtime only.
     */
    static Set<String> discoverStoreNames(ChromaEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        return NamedConfigDiscovery.discoverNames(CONFIG_PREFIX, buildTimeConfig.namedConfig().keySet(),
                ChromaEmbeddingStoreBuildTimeConfig.class, ChromaEmbeddingStoreConfig.class);
    }

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that Jackson is used.
     * This is not a proper solution as it affects all clients, but it's better than the having the reader/writers be selected
     * at random.
     */
    @BuildStep
    public void deprioritizeJsonb(Capabilities capabilities,
            BuildProducer<MessageBodyReaderOverrideBuildItem> readerOverrideProducer,
            BuildProducer<MessageBodyWriterOverrideBuildItem> writerOverrideProducer) {
        if (capabilities.isPresent(Capability.REST_CLIENT_REACTIVE_JSONB)) {
            readerOverrideProducer.produce(
                    new MessageBodyReaderOverrideBuildItem("org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyReader",
                            Priorities.APPLICATION + 1, true));
            writerOverrideProducer.produce(new MessageBodyWriterOverrideBuildItem(
                    "org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyWriter", Priorities.APPLICATION + 1, true));
        }
    }
}
