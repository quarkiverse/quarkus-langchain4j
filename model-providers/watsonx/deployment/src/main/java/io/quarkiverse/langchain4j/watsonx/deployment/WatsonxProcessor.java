package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.TOKEN_COUNT_ESTIMATOR;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TEXT_EXTRACTION;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.deployment.items.BuiltinServiceBuildItem;
import io.quarkiverse.langchain4j.watsonx.deployment.items.TextExtractionClassBuildItem;
import io.quarkiverse.langchain4j.watsonx.runtime.BuiltinServiceRecorder;
import io.quarkiverse.langchain4j.watsonx.runtime.WatsonxRecorder;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxFixedRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
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
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBuiltinToolBeans(
            BuiltinServiceRecorder recorder,
            LangChain4jWatsonxConfig runtimeConfig,
            List<BuiltinServiceBuildItem> builtinToolClasses,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (BuiltinServiceBuildItem builtinToolClass : builtinToolClasses) {
            var builder = SyntheticBeanBuildItem
                    .configure(builtinToolClass.getDotName())
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class);

            if (builtinToolClass.getDotName().equals(WatsonxDotNames.GOOGLE_SEARCH_SERVICE))
                builder.supplier(recorder.googleSearch(runtimeConfig));
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.WEB_CRAWLER_SERVICE))
                builder.supplier(recorder.webCrawler(runtimeConfig));
            else if (builtinToolClass.getDotName().equals(WatsonxDotNames.WEATHER_SERVICE))
                builder.supplier(recorder.weather(runtimeConfig));
            else
                throw new RuntimeException("BuiltinServiceClass not recognised");

            beanProducer.produce(builder.done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(WatsonxRecorder recorder, LangChain4jWatsonxConfig runtimeConfig,
            LangChain4jWatsonxFixedRuntimeConfig fixedRuntimeConfig,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            List<SelectedScoringModelProviderBuildItem> selectedScoring,
            List<TextExtractionClassBuildItem> selectedTextExtraction,
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
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.textExtraction(runtimeConfig, configName));
                addQualifierIfNecessary(textExtractionBuilder, configName);
                beanProducer.produce(textExtractionBuilder.done());
            }
        }

        for (var selected : selectedChatItem) {

            if (!PROVIDER.equals(selected.getProvider()))
                continue;

            String configName = selected.getConfigName();
            String mode = NamedConfigUtil.isDefault(configName)
                    ? fixedRuntimeConfig.defaultConfig().mode()
                    : fixedRuntimeConfig.namedConfig().get(configName).mode();

            Function<SyntheticCreationalContext<ChatLanguageModel>, ChatLanguageModel> chatLanguageModel;
            Function<SyntheticCreationalContext<StreamingChatLanguageModel>, StreamingChatLanguageModel> streamingChatLanguageModel;

            if (mode.equalsIgnoreCase("chat")) {
                chatLanguageModel = recorder.chatModel(runtimeConfig, configName);
                streamingChatLanguageModel = recorder.streamingChatModel(runtimeConfig, configName);
            } else if (mode.equalsIgnoreCase("generation")) {
                chatLanguageModel = recorder.generationModel(runtimeConfig, configName);
                streamingChatLanguageModel = recorder.generationStreamingModel(runtimeConfig, configName);
            } else {
                throw new RuntimeException(
                        "The \"mode\" value for the model \"%s\" is not valid. Choose one between [\"chat\", \"generation\"]"
                                .formatted(mode, configName));
            }

            var chatBuilder = SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(chatLanguageModel);

            addQualifierIfNecessary(chatBuilder, configName);
            beanProducer.produce(chatBuilder.done());

            var tokenizerBuilder = SyntheticBeanBuildItem
                    .configure(TOKEN_COUNT_ESTIMATOR)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(chatLanguageModel);

            addQualifierIfNecessary(tokenizerBuilder, configName);
            beanProducer.produce(tokenizerBuilder.done());

            var streamingBuilder = SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(streamingChatLanguageModel);

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

    private boolean isABuiltinToolClass(DotName dotName) {
        if (dotName.equals(WatsonxDotNames.WEB_CRAWLER_SERVICE))
            return true;
        else if (dotName.equals(WatsonxDotNames.GOOGLE_SEARCH_SERVICE))
            return true;
        else if (dotName.equals(WatsonxDotNames.WEATHER_SERVICE))
            return true;
        else
            return false;
    }

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that Jackson is used. This is
     * not a proper solution as it affects all clients, but it's better than the having the reader/writers be selected at
     * random.
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
