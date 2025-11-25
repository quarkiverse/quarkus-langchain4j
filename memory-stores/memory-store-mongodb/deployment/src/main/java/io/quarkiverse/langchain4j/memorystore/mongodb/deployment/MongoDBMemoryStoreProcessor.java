
package io.quarkiverse.langchain4j.memorystore.mongodb.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import com.mongodb.client.MongoClient;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.MongoDBChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.mongodb.runtime.MongoDBMemoryStoreRecorder;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.mongodb.deployment.MongoClientBuildTimeConfig;
import io.quarkus.mongodb.deployment.MongoClientNameBuildItem;
import io.quarkus.mongodb.deployment.MongoUnremovableClientsBuildItem;

class MongoDBMemoryStoreProcessor {

    private static final DotName MONGODB_CHAT_MEMORY_STORE = DotName.createSimple(MongoDBChatMemoryStore.class);
    private static final DotName MONGO_CLIENT = DotName.createSimple(MongoClient.class.getName());
    private static final String FEATURE = "langchain4j-memory-store-mongodb";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void requestMongoClient(MongoDBMemoryStoreBuildTimeConfig config,
            MongoClientBuildTimeConfig mongoClientBuildTimeConfig,
            BeanRegistrationPhaseBuildItem registrationPhase,
            List<MongoUnremovableClientsBuildItem> mongoUnremovableClientsBuildItem,
            BuildProducer<MongoClientNameBuildItem> mongoClientName) {

        if (shouldCreateDefaultBean(mongoClientBuildTimeConfig, registrationPhase, mongoUnremovableClientsBuildItem)
                && config.clientName().isEmpty()) {
            mongoClientName.produce(new MongoClientNameBuildItem("<default>"));
        }

        config.clientName().ifPresent(clientName -> mongoClientName.produce(new MongoClientNameBuildItem(clientName)));

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
    public void createMemoryStoreBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            MongoDBMemoryStoreRecorder recorder,
            MongoDBMemoryStoreBuildTimeConfig buildTimeConfig) {
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
                .configure(MONGODB_CHAT_MEMORY_STORE)
                .types(ClassType.create(ChatMemoryStore.class))
                .setRuntimeInit()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(MongoClient.class)),
                        mongoClientQualifier)
                .createWith(recorder.chatMemoryStoreFunction(
                        clientName,
                        buildTimeConfig.database(),
                        buildTimeConfig.collection()))
                .done());
    }
}
