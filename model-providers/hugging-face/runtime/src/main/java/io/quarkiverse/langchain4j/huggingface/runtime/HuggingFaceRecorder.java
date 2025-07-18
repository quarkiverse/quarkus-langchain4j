package io.quarkiverse.langchain4j.huggingface.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
import java.time.Duration;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceChatModel;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceEmbeddingModel;
import io.quarkiverse.langchain4j.huggingface.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.LangChain4jHuggingFaceConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class HuggingFaceRecorder {

    private static final String DUMMY_KEY = "dummy";
    private static final String HUGGING_FACE_URL_MARKER = "api-inference.huggingface.co";

    private final RuntimeValue<LangChain4jHuggingFaceConfig> runtimeConfig;

    public HuggingFaceRecorder(RuntimeValue<LangChain4jHuggingFaceConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ChatModel> chatModel(String configName) {
        LangChain4jHuggingFaceConfig.HuggingFaceConfig huggingFaceConfig = correspondingHuggingFaceConfig(
                runtimeConfig.getValue(), configName);

        if (huggingFaceConfig.enableIntegration()) {
            String apiKey = huggingFaceConfig.apiKey();
            ChatModelConfig chatModelConfig = huggingFaceConfig.chatModel();
            URL url = chatModelConfig.inferenceEndpointUrl();

            if (DUMMY_KEY.equals(apiKey) && url.toExternalForm().contains(HUGGING_FACE_URL_MARKER)) { // when using the default base URL an API key is required
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = QuarkusHuggingFaceChatModel.builder()
                    .url(url)
                    .timeout(huggingFaceConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .temperature(chatModelConfig.temperature())
                    .waitForModel(chatModelConfig.waitForModel())
                    .doSample(chatModelConfig.doSample())
                    .topP(chatModelConfig.topP())
                    .topK(chatModelConfig.topK())
                    .repetitionPenalty(chatModelConfig.repetitionPenalty())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), huggingFaceConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), huggingFaceConfig.logResponses()))
                    .returnFullText(chatModelConfig.returnFullText());

            if (!DUMMY_KEY.equals(apiKey)) {
                builder.accessToken(apiKey);
            }

            if (chatModelConfig.maxNewTokens().isPresent()) {
                builder.maxNewTokens(chatModelConfig.maxNewTokens().get());
            }

            return new Supplier<>() {
                @Override
                public ChatModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ChatModel get() {
                    return new DisabledChatModel();
                }
            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(String configName) {
        LangChain4jHuggingFaceConfig.HuggingFaceConfig huggingFaceConfig = correspondingHuggingFaceConfig(
                runtimeConfig.getValue(), configName);

        if (huggingFaceConfig.enableIntegration()) {
            String apiKey = huggingFaceConfig.apiKey();
            EmbeddingModelConfig embeddingModelConfig = huggingFaceConfig.embeddingModel();
            URL url = embeddingModelConfig.inferenceEndpointUrl();

            if (DUMMY_KEY.equals(apiKey) && url.toExternalForm().contains(HUGGING_FACE_URL_MARKER)) { // when using the default base URL an API key is required
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = QuarkusHuggingFaceEmbeddingModel.builder()
                    .url(url)
                    .timeout(huggingFaceConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .waitForModel(embeddingModelConfig.waitForModel());

            if (!DUMMY_KEY.equals(apiKey)) {
                builder.accessToken(apiKey);
            }

            return new Supplier<>() {
                @Override
                public EmbeddingModel get() {
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

    private LangChain4jHuggingFaceConfig.HuggingFaceConfig correspondingHuggingFaceConfig(
            LangChain4jHuggingFaceConfig runtimeConfig, String configName) {
        LangChain4jHuggingFaceConfig.HuggingFaceConfig huggingFaceConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            huggingFaceConfig = runtimeConfig.defaultConfig();
        } else {
            huggingFaceConfig = runtimeConfig.namedConfig().get(configName);
        }
        return huggingFaceConfig;
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblem(String configName) {
        return createConfigProblems("api-key", configName);
    }

    private ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.huggingface%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
