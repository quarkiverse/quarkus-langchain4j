package io.quarkiverse.langchain4j.huggingface.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceChatModel;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceEmbeddingModel;
import io.quarkiverse.langchain4j.huggingface.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.Langchain4jHuggingFaceConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class HuggingFaceRecorder {

    private static final String DUMMY_KEY = "dummy";
    private static final String HUGGING_FACE_URL_MARKER = "api-inference.huggingface.co";

    public Supplier<?> chatModel(Langchain4jHuggingFaceConfig runtimeConfig, String modelName) {
        Langchain4jHuggingFaceConfig.HuggingFaceConfig huggingFaceConfig = correspondingHuggingFaceConfig(runtimeConfig,
                modelName);
        String apiKey = huggingFaceConfig.apiKey();
        ChatModelConfig chatModelConfig = huggingFaceConfig.chatModel();
        URL url = chatModelConfig.inferenceEndpointUrl();

        if (DUMMY_KEY.equals(apiKey) && url.toExternalForm().contains(HUGGING_FACE_URL_MARKER)) { // when using the default base URL an API key is required
            throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
        }

        var builder = QuarkusHuggingFaceChatModel.builder()
                .url(url)
                .timeout(huggingFaceConfig.timeout())
                .temperature(chatModelConfig.temperature())
                .waitForModel(chatModelConfig.waitForModel())
                .doSample(chatModelConfig.doSample())
                .topP(chatModelConfig.topP())
                .topK(chatModelConfig.topK())
                .repetitionPenalty(chatModelConfig.repetitionPenalty())
                .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), huggingFaceConfig.logRequests()))
                .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), huggingFaceConfig.logResponses()));

        if (!DUMMY_KEY.equals(apiKey)) {
            builder.accessToken(apiKey);
        }
        if (chatModelConfig.returnFullText().isPresent()) {
            builder.returnFullText(chatModelConfig.returnFullText().get());
        }

        if (chatModelConfig.maxNewTokens().isPresent()) {
            builder.maxNewTokens(chatModelConfig.maxNewTokens().get());
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    public Supplier<?> embeddingModel(Langchain4jHuggingFaceConfig runtimeConfig, String modelName) {
        Langchain4jHuggingFaceConfig.HuggingFaceConfig huggingFaceConfig = correspondingHuggingFaceConfig(runtimeConfig,
                modelName);
        String apiKey = huggingFaceConfig.apiKey();
        EmbeddingModelConfig embeddingModelConfig = huggingFaceConfig.embeddingModel();
        URL url = embeddingModelConfig.inferenceEndpointUrl();

        if (DUMMY_KEY.equals(apiKey) && url.toExternalForm().contains(HUGGING_FACE_URL_MARKER)) { // when using the default base URL an API key is required
            throw new ConfigValidationException(createApiKeyConfigProblem(modelName));
        }

        var builder = QuarkusHuggingFaceEmbeddingModel.builder()
                .url(url)
                .timeout(huggingFaceConfig.timeout())
                .waitForModel(embeddingModelConfig.waitForModel());

        if (!DUMMY_KEY.equals(apiKey)) {
            builder.accessToken(apiKey);
        }

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private Langchain4jHuggingFaceConfig.HuggingFaceConfig correspondingHuggingFaceConfig(
            Langchain4jHuggingFaceConfig runtimeConfig, String modelName) {
        Langchain4jHuggingFaceConfig.HuggingFaceConfig huggingFaceConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            huggingFaceConfig = runtimeConfig.defaultConfig();
        } else {
            huggingFaceConfig = runtimeConfig.namedConfig().get(modelName).huggingFace();
        }
        return huggingFaceConfig;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblem(String modelName) {
        return createConfigProblems("api-key", modelName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String modelName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, modelName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j%shuggingface.%s is required but it could not be found in any config source",
                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
    }
}
