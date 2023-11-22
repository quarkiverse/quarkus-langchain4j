package io.quarkiverse.langchain4j.azure.openai.deployment;

import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.CHAT_MODEL;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.azure.openai.runtime.AzureOpenAiRecorder;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.Langchain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ModerationModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;

public class AzureOpenAiProcessor {

    private static final String FEATURE = "langchain4j-azure-openai";
    private static final String PROVIDER = "azure-openai";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            BuildProducer<ModerationModelProviderCandidateBuildItem> moderationProducer,
            Langchain4jAzureOpenAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.moderationModel().enabled().isEmpty() || config.moderationModel().enabled().get()) {
            moderationProducer.produce(new ModerationModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(AzureOpenAiRecorder recorder,
            Optional<SelectedChatModelProviderBuildItem> selectedChatItem,
            Optional<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            Optional<SelectedModerationModelProviderBuildItem> selectedModeration,
            Langchain4jAzureOpenAiConfig config,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        if (selectedChatItem.isPresent() && PROVIDER.equals(selectedChatItem.get().getProvider())) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.chatModel(config))
                    .done());

            //            beanProducer.produce(SyntheticBeanBuildItem
            //                    .configure(STREAMING_CHAT_MODEL)
            //                    .setRuntimeInit()
            //                    .defaultBean()
            //                    .scope(ApplicationScoped.class)
            //                    .supplier(recorder.streamingChatModel(config))
            //                    .done());
        }

        if (selectedEmbedding.isPresent() && PROVIDER.equals(selectedEmbedding.get().getProvider())) {
            //            beanProducer.produce(SyntheticBeanBuildItem
            //                    .configure(EMBEDDING_MODEL)
            //                    .setRuntimeInit()
            //                    .defaultBean()
            //                    .scope(ApplicationScoped.class)
            //                    .supplier(recorder.embeddingModel(config))
            //                    .done());
        }

        if (selectedModeration.isPresent() && PROVIDER.equals(selectedModeration.get().getProvider())) {
            //            beanProducer.produce(SyntheticBeanBuildItem
            //                    .configure(MODERATION_MODEL)
            //                    .setRuntimeInit()
            //                    .defaultBean()
            //                    .scope(ApplicationScoped.class)
            //                    .supplier(recorder.moderationModel(config))
            //                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void cleanUp(AzureOpenAiRecorder recorder, ShutdownContextBuildItem shutdown) {
        recorder.cleanUp(shutdown);
    }
}
