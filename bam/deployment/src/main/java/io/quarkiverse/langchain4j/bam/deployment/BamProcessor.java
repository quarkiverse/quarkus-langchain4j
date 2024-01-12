package io.quarkiverse.langchain4j.bam.deployment;

import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.CHAT_MODEL;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.bam.runtime.BamRecorder;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class BamProcessor {

    private static final String FEATURE = "langchain4j-bam";

    private static final String PROVIDER = "bam";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            Langchain4jBamBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(BamRecorder recorder,
            Optional<SelectedChatModelProviderBuildItem> selectedChatItem,
            Langchain4jBamConfig config,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        if (selectedChatItem.isPresent() && PROVIDER.equals(selectedChatItem.get().getProvider())) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.chatModel(config))
                    .done());
        }
    }
}
