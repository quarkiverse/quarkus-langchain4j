package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MODERATION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.CLUSTER_SCHEMA;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.CREATE_SCHEMA;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.IMPROVE_SCHEMA;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.MERGE_SCHEMA;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TEXT_CLASSIFICATION;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TEXT_EXTRACTION;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxDotNames.TOOL_SERVICE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import dev.langchain4j.model.watsonx.WatsonxEmbeddingModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayStreamingChatModel;
import dev.langchain4j.model.watsonx.WatsonxModerationModel;
import dev.langchain4j.model.watsonx.WatsonxScoringModel;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
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
import io.quarkiverse.langchain4j.watsonx.deployment.items.ClusterSchemaClassBuildItem;
import io.quarkiverse.langchain4j.watsonx.deployment.items.CreateSchemaClassBuildItem;
import io.quarkiverse.langchain4j.watsonx.deployment.items.ImproveSchemaClassBuildItem;
import io.quarkiverse.langchain4j.watsonx.deployment.items.MergeSchemaClassBuildItem;
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

    private static final DotName WATSONX_CHAT_MODEL_BUILDER = DotName
            .createSimple(WatsonxChatModel.Builder.class);
    private static final DotName WATSONX_STREAMING_CHAT_MODEL_BUILDER = DotName
            .createSimple(WatsonxStreamingChatModel.Builder.class);
    private static final DotName WATSONX_DEPLOYMENT_CHAT_MODEL_BUILDER = DotName
            .createSimple(WatsonxDeploymentChatModel.Builder.class);
    private static final DotName WATSONX_DEPLOYMENT_STREAMING_CHAT_MODEL_BUILDER = DotName
            .createSimple(WatsonxDeploymentStreamingChatModel.Builder.class);
    private static final DotName WATSONX_GATEWAY_CHAT_MODEL_BUILDER = DotName
            .createSimple(WatsonxGatewayChatModel.Builder.class);
    private static final DotName WATSONX_GATEWAY_STREAMING_CHAT_MODEL_BUILDER = DotName
            .createSimple(WatsonxGatewayStreamingChatModel.Builder.class);
    private static final DotName WATSONX_EMBEDDING_MODEL_BUILDER = DotName
            .createSimple(WatsonxEmbeddingModel.Builder.class);
    private static final DotName WATSONX_SCORING_MODEL_BUILDER = DotName
            .createSimple(WatsonxScoringModel.Builder.class);
    private static final DotName WATSONX_MODERATION_MODEL_BUILDER = DotName
            .createSimple(WatsonxModerationModel.Builder.class);
    private static final DotName TEXT_EXTRACTION_SERVICE_BUILDER = DotName
            .createSimple(TextExtractionService.Builder.class);
    private static final DotName TEXT_CLASSIFICATION_SERVICE_BUILDER = DotName
            .createSimple(TextClassificationService.Builder.class);
    private static final DotName CREATE_SCHEMA_SERVICE_BUILDER = DotName
            .createSimple(CreateSchemaService.Builder.class);
    private static final DotName IMPROVE_SCHEMA_SERVICE_BUILDER = DotName
            .createSimple(ImproveSchemaService.Builder.class);
    private static final DotName MERGE_SCHEMA_SERVICE_BUILDER = DotName
            .createSimple(MergeSchemaService.Builder.class);
    private static final DotName CLUSTER_SCHEMA_SERVICE_BUILDER = DotName
            .createSimple(ClusterSchemaService.Builder.class);

    private static final AnnotationInstance ANY = AnnotationInstance.builder(DotName.createSimple(
            Any.class)).build();

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

        configNamesOf(beans, WatsonxDotNames.TEXT_EXTRACTION).stream()
                .map(TextExtractionClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    void discoverTextClassificationBeans(
            CombinedIndexBuildItem indexBuildItem,
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<TextClassificationClassBuildItem> producer) {

        configNamesOf(beans, WatsonxDotNames.TEXT_CLASSIFICATION).stream()
                .map(TextClassificationClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    void discoverCreateSchemaBeans(
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<CreateSchemaClassBuildItem> producer) {

        configNamesOf(beans, WatsonxDotNames.CREATE_SCHEMA).stream()
                .map(CreateSchemaClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    void discoverImproveSchemaBeans(
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<ImproveSchemaClassBuildItem> producer) {

        configNamesOf(beans, WatsonxDotNames.IMPROVE_SCHEMA).stream()
                .map(ImproveSchemaClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    void discoverMergeSchemaBeans(
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<MergeSchemaClassBuildItem> producer) {

        configNamesOf(beans, WatsonxDotNames.MERGE_SCHEMA).stream()
                .map(MergeSchemaClassBuildItem::new)
                .forEach(producer::produce);
    }

    @BuildStep
    void discoverClusterSchemaBeans(
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<ClusterSchemaClassBuildItem> producer) {

        configNamesOf(beans, WatsonxDotNames.CLUSTER_SCHEMA).stream()
                .map(ClusterSchemaClassBuildItem::new)
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
            List<CreateSchemaClassBuildItem> selectedCreateSchema,
            List<ImproveSchemaClassBuildItem> selectedImproveSchema,
            List<MergeSchemaClassBuildItem> selectedMergeSchema,
            List<ClusterSchemaClassBuildItem> selectedClusterSchema,
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
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(TEXT_EXTRACTION_SERVICE_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.textExtraction(configName));
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
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(TEXT_CLASSIFICATION_SERVICE_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.textClassification(configName));
                addQualifierIfNecessary(textClassificationBuilder, configName);
                beanProducer.produce(textClassificationBuilder.done());
            }
        }

        for (var selected : selectedCreateSchema) {

            String configName = selected.getQualifier();

            var createSchemaBuilder = SyntheticBeanBuildItem
                    .configure(CREATE_SCHEMA)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(CREATE_SCHEMA_SERVICE_BUILDER) }, null) },
                            null), ANY)
                    .createWith(recorder.createSchema(configName));
            addQualifierIfNecessary(createSchemaBuilder, configName);
            beanProducer.produce(createSchemaBuilder.done());
        }

        for (var selected : selectedImproveSchema) {

            String configName = selected.getQualifier();

            var improveSchemaBuilder = SyntheticBeanBuildItem
                    .configure(IMPROVE_SCHEMA)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(IMPROVE_SCHEMA_SERVICE_BUILDER) }, null) },
                            null), ANY)
                    .createWith(recorder.improveSchema(configName));
            addQualifierIfNecessary(improveSchemaBuilder, configName);
            beanProducer.produce(improveSchemaBuilder.done());
        }

        for (var selected : selectedMergeSchema) {

            String configName = selected.getQualifier();

            var mergeSchemaBuilder = SyntheticBeanBuildItem
                    .configure(MERGE_SCHEMA)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(MERGE_SCHEMA_SERVICE_BUILDER) }, null) },
                            null), ANY)
                    .createWith(recorder.mergeSchema(configName));
            addQualifierIfNecessary(mergeSchemaBuilder, configName);
            beanProducer.produce(mergeSchemaBuilder.done());
        }

        for (var selected : selectedClusterSchema) {

            String configName = selected.getQualifier();

            var clusterSchemaBuilder = SyntheticBeanBuildItem
                    .configure(CLUSTER_SCHEMA)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(CLUSTER_SCHEMA_SERVICE_BUILDER) }, null) },
                            null), ANY)
                    .createWith(recorder.clusterSchema(configName));
            addQualifierIfNecessary(clusterSchemaBuilder, configName);
            beanProducer.produce(clusterSchemaBuilder.done());
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
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(WATSONX_CHAT_MODEL_BUILDER) }, null) },
                            null), ANY)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(WATSONX_DEPLOYMENT_CHAT_MODEL_BUILDER) }, null) },
                            null), ANY)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(WATSONX_GATEWAY_CHAT_MODEL_BUILDER) }, null) },
                            null), ANY)
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
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(WATSONX_STREAMING_CHAT_MODEL_BUILDER) }, null) },
                            null), ANY)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] {
                                            ClassType.create(WATSONX_DEPLOYMENT_STREAMING_CHAT_MODEL_BUILDER) },
                                    null) },
                            null), ANY)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                    new Type[] { ClassType.create(WATSONX_GATEWAY_STREAMING_CHAT_MODEL_BUILDER) },
                                    null) },
                            null), ANY)
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
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(WATSONX_EMBEDDING_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.embeddingModel(configName));
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
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(WATSONX_SCORING_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.scoringModel(configName));
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
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(WATSONX_MODERATION_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.moderationModel(configName));
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

    private Set<String> configNamesOf(BeanDiscoveryFinishedBuildItem beans, DotName type) {
        return beans.getInjectionPoints().stream()
                .filter(injectionPoint -> injectionPoint.getRequiredType().name().equals(type))
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
