package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import static io.quarkiverse.langchain4j.watsonx.runtime.TokenGenerationCache.getOrCreateTokenGenerator;
import static java.util.Objects.isNull;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

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
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxEmbeddingModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxGenerationModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxGenerationStreamingModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxScoringModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxStreamingChatModel;
import io.quarkiverse.langchain4j.watsonx.client.COSRestApi;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxClientLogger;
import io.quarkiverse.langchain4j.watsonx.client.WatsonxRestApi;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerTokenHeaderFactory;
import io.quarkiverse.langchain4j.watsonx.runtime.TextExtraction.Reference;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GenerationModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ScoringModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.TextExtractionConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class WatsonxRecorder {

    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];
    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(
            LangChain4jWatsonxConfig runtimeConfig, String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(null, watsonRuntimeConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {
            var builder = chatBuilder(runtimeConfig, configName);
            var iamBaseUrl = watsonRuntimeConfig.iam().baseUrl();
            var granType = watsonRuntimeConfig.iam().grantType();
            var duration = watsonRuntimeConfig.iam().timeout().orElse(Duration.ofSeconds(10));
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return builder
                            .tokenGenerator(getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, duration))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
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

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(
            LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(runtimeConfig.defaultConfig().apiKey().orElse(null), watsonRuntimeConfig.apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {
            var builder = streamingChatBuilder(runtimeConfig, configName);
            var iamBaseUrl = watsonRuntimeConfig.iam().baseUrl();
            var granType = watsonRuntimeConfig.iam().grantType();
            var duration = watsonRuntimeConfig.iam().timeout().orElse(Duration.ofSeconds(10));
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return builder
                            .tokenGenerator(getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, duration))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
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

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> generationModel(
            LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(runtimeConfig.defaultConfig().apiKey().orElse(null), watsonRuntimeConfig.apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {
            var builder = generationBuilder(runtimeConfig, configName);
            var iamBaseUrl = watsonRuntimeConfig.iam().baseUrl();
            var granType = watsonRuntimeConfig.iam().grantType();
            var duration = watsonRuntimeConfig.iam().timeout().orElse(Duration.ofSeconds(10));
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return builder
                            .tokenGenerator(getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, duration))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
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

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> generationStreamingModel(
            LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(runtimeConfig.defaultConfig().apiKey().orElse(null), watsonRuntimeConfig.apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {
            var builder = generationStreamingBuilder(runtimeConfig, configName);
            var iamBaseUrl = watsonRuntimeConfig.iam().baseUrl();
            var granType = watsonRuntimeConfig.iam().grantType();
            var duration = watsonRuntimeConfig.iam().timeout().orElse(Duration.ofSeconds(10));
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return builder
                            .tokenGenerator(getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, duration))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
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

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig defaultConfig = runtimeConfig.defaultConfig();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonConfig.enableIntegration()) {
            var configProblems = checkConfigurations(runtimeConfig, configName);

            if (!configProblems.isEmpty()) {
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
            }

            String apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonConfig.apiKey());

            URL url;
            try {
                url = URI.create(firstOrDefault(defaultConfig.baseUrl().orElse(null), watsonConfig.baseUrl())).toURL();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            EmbeddingModelConfig embeddingModelConfig = watsonConfig.embeddingModel();

            var timeout = firstOrDefault(Duration.ofSeconds(10), watsonConfig.timeout(), defaultConfig.timeout());
            var logRequests = firstOrDefault(defaultConfig.logRequests().orElse(false), embeddingModelConfig.logRequests(),
                    watsonConfig.logRequests());
            var logResponses = firstOrDefault(defaultConfig.logResponses().orElse(false), embeddingModelConfig.logResponses(),
                    watsonConfig.logResponses());
            var spaceId = firstOrDefault(defaultConfig.spaceId().orElse(null), watsonConfig.spaceId());
            var projectId = firstOrDefault(defaultConfig.projectId().orElse(null), watsonConfig.projectId());

            var builder = WatsonxEmbeddingModel.builder()
                    .url(url)
                    .timeout(timeout)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .version(watsonConfig.version())
                    .spaceId(spaceId)
                    .projectId(projectId)
                    .modelId(embeddingModelConfig.modelId())
                    .truncateInputTokens(embeddingModelConfig.truncateInputTokens().orElse(null));
            var iamBaseUrl = watsonConfig.iam().baseUrl();
            var granType = watsonConfig.iam().grantType();
            var duration = watsonConfig.iam().timeout().orElse(Duration.ofSeconds(10));
            return new Supplier<>() {
                @Override
                public WatsonxEmbeddingModel get() {
                    return builder
                            .tokenGenerator(getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, duration))
                            .build();
                }
            };

        } else {
            return new Supplier<>() {

                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }

            };
        }
    }

    public Supplier<ScoringModel> scoringModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig defaultConfig = runtimeConfig.defaultConfig();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String apiKey = firstOrDefault(defaultConfig.apiKey().orElse(null), watsonConfig.apiKey());

        URL url;
        try {
            url = URI.create(firstOrDefault(defaultConfig.baseUrl().orElse(null), watsonConfig.baseUrl())).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ScoringModelConfig rerankModelConfig = watsonConfig.scoringModel();

        var timeout = firstOrDefault(Duration.ofSeconds(10), watsonConfig.timeout(), defaultConfig.timeout());
        var logRequests = firstOrDefault(defaultConfig.logRequests().orElse(false), rerankModelConfig.logRequests(),
                watsonConfig.logRequests());
        var logResponses = firstOrDefault(defaultConfig.logResponses().orElse(false), rerankModelConfig.logResponses(),
                watsonConfig.logResponses());
        var spaceId = firstOrDefault(defaultConfig.spaceId().orElse(null), watsonConfig.spaceId());
        var projectId = firstOrDefault(defaultConfig.projectId().orElse(null), watsonConfig.projectId());

        var builder = WatsonxScoringModel.builder()
                .url(url)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .version(watsonConfig.version())
                .spaceId(spaceId)
                .projectId(projectId)
                .modelId(rerankModelConfig.modelId())
                .truncateInputTokens(rerankModelConfig.truncateInputTokens().orElse(null));
        var iamBaseUrl = watsonConfig.iam().baseUrl();
        var granType = watsonConfig.iam().grantType();
        var duration = watsonConfig.iam().timeout().orElse(Duration.ofSeconds(10));
        return new Supplier<>() {
            @Override
            public WatsonxScoringModel get() {
                return builder
                        .tokenGenerator(getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, duration))
                        .build();
            }
        };
    }

    public Supplier<TextExtraction> textExtraction(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
        TextExtractionConfig textExtractionConfig = firstOrDefault(runtimeConfig.defaultConfig().textExtraction().orElse(null),
                watsonConfig.textExtraction());

        if (isNull(textExtractionConfig)) {
            configProblems.add(createConfigProblem("text-extraction", configName));
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String apiKey = firstOrDefault(runtimeConfig.defaultConfig().apiKey().orElse(null), watsonConfig.apiKey());
        URL iamBaseUrl = watsonConfig.iam().baseUrl();
        String granType = watsonConfig.iam().grantType();
        Duration iamDuration = watsonConfig.iam().timeout().orElse(Duration.ofSeconds(10));

        URL watsonxUrl;
        Duration watsonxDuration = watsonConfig.timeout().orElse(Duration.ofSeconds(10));

        URL cosUrl;
        boolean logRequests = watsonConfig.logRequests().orElse(false);
        boolean logResponses = watsonConfig.logResponses().orElse(false);

        try {
            cosUrl = URI.create(textExtractionConfig.baseUrl()).toURL();
            watsonxUrl = URI
                    .create(firstOrDefault(runtimeConfig.defaultConfig().baseUrl().orElse(null), watsonConfig.baseUrl()))
                    .toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new Supplier<TextExtraction>() {
            @Override
            public TextExtraction get() {

                var tokenGenerator = getOrCreateTokenGenerator(apiKey, iamBaseUrl, granType, iamDuration);

                var watsonxClient = QuarkusRestClientBuilder.newBuilder()
                        .baseUrl(watsonxUrl)
                        .clientHeadersFactory(new BearerTokenHeaderFactory(tokenGenerator))
                        .connectTimeout(watsonxDuration.toSeconds(), TimeUnit.SECONDS)
                        .readTimeout(watsonxDuration.toSeconds(), TimeUnit.SECONDS);

                if (logRequests || logResponses) {
                    watsonxClient.loggingScope(LoggingScope.REQUEST_RESPONSE);
                    watsonxClient.clientLogger(new WatsonxClientLogger(logRequests, logResponses));
                }

                var cosClient = QuarkusRestClientBuilder.newBuilder()
                        .baseUrl(cosUrl)
                        .clientHeadersFactory(new BearerTokenHeaderFactory(tokenGenerator))
                        .connectTimeout(0, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS);

                if (logRequests || logResponses) {
                    cosClient.loggingScope(LoggingScope.REQUEST_RESPONSE);
                    cosClient.clientLogger(new WatsonxClientLogger(logRequests, logResponses));
                }

                return new TextExtraction(
                        new Reference(textExtractionConfig.documentReference().connection(),
                                textExtractionConfig.documentReference().bucketName()),
                        new Reference(textExtractionConfig.resultsReference().connection(),
                                textExtractionConfig.resultsReference().bucketName()),
                        firstOrDefault(runtimeConfig.defaultConfig().projectId().orElse(null), watsonConfig.projectId()),
                        firstOrDefault(runtimeConfig.defaultConfig().spaceId().orElse(null), watsonConfig.spaceId()),
                        watsonConfig.version(),
                        cosClient.build(COSRestApi.class),
                        watsonxClient.build(WatsonxRestApi.class));
            }
        };
    }

    private WatsonxChatModel.Builder chatBuilder(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig defaultConfig = runtimeConfig.defaultConfig();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        ChatModelConfig chatModelConfig = watsonConfig.chatModel();

        URL url;
        try {
            url = URI.create(firstOrDefault(defaultConfig.baseUrl().orElse(null), watsonConfig.baseUrl())).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ResponseFormat responseFormat = null;
        if (chatModelConfig.responseFormat().isPresent()) {
            responseFormat = switch (chatModelConfig.responseFormat().get().toLowerCase()) {
                case "json_object" -> ResponseFormat.JSON;
                default -> throw new IllegalArgumentException(
                        "The value '%s' for the response-format property is not available. Use one of the values: [%s]"
                                .formatted(chatModelConfig.responseFormat().get(), "json_object"));
            };
        }

        String toolChoiceName = chatModelConfig.toolChoiceName().orElse(null);
        ToolChoice toolChoice = toolChoiceName != null ? ToolChoice.REQUIRED : chatModelConfig.toolChoice().orElse(null);

        var timeout = firstOrDefault(Duration.ofSeconds(10), watsonConfig.timeout(), defaultConfig.timeout());
        var logRequests = firstOrDefault(defaultConfig.logRequests().orElse(false), chatModelConfig.logRequests(),
                watsonConfig.logRequests());
        var logResponses = firstOrDefault(defaultConfig.logResponses().orElse(false), chatModelConfig.logResponses(),
                watsonConfig.logResponses());
        var spaceId = firstOrDefault(defaultConfig.spaceId().orElse(null), watsonConfig.spaceId());
        var projectId = firstOrDefault(defaultConfig.projectId().orElse(null), watsonConfig.projectId());

        return WatsonxChatModel.builder()
                .url(url)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .version(watsonConfig.version())
                .spaceId(spaceId)
                .projectId(projectId)
                .modelId(watsonConfig.chatModel().modelId())
                .toolChoice(toolChoice)
                .toolChoiceName(toolChoiceName)
                .frequencyPenalty(chatModelConfig.frequencyPenalty())
                .logprobs(chatModelConfig.logprobs())
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxTokens(chatModelConfig.maxTokens())
                .n(chatModelConfig.n())
                .presencePenalty(chatModelConfig.presencePenalty())
                .seed(chatModelConfig.seed().orElse(null))
                .stop(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .responseFormat(responseFormat);
    }

    private WatsonxStreamingChatModel.Builder streamingChatBuilder(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {
        LangChain4jWatsonxConfig.WatsonConfig defaultConfig = runtimeConfig.defaultConfig();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        ChatModelConfig chatModelConfig = watsonConfig.chatModel();

        URL url;
        try {
            url = URI.create(firstOrDefault(defaultConfig.baseUrl().orElse(null), watsonConfig.baseUrl())).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ResponseFormat responseFormat = null;
        if (chatModelConfig.responseFormat().isPresent()) {
            responseFormat = switch (chatModelConfig.responseFormat().get().toLowerCase()) {
                case "json_object" -> ResponseFormat.JSON;
                default -> throw new IllegalArgumentException(
                        "The value '%s' for the response-format property is not available. Use one of the values: [%s]"
                                .formatted(chatModelConfig.responseFormat().get(), "json_object"));
            };
        }

        String toolChoiceName = chatModelConfig.toolChoiceName().orElse(null);
        ToolChoice toolChoice = toolChoiceName != null ? ToolChoice.REQUIRED : chatModelConfig.toolChoice().orElse(null);

        var timeout = firstOrDefault(Duration.ofSeconds(10), watsonConfig.timeout(), defaultConfig.timeout());
        var logRequests = firstOrDefault(defaultConfig.logRequests().orElse(false), chatModelConfig.logRequests(),
                watsonConfig.logRequests());
        var logResponses = firstOrDefault(defaultConfig.logResponses().orElse(false), chatModelConfig.logResponses(),
                watsonConfig.logResponses());
        var spaceId = firstOrDefault(defaultConfig.spaceId().orElse(null), watsonConfig.spaceId());
        var projectId = firstOrDefault(defaultConfig.projectId().orElse(null), watsonConfig.projectId());

        return WatsonxStreamingChatModel.builder()
                .url(url)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .version(watsonConfig.version())
                .spaceId(spaceId)
                .projectId(projectId)
                .modelId(watsonConfig.chatModel().modelId())
                .toolChoice(toolChoice)
                .toolChoiceName(toolChoiceName)
                .frequencyPenalty(chatModelConfig.frequencyPenalty())
                .logprobs(chatModelConfig.logprobs())
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxTokens(chatModelConfig.maxTokens())
                .n(chatModelConfig.n())
                .presencePenalty(chatModelConfig.presencePenalty())
                .seed(chatModelConfig.seed().orElse(null))
                .stop(chatModelConfig.stop().orElse(null))
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .responseFormat(responseFormat);
    }

    private WatsonxGenerationModel.Builder generationBuilder(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {
        LangChain4jWatsonxConfig.WatsonConfig defaultConfig = runtimeConfig.defaultConfig();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        GenerationModelConfig generationModelConfig = watsonConfig.generationModel();

        URL url;
        try {
            url = URI.create(firstOrDefault(defaultConfig.baseUrl().orElse(null), watsonConfig.baseUrl())).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Double decayFactor = generationModelConfig.lengthPenalty().decayFactor().orElse(null);
        Integer startIndex = generationModelConfig.lengthPenalty().startIndex().orElse(null);
        String promptJoiner = generationModelConfig.promptJoiner();

        var timeout = firstOrDefault(Duration.ofSeconds(10), watsonConfig.timeout(), defaultConfig.timeout());
        var logRequests = firstOrDefault(defaultConfig.logRequests().orElse(false), generationModelConfig.logRequests(),
                watsonConfig.logRequests());
        var logResponses = firstOrDefault(defaultConfig.logResponses().orElse(false), generationModelConfig.logResponses(),
                watsonConfig.logResponses());
        var spaceId = firstOrDefault(defaultConfig.spaceId().orElse(null), watsonConfig.spaceId());
        var projectId = firstOrDefault(defaultConfig.projectId().orElse(null), watsonConfig.projectId());

        return WatsonxGenerationModel.builder()
                .url(url)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .version(watsonConfig.version())
                .spaceId(spaceId)
                .projectId(projectId)
                .modelId(watsonConfig.generationModel().modelId())
                .decodingMethod(generationModelConfig.decodingMethod())
                .decayFactor(decayFactor)
                .startIndex(startIndex)
                .maxNewTokens(generationModelConfig.maxNewTokens())
                .minNewTokens(generationModelConfig.minNewTokens())
                .temperature(generationModelConfig.temperature())
                .randomSeed(generationModelConfig.randomSeed().orElse(null))
                .stopSequences(generationModelConfig.stopSequences().orElse(null))
                .topK(generationModelConfig.topK().orElse(null))
                .topP(generationModelConfig.topP().orElse(null))
                .repetitionPenalty(generationModelConfig.repetitionPenalty().orElse(null))
                .truncateInputTokens(generationModelConfig.truncateInputTokens().orElse(null))
                .includeStopSequence(generationModelConfig.includeStopSequence().orElse(null))
                .promptJoiner(promptJoiner);
    }

    private WatsonxGenerationStreamingModel.Builder generationStreamingBuilder(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {
        LangChain4jWatsonxConfig.WatsonConfig defaultConfig = runtimeConfig.defaultConfig();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        GenerationModelConfig generationModelConfig = watsonConfig.generationModel();

        URL url;
        try {
            url = URI.create(firstOrDefault(defaultConfig.baseUrl().orElse(null), watsonConfig.baseUrl())).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Double decayFactor = generationModelConfig.lengthPenalty().decayFactor().orElse(null);
        Integer startIndex = generationModelConfig.lengthPenalty().startIndex().orElse(null);
        String promptJoiner = generationModelConfig.promptJoiner();

        var timeout = firstOrDefault(Duration.ofSeconds(10), watsonConfig.timeout(), defaultConfig.timeout());
        var logRequests = firstOrDefault(defaultConfig.logRequests().orElse(false), generationModelConfig.logRequests(),
                watsonConfig.logRequests());
        var logResponses = firstOrDefault(defaultConfig.logResponses().orElse(false), generationModelConfig.logResponses(),
                watsonConfig.logResponses());
        var spaceId = firstOrDefault(defaultConfig.spaceId().orElse(null), watsonConfig.spaceId());
        var projectId = firstOrDefault(defaultConfig.projectId().orElse(null), watsonConfig.projectId());

        return WatsonxGenerationStreamingModel.builder()
                .url(url)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .version(watsonConfig.version())
                .spaceId(spaceId)
                .projectId(projectId)
                .modelId(watsonConfig.generationModel().modelId())
                .decodingMethod(generationModelConfig.decodingMethod())
                .decayFactor(decayFactor)
                .startIndex(startIndex)
                .maxNewTokens(generationModelConfig.maxNewTokens())
                .minNewTokens(generationModelConfig.minNewTokens())
                .temperature(generationModelConfig.temperature())
                .randomSeed(generationModelConfig.randomSeed().orElse(null))
                .stopSequences(generationModelConfig.stopSequences().orElse(null))
                .topK(generationModelConfig.topK().orElse(null))
                .topP(generationModelConfig.topP().orElse(null))
                .repetitionPenalty(generationModelConfig.repetitionPenalty().orElse(null))
                .truncateInputTokens(generationModelConfig.truncateInputTokens().orElse(null))
                .includeStopSequence(generationModelConfig.includeStopSequence().orElse(null))
                .promptJoiner(promptJoiner);
    }

    private LangChain4jWatsonxConfig.WatsonConfig correspondingWatsonRuntimeConfig(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            watsonConfig = runtimeConfig.defaultConfig();
        } else {
            watsonConfig = runtimeConfig.namedConfig().get(configName);
        }
        return watsonConfig;
    }

    private List<ConfigValidationException.Problem> checkConfigurations(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {
        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonConfig.baseUrl().isEmpty() && runtimeConfig.defaultConfig().baseUrl().isEmpty()) {
            configProblems.add(createBaseURLConfigProblem(configName));
        }
        if (watsonConfig.apiKey().isEmpty() && runtimeConfig.defaultConfig().apiKey().isEmpty()) {
            configProblems.add(createApiKeyConfigProblem(configName));
        }
        if (watsonConfig.projectId().isEmpty() && runtimeConfig.defaultConfig().projectId().isEmpty() &&
                watsonConfig.spaceId().isEmpty() && runtimeConfig.defaultConfig().spaceId().isEmpty()) {
            var config = NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + ".");
            var errorMessage = "One of the properties quarkus.langchain4j.watsonx%s%s / quarkus.langchain4j.watsonx%s%s is required, but could not be found in any config source";
            configProblems.add(new ConfigValidationException.Problem(
                    String.format(errorMessage, config, "project-id", config, "space-id")));
        }

        return configProblems;
    }

    private ConfigValidationException.Problem createBaseURLConfigProblem(String configName) {
        return createConfigProblem("base-url", configName);
    }

    private ConfigValidationException.Problem createApiKeyConfigProblem(String configName) {
        return createConfigProblem("api-key", configName);
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.watsonx%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
