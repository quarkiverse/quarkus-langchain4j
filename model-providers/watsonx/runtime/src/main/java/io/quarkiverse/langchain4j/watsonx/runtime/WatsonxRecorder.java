package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
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
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class WatsonxRecorder {

    private static final String DUMMY_URL = "https://dummy.ai/api";
    private static final String DUMMY_API_KEY = "dummy";
    private static final String DUMMY_PROJECT_ID = "dummy";
    private static final Map<String, WatsonxTokenGenerator> tokenGeneratorCache = new HashMap<>();
    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    public Supplier<ChatLanguageModel> chatModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = chatBuilder(watsonRuntimeConfig, configName);
            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build();
                }
            };

        } else {
            return new Supplier<>() {

                @Override
                public ChatLanguageModel get() {
                    return new DisabledChatLanguageModel();
                }

            };
        }
    }

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = chatBuilder(watsonRuntimeConfig, configName);
            return new Supplier<>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return builder.build();
                }
            };

        } else {
            return new Supplier<>() {

                @Override
                public StreamingChatLanguageModel get() {
                    return new DisabledStreamingChatLanguageModel();
                }

            };
        }
    }

    public Supplier<ChatLanguageModel> generationModel(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = generationBuilder(watsonRuntimeConfig, configName);
            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build();
                }
            };

        } else {
            return new Supplier<>() {

                @Override
                public ChatLanguageModel get() {
                    return new DisabledChatLanguageModel();
                }

            };
        }
    }

    public Supplier<StreamingChatLanguageModel> generationStreamingModel(LangChain4jWatsonxConfig runtimeConfig,
            String configName) {

        LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonRuntimeConfig.enableIntegration()) {

            var builder = generationBuilder(watsonRuntimeConfig, configName);
            return new Supplier<>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return builder.build();
                }
            };

        } else {
            return new Supplier<>() {

                @Override
                public StreamingChatLanguageModel get() {
                    return new DisabledStreamingChatLanguageModel();
                }

            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonRuntimeConfig(runtimeConfig, configName);

        if (watsonConfig.enableIntegration()) {
            var configProblems = checkConfigurations(watsonConfig, configName);

            if (!configProblems.isEmpty()) {
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
            }

            String iamUrl = watsonConfig.iam().baseUrl().toExternalForm();
            WatsonxTokenGenerator tokenGenerator = tokenGeneratorCache.computeIfAbsent(iamUrl,
                    createTokenGenerator(watsonConfig.iam(), watsonConfig.apiKey()));

            URL url;
            try {
                url = new URL(watsonConfig.baseUrl());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            EmbeddingModelConfig embeddingModelConfig = watsonConfig.embeddingModel();
            var builder = WatsonxEmbeddingModel.builder()
                    .tokenGenerator(tokenGenerator)
                    .url(url)
                    .timeout(watsonConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), watsonConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), watsonConfig.logResponses()))
                    .version(watsonConfig.version())
                    .projectId(watsonConfig.projectId())
                    .modelId(embeddingModelConfig.modelId())
                    .truncateInputTokens(embeddingModelConfig.truncateInputTokens().orElse(null));

            return new Supplier<>() {
                @Override
                public WatsonxEmbeddingModel get() {
                    return builder.build();
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

        var configProblems = checkConfigurations(watsonConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String iamUrl = watsonConfig.iam().baseUrl().toExternalForm();
        WatsonxTokenGenerator tokenGenerator = tokenGeneratorCache.computeIfAbsent(iamUrl,
                createTokenGenerator(watsonConfig.iam(), watsonConfig.apiKey()));

        URL url;
        try {
            url = new URL(watsonConfig.baseUrl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ScoringModelConfig rerankModelConfig = watsonConfig.scoringModel();
        var builder = WatsonxScoringModel.builder()
                .tokenGenerator(tokenGenerator)
                .url(url)
                .timeout(watsonConfig.timeout().orElse(Duration.ofSeconds(10)))
                .logRequests(firstOrDefault(false, rerankModelConfig.logRequests(), watsonConfig.logRequests()))
                .logResponses(firstOrDefault(false, rerankModelConfig.logResponses(), watsonConfig.logResponses()))
                .version(watsonConfig.version())
                .projectId(watsonConfig.projectId())
                .modelId(rerankModelConfig.modelId())
                .truncateInputTokens(rerankModelConfig.truncateInputTokens().orElse(null));

        return new Supplier<>() {
            @Override
            public WatsonxScoringModel get() {
                return builder.build();
            }
        };
    }

    private Function<? super String, ? extends WatsonxTokenGenerator> createTokenGenerator(IAMConfig iamConfig, String apiKey) {
        return new Function<String, WatsonxTokenGenerator>() {

            @Override
            public WatsonxTokenGenerator apply(String iamUrl) {
                return new WatsonxTokenGenerator(iamConfig.baseUrl(), iamConfig.timeout().orElse(Duration.ofSeconds(10)),
                        iamConfig.grantType(), apiKey);
            }
        };
    }

    private WatsonxChatModel.Builder chatBuilder(
            LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig,
            String configName) {

        ChatModelConfig chatModelConfig = watsonRuntimeConfig.chatModel();
        var configProblems = checkConfigurations(watsonRuntimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String iamUrl = watsonRuntimeConfig.iam().baseUrl().toExternalForm();
        WatsonxTokenGenerator tokenGenerator = tokenGeneratorCache.computeIfAbsent(iamUrl,
                createTokenGenerator(watsonRuntimeConfig.iam(), watsonRuntimeConfig.apiKey()));

        URL url;
        try {
            url = new URL(watsonRuntimeConfig.baseUrl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return WatsonxChatModel.builder()
                .tokenGenerator(tokenGenerator)
                .url(url)
                .timeout(watsonRuntimeConfig.timeout().orElse(Duration.ofSeconds(10)))
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), watsonRuntimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), watsonRuntimeConfig.logResponses()))
                .version(watsonRuntimeConfig.version())
                .projectId(watsonRuntimeConfig.projectId())
                .modelId(watsonRuntimeConfig.generationModel().modelId())
                .frequencyPenalty(chatModelConfig.frequencyPenalty())
                .logprobs(chatModelConfig.logprobs())
                .topLogprobs(chatModelConfig.topLogprobs().orElse(null))
                .maxTokens(chatModelConfig.maxTokens())
                .n(chatModelConfig.n())
                .presencePenalty(chatModelConfig.presencePenalty())
                .temperature(chatModelConfig.temperature())
                .topP(chatModelConfig.topP())
                .responseFormat(chatModelConfig.responseFormat().orElse(null));
    }

    private WatsonxGenerationModel.Builder generationBuilder(
            LangChain4jWatsonxConfig.WatsonConfig watsonRuntimeConfig,
            String configName) {

        GenerationModelConfig generationModelConfig = watsonRuntimeConfig.generationModel();
        var configProblems = checkConfigurations(watsonRuntimeConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String iamUrl = watsonRuntimeConfig.iam().baseUrl().toExternalForm();
        WatsonxTokenGenerator tokenGenerator = tokenGeneratorCache.computeIfAbsent(iamUrl,
                createTokenGenerator(watsonRuntimeConfig.iam(), watsonRuntimeConfig.apiKey()));

        URL url;
        try {
            url = new URL(watsonRuntimeConfig.baseUrl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Double decayFactor = generationModelConfig.lengthPenalty().decayFactor().orElse(null);
        Integer startIndex = generationModelConfig.lengthPenalty().startIndex().orElse(null);
        String promptJoiner = generationModelConfig.promptJoiner();

        return WatsonxGenerationModel.builder()
                .tokenGenerator(tokenGenerator)
                .url(url)
                .timeout(watsonRuntimeConfig.timeout().orElse(Duration.ofSeconds(10)))
                .logRequests(firstOrDefault(false, generationModelConfig.logRequests(), watsonRuntimeConfig.logRequests()))
                .logResponses(firstOrDefault(false, generationModelConfig.logResponses(), watsonRuntimeConfig.logResponses()))
                .version(watsonRuntimeConfig.version())
                .projectId(watsonRuntimeConfig.projectId())
                .modelId(watsonRuntimeConfig.generationModel().modelId())
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

    private List<ConfigValidationException.Problem> checkConfigurations(LangChain4jWatsonxConfig.WatsonConfig watsonConfig,
            String configName) {
        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();

        if (DUMMY_URL.equals(watsonConfig.baseUrl())) {
            configProblems.add(createBaseURLConfigProblem(configName));
        }
        String apiKey = watsonConfig.apiKey();
        if (DUMMY_API_KEY.equals(apiKey)) {
            configProblems.add(createApiKeyConfigProblem(configName));
        }
        String projectId = watsonConfig.projectId();
        if (DUMMY_PROJECT_ID.equals(projectId)) {
            configProblems.add(createProjectIdProblem(configName));
        }

        return configProblems;
    }

    private ConfigValidationException.Problem createBaseURLConfigProblem(String configName) {
        return createConfigProblem("base-url", configName);
    }

    private ConfigValidationException.Problem createApiKeyConfigProblem(String configName) {
        return createConfigProblem("api-key", configName);
    }

    private ConfigValidationException.Problem createProjectIdProblem(String configName) {
        return createConfigProblem("project-id", configName);
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.watsonx%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
