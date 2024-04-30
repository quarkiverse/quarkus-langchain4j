package io.quarkiverse.langchain4j.vertexai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkiverse.langchain4j.vertexai.runtime.config.LangChain4jVertexAiConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class VertexAiRecorder {
    private static final String DUMMY_KEY = "dummy";

    public Supplier<ChatLanguageModel> chatModel(LangChain4jVertexAiConfig config, String modelName) {
        var vertexAiConfig = correspondingVertexAiConfig(config, modelName);

        if (vertexAiConfig.enableIntegration()) {
            var chatModelConfig = vertexAiConfig.chatModel();

            String location = vertexAiConfig.location();
            if (DUMMY_KEY.equals(location)) {
                throw new ConfigValidationException(createConfigProblems("location", modelName));
            }
            String projectId = vertexAiConfig.projectId();
            if (DUMMY_KEY.equals(projectId)) {
                throw new ConfigValidationException(createConfigProblems("project-id", modelName));
            }
            var builder = VertexAiChatLanguageModel.builder()
                    .location(location)
                    .projectId(projectId)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .temperature(chatModelConfig.temperature())
                    .topK(chatModelConfig.topK())
                    .topP(chatModelConfig.topP())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), vertexAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), vertexAiConfig.logResponses()));

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

    private LangChain4jVertexAiConfig.VertexAiConfig correspondingVertexAiConfig(
            LangChain4jVertexAiConfig runtimeConfig, String modelName) {

        return NamedModelUtil.isDefault(modelName) ? runtimeConfig.defaultConfig() : runtimeConfig.namedConfig().get(modelName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String modelName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, modelName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.vertexai%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
    }
}
