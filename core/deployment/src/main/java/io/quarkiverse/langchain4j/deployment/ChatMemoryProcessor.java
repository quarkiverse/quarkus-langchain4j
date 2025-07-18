package io.quarkiverse.langchain4j.deployment;

import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.runtime.ChatMemoryRecorder;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class ChatMemoryProcessor {

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupBeans(ChatMemoryBuildConfig buildConfig,
            ChatMemoryRecorder recorder,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {

        Function<SyntheticCreationalContext<ChatMemoryProvider>, ChatMemoryProvider> fun;

        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(ChatMemoryProvider.class)
                .setRuntimeInit()
                .addInjectionPoint(ClassType.create(ChatMemoryStore.class))
                .scope(ApplicationScoped.class)
                .defaultBean();

        if (buildConfig.type() == ChatMemoryBuildConfig.Type.MESSAGE_WINDOW) {
            fun = recorder.messageWindow();
        } else if (buildConfig.type() == ChatMemoryBuildConfig.Type.TOKEN_WINDOW) {
            configurator.addInjectionPoint(ClassType.create(TokenCountEstimator.class));
            fun = recorder.tokenWindow();
        } else {
            throw new IllegalStateException(
                    "Invalid configuration '" + buildConfig.type() + "' used in 'quarkus.langchain4j.chat-memory.type'");
        }
        configurator.createWith(fun);

        syntheticBeanProducer.produce(configurator.done());
        unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(ChatMemoryStore.class));
    }
}
