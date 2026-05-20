package io.quarkiverse.langchain4j.infinispan;

import static io.quarkus.infinispan.client.runtime.InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.protostream.BaseMarshaller;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainItemMarshaller;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainMetadataMarshaller;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;

/**
 * Quarkus build step processor that registers the Infinispan embedding store extension.
 * Sets up the schema producer, Infinispan client, and creates the embedding store bean.
 */
public class InfinispanEmbeddingStoreProcessor {

    public static final DotName INFINISPAN_EMBEDDING_STORE = DotName.createSimple(InfinispanEmbeddingStore.class);
    private static final DotName LANGCHAIN_METADATA_MARSHALLER = DotName.createSimple(LangchainMetadataMarshaller.class);
    private static final DotName LANGCHAIN_ITEM_MARSHALLER = DotName.createSimple(LangchainItemMarshaller.class);

    private static final String FEATURE = "langchain4j-infinispan";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void requestInfinispanClients(
            BuildProducer<InfinispanClientNameBuildItem> clientNameProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            InfinispanEmbeddingStoreBuildTimeConfig config) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(SchemaAndMarshallerProducer.class));
        // Register the default client
        if (config.defaultConfig().defaultStoreEnabled()) {
            clientNameProducer.produce(
                    new InfinispanClientNameBuildItem(
                            config.defaultConfig().clientName().orElse(DEFAULT_INFINISPAN_CLIENT_NAME)));
        }

        // Register each named store's client
        for (InfinispanNamedStoreBuildTimeConfig named : config.namedConfig().values()) {
            String clientName = named.clientName().orElse(DEFAULT_INFINISPAN_CLIENT_NAME);
            clientNameProducer.produce(new InfinispanClientNameBuildItem(clientName));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBeans(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            InfinispanEmbeddingStoreRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            InfinispanEmbeddingStoreBuildTimeConfig buildTimeConfig) {

        // Default store
        if (buildTimeConfig.defaultConfig().defaultStoreEnabled()) {
            String clientName = buildTimeConfig.defaultConfig().clientName().orElse(null);
            beanProducer
                    .produce(buildDefaultEmbeddingStoreSyntheticBean(recorder, clientName));
            beanProducer.produce(buildMarshallerItemSyntheticBean(recorder, DEFAULT_INFINISPAN_CLIENT_NAME));
            beanProducer.produce(buildMarshallerMetadataSyntheticBean(recorder, DEFAULT_INFINISPAN_CLIENT_NAME));
            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }

        // Named stores
        Map<String, InfinispanNamedStoreBuildTimeConfig> namedStores = buildTimeConfig.namedConfig();
        for (Map.Entry<String, InfinispanNamedStoreBuildTimeConfig> entry : namedStores.entrySet()) {
            String storeName = entry.getKey();
            String clientName = entry.getValue().clientName().orElse(null);

            beanProducer.produce(buildEmbeddingStoreSyntheticBean(recorder, clientName, storeName));
            beanProducer.produce(buildMarshallerItemSyntheticBean(recorder, storeName));
            beanProducer.produce(buildMarshallerMetadataSyntheticBean(recorder, storeName));
            embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
        }
    }

    private SyntheticBeanBuildItem buildDefaultEmbeddingStoreSyntheticBean(InfinispanEmbeddingStoreRecorder recorder,
            String clientName) {
        AnnotationInstance clientQualifier = resolveClientQualifier(clientName);
        return SyntheticBeanBuildItem
                .configure(INFINISPAN_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)), clientQualifier)
                .createWith(recorder.embeddingStoreFunction(clientName, DEFAULT_INFINISPAN_CLIENT_NAME))
                .done();

    }

    private SyntheticBeanBuildItem buildEmbeddingStoreSyntheticBean(InfinispanEmbeddingStoreRecorder recorder,
            String clientName, String storeName) {
        AnnotationInstance clientQualifier = resolveClientQualifier(clientName);
        return SyntheticBeanBuildItem
                .configure(INFINISPAN_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .addQualifier(AnnotationInstance.builder(EmbeddingStoreName.class).add("value", storeName).build())
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(RemoteCacheManager.class)), clientQualifier)
                .createWith(recorder.embeddingStoreFunction(clientName, storeName))
                .done();
    }

    private SyntheticBeanBuildItem buildMarshallerItemSyntheticBean(InfinispanEmbeddingStoreRecorder recorder,
            String storeName) {
        return SyntheticBeanBuildItem
                .configure(LANGCHAIN_ITEM_MARSHALLER)
                // the InfinispanClientProducer uses BaseMarshaller from BeanManager
                .types(ClassType.create(BaseMarshaller.class))
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.itemMarshallerSupplier(storeName))
                .done();
    }

    private SyntheticBeanBuildItem buildMarshallerMetadataSyntheticBean(InfinispanEmbeddingStoreRecorder recorder,
            String storeName) {
        return SyntheticBeanBuildItem
                .configure(LANGCHAIN_METADATA_MARSHALLER)
                // the InfinispanClientProducer uses BaseMarshaller from BeanManager
                .types(ClassType.create(BaseMarshaller.class))
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.metadataMarshallerSupplier(storeName))
                .done();
    }

    private AnnotationInstance resolveClientQualifier(String clientName) {
        return clientName == null ? AnnotationInstance.builder(Default.class).build()
                : AnnotationInstance.builder(InfinispanClientName.class)
                        .add("value", clientName)
                        .build();
    }
}
