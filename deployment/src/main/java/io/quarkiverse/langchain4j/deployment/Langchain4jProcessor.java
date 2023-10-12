package io.quarkiverse.langchain4j.deployment;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.DotName;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.runtime.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.runtime.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.Langchain4jRecorder;
import io.quarkiverse.langchain4j.runtime.ModelProvider;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class Langchain4jProcessor {

    private static final String FEATURE = "langchain4j";
    private static final DotName CHAT_MODEL = DotName.createSimple(ChatLanguageModel.class);
    private static final DotName LANGUAGE_MODEL = DotName.createSimple(LanguageModel.class);
    private static final DotName EMBEDDING_MODEL = DotName.createSimple(EmbeddingModel.class);
    private static final DotName MODERATION_MODEL = DotName.createSimple(ModerationModel.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.ai4j", "openai4j"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(LangChain4jBuildConfig buildConfig, LangChain4jRuntimeConfig runtimeConfig,
            BeanDiscoveryFinishedBuildItem beanDiscoveryFinished,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            Langchain4jRecorder recorder) {

        boolean chatModelBeanRequested = false;
        boolean languageModelBeanRequested = false;
        boolean embeddingModelBeanRequested = false;
        boolean moderationModelBeanRequested = false;
        for (InjectionPointInfo ip : beanDiscoveryFinished.getInjectionPoints()) {
            DotName requiredName = ip.getRequiredType().name();
            if (CHAT_MODEL.equals(requiredName)) {
                chatModelBeanRequested = true;
            } else if (LANGUAGE_MODEL.equals(requiredName)) {
                languageModelBeanRequested = true;
            } else if (EMBEDDING_MODEL.equals(requiredName)) {
                embeddingModelBeanRequested = true;
            } else if (MODERATION_MODEL.equals(requiredName)) {
                moderationModelBeanRequested = true;
            }
        }

        if (chatModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.chatModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(CHAT_MODEL, "chat-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.chatModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (languageModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.languageModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(LANGUAGE_MODEL, "language-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(LANGUAGE_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.languageModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (embeddingModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.embeddingModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(EMBEDDING_MODEL, "embedding-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(EMBEDDING_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.embeddingModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (moderationModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.moderationModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(MODERATION_MODEL, "moderation-model"));
            }
            if (ModelProvider.OPEN_AI != provider.get()) {
                throw new ConfigurationException("Currently only 'openai' is the only supported provider of ModerationModel");
            }
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(EMBEDDING_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.moderationModel(provider.get(), runtimeConfig))
                    .done());
        }
    }

    private static String configErrorMessage(DotName beanType, String configNamespace) {
        return String.format(
                "When a bean of type '%s' is being used the 'quarkus.langchain4j.%s.provider' property must be set", beanType,
                configNamespace);
    }
}
