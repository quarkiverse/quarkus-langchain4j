package io.quarkiverse.langchain4j.gpullama3.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.gpullama3.runtime.GpuLlama3Recorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class GpuLlama3Processor {

    private static final String PROVIDER = "gpu-llama3";

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            LangChain4jGPULlama3BuildTimeConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(GpuLlama3Recorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();

                var builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.chatModel(configName));

                beanProducer.produce(builder.done());
            }
        }
    }

}
