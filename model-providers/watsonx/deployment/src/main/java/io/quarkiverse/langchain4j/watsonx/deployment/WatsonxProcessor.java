package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MODERATION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TEXT_CLASSIFICATION;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TEXT_EXTRACTION;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TOOL_SERVICE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ModerationModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.deployment.items.BuiltinServiceBuildItem;
import io.quarkiverse.langchain4j.watsonx.deployment.items.TextClassificationClassBuildItem;
import io.quarkiverse.langchain4j.watsonx.deployment.items.TextExtractionClassBuildItem;
import io.quarkiverse.langchain4j.watsonx.runtime.BuiltinToolRecorder;
import io.quarkiverse.langchain4j.watsonx.runtime.WatsonxRecorder;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyReaderOverrideBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyWriterOverrideBuildItem;
import io.smallrye.config.Priorities;

public class WatsonxProcessor {

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
            BuildProducer<ModerationModelProviderCandidateBuildItem> moderationProducer,
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

        if (config.moderationModel().enabled().isEmpty() || config.moderationModel().enabled().get()) {
            moderationProducer.produce(new ModerationModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    void discoverBuiltinToolBeans(
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<BuiltinServiceBuildItem> producer) {

        Set<DotName> dotNames = new HashSet<>();
        beans.getInjectionPoints().stream()
                .map(ip -> ip.getRequiredType().name())
                .filter(this::isABuiltinToolClass)
                .forEach(dotNames::add);

        if (dotNames.isEmpty())
            return; // Nothing to produce..

        dotNames.stream().map(BuiltinServiceBuildItem::new).forEach(producer::produce);
    }

    @BuildStep
    void discoverTextExtractionBeans(
            CombinedIndexBuildItem indexBuildItem,
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<TextExtractionClassBuildItem> producer) {

        Set<String> qualifiers = beans.getInjectionPoints().stream()
                .filter(injectionPoint -> injectionPoint.getRequiredType().name().equals(WatsonxDotNames.TEXT_EXTRACTION))
                .map(injectionPoint -> {
                    AnnotationInstance modelName = injectionPoint.getRequiredQualifier(LangChain4jDotNames.MODEL_NAME);
                    if (modelName != null) {
                        String value = modelName.value().asString();
                        if ((value != null) && !value.isEmpty()) {
                            return value;
                        }
                    }
                    if (modelName == null && injectionPoint.isProgrammaticLookup()) {
                        return null;
                    }
                    return NamedConfigUtil.DEFAULT_NAME;
                }).collect(Collectors.toSet());

        qualifiers.stream()
                .map(TextExtractionClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    void discoverTextClassificationBeans(
            CombinedIndexBuildItem indexBuildItem,
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<TextClassificationClassBuildItem> producer) {

        Set<String> qualifiers = beans.getInjectionPoints().stream()
                .filter(injectionPoint -> injectionPoint.getRequiredType().name().equals(WatsonxDotNames.TEXT_CLASSIFICATION))
                .map(injectionPoint -> {
                    AnnotationInstance modelName = injectionPoint.getRequiredQualifier(LangChain4jDotNames.MODEL_NAME);
                    if (modelName != null) {
                        String value = modelName.value().asString();
                        if ((value != null) && !value.isEmpty()) {
                            return value;
                        }
                    }
                    if (modelName == null && injectionPoint.isProgrammaticLookup()) {
                        return null;
                    }
                    return NamedConfigUtil.DEFAULT_NAME;
                }).collect(Collectors.toSet());

        qualifiers.stream()
                .map(TextClassificationClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBuiltinToolBeans(
            BuiltinToolRecorder recorder,
            List<BuiltinServiceBuildItem> builtinToolClasses,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        if (builtinToolClasses.isEmpty())
            return;

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(TOOL_SERVICE)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.toolService())
                .done());

        for (BuiltinServiceBuildItem builtinToolClass : builtinToolClasses) {
            var builder = SyntheticBeanBuildItem
                    .configure(builtinToolClass.getDotName())
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(WatsonxDotNames.TOOL_SERVICE) }, null))
                    .scope(ApplicationScoped.class);

            if (builtinToolClass.getDotName().equals(WatsonxDotNames.GOOGLE_SEARCH_TOOL))
                builder.createWith(recorder.googleSearch());
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.WEB_CRAWLER_TOOL))
                builder.createWith(recorder.webCrawler());
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.WEATHER_TOOL))
                builder.createWith(recorder.weather());
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.WIKIPEDIA_TOOL))
                builder.createWith(recorder.wikipedia());
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.TAVILY_SEARCH_TOOL))
                builder.createWith(recorder.tavilySearch());
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.PYTHON_INTERPRETER_TOOL))
                builder.createWith(recorder.pythonInterpreter());
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.RAG_QUERY_TOOL))
                builder.createWith(recorder.ragQuery());
            else
                throw new RuntimeException("BuiltinServiceClass not recognised");

            beanProducer.produce(builder.done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(WatsonxRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            List<SelectedScoringModelProviderBuildItem> selectedScoring,
            List<SelectedModerationModelProviderBuildItem> selectedModeration,
            List<TextExtractionClassBuildItem> selectedTextExtraction,
            List<TextClassificationClassBuildItem> selectedTextClassification,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedTextExtraction) {

            String configName = selected.getQualifier();

            var textExtraction = selectedTextExtraction.stream()
                    .filter(value -> value.getQualifier().equals(configName))
                    .findFirst();

            if (textExtraction.isPresent()) {
                var textExtractionBuilder = SyntheticBeanBuildItem
                        .configure(TEXT_EXTRACTION)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.textExtraction(configName));
                addQualifierIfNecessary(textExtractionBuilder, configName);
                beanProducer.produce(textExtractionBuilder.done());
            }
        }

        for (var selected : selectedTextClassification) {

            String configName = selected.getQualifier();

            var textClassification = selectedTextClassification.stream()
                    .filter(value -> value.getQualifier().equals(configName))
                    .findFirst();

            if (textClassification.isPresent()) {
                var textClassificationBuilder = SyntheticBeanBuildItem
                        .configure(TEXT_CLASSIFICATION)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.textClassification(configName));
                addQualifierIfNecessary(textClassificationBuilder, configName);
                beanProducer.produce(textClassificationBuilder.done());
            }
        }

        for (var selected : selectedChatItem) {

            if (!PROVIDER.equals(selected.getProvider()))
                continue;

            String configName = selected.getConfigName();

            var chatModel = recorder.chatModel(configName);
            var streamingChatModel = recorder.streamingChatModel(configName);

            var chatBuilder = SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(chatModel);

            addQualifierIfNecessary(chatBuilder, configName);
            beanProducer.produce(chatBuilder.done());

            var streamingBuilder = SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(streamingChatModel);

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
                        .supplier(recorder.embeddingModel(configName));
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
                        .supplier(recorder.scoringModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());
            }
        }

        for (var selected : selectedModeration) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(MODERATION_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.moderationModel(configName));
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

    private boolean isABuiltinToolClass(DotName dotName) {
        if (dotName.equals(WatsonxDotNames.WEB_CRAWLER_TOOL))
            return true;
        else if (dotName.equals(WatsonxDotNames.GOOGLE_SEARCH_TOOL))
            return true;
        else if (dotName.equals(WatsonxDotNames.WEATHER_TOOL))
            return true;
        else if (dotName.equals(WatsonxDotNames.WIKIPEDIA_TOOL))
            return true;
        else if (dotName.equals(WatsonxDotNames.TAVILY_SEARCH_TOOL))
            return true;
        else if (dotName.equals(WatsonxDotNames.PYTHON_INTERPRETER_TOOL))
            return true;
        else if (dotName.equals(WatsonxDotNames.RAG_QUERY_TOOL))
            return true;
        else
            return false;
    }

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that Jackson is used.
     * This is not a proper solution as it affects all clients, but it's better than the having the reader/writers be selected
     * at random.
     */
    @BuildStep
    public void deprioritizeJsonb(Capabilities capabilities,
            BuildProducer<MessageBodyReaderOverrideBuildItem> readerOverrideProducer,
            BuildProducer<MessageBodyWriterOverrideBuildItem> writerOverrideProducer) {
        if (capabilities.isPresent(Capability.REST_CLIENT_REACTIVE_JSONB)) {
            readerOverrideProducer.produce(
                    new MessageBodyReaderOverrideBuildItem("org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyReader",
                            Priorities.APPLICATION + 1, true));
            writerOverrideProducer.produce(new MessageBodyWriterOverrideBuildItem(
                    "org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyWriter", Priorities.APPLICATION + 1, true));
        }
    }
}
