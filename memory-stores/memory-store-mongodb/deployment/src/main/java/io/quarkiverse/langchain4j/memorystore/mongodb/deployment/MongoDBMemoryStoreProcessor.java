package io.quarkiverse.langchain4j.memorystore.mongodb.deployment;

import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import com.mongodb.client.MongoClient;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.MongoDBChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.mongodb.runtime.MongoDBMemoryStoreRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.mongodb.deployment.MongoClientNameBuildItem;

class MongoDBMemoryStoreProcessor {

    public static final DotName MONGODB_CHAT_MEMORY_STORE = DotName.createSimple(MongoDBChatMemoryStore.class);
    private static final String FEATURE = "langchain4j-memory-store-mongodb";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public MongoClientNameBuildItem requestMongoClient(MongoDBMemoryStoreBuildTimeConfig config) {
        return new MongoClientNameBuildItem(config.clientName().orElse("default"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createMemoryStoreBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            MongoDBMemoryStoreRecorder recorder,
            MongoDBMemoryStoreBuildTimeConfig buildTimeConfig) {
        String clientName = buildTimeConfig.clientName().orElse(null);
        AnnotationInstance mongoClientQualifier;
        mongoClientQualifier = AnnotationInstance.builder(MongoClientName.class)
                .add("value", Objects.requireNonNullElse(clientName, "default"))
                .build();

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
