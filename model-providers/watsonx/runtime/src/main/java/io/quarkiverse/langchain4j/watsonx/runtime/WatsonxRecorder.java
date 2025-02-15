package io.quarkiverse.langchain4j.watsonx.runtime;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
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
import io.quarkiverse.langchain4j.watsonx.WatsonxScoringModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxTokenGenerator;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GenerationModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.IAMConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ScoringModelConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class WatsonxRecorder {

    private static final Map<String, WatsonxTokenGenerator> tokenGeneratorCache = new HashMap<>();
    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];
    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public Function<SyntheticCreationalContext<ChatLanguageModel>, ChatLanguageModel> chatModel(
            LangChain4jWatsonxConfig runtimeConfig, String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(null, watsonRuntimeConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = chatBuilder(runtimeConfig, configName);

            return new Function<>() {
                @Override
                public ChatLanguageModel apply(SyntheticCreationalContext<ChatLanguageModel> context) {
                    return builder
                            .tokenGenerator(createTokenGenerator(watsonRuntimeConfig.iam(), apiKey))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
                }
            };

        } else {
            return new Function<>() {
                @Override
                public ChatLanguageModel apply(SyntheticCreationalContext<ChatLanguageModel> context) {
                    return new DisabledChatLanguageModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<StreamingChatLanguageModel>, StreamingChatLanguageModel> streamingChatModel(
            LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(null, watsonRuntimeConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = chatBuilder(runtimeConfig, configName);

            return new Function<>() {
                @Override
                public StreamingChatLanguageModel apply(SyntheticCreationalContext<StreamingChatLanguageModel> context) {
                    return builder
                            .tokenGenerator(createTokenGenerator(watsonRuntimeConfig.iam(), apiKey))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
                }
            };

        } else {
            return new Function<>() {
                @Override
                public StreamingChatLanguageModel apply(SyntheticCreationalContext<StreamingChatLanguageModel> context) {
                    return new DisabledStreamingChatLanguageModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<ChatLanguageModel>, ChatLanguageModel> generationModel(
            LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(null, watsonRuntimeConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = generationBuilder(runtimeConfig, configName);
            return new Function<>() {
                @Override
                public ChatLanguageModel apply(SyntheticCreationalContext<ChatLanguageModel> context) {
                    return builder
                            .tokenGenerator(createTokenGenerator(watsonRuntimeConfig.iam(), apiKey))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
                }
            };

        } else {
            return new Function<>() {
                @Override
                public ChatLanguageModel apply(SyntheticCreationalContext<ChatLanguageModel> context) {
                    return new DisabledChatLanguageModel();
                }

            };
        }
    }

    public Function<SyntheticCreationalContext<StreamingChatLanguageModel>, StreamingChatLanguageModel> generationStreamingModel(
            LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);
        String apiKey = firstOrDefault(null, watsonRuntimeConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = generationBuilder(runtimeConfig, configName);
            return new Function<>() {
                @Override
                public StreamingChatLanguageModel apply(SyntheticCreationalContext<StreamingChatLanguageModel> context) {
                    return builder
                            .tokenGenerator(createTokenGenerator(watsonRuntimeConfig.iam(), apiKey))
                            .listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream().toList())
                            .build();
                }
            };

        } else {
            return new Function<>() {
                @Override
                public StreamingChatLanguageModel apply(SyntheticCreationalContext<StreamingChatLanguageModel> context) {
                    return new DisabledStreamingChatLanguageModel();
                }

            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonConfig.enableIntegration()) {
            var configProblems = checkConfigurations(runtimeConfig, configName);

            if (!configProblems.isEmpty()) {
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
            }

            String apiKey = firstOrDefault(null, watsonConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

            URL url;
            try {
                url = new URL(firstOrDefault(null, watsonConfig.baseUrl(), runtimeConfig.defaultConfig().baseUrl()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            EmbeddingModelConfig embeddingModelConfig = watsonConfig.embeddingModel();
            var builder = WatsonxEmbeddingModel.builder()
                    .url(url)
                    .timeout(watsonConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), watsonConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), watsonConfig.logResponses()))
                    .version(watsonConfig.version())
                    .spaceId(firstOrDefault(null, watsonConfig.spaceId(), runtimeConfig.defaultConfig().spaceId()))
                    .projectId(firstOrDefault(null, watsonConfig.projectId(), runtimeConfig.defaultConfig().projectId()))
                    .modelId(embeddingModelConfig.modelId())
                    .truncateInputTokens(embeddingModelConfig.truncateInputTokens().orElse(null));

            return new Supplier<>() {
                @Override
                public WatsonxEmbeddingModel get() {
                    return builder
                            .tokenGenerator(createTokenGenerator(watsonConfig.iam(), apiKey))
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
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String apiKey = firstOrDefault(null, watsonConfig.apiKey(), runtimeConfig.defaultConfig().apiKey());

        URL url;
        try {
            url = new URL(firstOrDefault(null, watsonConfig.baseUrl(), runtimeConfig.defaultConfig().baseUrl()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ScoringModelConfig rerankModelConfig = watsonConfig.scoringModel();
        var builder = WatsonxScoringModel.builder()
                .url(url)
                .timeout(watsonConfig.timeout().orElse(Duration.ofSeconds(10)))
                .logRequests(firstOrDefault(false, rerankModelConfig.logRequests(), watsonConfig.logRequests()))
                .logResponses(firstOrDefault(false, rerankModelConfig.logResponses(), watsonConfig.logResponses()))
                .version(watsonConfig.version())
                .spaceId(firstOrDefault(null, watsonConfig.spaceId(), runtimeConfig.defaultConfig().spaceId()))
                .projectId(firstOrDefault(null, watsonConfig.projectId(), runtimeConfig.defaultConfig().projectId()))
                .modelId(rerankModelConfig.modelId())
                .truncateInputTokens(rerankModelConfig.truncateInputTokens().orElse(null));

        return new Supplier<>() {
            @Override
            public WatsonxScoringModel get() {
                return builder
                        .tokenGenerator(createTokenGenerator(watsonConfig.iam(), apiKey))
                        .build();
            }
        };
    }

    private WatsonxChatModel.Builder chatBuilder(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        ChatModelConfig chatModelConfig = watsonConfig.chatModel();

        URL url;
        try {
            url = new URL(firstOrDefault(null, watsonConfig.baseUrl(), runtimeConfig.defaultConfig().baseUrl()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ToolChoice toolChoice = null;
        String toolChoiceName = null;
        if (chatModelConfig.toolChoice().isPresent() && !chatModelConfig.toolChoice().get().isBlank()) {
            toolChoice = REQUIRED;
            toolChoiceName = chatModelConfig.toolChoice().get();
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

        return WatsonxChatModel.builder()
                .url(url)
                .timeout(watsonConfig.timeout().orElse(Duration.ofSeconds(10)))
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), watsonConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), watsonConfig.logResponses()))
                .version(watsonConfig.version())
                .spaceId(firstOrDefault(null, watsonConfig.spaceId(), runtimeConfig.defaultConfig().spaceId()))
                .projectId(firstOrDefault(null, watsonConfig.projectId(), runtimeConfig.defaultConfig().projectId()))
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

    private WatsonxGenerationModel.Builder generationBuilder(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        var configProblems = checkConfigurations(runtimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        GenerationModelConfig generationModelConfig = watsonConfig.generationModel();

        URL url;
        try {
            url = new URL(firstOrDefault(null, watsonConfig.baseUrl(), runtimeConfig.defaultConfig().baseUrl()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Double decayFactor = generationModelConfig.lengthPenalty().decayFactor().orElse(null);
        Integer startIndex = generationModelConfig.lengthPenalty().startIndex().orElse(null);
        String promptJoiner = generationModelConfig.promptJoiner();

        return WatsonxGenerationModel.builder()
                .url(url)
                .timeout(watsonConfig.timeout().orElse(Duration.ofSeconds(10)))
                .logRequests(firstOrDefault(false, generationModelConfig.logRequests(), watsonConfig.logRequests()))
                .logResponses(firstOrDefault(false, generationModelConfig.logResponses(), watsonConfig.logResponses()))
                .version(watsonConfig.version())
                .spaceId(firstOrDefault(null, watsonConfig.spaceId(), runtimeConfig.defaultConfig().spaceId()))
                .projectId(firstOrDefault(null, watsonConfig.projectId(), runtimeConfig.defaultConfig().projectId()))
                .modelId(watsonConfig.generationModel().modelId())
                .decodingMethod(generationModelConfig.decodingMethod())
                .decayFactor(decayFactor)
                .startIndex(startIndex)
                .maxNewTokens(generationModelConfig.maxNewTokens())
                .minNewTokens(generationModelConfig.minNewTokens())
                .temperature(generationModelConfig.temperature())
                .randomSeed(firstOrDefault(null, generationModelConfig.randomSeed()))
                .stopSequences(firstOrDefault(null, generationModelConfig.stopSequences()))
                .topK(firstOrDefault(null, generationModelConfig.topK()))
                .topP(firstOrDefault(null, generationModelConfig.topP()))
                .repetitionPenalty(firstOrDefault(null, generationModelConfig.repetitionPenalty()))
                .truncateInputTokens(generationModelConfig.truncateInputTokens().orElse(null))
                .includeStopSequence(generationModelConfig.includeStopSequence().orElse(null))
                .promptJoiner(promptJoiner);
    }

    private WatsonxTokenGenerator createTokenGenerator(IAMConfig iamConfig, String apiKey) {
        return tokenGeneratorCache.computeIfAbsent(apiKey,
                new Function<String, WatsonxTokenGenerator>() {
                    @Override
                    public WatsonxTokenGenerator apply(String apiKey) {
                        return new WatsonxTokenGenerator(iamConfig.baseUrl(),
                                iamConfig.timeout().orElse(Duration.ofSeconds(10)),
                                iamConfig.grantType(), apiKey);
                    }
                });
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
