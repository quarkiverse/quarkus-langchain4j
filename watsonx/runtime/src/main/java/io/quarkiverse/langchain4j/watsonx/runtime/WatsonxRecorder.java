package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
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
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.TokenGenerator;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxEmbeddingModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxStreamingChatModel;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig.LengthPenaltyConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.IAMConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class WatsonxRecorder {

    private static final String DUMMY_URL = "https://dummy.ai/api";
    private static final String DUMMY_API_KEY = "dummy";
    private static final String DUMMY_PROJECT_ID = "dummy";
    private static final Map<String, TokenGenerator> tokenGeneratorCache = new HashMap<>();
    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    public Supplier<ChatLanguageModel> chatModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonConfig(runtimeConfig, configName);

        if (watsonConfig.enableIntegration()) {

            var builder = generateChatBuilder(watsonConfig, configName);

            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build(WatsonxChatModel.class);
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

    public Supplier<StreamingChatLanguageModel> streamingChatModel(LangChain4jWatsonxConfig runtimeConfig, String configName) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonConfig(runtimeConfig, configName);

        if (watsonConfig.enableIntegration()) {

            var builder = generateChatBuilder(watsonConfig, configName);

            return new Supplier<>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return builder.build(WatsonxStreamingChatModel.class);
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
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = correspondingWatsonConfig(runtimeConfig, configName);

        if (watsonConfig.enableIntegration()) {
            var configProblems = checkConfigurations(watsonConfig, configName);

            if (!configProblems.isEmpty()) {
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
            }

            String iamUrl = watsonConfig.iam().baseUrl().toExternalForm();
            TokenGenerator tokenGenerator = tokenGeneratorCache.computeIfAbsent(iamUrl,
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
                    .timeout(watsonConfig.timeout())
                    .logRequests(embeddingModelConfig.logRequests().orElse(false))
                    .logResponses(embeddingModelConfig.logResponses().orElse(false))
                    .version(watsonConfig.version())
                    .projectId(watsonConfig.projectId())
                    .modelId(embeddingModelConfig.modelId());

            return new Supplier<>() {
                @Override
                public WatsonxEmbeddingModel get() {
                    return builder.build(WatsonxEmbeddingModel.class);
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

    private Function<? super String, ? extends TokenGenerator> createTokenGenerator(IAMConfig iamConfig, String apiKey) {
        return new Function<String, TokenGenerator>() {

            @Override
            public TokenGenerator apply(String iamUrl) {
                return new TokenGenerator(iamConfig.baseUrl(), iamConfig.timeout(), iamConfig.grantType(), apiKey);
            }
        };
    }

    private WatsonxModel.Builder generateChatBuilder(LangChain4jWatsonxConfig.WatsonConfig watsonConfig, String configName) {

        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        var configProblems = checkConfigurations(watsonConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        String iamUrl = watsonConfig.iam().baseUrl().toExternalForm();
        TokenGenerator tokenGenerator = tokenGeneratorCache.computeIfAbsent(iamUrl,
                createTokenGenerator(watsonConfig.iam(), watsonConfig.apiKey()));

        URL url;
        try {
            url = new URL(watsonConfig.baseUrl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Double decayFactor = null;
        Integer startIndex = null;

        if (chatModelConfig.lengthPenalty().isPresent()) {
            decayFactor = chatModelConfig.lengthPenalty().map(LengthPenaltyConfig::decayFactor).get().orElse(null);
            startIndex = chatModelConfig.lengthPenalty().map(LengthPenaltyConfig::startIndex).get().orElse(null);
        }

        return WatsonxChatModel.builder()
                .tokenGenerator(tokenGenerator)
                .url(url)
                .timeout(watsonConfig.timeout())
                .logRequests(chatModelConfig.logRequests().orElse(false))
                .logResponses(chatModelConfig.logResponses().orElse(false))
                .version(watsonConfig.version())
                .projectId(watsonConfig.projectId())
                .modelId(chatModelConfig.modelId())
                .decodingMethod(chatModelConfig.decodingMethod())
                .decayFactor(decayFactor)
                .startIndex(startIndex)
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .minNewTokens(chatModelConfig.minNewTokens())
                .temperature(chatModelConfig.temperature())
                .randomSeed(firstOrDefault(null, chatModelConfig.randomSeed()))
                .stopSequences(firstOrDefault(null, chatModelConfig.stopSequences()))
                .topK(firstOrDefault(null, chatModelConfig.topK()))
                .topP(firstOrDefault(null, chatModelConfig.topP()))
                .repetitionPenalty(firstOrDefault(null, chatModelConfig.repetitionPenalty()))
                .truncateInputTokens(chatModelConfig.truncateInputTokens().orElse(null))
                .includeStopSequence(chatModelConfig.includeStopSequence().orElse(null));
    }

    private LangChain4jWatsonxConfig.WatsonConfig correspondingWatsonConfig(LangChain4jWatsonxConfig runtimeConfig,
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
