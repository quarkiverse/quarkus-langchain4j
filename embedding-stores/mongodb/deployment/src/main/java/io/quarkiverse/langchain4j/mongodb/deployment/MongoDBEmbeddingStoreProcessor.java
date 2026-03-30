package io.quarkiverse.langchain4j.mongodb.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import com.mongodb.client.MongoClient;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.mongodb.MongoDBEmbeddingStore;
import io.quarkiverse.langchain4j.mongodb.runtime.MongoDBEmbeddingStoreRecorder;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.mongodb.deployment.MongoClientBuildTimeConfig;
import io.quarkus.mongodb.deployment.MongoClientNameBuildItem;
import io.quarkus.mongodb.deployment.MongoUnremovableClientsBuildItem;

public class MongoDBEmbeddingStoreProcessor {

    private static final DotName MONGODB_EMBEDDING_STORE = DotName.createSimple(MongoDBEmbeddingStore.class);
    private static final DotName MONGO_CLIENT = DotName.createSimple(MongoClient.class.getName());
    private static final String FEATURE = "langchain4j-mongodb";
    private static final String MONGODB_MEMORY_STORE_FEATURE = "langchain4j-memory-store-mongodb";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void requestMongoClient(
            List<FeatureBuildItem> activeFeatures,
            MongoDBEmbeddingStoreBuildTimeConfig config,
            BeanRegistrationPhaseBuildItem registrationPhase,
            MongoClientBuildTimeConfig mongoClientBuildTimeConfig,
            BuildProducer<MongoClientNameBuildItem> mongoClientName,
            List<MongoUnremovableClientsBuildItem> mongoUnremovableClientsBuildItem) {

        var sameAsMemoryStore = hasMongoDBMemoryStoreFeatureWithSameClient(activeFeatures,
                config.clientName().orElse("<default>"));
        if (sameAsMemoryStore)
            return;
        if (shouldCreateDefaultBean(mongoClientBuildTimeConfig, registrationPhase, mongoUnremovableClientsBuildItem)
                && config.clientName().isEmpty()) {
            mongoClientName.produce(new MongoClientNameBuildItem("<default>"));
        }

        config.clientName().ifPresent(clientName -> mongoClientName.produce(new MongoClientNameBuildItem(clientName)));

    }

    private boolean hasMongoDBMemoryStoreFeatureWithSameClient(List<FeatureBuildItem> activeFeatures, String clientName) {
        return activeFeatures.stream()
                .map(FeatureBuildItem::getName)
                .map(MONGODB_MEMORY_STORE_FEATURE::equals)
                .filter(isMemoryStoreFeature -> isMemoryStoreFeature)
                .findFirst()
                .map(aBoolean -> ConfigProvider.getConfig()
                        .getOptionalValue("quarkus.langchain4j.memory-store.mongodb.client-name", String.class))
                .map(memoryStoreClientConfigName -> memoryStoreClientConfigName.orElse("<default>"))
                .map(memoryStoreClientConfigName -> memoryStoreClientConfigName.equals(clientName))
                .orElse(false);
    }

    private boolean shouldCreateDefaultBean(MongoClientBuildTimeConfig mongoClientBuildTimeConfig,
            BeanRegistrationPhaseBuildItem registrationPhase,
            List<MongoUnremovableClientsBuildItem> mongoUnremovableClientsBuildItem) {

        // Don't create if there are unremovable clients
        if (!mongoUnremovableClientsBuildItem.isEmpty()) {
            return false;
        }

        // Don't create if force default clients is enabled
        if (mongoClientBuildTimeConfig.forceDefaultClients()) {
            return false;
        }

        // Don't create if there's already a default MongoClient injection point
        for (InjectionPointInfo injectionPoint : registrationPhase.getContext().get(BuildExtension.Key.INJECTION_POINTS)) {
            if (injectionPoint.getRequiredType().name().equals(MONGO_CLIENT) && injectionPoint.hasDefaultedQualifier()) {
                return false;
            }
        }

        return true;
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            MongoDBEmbeddingStoreRecorder recorder,
            BuildProducer<EmbeddingStoreBuildItem> embeddingStoreProducer,
            MongoDBEmbeddingStoreBuildTimeConfig buildTimeConfig) {
        String clientName = buildTimeConfig.clientName().orElse(null);
        AnnotationInstance mongoClientQualifier;
        if (clientName == null) {
            mongoClientQualifier = AnnotationInstance.builder(Default.class).build();
        } else {
            mongoClientQualifier = AnnotationInstance.builder(MongoClientName.class)
                    .add("value", clientName)
                    .build();
        }
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(MONGODB_EMBEDDING_STORE)
                .types(ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(MongoClient.class)),
                        mongoClientQualifier)
                .createWith(recorder.embeddingStoreFunction(clientName))
                .done());
        embeddingStoreProducer.produce(new EmbeddingStoreBuildItem());
    }
}
