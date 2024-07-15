package io.quarkiverse.langchain4j.ollama.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.devservice.Langchain4jDevServicesEnabled;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesChatModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesEmbeddingModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.ollama.runtime.OllamaRecorder;
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.configuration.ConfigUtils;

public class OllamaProcessor {

    private static final String FEATURE = "langchain4j-ollama";
    private static final String PROVIDER = "ollama";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            LangChain4jOllamaOpenAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = Langchain4jDevServicesEnabled.class)
    public void devServicesSupport(List<SelectedChatModelProviderBuildItem> selectedChatModels,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
            LangChain4jOllamaFixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<DevServicesChatModelRequiredBuildItem> chatProducer,
            BuildProducer<DevServicesEmbeddingModelRequiredBuildItem> embeddingProducer) {
        for (var selected : selectedChatModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                String baseUrlProperty = String.format("quarkus.langchain4j.ollama%s%s",
                        NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), "base-url");

                // same as with other dev services, we only need it if the base URL has not been set by the user
                if (!ConfigUtils.isPropertyPresent(baseUrlProperty)) {
                    String modelId = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().chatModel().modelId()
                            : fixedRuntimeConfig.namedConfig().get(configName).chatModel().modelId();
                    chatProducer.produce(new DevServicesChatModelRequiredBuildItem(PROVIDER, modelId, baseUrlProperty));
                }
            }
        }

        for (var selected : selectedEmbeddingModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                String baseUrlProperty = String.format("quarkus.langchain4j.ollama%s%s",
                        NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), "base-url");

                // same as with other dev services, we only need it if the base URL has not been set by the user
                if (!ConfigUtils.isPropertyPresent(baseUrlProperty)) {
                    String modelId = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().embeddingModel().modelId()
                            : fixedRuntimeConfig.namedConfig().get(configName).embeddingModel().modelId();
                    embeddingProducer
                            .produce(new DevServicesEmbeddingModelRequiredBuildItem(PROVIDER, modelId, baseUrlProperty));
                }
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(OllamaRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            LangChain4jOllamaConfig config,
            LangChain4jOllamaFixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatItem) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.chatModel(config, fixedRuntimeConfig, configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingBuilder = SyntheticBeanBuildItem
                        .configure(STREAMING_CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.streamingChatModel(config, fixedRuntimeConfig, configName));
                addQualifierIfNecessary(streamingBuilder, configName);
                beanProducer.produce(streamingBuilder.done());
            }
        }

        for (var selected : selectedEmbedding) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(EMBEDDING_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.embeddingModel(config, fixedRuntimeConfig, configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());
            }
        }

    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }
}
