package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.vertexai.runtime.gemini.config.LangChain4jVertexAiGeminiConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class VertexAiGeminiRecorder {
    private static final String DUMMY_KEY = "dummy";

    public Supplier<ChatLanguageModel> chatModel(LangChain4jVertexAiGeminiConfig config, String configName) {
        var vertexAiConfig = correspondingVertexAiConfig(config, configName);

        if (vertexAiConfig.enableIntegration()) {
            var chatModelConfig = vertexAiConfig.chatModel();
            Optional<String> baseUrl = vertexAiConfig.baseUrl();

            String location = vertexAiConfig.location();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(location)) {
                throw new ConfigValidationException(createConfigProblems("location", configName));
            }
            String projectId = vertexAiConfig.projectId();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(projectId)) {
                throw new ConfigValidationException(createConfigProblems("project-id", configName));
            }
            var builder = VertexAiGeminiChatLanguageModel.builder()
                    .baseUrl(baseUrl)
                    .location(location)
                    .projectId(projectId)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false));

            if (chatModelConfig.temperature().isEmpty()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.timeout().isPresent()) {
                builder.timeout(chatModelConfig.timeout().get());
            }

            // TODO: add the rest of the properties

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

    private LangChain4jVertexAiGeminiConfig.VertexAiGeminiConfig correspondingVertexAiConfig(
            LangChain4jVertexAiGeminiConfig runtimeConfig, String configName) {

        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.defaultConfig()
                : runtimeConfig.namedConfig().get(configName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.vertexai.gemini%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
