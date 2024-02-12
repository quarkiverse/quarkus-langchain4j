package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkiverse.langchain4j.watsonx.TokenGenerator;
import io.quarkiverse.langchain4j.watsonx.WatsonChatModel;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.IAMConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.Langchain4jWatsonConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class WatsonRecorder {

    private static final String DUMMY_URL = "https://dummy.ai/api";
    private static final String DUMMY_API_KEY = "dummy";
    private static final String DUMMY_PROJECT_ID = "dummy";
    public static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    public Supplier<ChatLanguageModel> chatModel(Langchain4jWatsonConfig runtimeConfig, String modelName) {
        Langchain4jWatsonConfig.WatsonConfig watsonConfig = correspondingWatsonConfig(runtimeConfig, modelName);

        if (watsonConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = watsonConfig.chatModel();

            List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
            URL baseUrl = watsonConfig.baseUrl();
            if (DUMMY_URL.equals(baseUrl.toString())) {
                configProblems.add(createBaseURLConfigProblem(modelName));
            }
            String apiKey = watsonConfig.apiKey();
            if (DUMMY_API_KEY.equals(apiKey)) {
                configProblems.add(createApiKeyConfigProblem(modelName));
            }
            String projectId = watsonConfig.projectId();
            if (DUMMY_PROJECT_ID.equals(projectId)) {
                configProblems.add(createProjectIdProblem(modelName));
            }

            if (!configProblems.isEmpty()) {
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
            }

            IAMConfig iamConfig = watsonConfig.iam();
            var tokenGenerator = new TokenGenerator(iamConfig.baseUrl(), iamConfig.timeout(), iamConfig.grantType(),
                    watsonConfig.apiKey());

            var builder = WatsonChatModel.builder()
                    .tokenGenerator(tokenGenerator)
                    .url(baseUrl)
                    .timeout(watsonConfig.timeout())
                    .logRequests(watsonConfig.logRequests())
                    .logResponses(watsonConfig.logResponses())
                    .version(watsonConfig.version())
                    .projectId(projectId)
                    .modelId(chatModelConfig.modelId())
                    .decodingMethod(chatModelConfig.decodingMethod())
                    .minNewTokens(chatModelConfig.minNewTokens())
                    .maxNewTokens(chatModelConfig.maxNewTokens())
                    .temperature(chatModelConfig.temperature())
                    .randomSeed(firstOrDefault(null, chatModelConfig.randomSeed()))
                    .stopSequences(firstOrDefault(null, chatModelConfig.stopSequences()))
                    .topK(firstOrDefault(null, chatModelConfig.topK()))
                    .topP(firstOrDefault(null, chatModelConfig.topP()))
                    .repetitionPenalty(firstOrDefault(null, chatModelConfig.repetitionPenalty()));

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

    private Langchain4jWatsonConfig.WatsonConfig correspondingWatsonConfig(Langchain4jWatsonConfig runtimeConfig,
            String modelName) {
        Langchain4jWatsonConfig.WatsonConfig watsonConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            watsonConfig = runtimeConfig.defaultConfig();
        } else {
            watsonConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return watsonConfig;
    }

    private ConfigValidationException.Problem createBaseURLConfigProblem(String modelName) {
        return createConfigProblem("base-url", modelName);
    }

    private ConfigValidationException.Problem createApiKeyConfigProblem(String modelName) {
        return createConfigProblem("api-key", modelName);
    }

    private ConfigValidationException.Problem createProjectIdProblem(String modelName) {
        return createConfigProblem("project-id", modelName);
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.watsonx%s%s is required but it could not be found in any config source",
                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
    }
}
