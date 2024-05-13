package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.vertexai.runtime.gemini.VertexAiGeminiRecorder;
import io.quarkiverse.langchain4j.vertexai.runtime.gemini.config.LangChain4jVertexAiGeminiConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class VertexAiGeminiProcessor {

    private static final String FEATURE = "langchain4j-vertexai-gemini";
    private static final String PROVIDER = "vertexai-gemini";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            LangChain4jVertexAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(VertexAiGeminiRecorder recorder, List<SelectedChatModelProviderBuildItem> selectedChatItem,
            LangChain4jVertexAiGeminiConfig config, BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedChatItem) {
            if (PROVIDER.equals(selected.getProvider())) {
                var configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.chatModel(config, configName));

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
