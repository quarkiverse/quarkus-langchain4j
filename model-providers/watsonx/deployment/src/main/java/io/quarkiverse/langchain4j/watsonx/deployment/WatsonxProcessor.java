package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.TOKEN_COUNT_ESTIMATOR;
import static io.quarkiverse.langchain4j.deployment.TemplateUtil.getTemplateFromAnnotationInstance;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.deployment.items.WatsonxChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatterMapper;
import io.quarkiverse.langchain4j.watsonx.runtime.WatsonxRecorder;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxFixedRuntimeConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class WatsonxProcessor {

    private static final Logger log = Logger.getLogger(WatsonxProcessor.class);
    private static final String FEATURE = "langchain4j-watsonx";
    private static final String PROVIDER = "watsonx";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            BuildProducer<ScoringModelProviderCandidateBuildItem> scoringProducer,
            LangChain4jWatsonBuildConfig config) {

        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }

        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }

        if (config.scoringModel().enabled().isEmpty() || config.scoringModel().enabled().get()) {
            scoringProducer.produce(new ScoringModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    void findChatModels(
            LangChain4jWatsonxFixedRuntimeConfig fixedRuntimeConfig,
            CombinedIndexBuildItem indexBuildItem,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            BuildProducer<WatsonxChatModelProviderBuildItem> chatModelBuilder) {

        var index = indexBuildItem.getIndex();
        var annotationInstances = index.getAnnotations(LangChain4jDotNames.REGISTER_AI_SERVICES);

        for (var selected : selectedChatItem) {

            if (!PROVIDER.equals(selected.getProvider())) {
                continue;
            }

            String configName = selected.getConfigName();

            String modelId = NamedConfigUtil.isDefault(configName)
                    ? fixedRuntimeConfig.defaultConfig().chatModel().modelId()
                    : fixedRuntimeConfig.namedConfig().get(configName).chatModel().modelId();

            String mode = NamedConfigUtil.isDefault(configName)
                    ? fixedRuntimeConfig.defaultConfig().chatModel().mode()
                    : fixedRuntimeConfig.namedConfig().get(configName).chatModel().mode();

            if (mode.equalsIgnoreCase("chat")) {

                chatModelBuilder.produce(new WatsonxChatModelProviderBuildItem(configName, mode, null));

            } else if (mode.equalsIgnoreCase("generation")) {

                boolean promptFormatterIsEnabled = NamedConfigUtil.isDefault(configName)
                        ? fixedRuntimeConfig.defaultConfig().chatModel().promptFormatter()
                        : fixedRuntimeConfig.namedConfig().get(configName).chatModel().promptFormatter();

                PromptFormatter promptFormatter = null;

                if (promptFormatterIsEnabled) {
                    promptFormatter = PromptFormatterMapper.get(modelId);
                    if (promptFormatter == null) {
                        log.warnf(
                                "The \"%s\" model does not have a PromptFormatter implementation, no tags are automatically generated.",
                                modelId);
                    }
                }

                var registerAiService = annotationInstances.stream()
                        .filter(annotationInstance -> {
                            var modelName = annotationInstance.value("modelName");
                            if (modelName == null) {
                                return configName.equals(NamedConfigUtil.DEFAULT_NAME);
                            } else {
                                return configName.equals(modelName.asString());
                            }
                        }).findFirst();

                if (!registerAiService.isEmpty()) {

                    var classInfo = registerAiService.get().target().asClass();
                    var tools = classInfo.annotation(LangChain4jDotNames.REGISTER_AI_SERVICES).value("tools");

                    if (tools != null) {
                        if (!promptFormatterIsEnabled)
                            throw new RuntimeException("The prompt-formatter must be enabled to use the tool functionality");

                        if (!PromptFormatterMapper.toolIsSupported(modelId))
                            throw new RuntimeException(
                                    "The tool functionality is not supported for the model \"%s\"".formatted(modelId));
                    }

                    if (promptFormatter != null) {
                        var systemMessage = getTemplateFromAnnotationInstance(
                                classInfo.annotation(LangChain4jDotNames.SYSTEM_MESSAGE));
                        var userMessage = getTemplateFromAnnotationInstance(
                                classInfo.annotation(LangChain4jDotNames.USER_MESSAGE));
                        var tokenAlreadyExist = promptFormatter.tokens().stream()
                                .filter(token -> systemMessage.contains(token) || userMessage.contains(token))
                                .findFirst();

                        if (tokenAlreadyExist.isPresent()) {
                            log.warnf(
                                    "The prompt in the AIService \"%s\" already contains one or more tags for the model \"%s\", the prompt-formatter option is disabled."
                                            .formatted(classInfo.name().toString(), modelId));
                            promptFormatter = null;
                        }
                    }
                }

                chatModelBuilder.produce(new WatsonxChatModelProviderBuildItem(configName, mode, promptFormatter));

            } else {
                throw new RuntimeException(
                        "The \"mode\" value for the model \"%s\" is not valid. Choose one between [\"chat\", \"generation\"]"
                                .formatted(mode, configName));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(WatsonxRecorder recorder, LangChain4jWatsonxConfig runtimeConfig,
            LangChain4jWatsonxFixedRuntimeConfig fixedRuntimeConfig,
            List<WatsonxChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            List<SelectedScoringModelProviderBuildItem> selectedScoring,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatItem) {

            String configName = selected.getConfigName();

            Supplier<ChatLanguageModel> chatLanguageModel;
            Supplier<StreamingChatLanguageModel> streamingChatLanguageModel;

            if (selected.getMode().equals("chat")) {
                chatLanguageModel = recorder.chatModel(runtimeConfig, fixedRuntimeConfig, configName);
                streamingChatLanguageModel = recorder.streamingChatModel(runtimeConfig, fixedRuntimeConfig, configName);
            } else {
                PromptFormatter promptFormatter = selected.getPromptFormatter();
                chatLanguageModel = recorder.generationModel(runtimeConfig, fixedRuntimeConfig, configName, promptFormatter);
                streamingChatLanguageModel = recorder.generationStreamingModel(runtimeConfig, fixedRuntimeConfig, configName,
                        promptFormatter);
            }

            var chatBuilder = SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(chatLanguageModel);

            addQualifierIfNecessary(chatBuilder, configName);
            beanProducer.produce(chatBuilder.done());

            var tokenizerBuilder = SyntheticBeanBuildItem
                    .configure(TOKEN_COUNT_ESTIMATOR)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(chatLanguageModel);

            addQualifierIfNecessary(tokenizerBuilder, configName);
            beanProducer.produce(tokenizerBuilder.done());

            var streamingBuilder = SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(streamingChatLanguageModel);

            addQualifierIfNecessary(streamingBuilder, configName);
            beanProducer.produce(streamingBuilder.done());
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
                        .supplier(recorder.embeddingModel(runtimeConfig, configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());
            }
        }

        for (var selected : selectedScoring) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(SCORING_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.scoringModel(runtimeConfig, configName));
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
