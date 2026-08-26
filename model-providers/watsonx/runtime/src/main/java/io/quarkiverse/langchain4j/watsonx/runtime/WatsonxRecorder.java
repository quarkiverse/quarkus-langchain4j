package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import static io.quarkiverse.langchain4j.watsonx.runtime.AuthenticatorCache.getOrCreateTokenGenerator;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import com.ibm.watsonx.ai.chat.ChatModeration;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Response;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Think;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.detection.detector.BaseDetector;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Hap;
import com.ibm.watsonx.ai.detection.detector.Pii;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatParameters.Cache;
import com.ibm.watsonx.ai.textprocessing.schema.cluster.ClusterSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaService;
import com.ibm.watsonx.ai.textprocessing.schema.merge.MergeSchemaService;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import dev.langchain4j.model.watsonx.WatsonxEmbeddingModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayEmbeddingModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayImageModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayStreamingChatModel;
import dev.langchain4j.model.watsonx.WatsonxModerationModel;
import dev.langchain4j.model.watsonx.WatsonxScoringModel;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ClusterSchemaConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.CommonChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.CreateSchemaConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.DeploymentChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.FoundationChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.FoundationChatModelConfig.ExtractionTagsConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.FoundationChatModelConfig.ThinkingConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GatewayChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GatewayChatModelConfig.CacheConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GatewayEmbeddingModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GatewayImageModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ImproveSchemaConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig.WatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.MergeSchemaConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationModelConfig.GraniteGuardianConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationModelConfig.HapConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationsConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ScoringModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.TextClassificationConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.TextExtractionConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class WatsonxRecorder {

    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];
    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxChatModel.Builder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxStreamingChatModel.Builder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxDeploymentChatModel.Builder>>> DEPLOYMENT_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxDeploymentStreamingChatModel.Builder>>> DEPLOYMENT_STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxGatewayChatModel.Builder>>> GATEWAY_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxGatewayStreamingChatModel.Builder>>> GATEWAY_STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxEmbeddingModel.Builder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxGatewayEmbeddingModel.Builder>>> GATEWAY_EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxGatewayImageModel.Builder>>> GATEWAY_IMAGE_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxScoringModel.Builder>>> SCORING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<WatsonxModerationModel.Builder>>> MODERATION_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<TextExtractionService.Builder>>> TEXT_EXTRACTION_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<TextClassificationService.Builder>>> TEXT_CLASSIFICATION_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<CreateSchemaService.Builder>>> CREATE_SCHEMA_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<ImproveSchemaService.Builder>>> IMPROVE_SCHEMA_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<MergeSchemaService.Builder>>> MERGE_SCHEMA_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<ClusterSchemaService.Builder>>> CLUSTER_SCHEMA_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jWatsonxConfig> runtimeConfig;

    public WatsonxRecorder(RuntimeValue<LangChain4jWatsonxConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (!watsonxConfig.enableIntegration()) {
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return new DisabledChatModel();
                }
            };
        }

        ChatBackend backend = resolveChatBackend(configName);
        var configProblems = checkConfigurations(configName, backend);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        return switch (backend) {
            case STANDARD -> standardChatModel(configName);
            case DEPLOYMENT -> deploymentChatModel(configName);
            case GATEWAY -> gatewayChatModel(configName);
        };
    }

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (!watsonxConfig.enableIntegration()) {
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return new DisabledStreamingChatModel();
                }
            };
        }

        ChatBackend backend = resolveChatBackend(configName);
        var configProblems = checkConfigurations(configName, backend);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        return switch (backend) {
            case STANDARD -> standardStreamingChatModel(configName);
            case DEPLOYMENT -> deploymentStreamingChatModel(configName);
            case GATEWAY -> gatewayStreamingChatModel(configName);
        };
    }

    private Function<SyntheticCreationalContext<ChatModel>, ChatModel> standardChatModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        ChatModelConfig chatModelConfig = specificConfig.chatModel();

        WatsonxChatModel.Builder builder = WatsonxChatModel.builder()
                .modelName(chatModelConfig.modelName())
                .projectId(firstOrDefault(defaultConfig.projectId().orElse(null), specificConfig.projectId()))
                .spaceId(firstOrDefault(defaultConfig.spaceId().orElse(null), specificConfig.spaceId()))
                .moderations(resolveModerations(chatModelConfig))
                .guidedGrammar(chatModelConfig.guidedGrammar().orElse(null))
                .guidedRegex(chatModelConfig.guidedRegex().orElse(null))
                .lengthPenalty(chatModelConfig.lengthPenalty().orElse(null))
                .repetitionPenalty(chatModelConfig.repetitionPenalty().orElse(null))
                .baseUrl(resolveBaseUrl(specificConfig, defaultConfig))
                .version(specificConfig.version().orElse(null))
                .timeout(specificConfig.timeout().orElse(null))
                .frequencyPenalty(chatModelConfig.frequencyPenalty().orElse(null))
                .logprobs(chatModelConfig.logprobs().orElse(null))
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxOutputTokens(chatModelConfig.maxOutputTokens())
                .presencePenalty(chatModelConfig.presencePenalty().orElse(null))
                .seed(chatModelConfig.seed().orElse(null))
                .stopSequences(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP().orElse(null))
                .toolChoice(resolveToolChoice(chatModelConfig))
                .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                .logRequests(resolveLogRequests(defaultConfig, specificConfig, chatModelConfig))
                .logResponses(resolveLogResponses(defaultConfig, specificConfig, chatModelConfig));

        if (chatModelConfig.guidedChoice().isPresent())
            builder.guidedChoice(chatModelConfig.guidedChoice().orElseThrow());

        Thinking thinking = resolveThinking(chatModelConfig);
        if (nonNull(thinking))
            builder.thinking(thinking);

        ResponseFormat responseFormat = resolveResponseFormat(chatModelConfig);
        if (nonNull(responseFormat))
            builder.responseFormat(responseFormat);

        Capability capability = resolveCapability(chatModelConfig);
        if (nonNull(capability))
            builder.supportedCapabilities(capability);

        return chatModelFunction(configName, chatModelConfig,
                new BiFunction<SyntheticCreationalContext<ChatModel>, Authenticator, ChatModel>() {
                    @Override
                    public ChatModel apply(SyntheticCreationalContext<ChatModel> context, Authenticator authenticator) {
                        builder.authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList());
                        ModelBuilderCustomizer.applyCustomizers(
                                context.getInjectedReference(CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                                builder, configName);
                        return builder.build();
                    }
                });
    }

    private Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> standardStreamingChatModel(
            String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        ChatModelConfig chatModelConfig = specificConfig.chatModel();

        WatsonxStreamingChatModel.Builder builder = WatsonxStreamingChatModel.builder()
                .modelName(chatModelConfig.modelName())
                .projectId(firstOrDefault(defaultConfig.projectId().orElse(null), specificConfig.projectId()))
                .spaceId(firstOrDefault(defaultConfig.spaceId().orElse(null), specificConfig.spaceId()))
                .moderations(resolveModerations(chatModelConfig))
                .guidedGrammar(chatModelConfig.guidedGrammar().orElse(null))
                .guidedRegex(chatModelConfig.guidedRegex().orElse(null))
                .lengthPenalty(chatModelConfig.lengthPenalty().orElse(null))
                .repetitionPenalty(chatModelConfig.repetitionPenalty().orElse(null))
                .baseUrl(resolveBaseUrl(specificConfig, defaultConfig))
                .version(specificConfig.version().orElse(null))
                .timeout(specificConfig.timeout().orElse(null))
                .frequencyPenalty(chatModelConfig.frequencyPenalty().orElse(null))
                .logprobs(chatModelConfig.logprobs().orElse(null))
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxOutputTokens(chatModelConfig.maxOutputTokens())
                .presencePenalty(chatModelConfig.presencePenalty().orElse(null))
                .seed(chatModelConfig.seed().orElse(null))
                .stopSequences(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP().orElse(null))
                .toolChoice(resolveToolChoice(chatModelConfig))
                .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                .logRequests(resolveLogRequests(defaultConfig, specificConfig, chatModelConfig))
                .logResponses(resolveLogResponses(defaultConfig, specificConfig, chatModelConfig));

        if (chatModelConfig.guidedChoice().isPresent())
            builder.guidedChoice(chatModelConfig.guidedChoice().orElseThrow());

        Thinking thinking = resolveThinking(chatModelConfig);
        if (nonNull(thinking))
            builder.thinking(thinking);

        ResponseFormat responseFormat = resolveResponseFormat(chatModelConfig);
        if (nonNull(responseFormat))
            builder.responseFormat(responseFormat);

        Capability capability = resolveCapability(chatModelConfig);
        if (nonNull(capability))
            builder.supportedCapabilities(capability);

        return streamingChatModelFunction(configName, chatModelConfig,
                new BiFunction<SyntheticCreationalContext<StreamingChatModel>, Authenticator, StreamingChatModel>() {
                    @Override
                    public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context,
                            Authenticator authenticator) {
                        builder.authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList());
                        ModelBuilderCustomizer.applyCustomizers(
                                context.getInjectedReference(STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                        Any.Literal.INSTANCE),
                                builder, configName);
                        return builder.build();
                    }
                });
    }

    private Function<SyntheticCreationalContext<ChatModel>, ChatModel> deploymentChatModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        DeploymentChatModelConfig chatModelConfig = specificConfig.deploymentChatModel();

        WatsonxDeploymentChatModel.Builder builder = WatsonxDeploymentChatModel.builder()
                .deploymentId(chatModelConfig.deploymentId().orElseThrow())
                .guidedGrammar(chatModelConfig.guidedGrammar().orElse(null))
                .guidedRegex(chatModelConfig.guidedRegex().orElse(null))
                .lengthPenalty(chatModelConfig.lengthPenalty().orElse(null))
                .repetitionPenalty(chatModelConfig.repetitionPenalty().orElse(null))
                .baseUrl(resolveBaseUrl(specificConfig, defaultConfig))
                .version(specificConfig.version().orElse(null))
                .timeout(specificConfig.timeout().orElse(null))
                .frequencyPenalty(chatModelConfig.frequencyPenalty().orElse(null))
                .logprobs(chatModelConfig.logprobs().orElse(null))
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxOutputTokens(chatModelConfig.maxOutputTokens())
                .presencePenalty(chatModelConfig.presencePenalty().orElse(null))
                .seed(chatModelConfig.seed().orElse(null))
                .stopSequences(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP().orElse(null))
                .toolChoice(resolveToolChoice(chatModelConfig))
                .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                .logRequests(resolveLogRequests(defaultConfig, specificConfig, chatModelConfig))
                .logResponses(resolveLogResponses(defaultConfig, specificConfig, chatModelConfig));

        if (chatModelConfig.guidedChoice().isPresent())
            builder.guidedChoice(chatModelConfig.guidedChoice().orElseThrow());

        Thinking thinking = resolveThinking(chatModelConfig);
        if (nonNull(thinking))
            builder.thinking(thinking);

        ResponseFormat responseFormat = resolveResponseFormat(chatModelConfig);
        if (nonNull(responseFormat))
            builder.responseFormat(responseFormat);

        Capability capability = resolveCapability(chatModelConfig);
        if (nonNull(capability))
            builder.supportedCapabilities(capability);

        return chatModelFunction(configName, chatModelConfig,
                new BiFunction<SyntheticCreationalContext<ChatModel>, Authenticator, ChatModel>() {
                    @Override
                    public ChatModel apply(SyntheticCreationalContext<ChatModel> context, Authenticator authenticator) {
                        builder.authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList());
                        ModelBuilderCustomizer.applyCustomizers(
                                context.getInjectedReference(DEPLOYMENT_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                        Any.Literal.INSTANCE),
                                builder, configName);
                        return builder.build();
                    }
                });
    }

    private Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> deploymentStreamingChatModel(
            String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        DeploymentChatModelConfig chatModelConfig = specificConfig.deploymentChatModel();

        WatsonxDeploymentStreamingChatModel.Builder builder = WatsonxDeploymentStreamingChatModel.builder()
                .deploymentId(chatModelConfig.deploymentId().orElseThrow())
                .guidedGrammar(chatModelConfig.guidedGrammar().orElse(null))
                .guidedRegex(chatModelConfig.guidedRegex().orElse(null))
                .lengthPenalty(chatModelConfig.lengthPenalty().orElse(null))
                .repetitionPenalty(chatModelConfig.repetitionPenalty().orElse(null))
                .baseUrl(resolveBaseUrl(specificConfig, defaultConfig))
                .version(specificConfig.version().orElse(null))
                .timeout(specificConfig.timeout().orElse(null))
                .frequencyPenalty(chatModelConfig.frequencyPenalty().orElse(null))
                .logprobs(chatModelConfig.logprobs().orElse(null))
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxOutputTokens(chatModelConfig.maxOutputTokens())
                .presencePenalty(chatModelConfig.presencePenalty().orElse(null))
                .seed(chatModelConfig.seed().orElse(null))
                .stopSequences(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP().orElse(null))
                .toolChoice(resolveToolChoice(chatModelConfig))
                .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                .logRequests(resolveLogRequests(defaultConfig, specificConfig, chatModelConfig))
                .logResponses(resolveLogResponses(defaultConfig, specificConfig, chatModelConfig));

        if (chatModelConfig.guidedChoice().isPresent())
            builder.guidedChoice(chatModelConfig.guidedChoice().orElseThrow());

        Thinking thinking = resolveThinking(chatModelConfig);
        if (nonNull(thinking))
            builder.thinking(thinking);

        ResponseFormat responseFormat = resolveResponseFormat(chatModelConfig);
        if (nonNull(responseFormat))
            builder.responseFormat(responseFormat);

        Capability capability = resolveCapability(chatModelConfig);
        if (nonNull(capability))
            builder.supportedCapabilities(capability);

        return streamingChatModelFunction(configName, chatModelConfig,
                new BiFunction<SyntheticCreationalContext<StreamingChatModel>, Authenticator, StreamingChatModel>() {
                    @Override
                    public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context,
                            Authenticator authenticator) {
                        builder.authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList());
                        ModelBuilderCustomizer.applyCustomizers(
                                context.getInjectedReference(DEPLOYMENT_STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                        Any.Literal.INSTANCE),
                                builder, configName);
                        return builder.build();
                    }
                });
    }

    private Function<SyntheticCreationalContext<ChatModel>, ChatModel> gatewayChatModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        GatewayChatModelConfig chatModelConfig = specificConfig.gatewayChatModel();

        WatsonxGatewayChatModel.Builder builder = WatsonxGatewayChatModel.builder()
                .modelName(chatModelConfig.modelName().orElseThrow())
                .serviceTier(chatModelConfig.serviceTier().orElse(null))
                .reasoningEffort(chatModelConfig.reasoningEffort().orElse(null))
                .cache(resolveCache(chatModelConfig))
                .modalities(chatModelConfig.modalities().orElse(null))
                .store(chatModelConfig.store().orElse(null))
                .parallelToolCalls(chatModelConfig.parallelToolCalls().orElse(null))
                .user(chatModelConfig.user().orElse(null))
                .metadata(chatModelConfig.metadata().isEmpty() ? null : chatModelConfig.metadata())
                .baseUrl(resolveBaseUrl(specificConfig, defaultConfig))
                .version(specificConfig.version().orElse(null))
                .timeout(specificConfig.timeout().orElse(null))
                .frequencyPenalty(chatModelConfig.frequencyPenalty().orElse(null))
                .logprobs(chatModelConfig.logprobs().orElse(null))
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxOutputTokens(chatModelConfig.maxOutputTokens())
                .presencePenalty(chatModelConfig.presencePenalty().orElse(null))
                .seed(chatModelConfig.seed().orElse(null))
                .stopSequences(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP().orElse(null))
                .toolChoice(resolveToolChoice(chatModelConfig))
                .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                .logRequests(resolveLogRequests(defaultConfig, specificConfig, chatModelConfig))
                .logResponses(resolveLogResponses(defaultConfig, specificConfig, chatModelConfig));

        ResponseFormat responseFormat = resolveResponseFormat(chatModelConfig);
        if (nonNull(responseFormat))
            builder.responseFormat(responseFormat);

        Capability capability = resolveCapability(chatModelConfig);
        if (nonNull(capability))
            builder.supportedCapabilities(capability);

        return chatModelFunction(configName, chatModelConfig,
                new BiFunction<SyntheticCreationalContext<ChatModel>, Authenticator, ChatModel>() {
                    @Override
                    public ChatModel apply(SyntheticCreationalContext<ChatModel> context, Authenticator authenticator) {
                        builder.authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList());
                        ModelBuilderCustomizer.applyCustomizers(
                                context.getInjectedReference(GATEWAY_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                        Any.Literal.INSTANCE),
                                builder, configName);
                        return builder.build();
                    }
                });
    }

    private Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> gatewayStreamingChatModel(
            String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        GatewayChatModelConfig chatModelConfig = specificConfig.gatewayChatModel();

        WatsonxGatewayStreamingChatModel.Builder builder = WatsonxGatewayStreamingChatModel.builder()
                .modelName(chatModelConfig.modelName().orElseThrow())
                .serviceTier(chatModelConfig.serviceTier().orElse(null))
                .reasoningEffort(chatModelConfig.reasoningEffort().orElse(null))
                .cache(resolveCache(chatModelConfig))
                .modalities(chatModelConfig.modalities().orElse(null))
                .store(chatModelConfig.store().orElse(null))
                .parallelToolCalls(chatModelConfig.parallelToolCalls().orElse(null))
                .user(chatModelConfig.user().orElse(null))
                .metadata(chatModelConfig.metadata().isEmpty() ? null : chatModelConfig.metadata())
                .baseUrl(resolveBaseUrl(specificConfig, defaultConfig))
                .version(specificConfig.version().orElse(null))
                .timeout(specificConfig.timeout().orElse(null))
                .frequencyPenalty(chatModelConfig.frequencyPenalty().orElse(null))
                .logprobs(chatModelConfig.logprobs().orElse(null))
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxOutputTokens(chatModelConfig.maxOutputTokens())
                .presencePenalty(chatModelConfig.presencePenalty().orElse(null))
                .seed(chatModelConfig.seed().orElse(null))
                .stopSequences(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP().orElse(null))
                .toolChoice(resolveToolChoice(chatModelConfig))
                .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                .strictJsonSchema(chatModelConfig.strictJsonSchema().orElse(null))
                .logRequests(resolveLogRequests(defaultConfig, specificConfig, chatModelConfig))
                .logResponses(resolveLogResponses(defaultConfig, specificConfig, chatModelConfig));

        ResponseFormat responseFormat = resolveResponseFormat(chatModelConfig);
        if (nonNull(responseFormat))
            builder.responseFormat(responseFormat);

        Capability capability = resolveCapability(chatModelConfig);
        if (nonNull(capability))
            builder.supportedCapabilities(capability);

        return streamingChatModelFunction(configName, chatModelConfig,
                new BiFunction<SyntheticCreationalContext<StreamingChatModel>, Authenticator, StreamingChatModel>() {
                    @Override
                    public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context,
                            Authenticator authenticator) {
                        builder.authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList());
                        ModelBuilderCustomizer.applyCustomizers(
                                context.getInjectedReference(GATEWAY_STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                        Any.Literal.INSTANCE),
                                builder, configName);
                        return builder.build();
                    }
                });
    }

    /**
     * Wraps the creation of a {@link ChatModel} so that the authenticator is resolved lazily and the cURL logging flag is
     * only visible to the REST client built by {@code builderFunction}.
     */
    private Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModelFunction(String configName,
            CommonChatModelConfig chatModelConfig,
            BiFunction<SyntheticCreationalContext<ChatModel>, Authenticator, ChatModel> builderFunction) {

        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        String apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), specificConfig.apiKey());

        return new Function<>() {
            @Override
            public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                var authenticator = getOrCreateTokenGenerator(specificConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                chatModelConfig.logRequestsCurl(),
                                specificConfig.logRequestsCurl()));
                try {
                    return builderFunction.apply(context, authenticator);
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    /**
     * Streaming counterpart of {@link #chatModelFunction(String, CommonChatModelConfig, BiFunction)}.
     */
    private Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModelFunction(
            String configName,
            CommonChatModelConfig chatModelConfig,
            BiFunction<SyntheticCreationalContext<StreamingChatModel>, Authenticator, StreamingChatModel> builderFunction) {

        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
        String apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), specificConfig.apiKey());

        return new Function<>() {
            @Override
            public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                var authenticator = getOrCreateTokenGenerator(specificConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                chatModelConfig.logRequestsCurl(),
                                specificConfig.logRequestsCurl()));
                try {
                    return builderFunction.apply(context, authenticator);
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    /**
     * Deduces which watsonx.ai chat service must serve the requests of the given configuration.
     * <p>
     * Only one of the three chat configuration groups may be used at a time: {@code deployment-chat-model} selects the
     * deployment API, {@code gateway-chat-model} selects the Model Gateway and, when neither is configured, the
     * foundation-model API of {@code chat-model} is used.
     */
    private ChatBackend resolveChatBackend(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.deploymentChatModel().deploymentId().isPresent())
            return ChatBackend.DEPLOYMENT;

        if (watsonxConfig.gatewayChatModel().modelName().isPresent())
            return ChatBackend.GATEWAY;

        return ChatBackend.STANDARD;
    }

    /**
     * Deduces which watsonx.ai embedding service must serve the requests of the given configuration.
     * <p>
     * Setting {@code gateway-embedding-model.model-name} selects the Model Gateway, otherwise the foundation-model API of
     * {@code embedding-model} is used.
     */
    private EmbeddingBackend resolveEmbeddingBackend(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.gatewayEmbeddingModel().modelName().isPresent())
            return EmbeddingBackend.GATEWAY;

        return EmbeddingBackend.STANDARD;
    }

    private static URI resolveBaseUrl(WatsonxConfig specificConfig, WatsonxConfig defaultConfig) {
        return specificConfig.baseUrl()
                .or(new Supplier<Optional<String>>() {
                    @Override
                    public Optional<String> get() {
                        return defaultConfig.baseUrl();
                    }
                })
                .map(URI::create)
                .orElseThrow();
    }

    private static Boolean resolveLogRequests(WatsonxConfig defaultConfig, WatsonxConfig specificConfig,
            CommonChatModelConfig chatModelConfig) {
        return firstOrDefault(
                defaultConfig.logRequests().orElse(false),
                chatModelConfig.logRequests(),
                specificConfig.logRequests());
    }

    private static Boolean resolveLogResponses(WatsonxConfig defaultConfig, WatsonxConfig specificConfig,
            CommonChatModelConfig chatModelConfig) {
        return firstOrDefault(
                defaultConfig.logResponses().orElse(false),
                chatModelConfig.logResponses(),
                specificConfig.logResponses());
    }

    private static ToolChoice resolveToolChoice(CommonChatModelConfig chatModelConfig) {
        return chatModelConfig.toolChoiceName()
                .map(toolChoiceName -> ToolChoice.REQUIRED)
                .orElse(chatModelConfig.toolChoice().orElse(null));
    }

    private static Thinking resolveThinking(FoundationChatModelConfig chatModelConfig) {
        if (chatModelConfig.thinking().isEmpty())
            return null;

        ThinkingConfig config = chatModelConfig.thinking().get();
        ExtractionTags extractionTags = config.tags()
                .map(new Function<ExtractionTagsConfig, ExtractionTags>() {
                    @Override
                    public ExtractionTags apply(ExtractionTagsConfig extractionTagsConfig) {
                        Think think = new Think(extractionTagsConfig.think().opening(),
                                extractionTagsConfig.think().closing());
                        Response response = extractionTagsConfig.response()
                                .map(r -> new Response(r.opening(), r.closing())).orElse(null);
                        return new ExtractionTags(think, response);
                    }
                }).orElse(null);

        return Thinking.builder()
                .enabled(config.enabled().orElse(null))
                .extractionTags(extractionTags)
                .includeReasoning(config.includeReasoning().orElse(null))
                .thinkingEffort(config.effort().orElse(null))
                .build();
    }

    /**
     * Builds the {@link ChatModeration} requested by the {@code chat-model.moderations} configuration, or {@code null} when
     * no detector is configured.
     * <p>
     * A detector is only sent to watsonx.ai when at least one of its {@code input} or {@code output} properties is set:
     * {@code mask} alone is not enough, because it only tells watsonx.ai what to do with the matches of a detector that is
     * already running.
     */
    private static ChatModeration resolveModerations(ChatModelConfig chatModelConfig) {
        if (chatModelConfig.moderations().isEmpty())
            return null;

        ModerationsConfig config = chatModelConfig.moderations().get();
        Optional<ModerationsConfig.HapConfig> hap = config.hap()
                .filter(new Predicate<ModerationsConfig.HapConfig>() {
                    @Override
                    public boolean test(ModerationsConfig.HapConfig hapConfig) {
                        return hapConfig.input().isPresent() || hapConfig.output().isPresent();
                    }
                });
        Optional<ModerationsConfig.PiiConfig> pii = config.pii()
                .filter(new Predicate<ModerationsConfig.PiiConfig>() {
                    @Override
                    public boolean test(ModerationsConfig.PiiConfig piiConfig) {
                        return piiConfig.input().isPresent() || piiConfig.output().isPresent();
                    }
                });
        Optional<ModerationsConfig.GraniteGuardianConfig> graniteGuardian = config.graniteGuardian()
                .filter(new Predicate<ModerationsConfig.GraniteGuardianConfig>() {
                    @Override
                    public boolean test(ModerationsConfig.GraniteGuardianConfig graniteGuardianConfig) {
                        return graniteGuardianConfig.input().isPresent();
                    }
                });

        if (hap.isEmpty() && pii.isEmpty() && graniteGuardian.isEmpty())
            return null;

        ChatModeration.Builder builder = ChatModeration.builder();

        hap.ifPresent(new Consumer<ModerationsConfig.HapConfig>() {
            @Override
            public void accept(ModerationsConfig.HapConfig hapConfig) {
                builder.hap(new Consumer<ChatModeration.Hap.Builder>() {
                    @Override
                    public void accept(ChatModeration.Hap.Builder hapBuilder) {
                        if (hapConfig.input().isPresent())
                            hapBuilder.input((float) hapConfig.input().getAsDouble());
                        if (hapConfig.output().isPresent())
                            hapBuilder.output((float) hapConfig.output().getAsDouble());
                        if (hapConfig.mask().isPresent())
                            hapBuilder.mask(hapConfig.mask().get());
                    }
                });
            }
        });

        pii.ifPresent(new Consumer<ModerationsConfig.PiiConfig>() {
            @Override
            public void accept(ModerationsConfig.PiiConfig piiConfig) {
                builder.pii(new Consumer<ChatModeration.Pii.Builder>() {
                    @Override
                    public void accept(ChatModeration.Pii.Builder piiBuilder) {
                        if (piiConfig.input().isPresent())
                            piiBuilder.input(piiConfig.input().get());
                        if (piiConfig.output().isPresent())
                            piiBuilder.output(piiConfig.output().get());
                        if (piiConfig.mask().isPresent())
                            piiBuilder.mask(piiConfig.mask().get());
                    }
                });
            }
        });

        graniteGuardian.ifPresent(new Consumer<ModerationsConfig.GraniteGuardianConfig>() {
            @Override
            public void accept(ModerationsConfig.GraniteGuardianConfig graniteGuardianConfig) {
                builder.graniteGuardian(new Consumer<ChatModeration.GraniteGuardian.Builder>() {
                    @Override
                    public void accept(ChatModeration.GraniteGuardian.Builder graniteGuardianBuilder) {
                        graniteGuardianBuilder.input((float) graniteGuardianConfig.input().getAsDouble());
                        if (graniteGuardianConfig.mask().isPresent())
                            graniteGuardianBuilder.mask(graniteGuardianConfig.mask().get());
                    }
                });
            }
        });

        return builder.build();
    }

    private static Cache resolveCache(GatewayChatModelConfig chatModelConfig) {
        return chatModelConfig.cache()
                .map(new Function<CacheConfig, Cache>() {
                    @Override
                    public Cache apply(CacheConfig cacheConfig) {
                        return new Cache(cacheConfig.enabled().orElse(true), null, cacheConfig.threshold().orElse(null));
                    }
                }).orElse(null);
    }

    private static ResponseFormat resolveResponseFormat(CommonChatModelConfig chatModelConfig) {
        if (chatModelConfig.responseFormat().isEmpty())
            return null;

        return switch (chatModelConfig.responseFormat().get()) {
            case JSON -> ResponseFormat.JSON;
            case TEXT -> ResponseFormat.TEXT;
            case JSON_SCHEMA -> null;
            default -> throw new IllegalArgumentException(
                    "Unknown response format: " + chatModelConfig.responseFormat().get()
                            + ", must be one of: [text, json, json_schema]");
        };
    }

    private static Capability resolveCapability(CommonChatModelConfig chatModelConfig) {
        if (chatModelConfig.responseFormat().isEmpty())
            return null;

        return switch (chatModelConfig.responseFormat().get()) {
            case JSON_SCHEMA -> Capability.RESPONSE_FORMAT_JSON_SCHEMA;
            case JSON, TEXT -> null;
            default -> throw new IllegalArgumentException(
                    "Unknown response format: " + chatModelConfig.responseFormat().get()
                            + ", must be one of: [text, json, json_schema]");
        };
    }

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (!watsonxConfig.enableIntegration()) {
            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    return new DisabledEmbeddingModel();
                }
            };
        }

        return switch (resolveEmbeddingBackend(configName)) {
            case STANDARD -> standardEmbeddingModel(configName);
            case GATEWAY -> gatewayEmbeddingModel(configName);
        };
    }

    private Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> standardEmbeddingModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        EmbeddingModelConfig embeddingModelConfig = watsonxConfig.embeddingModel();

        var configProblems = checkConfigurations(configName);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        var apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null), watsonxConfig.apiKey());

        URI url = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        WatsonxEmbeddingModel.Builder builder = WatsonxEmbeddingModel.builder()
                .baseUrl(url)
                .timeout(watsonxConfig.timeout().orElse(null))
                .version(watsonxConfig.version().orElse(null))
                .modelName(embeddingModelConfig.modelName());

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        embeddingModelConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        embeddingModelConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                embeddingModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };

    }

    private Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> gatewayEmbeddingModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        GatewayEmbeddingModelConfig embeddingModelConfig = watsonxConfig.gatewayEmbeddingModel();

        // The Model Gateway resolves the credentials of the backing provider on its own, so neither the project nor the
        // space is validated here.
        var configProblems = checkConnectionConfigurations(configName);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        var apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonxConfig.apiKey());

        WatsonxGatewayEmbeddingModel.Builder builder = WatsonxGatewayEmbeddingModel.builder()
                .baseUrl(resolveBaseUrl(watsonxConfig, defaultConfig))
                .timeout(watsonxConfig.timeout().orElse(null))
                .version(watsonxConfig.version().orElse(null))
                .modelName(embeddingModelConfig.modelName().orElseThrow())
                .dimensions(embeddingModelConfig.dimensions().orElse(null))
                .encodingFormat(embeddingModelConfig.encodingFormat().orElse(null))
                .user(embeddingModelConfig.user().orElse(null));

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        embeddingModelConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        embeddingModelConfig.logResponses(),
                        watsonxConfig.logResponses()));

        return new Function<>() {
            @Override
            public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                embeddingModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(GATEWAY_EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                    Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<ImageModel>, ImageModel> imageModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        GatewayImageModelConfig imageModelConfig = watsonxConfig.gatewayImageModel();

        if (!watsonxConfig.enableIntegration()) {
            return new Function<>() {
                @Override
                public ImageModel apply(SyntheticCreationalContext<ImageModel> context) {
                    return new DisabledImageModel();
                }
            };
        }

        // Images are only served through the Model Gateway, which resolves the credentials of the backing provider on its
        // own, so neither the project nor the space is validated here.
        var configProblems = checkConnectionConfigurations(configName);

        if (imageModelConfig.modelName().isEmpty())
            configProblems.add(createConfigProblem("gateway-image-model.model-name", configName));

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        var apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonxConfig.apiKey());

        WatsonxGatewayImageModel.Builder builder = WatsonxGatewayImageModel.builder()
                .baseUrl(resolveBaseUrl(watsonxConfig, defaultConfig))
                .timeout(watsonxConfig.timeout().orElse(null))
                .version(watsonxConfig.version().orElse(null))
                .modelName(imageModelConfig.modelName().orElseThrow())
                .background(imageModelConfig.background().orElse(null))
                .moderation(imageModelConfig.moderation().orElse(null))
                .outputCompression(imageModelConfig.outputCompression().orElse(null))
                .outputFormat(imageModelConfig.outputFormat().orElse(null))
                .quality(imageModelConfig.quality().orElse(null))
                .responseFormat(imageModelConfig.responseFormat().orElse(null))
                .size(imageModelConfig.size().orElse(null))
                .style(imageModelConfig.style().orElse(null))
                .user(imageModelConfig.user().orElse(null));

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        imageModelConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        imageModelConfig.logResponses(),
                        watsonxConfig.logResponses()));

        return new Function<>() {
            @Override
            public ImageModel apply(SyntheticCreationalContext<ImageModel> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                imageModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(GATEWAY_IMAGE_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<ScoringModel>, ScoringModel> scoringModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        ScoringModelConfig rerankModelConfig = watsonxConfig.scoringModel();

        var configProblems = checkConfigurations(configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        var apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null), watsonxConfig.apiKey());

        URI url = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        WatsonxScoringModel.Builder builder = WatsonxScoringModel.builder()
                .baseUrl(url)
                .timeout(watsonxConfig.timeout().orElse(null))
                .version(watsonxConfig.version().orElse(null))
                .modelName(rerankModelConfig.modelName());

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        rerankModelConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        rerankModelConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public ScoringModel apply(SyntheticCreationalContext<ScoringModel> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                rerankModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(SCORING_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<WatsonxModerationModel>, WatsonxModerationModel> moderationModel(
            String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        ModerationModelConfig moderationModelConfig = watsonxConfig.moderationModel();

        var configProblems = checkConfigurations(configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        var apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null), watsonxConfig.apiKey());

        URI url = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        List<BaseDetector> detectors = new ArrayList<>();

        if (moderationModelConfig.pii().isPresent() && moderationModelConfig.pii().get().enabled())
            detectors.add(Pii.ofDefaults());

        if (moderationModelConfig.hap().isPresent() && moderationModelConfig.hap().get().enabled()) {
            HapConfig hapConfig = moderationModelConfig.hap().get();
            detectors.add(hapConfig.threshold().map(new Function<Double, Hap>() {
                @Override
                public Hap apply(Double threshold) {
                    return Hap.builder().threshold(threshold).build();
                }
            }).orElse(Hap.ofDefaults()));
        }

        if (moderationModelConfig.graniteGuardian().isPresent() && moderationModelConfig.graniteGuardian().get().enabled()) {
            GraniteGuardianConfig graniteGuardianConfig = moderationModelConfig.graniteGuardian().get();
            detectors.add(graniteGuardianConfig.threshold().map(new Function<Double, GraniteGuardian>() {
                @Override
                public GraniteGuardian apply(Double threshold) {
                    return GraniteGuardian.builder().threshold(threshold).build();
                }
            }).orElse(GraniteGuardian.ofDefaults()));
        }

        WatsonxModerationModel.Builder builder = WatsonxModerationModel.builder()
                .baseUrl(url)
                .timeout(watsonxConfig.timeout().orElse(null))
                .version(watsonxConfig.version().orElse(null))
                .detectors(detectors);

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        moderationModelConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        moderationModelConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public WatsonxModerationModel apply(SyntheticCreationalContext<WatsonxModerationModel> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                moderationModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(MODERATION_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<TextExtractionService>, TextExtractionService> textExtraction(
            String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        TextExtractionConfig textExtractionConfig = watsonxConfig.textExtraction().orElse(null);

        var apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null), watsonxConfig.apiKey());

        URI baseUrl = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        TextExtractionService.Builder builder = TextExtractionService.builder()
                .baseUrl(baseUrl)
                .timeout(watsonxConfig.timeout().orElse(null))
                .documentReference(textExtractionConfig.documentReference().connection(),
                        textExtractionConfig.documentReference().bucketName())
                .resultReference(textExtractionConfig.resultsReference().connection(),
                        textExtractionConfig.resultsReference().bucketName())
                .cosUrl(textExtractionConfig.cosUrl());

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        textExtractionConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        textExtractionConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public TextExtractionService apply(SyntheticCreationalContext<TextExtractionService> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                textExtractionConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(TEXT_EXTRACTION_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<TextClassificationService>, TextClassificationService> textClassification(
            String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        TextClassificationConfig textClassificationConfig = watsonxConfig.textClassification().orElse(null);

        var apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null), watsonxConfig.apiKey());

        URI baseUrl = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        TextClassificationService.Builder builder = TextClassificationService.builder()
                .baseUrl(baseUrl)
                .timeout(watsonxConfig.timeout().orElse(null))
                .documentReference(textClassificationConfig.documentReference().connection(),
                        textClassificationConfig.documentReference().bucketName())
                .cosUrl(textClassificationConfig.cosUrl());

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        textClassificationConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        textClassificationConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public TextClassificationService apply(SyntheticCreationalContext<TextClassificationService> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                textClassificationConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(TEXT_CLASSIFICATION_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<CreateSchemaService>, CreateSchemaService> createSchema(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        var configProblems = checkConfigurations(configName);
        checkCreateSchemaConfigurations(configName, configProblems);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        CreateSchemaConfig createSchemaConfig = watsonxConfig.schema().create().orElseThrow();

        var apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonxConfig.apiKey());

        URI baseUrl = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        CreateSchemaService.Builder builder = CreateSchemaService.builder()
                .baseUrl(baseUrl)
                .timeout(watsonxConfig.timeout().orElse(null))
                .documentReference(createSchemaConfig.documentReference().connection(),
                        createSchemaConfig.documentReference().bucketName())
                .cosUrl(createSchemaConfig.cosUrl());

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        createSchemaConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        createSchemaConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public CreateSchemaService apply(SyntheticCreationalContext<CreateSchemaService> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                createSchemaConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(CREATE_SCHEMA_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<ImproveSchemaService>, ImproveSchemaService> improveSchema(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        var configProblems = checkConfigurations(configName);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        ImproveSchemaConfig improveSchemaConfig = watsonxConfig.schema().improve();

        var apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonxConfig.apiKey());

        URI baseUrl = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        ImproveSchemaService.Builder builder = ImproveSchemaService.builder()
                .baseUrl(baseUrl)
                .timeout(watsonxConfig.timeout().orElse(null));

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        improveSchemaConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        improveSchemaConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public ImproveSchemaService apply(SyntheticCreationalContext<ImproveSchemaService> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                improveSchemaConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(IMPROVE_SCHEMA_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<MergeSchemaService>, MergeSchemaService> mergeSchema(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        var configProblems = checkConfigurations(configName);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        MergeSchemaConfig mergeSchemaConfig = watsonxConfig.schema().merge();

        var apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonxConfig.apiKey());

        URI baseUrl = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        MergeSchemaService.Builder builder = MergeSchemaService.builder()
                .baseUrl(baseUrl)
                .timeout(watsonxConfig.timeout().orElse(null));

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        mergeSchemaConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        mergeSchemaConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public MergeSchemaService apply(SyntheticCreationalContext<MergeSchemaService> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                mergeSchemaConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(MERGE_SCHEMA_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Function<SyntheticCreationalContext<ClusterSchemaService>, ClusterSchemaService> clusterSchema(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        var configProblems = checkConfigurations(configName);

        if (!configProblems.isEmpty())
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

        ClusterSchemaConfig clusterSchemaConfig = watsonxConfig.schema().cluster();

        var apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonxConfig.apiKey());

        URI baseUrl = watsonxConfig.baseUrl()
                .or(() -> defaultConfig.baseUrl())
                .map(URI::create)
                .orElseThrow();

        ClusterSchemaService.Builder builder = ClusterSchemaService.builder()
                .baseUrl(baseUrl)
                .timeout(watsonxConfig.timeout().orElse(null));

        builder.logRequests(
                firstOrDefault(
                        defaultConfig.logRequests().orElse(false),
                        clusterSchemaConfig.logRequests(),
                        watsonxConfig.logRequests()));

        builder.logResponses(
                firstOrDefault(
                        defaultConfig.logResponses().orElse(false),
                        clusterSchemaConfig.logResponses(),
                        watsonxConfig.logResponses()));

        builder.spaceId(
                firstOrDefault(
                        defaultConfig.spaceId().orElse(null),
                        watsonxConfig.spaceId()));

        builder.projectId(
                firstOrDefault(
                        defaultConfig.projectId().orElse(null),
                        watsonxConfig.projectId()));

        return new Function<>() {
            @Override
            public ClusterSchemaService apply(SyntheticCreationalContext<ClusterSchemaService> context) {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                clusterSchemaConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    builder.authenticator(authenticator);
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(CLUSTER_SCHEMA_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    private LangChain4jWatsonxConfig.WatsonxConfig correspondingWatsonxRuntimeConfig(String configName) {
        LangChain4jWatsonxConfig.WatsonxConfig watsonxConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            watsonxConfig = runtimeConfig.getValue().defaultConfig();
        } else {
            watsonxConfig = runtimeConfig.getValue().namedConfig().get(configName);
        }
        return watsonxConfig;
    }

    private List<ConfigValidationException.Problem> checkConfigurations(String configName) {
        List<ConfigValidationException.Problem> configProblems = checkConnectionConfigurations(configName);
        checkProjectIdOrSpaceId(configName, configProblems);
        return configProblems;
    }

    private List<ConfigValidationException.Problem> checkConfigurations(String configName, ChatBackend backend) {
        List<ConfigValidationException.Problem> configProblems = checkConnectionConfigurations(configName);
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.deploymentChatModel().deploymentId().isPresent()
                && watsonxConfig.gatewayChatModel().modelName().isPresent()) {
            var config = NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + ".");
            var errorMessage = "The properties quarkus.langchain4j.watsonx%s%s and quarkus.langchain4j.watsonx%s%s cannot be configured at the same time, only one chat backend can be used for a given configuration";
            configProblems.add(new ConfigValidationException.Problem(
                    String.format(errorMessage, config, "deployment-chat-model.deployment-id", config,
                            "gateway-chat-model.model-name")));
        }

        // Only the foundation model chat api requires the resource that contains the model.
        if (backend == ChatBackend.STANDARD)
            checkProjectIdOrSpaceId(configName, configProblems);

        return configProblems;
    }

    private List<ConfigValidationException.Problem> checkConnectionConfigurations(String configName) {
        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.baseUrl().isEmpty() && defaultConfig.baseUrl().isEmpty())
            configProblems.add(createConfigProblem("base-url", configName));

        if (watsonxConfig.apiKey().isEmpty() && defaultConfig.apiKey().isEmpty())
            configProblems.add(createConfigProblem("api-key", configName));

        return configProblems;
    }

    /**
     * The {@code schema.create} group is optional, but the {@code CreateSchemaService} cannot work without the Cloud Object
     * Storage coordinates. Report every missing property instead of letting the bean creation fail with a
     * {@link NullPointerException}.
     */
    private void checkCreateSchemaConfigurations(String configName, List<ConfigValidationException.Problem> configProblems) {
        if (correspondingWatsonxRuntimeConfig(configName).schema().create().isPresent())
            return;

        configProblems.add(createConfigProblem("schema.create.cos-url", configName));
        configProblems.add(createConfigProblem("schema.create.document-reference.connection", configName));
        configProblems.add(createConfigProblem("schema.create.document-reference.bucket-name", configName));
    }

    private void checkProjectIdOrSpaceId(String configName, List<ConfigValidationException.Problem> configProblems) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        boolean noProjectId = watsonxConfig.projectId().isEmpty() && defaultConfig.projectId().isEmpty();
        boolean noSpaceId = watsonxConfig.spaceId().isEmpty() && defaultConfig.spaceId().isEmpty();

        if (noProjectId && noSpaceId) {
            var config = NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + ".");
            var errorMessage = "One of the properties quarkus.langchain4j.watsonx%s%s / quarkus.langchain4j.watsonx%s%s is required, but could not be found in any config source";
            configProblems.add(new ConfigValidationException.Problem(
                    String.format(errorMessage, config, "project-id", config, "space-id")));
        }
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.watsonx%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }

    /**
     * The watsonx.ai service used to serve the chat requests, deduced from the configuration.
     */
    private enum ChatBackend {
        /**
         * The foundation model chat api ({@code /ml/v1/text/chat}), configured via {@code chat-model}.
         */
        STANDARD,
        /**
         * The chat api of a model deployed in watsonx.ai ({@code /ml/v1/deployments/{deployment_id}/text/chat}),
         * configured via {@code deployment-chat-model}.
         */
        DEPLOYMENT,
        /**
         * The Model Gateway ({@code /ml/gateway/v1/chat/completions}), configured via {@code gateway-chat-model}.
         */
        GATEWAY
    }

    /**
     * The watsonx.ai service used to serve the embedding requests, deduced from the configuration.
     */
    private enum EmbeddingBackend {
        /**
         * The foundation model embedding api ({@code /ml/v1/text/embeddings}), configured via {@code embedding-model}.
         */
        STANDARD,
        /**
         * The Model Gateway ({@code /ml/gateway/v1/embeddings}), configured via {@code gateway-embedding-model}.
         */
        GATEWAY
    }
}
