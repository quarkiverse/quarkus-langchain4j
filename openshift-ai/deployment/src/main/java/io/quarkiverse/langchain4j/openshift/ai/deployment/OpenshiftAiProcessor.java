package io.quarkiverse.langchain4j.openshift.ai.deployment;

import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.CHAT_MODEL;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.openshiftai.runtime.OpenshiftAiRecorder;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.Langchain4jOpenshiftAiConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class OpenshiftAiProcessor {

    private static final String FEATURE = "langchain4j-openshift-ai";

    private static final String PROVIDER = "openshift-ai";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            Langchain4jOpenshiftAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(OpenshiftAiRecorder recorder,
            Optional<SelectedChatModelProviderBuildItem> selectedChatItem,
            Langchain4jOpenshiftAiConfig config,
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
