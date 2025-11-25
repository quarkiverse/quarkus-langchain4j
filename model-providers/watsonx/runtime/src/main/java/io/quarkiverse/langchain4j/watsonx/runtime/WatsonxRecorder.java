package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import static io.quarkiverse.langchain4j.watsonx.runtime.AuthenticatorCache.getOrCreateTokenGenerator;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.detection.detector.BaseDetector;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Hap;
import com.ibm.watsonx.ai.detection.detector.Pii;
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
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxEmbeddingModel;
import dev.langchain4j.model.watsonx.WatsonxModerationModel;
import dev.langchain4j.model.watsonx.WatsonxScoringModel;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig.ExtractionTagsConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig.ThinkingConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig.WatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationModelConfig.GraniteGuardianConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ModerationModelConfig.HapConfig;
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

    private final RuntimeValue<LangChain4jWatsonxConfig> runtimeConfig;

    public WatsonxRecorder(RuntimeValue<LangChain4jWatsonxConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.enableIntegration()) {

            var configProblems = checkConfigurations(configName);

            if (!configProblems.isEmpty())
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

            WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
            WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
            ChatModelConfig chatModelConfig = specificConfig.chatModel();

            URI url = specificConfig.baseUrl()
                    .or(() -> defaultConfig.baseUrl())
                    .map(URI::create)
                    .orElseThrow();

            WatsonxChatModel.Builder builder = WatsonxChatModel.builder()
                    .baseUrl(url)
                    .version(specificConfig.version().orElse(null))
                    .modelName(specificConfig.chatModel().modelName())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .logprobs(chatModelConfig.logprobs())
                    .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .seed(chatModelConfig.seed().orElse(null))
                    .stopSequences(chatModelConfig.stop().orElse(null))
                    .temperature(chatModelConfig.temperature())
                    .topP(chatModelConfig.topP())
                    .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                    .timeout(specificConfig.timeout().orElse(null))
                    .guidedGrammar(chatModelConfig.guidedGrammar().orElse(null))
                    .guidedRegex(chatModelConfig.guidedRegex().orElse(null))
                    .lengthPenalty(chatModelConfig.lengthPenalty().orElse(null))
                    .repetitionPenalty(chatModelConfig.repetitionPenalty().orElse(null));

            if (chatModelConfig.guidedChoice().isPresent())
                builder.guidedChoice(chatModelConfig.guidedChoice().orElseThrow());

            if (chatModelConfig.responseFormat().isPresent()) {
                switch (chatModelConfig.responseFormat().get()) {
                    case JSON -> builder.responseFormat(ResponseFormat.JSON);
                    case JSON_SCHEMA -> builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
                    case TEXT -> builder.responseFormat(ResponseFormat.TEXT);
                    default -> throw new IllegalArgumentException(
                            "Unknown response format: " + chatModelConfig.responseFormat().get()
                                    + ", must be one of: [json_object, json_schema, text]");
                }
            }

            if (chatModelConfig.thinking().isPresent()) {
                ThinkingConfig config = chatModelConfig.thinking().get();
                ExtractionTags extractionTags = config.tags()
                        .map(new Function<ExtractionTagsConfig, ExtractionTags>() {
                            @Override
                            public ExtractionTags apply(ExtractionTagsConfig extractionTagsConfig) {
                                return new ExtractionTags(extractionTagsConfig.think(),
                                        extractionTagsConfig.response().orElse(null));
                            }
                        }).orElse(null);

                Thinking thinking = Thinking.builder()
                        .enabled(config.enabled().orElse(null))
                        .extractionTags(extractionTags)
                        .includeReasoning(config.includeReasoning().orElse(null))
                        .thinkingEffort(config.effort().orElse(null))
                        .build();

                builder.thinking(thinking);
            }

            ToolChoice toolChoice = chatModelConfig.toolChoiceName()
                    .map(toolChoiceName -> ToolChoice.REQUIRED)
                    .orElse(chatModelConfig.toolChoice().orElse(null));

            builder.toolChoice(toolChoice);

            builder.logRequests(
                    firstOrDefault(
                            defaultConfig.logRequests().orElse(false),
                            chatModelConfig.logRequests(),
                            specificConfig.logRequests()));

            builder.logResponses(
                    firstOrDefault(
                            defaultConfig.logResponses().orElse(false),
                            chatModelConfig.logResponses(),
                            specificConfig.logResponses()));

            builder.spaceId(
                    firstOrDefault(
                            defaultConfig.spaceId().orElse(null),
                            specificConfig.spaceId()));

            builder.projectId(
                    firstOrDefault(
                            defaultConfig.projectId().orElse(null),
                            specificConfig.projectId()));

            String apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null),
                    watsonxConfig.apiKey());

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                    QuarkusRestClientConfig.setLogCurl(
                            firstOrDefault(
                                    defaultConfig.logRequestsCurl().orElse(false),
                                    chatModelConfig.logRequestsCurl(),
                                    specificConfig.logRequestsCurl()));
                    try {
                        return builder
                                .authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                                .build();
                    } finally {
                        QuarkusRestClientConfig.clear();
                    }
                }
            };
        } else {
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return new DisabledChatModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.enableIntegration()) {
            var configProblems = checkConfigurations(configName);

            if (!configProblems.isEmpty())
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));

            WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
            WatsonxConfig specificConfig = correspondingWatsonxRuntimeConfig(configName);
            ChatModelConfig chatModelConfig = specificConfig.chatModel();

            URI url = specificConfig.baseUrl()
                    .or(() -> defaultConfig.baseUrl())
                    .map(URI::create)
                    .orElseThrow();

            WatsonxStreamingChatModel.Builder builder = WatsonxStreamingChatModel.builder()
                    .baseUrl(url)
                    .version(specificConfig.version().orElse(null))
                    .modelName(specificConfig.chatModel().modelName())
                    .frequencyPenalty(chatModelConfig.frequencyPenalty())
                    .logprobs(chatModelConfig.logprobs())
                    .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .presencePenalty(chatModelConfig.presencePenalty())
                    .seed(chatModelConfig.seed().orElse(null))
                    .stopSequences(chatModelConfig.stop().orElse(null))
                    .temperature(chatModelConfig.temperature())
                    .topP(chatModelConfig.topP())
                    .toolChoiceName(chatModelConfig.toolChoiceName().orElse(null))
                    .timeout(specificConfig.timeout().orElse(null))
                    .guidedGrammar(chatModelConfig.guidedGrammar().orElse(null))
                    .guidedRegex(chatModelConfig.guidedRegex().orElse(null))
                    .lengthPenalty(chatModelConfig.lengthPenalty().orElse(null))
                    .repetitionPenalty(chatModelConfig.repetitionPenalty().orElse(null));

            if (chatModelConfig.guidedChoice().isPresent())
                builder.guidedChoice(chatModelConfig.guidedChoice().orElseThrow());

            if (chatModelConfig.responseFormat().isPresent()) {
                switch (chatModelConfig.responseFormat().get()) {
                    case JSON -> builder.responseFormat(ResponseFormat.JSON);
                    case JSON_SCHEMA -> builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
                    case TEXT -> builder.responseFormat(ResponseFormat.TEXT);
                    default -> throw new IllegalArgumentException(
                            "Unknown response format: " + chatModelConfig.responseFormat().get()
                                    + ", must be one of: [json_object, json_schema, text]");
                }
            }

            if (chatModelConfig.thinking().isPresent()) {
                ThinkingConfig config = chatModelConfig.thinking().get();
                ExtractionTags extractionTags = config.tags()
                        .map(new Function<ExtractionTagsConfig, ExtractionTags>() {
                            @Override
                            public ExtractionTags apply(ExtractionTagsConfig extractionTagsConfig) {
                                return new ExtractionTags(extractionTagsConfig.think(),
                                        extractionTagsConfig.response().orElse(null));
                            }
                        }).orElse(null);

                Thinking thinking = Thinking.builder()
                        .enabled(config.enabled().orElse(null))
                        .extractionTags(extractionTags)
                        .includeReasoning(config.includeReasoning().orElse(null))
                        .thinkingEffort(config.effort().orElse(null))
                        .build();

                builder.thinking(thinking);
            }

            ToolChoice toolChoice = chatModelConfig.toolChoiceName()
                    .map(toolChoiceName -> ToolChoice.REQUIRED)
                    .orElse(chatModelConfig.toolChoice().orElse(null));

            builder.toolChoice(toolChoice);

            builder.logRequests(
                    firstOrDefault(
                            defaultConfig.logRequests().orElse(false),
                            chatModelConfig.logRequests(),
                            specificConfig.logRequests()));

            builder.logResponses(
                    firstOrDefault(
                            defaultConfig.logResponses().orElse(false),
                            chatModelConfig.logResponses(),
                            specificConfig.logResponses()));

            builder.spaceId(
                    firstOrDefault(
                            defaultConfig.spaceId().orElse(null),
                            specificConfig.spaceId()));

            builder.projectId(
                    firstOrDefault(
                            defaultConfig.projectId().orElse(null),
                            specificConfig.projectId()));

            String apiKey = firstOrDefault(runtimeConfig.getValue().defaultConfig().apiKey().orElse(null),
                    watsonxConfig.apiKey());

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                    QuarkusRestClientConfig.setLogCurl(
                            firstOrDefault(
                                    defaultConfig.logRequestsCurl().orElse(false),
                                    chatModelConfig.logRequestsCurl(),
                                    specificConfig.logRequestsCurl()));
                    try {
                        return builder
                                .authenticator(authenticator)
                                .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                                .build();
                    } finally {
                        QuarkusRestClientConfig.clear();
                    }
                }
            };
        } else {
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return new DisabledStreamingChatModel();
                }
            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(String configName) {
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);
        EmbeddingModelConfig embeddingModelConfig = watsonxConfig.embeddingModel();

        if (!watsonxConfig.enableIntegration()) {
            return new Supplier<>() {
                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }
            };
        }

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

        return new Supplier<>() {
            @Override
            public WatsonxEmbeddingModel get() {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                embeddingModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    return builder.authenticator(authenticator).build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };

    }

    public Supplier<ScoringModel> scoringModel(String configName) {
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

        return new Supplier<>() {
            @Override
            public WatsonxScoringModel get() {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                rerankModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    return builder.authenticator(authenticator).build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Supplier<WatsonxModerationModel> moderationModel(String configName) {
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

        return new Supplier<>() {
            @Override
            public WatsonxModerationModel get() {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                moderationModelConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    return builder.authenticator(authenticator).build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Supplier<TextExtractionService> textExtraction(String configName) {
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

        return new Supplier<>() {
            @Override
            public TextExtractionService get() {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                textExtractionConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    return builder.authenticator(authenticator).build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };
    }

    public Supplier<TextClassificationService> textClassification(String configName) {
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

        return new Supplier<>() {
            @Override
            public TextClassificationService get() {
                var authenticator = getOrCreateTokenGenerator(watsonxConfig.iam().baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(
                        firstOrDefault(
                                defaultConfig.logRequestsCurl().orElse(false),
                                textClassificationConfig.logRequestsCurl(),
                                watsonxConfig.logRequestsCurl()));
                try {
                    return builder.authenticator(authenticator).build();
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
        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
        WatsonxConfig defaultConfig = runtimeConfig.getValue().defaultConfig();
        WatsonxConfig watsonxConfig = correspondingWatsonxRuntimeConfig(configName);

        if (watsonxConfig.baseUrl().isEmpty() && defaultConfig.baseUrl().isEmpty())
            configProblems.add(createConfigProblem("base-url", configName));

        if (watsonxConfig.apiKey().isEmpty() && defaultConfig.apiKey().isEmpty())
            configProblems.add(createConfigProblem("api-key", configName));

        boolean noProjectId = watsonxConfig.projectId().isEmpty() && defaultConfig.projectId().isEmpty();
        boolean noSpaceId = watsonxConfig.spaceId().isEmpty() && defaultConfig.spaceId().isEmpty();

        if (noProjectId && noSpaceId) {
            var config = NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + ".");
            var errorMessage = "One of the properties quarkus.langchain4j.watsonx%s%s / quarkus.langchain4j.watsonx%s%s is required, but could not be found in any config source";
            configProblems.add(new ConfigValidationException.Problem(
                    String.format(errorMessage, config, "project-id", config, "space-id")));
        }

        return configProblems;
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.watsonx%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
